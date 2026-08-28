package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;

/** Separate-process producer/consumer for one fixed detached Artifact payload. */
public final class Phase2DTransportVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase2d.transport.";
    private static final int MAGIC = 0x32444152;

    private Phase2DTransportVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "mode");
        if (mode == null || mode.isBlank()) return;
        if (mode.equals("produce")) produce(server);
        else if (mode.equals("consume")) consume(server);
        else throw new IllegalArgumentException("Unknown Phase 2D transport mode " + mode);
        server.execute(() -> server.halt(false));
    }

    private static void produce(MinecraftServer server) {
        long masterSeed = Long.parseLong(require("masterSeed"));
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse(require("dimension")));
        ChunkPos target = new ChunkPos(
                Integer.parseInt(require("chunkX")), Integer.parseInt(require("chunkZ")));
        ServerLevel level = requireLevel(server, dimension);
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        long localSeed = new MosaicSeedResolver(profile)
                .resolveLocalWorldSeed(masterSeed, dimension, target);
        CanonicalChunkArtifact artifact;
        String semanticHash;
        try (IsolatedGenerationContext context = IsolatedGenerationContext.create(
                IsolatedGenerationMode.ISOLATED_MOSAIC, level, localSeed, target)) {
            var run = context.generateFeaturesStable();
            semanticHash = FeatureStableSnapshot.capture(
                    dimension.identifier().toString(), run.targetChunk(), level.registryAccess()).hash();
            artifact = CanonicalChunkArtifact.capture(
                    run, dimension.identifier().toString(), localSeed, profile,
                    level.registryAccess(), StructurePieceSerializationContext.fromLevel(level));
        }
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Transport producer retained generation context");
        }
        writeEnvelope(Path.of(require("artifact")), artifact, semanticHash);
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("mode", "produce");
        result.addProperty("semanticHash", semanticHash);
        result.addProperty("rawFingerprint", artifact.rawFingerprint());
        result.addProperty("encodedBytes", artifact.encodedSize());
        writeText(Path.of(require("output")), result.toString());
    }

    private static void consume(MinecraftServer server) {
        Envelope envelope = readEnvelope(Path.of(require("artifact")));
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse(envelope.artifact.dimension()));
        ServerLevel level = requireLevel(server, dimension);
        String frontier = System.getProperty(PREFIX + "frontier", "absent");
        prepareFrontier(level, envelope.artifact.chunkPos(), frontier);
        Phase2C1FVerification.PhysicalHostSnapshot before =
                Phase2C1FVerification.PhysicalHostSnapshot.capture(level, envelope.artifact.chunkPos());
        var staging = envelope.artifact.rehydrate(
                level.registryAccess(), StructurePieceSerializationContext.fromLevel(level));
        FeatureStableSnapshot snapshot = FeatureStableSnapshot.capture(
                envelope.artifact.dimension(), staging, level.registryAccess());
        if (!envelope.semanticHash.equals(snapshot.hash())
                || staging.getPersistedStatus() != ChunkStatus.FEATURES
                || staging.isLightCorrect()) {
            throw new IllegalStateException("Detached transport semantic mismatch");
        }
        Phase2C1FVerification.PhysicalHostSnapshot after =
                Phase2C1FVerification.PhysicalHostSnapshot.capture(level, envelope.artifact.chunkPos());
        if (!before.equals(after)) throw new IllegalStateException("Transport consumer mutated physical host");
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Transport consumer unexpectedly required generation context");
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("mode", "consume");
        result.addProperty("hostSeed", Long.toString(server.getWorldGenSettings().options().seed()));
        result.addProperty("frontier", frontier);
        result.addProperty("semanticHash", snapshot.hash());
        result.addProperty("rawFingerprint", envelope.artifact.rawFingerprint());
        writeText(Path.of(require("output")), result.toString());
    }

    private static void prepareFrontier(ServerLevel level, ChunkPos target, String frontier) {
        if (frontier.equals("absent")) return;
        if (!frontier.equals("generated") && !frontier.equals("mutated")) {
            throw new IllegalArgumentException("Unknown frontier " + frontier);
        }
        for (int z = target.z() - 1; z <= target.z() + 1; z++) {
            for (int x = target.x() - 1; x <= target.x() + 1; x++) {
                level.getChunk(x, z, ChunkStatus.FULL, true);
                if (frontier.equals("mutated")) {
                    BlockPos marker = new BlockPos((x << 4) + 8, level.getSeaLevel(), (z << 4) + 8);
                    level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void writeEnvelope(Path path, CanonicalChunkArtifact artifact, String semanticHash) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
                output.writeInt(MAGIC);
                output.writeUTF(artifact.dimension());
                output.writeInt(artifact.chunkPos().x()); output.writeInt(artifact.chunkPos().z());
                output.writeInt(artifact.minY()); output.writeInt(artifact.height());
                output.writeLong(artifact.localWorldSeed());
                output.writeInt(artifact.mosaicFormatVersion());
                output.writeInt(artifact.seedDerivationAlgorithmVersion());
                output.writeInt(artifact.featureOrderingAlgorithmVersion());
                output.writeUTF(semanticHash);
                byte[] payload = artifact.encodedPayloadCopy();
                output.writeInt(payload.length); output.write(payload);
                long[] positions = artifact.instantiatedBlockEntityPositionsCopy();
                output.writeInt(positions.length);
                for (long position : positions) output.writeLong(position);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write temporary Artifact envelope " + path, exception);
        }
    }

    private static Envelope readEnvelope(Path path) {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != MAGIC) throw new IllegalArgumentException("Invalid Artifact envelope");
            String dimension = input.readUTF();
            int x = input.readInt(); int z = input.readInt();
            int minY = input.readInt(); int height = input.readInt();
            long localSeed = input.readLong();
            int format = input.readInt(); int seedVersion = input.readInt(); int orderingVersion = input.readInt();
            String semanticHash = input.readUTF();
            byte[] payload = input.readNBytes(input.readInt());
            long[] positions = new long[input.readInt()];
            for (int index = 0; index < positions.length; index++) positions[index] = input.readLong();
            CanonicalChunkArtifact artifact = CanonicalChunkArtifact.fromDetachedTransport(
                    dimension, x, z, minY, height, localSeed, format, seedVersion, orderingVersion,
                    payload, positions);
            return new Envelope(artifact, semanticHash);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read temporary Artifact envelope " + path, exception);
        }
    }

    private static ServerLevel requireLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing transport dimension " + dimension.identifier());
        return level;
    }

    private static String require(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + PREFIX + suffix);
        return value;
    }

    private static void writeText(Path output, String value) {
        try {
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Files.writeString(output, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write transport result " + output, exception);
        }
    }

    private record Envelope(CanonicalChunkArtifact artifact, String semanticHash) {
    }
}
