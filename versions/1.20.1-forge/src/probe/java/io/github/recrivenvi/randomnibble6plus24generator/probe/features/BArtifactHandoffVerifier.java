package io.github.recrivenvi.randomnibble6plus24generator.probe.features;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Independent native load control and strict same-process raw transport, after owner close. */
public final class BArtifactHandoffVerifier {
    private static final String PREFIX = ModConstants.MOD_ID + ".featureBInputs.";
    private static final ThreadLocal<SeedObservation> LOADING = new ThreadLocal<>();

    private BArtifactHandoffVerifier() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PREFIX + "handoff");
    }

    static void verifyOwnerClosed(
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                            .StructureGenerationContext
                    owner) {
        try {
            owner.seed();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new IllegalStateException("Artifact owner remains open");
    }

    public static void recordLoadSeed(long seed) {
        var observation = LOADING.get();
        if (observation == null) return;
        if (seed != observation.expected)
            throw new IllegalStateException("Structure loader escaped S(C)");
        observation.calls++;
    }

    static void verifyPalettes(
            ServerLevel level,
            ChunkPos owner,
            long seed,
            SpawnBiomeSnapshot snapshot,
            Map<Long, ProtoChunk> terrain) {
        var profile = MosaicWorldProfile.targetOnly();
        snapshot.validate(level.dimension(), owner, seed, profile);
        expectStateRejected(() -> snapshot.validate(level.dimension(), owner, seed ^ 1L, profile));
        expectStateRejected(
                () ->
                        snapshot.validate(
                                level.dimension(),
                                new ChunkPos(owner.x + 1, owner.z),
                                seed,
                                profile));
        expectStateRejected(
                () ->
                        snapshot.validate(
                                level.dimension(), owner, seed, MosaicWorldProfile.current()));
        expectStateRejected(
                () ->
                        snapshot.validate(
                                level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                                        ? net.minecraft.world.level.Level.NETHER
                                        : net.minecraft.world.level.Level.OVERWORLD,
                                owner,
                                seed,
                                profile));
        int count = 0;
        for (var pos : snapshot.positions()) {
            var chunk = Objects.requireNonNull(terrain.get(pos.toLong()));
            int min = Math.floorDiv(chunk.getMinBuildHeight(), 4),
                    max = min + chunk.getHeight() / 4;
            for (int y = min - 4; y < max + 4; y++)
                for (int z = pos.z * 4; z < pos.z * 4 + 4; z++)
                    for (int x = pos.x * 4; x < pos.x * 4 + 4; x++) {
                        if (!chunk.getNoiseBiome(x, y, z).equals(snapshot.biome(pos, x, y, z)))
                            throw new IllegalStateException(
                                    "Detached SPAWN stored palette changed");
                        if (y >= min && y < max) count++;
                    }
        }
        if (count != snapshot.cellCount())
            throw new IllegalStateException("Incomplete detached SPAWN cells");
    }

    static JsonObject nativeControl(
            ServerLevel level, ProtoChunk target, CompoundTag generation, Path output)
            throws IOException {
        if (LOADING.get() != null) throw new IllegalStateException("Nested native load observer");
        var observation = new SeedObservation(level.getSeed());
        LOADING.set(observation);
        CompoundTag loaded;
        try {
            var nativeChunk =
                    ChunkSerializer.read(
                            level, level.getPoiManager(), target.getPos(), generation.copy());
            loaded = ChunkSerializer.write(level, nativeChunk);
        } finally {
            LOADING.remove();
        }
        NbtIo.writeCompressed(
                loaded, output.resolve("handoff-native-load-control.nbt.gz").toFile());
        JsonObject proof = new JsonObject();
        proof.addProperty("nativeLoadControl", true);
        proof.addProperty("structureLoadSeed", level.getSeed());
        proof.addProperty("structureLoadCalls", observation.calls);
        return proof;
    }

    static JsonObject roundtrip(
            ServerLevel level,
            ProtoChunk source,
            BFeaturePreparation.Result prepared,
            CompoundTag generation,
            Path output,
            Path nativeControl)
            throws IOException {
        if (FeatureExecutionScope.bindingCount() != 0 || SeedLocalRegionScope.isBound())
            throw new IllegalStateException("Handoff proof requires all generation scopes closed");
        var profile = MosaicWorldProfile.targetOnly();
        var artifact = prepared.artifact();
        var sourcePending = new CompoundTag();
        source.getBlockEntityNbts()
                .forEach((pos, tag) -> sourcePending.put(Long.toString(pos.asLong()), tag.copy()));
        if (!sourcePending.equals(artifact.pendingBlockEntitiesCopy()))
            throw new IllegalStateException("Artifact lost original pending BlockEntity data");
        var payload = artifact.payloadCopy();
        if (payload.getBoolean("isLightOn"))
            throw new IllegalStateException("Pre-light artifact claims light");
        for (var section : payload.getList("sections", Tag.TAG_COMPOUND))
            if (((CompoundTag) section).contains("SkyLight")
                    || ((CompoundTag) section).contains("BlockLight"))
                throw new IllegalStateException("Artifact captured physical host light");
        var serialized = artifact.encodedPayloadCopy();
        serialized[0] ^= 1;
        if (!payload.equals(artifact.payloadCopy()))
            throw new IllegalStateException("Mutable artifact payload escaped");
        var starts = prepared.externalStarts();
        if (!starts.isEmpty()) {
            var original = starts.get(0).copy();
            starts.get(0).putString("probe-mutation", "must-not-escape");
            if (!prepared.externalStarts().get(0).equals(original))
                throw new IllegalStateException("Mutable structure projection escaped");
        }
        prepared.spawnBiomes()
                .validate(level.dimension(), source.getPos(), artifact.localWorldSeed(), profile);
        if (prepared.spawnBiomes().positions().size() != 8
                || prepared.spawnBiomes().cellCount() <= 0)
            throw new IllegalStateException("Missing detached SPAWN palettes");
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        expectRejected(
                () ->
                        artifact.rehydrate(
                                level.dimension().location().toString(),
                                source.getPos(),
                                artifact.localWorldSeed() ^ 1L,
                                profile,
                                level,
                                level.registryAccess(),
                                serialization));
        expectRejected(
                () ->
                        artifact.rehydrate(
                                level.dimension().location().toString(),
                                new ChunkPos(source.getPos().x + 1, source.getPos().z),
                                artifact.localWorldSeed(),
                                profile,
                                level,
                                level.registryAccess(),
                                serialization));
        if (LOADING.get() != null) throw new IllegalStateException("Nested artifact load observer");
        var observation = new SeedObservation(artifact.localWorldSeed());
        LOADING.set(observation);
        ProtoChunk restored;
        try {
            restored =
                    artifact.rehydrate(
                            level.dimension().location().toString(),
                            source.getPos(),
                            artifact.localWorldSeed(),
                            profile,
                            level,
                            level.registryAccess(),
                            serialization);
        } finally {
            LOADING.remove();
        }
        if (restored == source
                || restored.getSections() == source.getSections()
                || restored.getBlockTicks() == source.getBlockTicks()
                || restored.getFluidTicks() == source.getFluidTicks())
            throw new IllegalStateException("Artifact retained mutable generation state");
        for (var entry : source.getHeightmaps()) {
            if (!restored.hasPrimedHeightmap(entry.getKey())
                    || !Arrays.equals(
                            entry.getValue().getRawData(),
                            restored.getOrCreateHeightmapUnprimed(entry.getKey()).getRawData()))
                throw new IllegalStateException(
                        "Artifact lost generation heightmap " + entry.getKey());
        }
        var restoredPending = new CompoundTag();
        restored.getBlockEntityNbts()
                .forEach(
                        (pos, tag) -> restoredPending.put(Long.toString(pos.asLong()), tag.copy()));
        if (!sourcePending.equals(restoredPending)
                || !restored.getBlockEntities().keySet().equals(source.getBlockEntities().keySet()))
            throw new IllegalStateException(
                    "Artifact changed pending/instantiated BlockEntity split");
        CompoundTag actual = ChunkSerializer.write(level, restored);
        CompoundTag expected = generation.copy();
        CompoundTag control = NbtIo.readCompressed(nativeControl.toFile());
        expected.put("structures", control.getCompound("structures").copy());
        NbtIo.writeCompressed(generation, output.resolve("handoff-before.nbt.gz").toFile());
        NbtIo.writeCompressed(actual, output.resolve("handoff-after.nbt.gz").toFile());
        NbtIo.writeCompressed(payload, output.resolve("handoff-payload.nbt.gz").toFile());
        NbtIo.writeCompressed(
                sourcePending, output.resolve("handoff-pending-before.nbt.gz").toFile());
        NbtIo.writeCompressed(
                restoredPending, output.resolve("handoff-pending-after.nbt.gz").toFile());
        if (!NativeStructureVerifier.canonicalNbt(expected)
                .equals(NativeStructureVerifier.canonicalNbt(actual))) {
            NativeStructureVerifier.write(
                    output.resolve("handoff-difference.json"),
                    new com.google.gson.Gson()
                            .toJsonTree(NativeFeatureNbtDiff.compare(expected, actual)));
            throw new IllegalStateException(
                    "Artifact differs from strict generation + independent native structure load");
        }
        var proof = new JsonObject();
        proof.addProperty("artifactRoundtrip", true);
        proof.addProperty("structureLoadSeed", artifact.localWorldSeed());
        proof.addProperty("structureLoadCalls", observation.calls);
        proof.addProperty("closedOwner", true);
        proof.addProperty("spawnBiomeCells", prepared.spawnBiomes().cellCount());
        proof.addProperty("pendingBlockEntities", source.getBlockEntityNbts().size());
        proof.addProperty("instantiatedBlockEntities", source.getBlockEntities().size());
        proof.addProperty("encodedBytes", artifact.encodedPayloadCopy().length);
        return proof;
    }

    private static void expectRejected(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new IllegalStateException("Foreign artifact provenance was accepted");
    }

    private static void expectStateRejected(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new IllegalStateException("Foreign SPAWN provenance was accepted");
    }

    private static final class SeedObservation {
        final long expected;
        int calls;

        SeedObservation(long expected) {
            this.expected = expected;
        }
    }
}
