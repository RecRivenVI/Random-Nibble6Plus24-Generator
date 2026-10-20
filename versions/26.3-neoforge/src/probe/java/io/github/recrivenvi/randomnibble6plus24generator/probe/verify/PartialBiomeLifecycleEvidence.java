package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureStableSnapshot;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Ordinary BIOMES requests, native save/restart and concurrent FULL upgrade; probe-only. */
public final class PartialBiomeLifecycleEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static boolean started;
    private static int ticks = 1;

    private PartialBiomeLifecycleEvidence() {}

    public static boolean runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "mode", "");
        if (!mode.equals("biomes-first") && !mode.equals("biomes-reload")) return false;
        if (started || ticks-- > 0) return true;
        started = true;
        if (!server.isSameThread() || MosaicWorldIdentity.runtimeContext(server).isEmpty())
            throw new IllegalStateException(
                    "Partial BIOMES evidence needs its native Mosaic server thread");
        var dimension =
                ResourceKey.create(
                        Registries.DIMENSION,
                        Identifier.parse(
                                System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        var level = Objects.requireNonNull(server.getLevel(dimension));
        verifyPartialBiomes(server, level, mode);
        return true;
    }

    private static void verifyPartialBiomes(
            MinecraftServer server, ServerLevel level, String mode) {
        boolean reload = mode.equals("biomes-reload");
        if (!reload && !mode.equals("biomes-first"))
            throw new IllegalArgumentException("Unknown partial biome lifecycle mode");
        var pos = target();
        ChunkStatus partialStage =
                Boolean.getBoolean(PREFIX + "partialTerrain")
                        ? ChunkStatus.TERRAIN
                        : ChunkStatus.BIOMES;
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                .PhysicalPreFeaturesPreparation.observeForVerification(level, pos, partialStage);
        long artifactsBefore = MosaicPhysicalMaterializer.artifactCaptures(level, pos);
        List<CompletableFuture<ChunkResult<ChunkAccess>>> requests = new ArrayList<>();
        for (int i = 0; i < 16; i++)
            requests.add(
                    level.getChunkSource().getChunkFuture(pos.x(), pos.z(), partialStage, true));
        server.managedBlock(() -> requests.stream().allMatch(CompletableFuture::isDone));
        var chunk =
                requests.getFirst()
                        .join()
                        .orElseThrow(() -> new IllegalStateException("No physical BIOMES"));
        for (var request : requests)
            if (request.join()
                            .orElseThrow(
                                    () -> new IllegalStateException("Missing duplicate BIOMES"))
                    != chunk)
                throw new IllegalStateException(
                        "Concurrent BIOMES requests published different chunks");
        if (chunk.getPersistedStatus() != partialStage
                || MosaicPhysicalMaterializer.artifactCaptures(level, pos) != artifactsBefore)
            throw new IllegalStateException("BIOMES-only request generated full canonical terrain");
        long local =
                MosaicWorldIdentity.runtimeContext(level)
                        .orElseThrow()
                        .resolveLocalWorldSeed(level.dimension(), pos);
        var capture =
                FeatureStableSnapshot.capture(
                        level.dimension().identifier().toString(), chunk, level.registryAccess());
        Path baseline = Path.of(System.getProperty(PREFIX + "biomeBaseline"));
        try {
            var raw =
                    net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf(
                                    level, chunk)
                            .write();
            net.minecraft.nbt.NbtIo.writeCompressed(
                    raw, Path.of(System.getProperty(PREFIX + "output") + ".nbt.gz"));
            if (reload) {
                var expected = FeatureStableSnapshot.read(baseline);
                if (!expected.diff(capture).equivalent())
                    throw new IllegalStateException(
                            "Reloaded BIOMES differs from native-load control: "
                                    + expected.diff(capture));
                if (io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                .PhysicalPreFeaturesPreparation.builds(level, pos, partialStage)
                        != 0L)
                    throw new IllegalStateException("Partial reload rebuilt BIOMES inputs");
            } else {
                // Native read also primes status-required heightmaps missing from a partial
                // save. Compare against that real load boundary, not only a starts-only decode.
                var serialized =
                        net.minecraft.world.level.chunk.storage.SerializableChunkData.parse(
                                level, level.palettedContainerFactory(), raw.copy());
                if (serialized == null)
                    throw new IllegalStateException("Native partial NBT parser rejected data");
                var loadControl =
                        serialized.read(
                                level,
                                level.getPoiManager(),
                                new net.minecraft.world.level.chunk.storage.RegionStorageInfo(
                                        "partial-biome-control", level.dimension(), "chunk"),
                                pos);
                // Explicit local seed keeps the control independent of the physical reload hook.
                loadControl.setAllStarts(
                        io.github.recrivenvi.randomnibble6plus24generator.mixin
                                .SerializableChunkDataInvoker
                                .randomnibble6plus24generator$unpackStructureStarts(
                                        net.minecraft.world.level.levelgen.structure.pieces
                                                .StructurePieceSerializationContext.fromLevel(
                                                level),
                                        raw.getCompoundOrEmpty("structures"),
                                        local));
                FeatureStableSnapshot.capture(
                                level.dimension().identifier().toString(),
                                loadControl,
                                level.registryAccess())
                        .write(baseline);
                net.minecraft.nbt.NbtIo.writeCompressed(
                        net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf(
                                        level, loadControl)
                                .write(),
                        Path.of(
                                System.getProperty(PREFIX + "output")
                                        + ".native-load-control.nbt.gz"));
                if (io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                .PhysicalPreFeaturesPreparation.builds(level, pos, partialStage)
                        != 1L)
                    throw new IllegalStateException("Native partial preparation was not exercised");
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Cannot retain partial BIOMES lifecycle evidence", failure);
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("mode", mode);
        result.addProperty("dimension", level.dimension().identifier().toString());
        result.addProperty("target", pos.toString());
        result.addProperty("chunkStatus", chunk.getPersistedStatus().getName());
        result.addProperty("localWorldSeed", Long.toString(local));
        result.addProperty(
                "artifactGenerations",
                MosaicPhysicalMaterializer.artifactCaptures(level, pos) - artifactsBefore);
        result.addProperty("canonicalHash", capture.hash());
        result.addProperty("sameChunkFor16Requests", true);
        if (!reload) {
            // Separate frontier: it cannot promote the BIOMES-only target being saved above.
            var racePos = new ChunkPos(pos.x() + 32, pos.z());
            var initial =
                    level.getChunkSource()
                            .getChunkFuture(racePos.x(), racePos.z(), partialStage, true);
            List<CompletableFuture<ChunkResult<ChunkAccess>>> upgrades = new ArrayList<>();
            for (int i = 0; i < 16; i++) upgrades.add(request(level, racePos));
            server.managedBlock(
                    () ->
                            initial.isDone()
                                    && upgrades.stream().allMatch(CompletableFuture::isDone));
            var complete =
                    upgrades.getFirst()
                            .join()
                            .orElseThrow(() -> new IllegalStateException("Upgrade race lost FULL"));
            if (!(complete instanceof LevelChunk)
                    || complete.getPersistedStatus() != ChunkStatus.FULL
                    || !complete.isLightCorrect()
                    || MosaicPhysicalMaterializer.artifactCaptures(level, racePos) != 1L)
                throw new IllegalStateException(
                        "BIOMES/FULL upgrade did not materialize exactly one canonical Artifact");
            for (var upgrade : upgrades)
                if (upgrade.join()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Upgrade race lost duplicate"))
                        != complete)
                    throw new IllegalStateException(
                            "Upgrade race published duplicate physical FULL chunks");
            if (initial.join()
                    .orElseThrow(() -> new IllegalStateException("Upgrade race lost BIOMES"))
                    .getPersistedStatus()
                    .isBefore(partialStage))
                throw new IllegalStateException("Upgrade race exposed an empty palette");
            result.addProperty("concurrentUpgradeExactlyOneArtifact", true);
        }
        result.add(
                "partialBiomes",
                map(
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                .PhysicalPreFeaturesPreparation.metrics(partialStage)));
        write(result);
        server.halt(false);
    }

    private static ChunkPos target() {
        return new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "125")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "-37")));
    }

    private static CompletableFuture<ChunkResult<ChunkAccess>> request(
            ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true);
    }

    private static JsonElement map(Object value) {
        return new Gson().toJsonTree(value);
    }

    private static void write(JsonObject result) {
        Path path = Path.of(System.getProperty(PREFIX + "output"));
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(
                    path, result.toString() + "\n", java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
