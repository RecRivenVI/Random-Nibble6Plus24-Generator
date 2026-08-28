package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Verifies a saved physical FEATURES ProtoChunk loads instead of regenerating its Artifact. */
public final class Phase3AReloadVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3a.reload.";

    private Phase3AReloadVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        if (System.getProperty(PREFIX + "verify", "").isBlank()) return;
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        ChunkPos target = new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "125")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "-37")));
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing Phase 3A reload dimension");
        MosaicPhysicalMaterializer.resetVerificationState();
        PhysicalMosaicTrace.reset();
        CompletableFuture<ChunkResult<ChunkAccess>> future = level.getChunkSource().getChunkFuture(
                target.x(), target.z(), ChunkStatus.FEATURES, true);
        server.managedBlock(future::isDone);
        ChunkAccess loaded = future.join().orElse(null);
        if (loaded == null || loaded.getPersistedStatus() != ChunkStatus.FEATURES || loaded.isLightCorrect()) {
            throw new IllegalStateException("Saved pre-light Mosaic Chunk did not reload at FEATURES");
        }
        FeatureStableSnapshot snapshot = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), loaded, level.registryAccess());
        String expectedHash = System.getProperty(PREFIX + "expectedHash", "");
        boolean vanillaHeightmapKeyNormalization = false;
        if (!expectedHash.isBlank() && !expectedHash.equals(snapshot.hash())) {
            String referencePath = System.getProperty(PREFIX + "referenceSnapshot", "");
            if (referencePath.isBlank()) {
                throw new IllegalStateException("Reload hash differs without a structured reference snapshot");
            }
            FeatureStableSnapshot reference = FeatureStableSnapshot.read(Path.of(referencePath));
            FeatureStageSnapshot.Diff diff = reference.diff(snapshot);
            String metadata = reference.deterministicMetadataDifference(snapshot);
            vanillaHeightmapKeyNormalization = diff.differingBlocks() == 0
                    && "Heightmap keys differ".equals(diff.firstDifference())
                    && "Heightmap keys differ".equals(metadata)
                    && reference.rawEntityNbt().equals(snapshot.rawEntityNbt())
                    && reference.blockEntityNbt().equals(snapshot.blockEntityNbt());
            if (!vanillaHeightmapKeyNormalization) {
                throw new IllegalStateException("Reloaded physical Mosaic data mismatch expected="
                        + expectedHash + " actual=" + snapshot.hash() + " diff=" + diff + " metadata=" + metadata);
            }
        }
        boolean expectMarker = Boolean.parseBoolean(System.getProperty(PREFIX + "expectMarker", "false"));
        if (expectMarker) {
            BlockPos marker = new BlockPos(target.getMiddleBlockX(), loaded.getMinY() + 8, target.getMiddleBlockZ());
            if (!loaded.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)) {
                throw new IllegalStateException("Saved physical Mosaic player marker was overwritten on reload");
            }
        }
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        if (metrics.isolatedGenerationCount() != 0
                || metrics.artifactCaptureCount() != 0
                || metrics.publishCount() != 0
                || metrics.inFlightCount() != 0
                || metrics.requestedTargetCount() != 0
                || PhysicalMosaicTrace.snapshot().totalGeneratorCalls() != 0
                || GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Saved Mosaic Chunk unexpectedly regenerated " + metrics);
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("dimension", dimension.identifier().toString());
        result.addProperty("chunkX", target.x());
        result.addProperty("chunkZ", target.z());
        result.addProperty("hash", snapshot.hash());
        result.addProperty("preSaveHash", expectedHash);
        result.addProperty("vanillaHeightmapKeyNormalization", vanillaHeightmapKeyNormalization);
        result.addProperty("markerPreserved", expectMarker);
        result.addProperty("isolatedGenerations", metrics.isolatedGenerationCount());
        result.addProperty("artifactCaptures", metrics.artifactCaptureCount());
        result.addProperty("publishes", metrics.publishCount());
        write(result);
        Phase3APhysicalMaterializationVerification.markCompleted();
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3A saved Chunk reload PASS dimension={} target={} hash={} marker={}",
                dimension.identifier(), target, snapshot.hash(), expectMarker);
        server.halt(false);
    }

    private static void write(JsonObject result) {
        Path output = Path.of(System.getProperty(PREFIX + "output"));
        try {
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Files.writeString(output, result.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3A reload result", exception);
        }
    }
}
