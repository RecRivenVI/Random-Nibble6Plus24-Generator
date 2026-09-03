package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalPoiReconciler;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;

/**
 * Test-only observer for the ordinary ServerChunkCache/ChunkMap request path.
 * The property selects this observer; production generation itself never checks
 * the property and is driven solely by the serialized Mosaic world identity.
 */
public final class Phase3C2ProductionVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3c2.production.";
    private static final Comparator<ChunkPos> Z_THEN_X = Comparator
            .comparingInt(ChunkPos::z)
            .thenComparingInt(ChunkPos::x);
    private static volatile int ticksBeforeRun = 1;
    private static volatile boolean started;

    private Phase3C2ProductionVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "mode", "");
        if (mode.isBlank() || started) return;
        if (ticksBeforeRun-- > 0) return;
        started = true;
        if (MosaicWorldIdentity.runtimeContext(server).isEmpty()) {
            throw new IllegalStateException("Phase 3C2 production observer requires a Mosaic world");
        }
        ServerLevel level = level(server);
        MosaicPhysicalMaterializer.Metrics baselineMetrics = MosaicPhysicalMaterializer.metrics();
        MosaicPhysicalPoiReconciler.Metrics baselinePoi = MosaicPhysicalPoiReconciler.metrics();
        PhysicalMosaicTrace.Snapshot baselineTrace = PhysicalMosaicTrace.snapshot();
        PhysicalMosaicTrace.enableDetailedObservation();

        List<ChunkPos> positions = positions(mode);
        int repeats = Math.max(1, Integer.getInteger(PREFIX + "repeats", 16));
        List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new ArrayList<>();
        if ("concurrent".equals(mode)) {
            for (int i = 0; i < repeats; i++) futures.add(request(level, target()));
        } else {
            for (ChunkPos pos : positions) futures.add(request(level, pos));
        }
        server.managedBlock(() -> futures.stream().allMatch(CompletableFuture::isDone));

        List<LevelChunk> chunks = new ArrayList<>();
        for (CompletableFuture<ChunkResult<ChunkAccess>> future : futures) {
            ChunkAccess chunk = future.join().orElseThrow(() ->
                    new IllegalStateException("Ordinary production request returned no ChunkAccess"));
            if (!(chunk instanceof LevelChunk levelChunk)
                    || levelChunk.getPersistedStatus() != ChunkStatus.FULL
                    || !levelChunk.isLightCorrect()) {
                throw new IllegalStateException("Ordinary production request did not reach FULL LevelChunk: "
                        + chunk.getClass().getName() + " status=" + chunk.getPersistedStatus());
            }
            chunks.add(levelChunk);
        }

        boolean reload = "reload".equals(mode);
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        PhysicalMosaicTrace.Snapshot trace = PhysicalMosaicTrace.snapshot();
        long artifactGenerations = metrics.artifactCaptureCount() - baselineMetrics.artifactCaptureCount();
        List<PhysicalMosaicTrace.SpawnSeed> spawnSeeds = appended(
                baselineTrace.spawnSeeds(), trace.spawnSeeds());
        if (reload && (artifactGenerations != 0 || !spawnSeeds.isEmpty())) {
            throw new IllegalStateException("Saved Mosaic Chunk re-entered canonical generation on reload: "
                    + metrics + " spawn=" + spawnSeeds);
        }
        long targetArtifactGenerations = MosaicPhysicalMaterializer.artifactCaptures(level, target());
        long targetPublishes = MosaicPhysicalMaterializer.publishes(level, target());
        if (!reload && "concurrent".equals(mode) && targetArtifactGenerations > 1) {
            throw new IllegalStateException("Concurrent ordinary requests generated duplicate target Artifacts: "
                    + metrics + " targetCaptures=" + targetArtifactGenerations);
        }

        BlockPos marker = marker(target(), level);
        boolean markerObservation = "full".equals(mode) || "reload".equals(mode);
        boolean markerExpected = markerObservation && Boolean.getBoolean(PREFIX + "expectMarker");
        if (markerExpected && !level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)) {
            throw new IllegalStateException("Saved Mosaic BlockState was overwritten during production reload");
        }
        if (markerObservation && Boolean.getBoolean(PREFIX + "mutate")) {
            level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        }

        MosaicPhysicalMaterializer.Metrics finalMetrics = MosaicPhysicalMaterializer.metrics();
        List<ChunkPos> observedPositions = "concurrent".equals(mode)
                ? List.of(target())
                : positions;
        if (observedPositions.stream().anyMatch(pos -> !MosaicPhysicalMaterializer.isIdle(level, pos))) {
            throw new IllegalStateException("Production observer left a requested target in flight: "
                    + finalMetrics);
        }
        if (GenerationContextRegistry.bindingCount() != 0
                || MosaicSpawnContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Production request leaked an isolated context");
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("mode", mode);
        result.addProperty("dimension", level.dimension().identifier().toString());
        result.addProperty("target", target().toString());
        result.addProperty("worldSeed", level.getServer().getWorldGenSettings().options().seed());
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        long targetLocalSeed = runtime.resolveLocalWorldSeed(level.dimension(), target());
        result.addProperty("localWorldSeed", targetLocalSeed);
        if ("pair".equals(mode)) {
            ChunkPos adjacent = positions.get(1);
            long adjacentLocalSeed = runtime.resolveLocalWorldSeed(level.dimension(), adjacent);
            if (adjacentLocalSeed == targetLocalSeed) {
                throw new IllegalStateException("Adjacent production pair unexpectedly reused local world seed: "
                        + target() + " and " + adjacent);
            }
            result.addProperty("adjacent", adjacent.toString());
            result.addProperty("adjacentLocalWorldSeed", adjacentLocalSeed);
        }
        result.addProperty("ordinaryRequestCount", futures.size());
        result.addProperty("returnedFullChunks", chunks.size());
        result.addProperty("artifactGenerations", artifactGenerations);
        result.addProperty("artifactPublishes", metrics.publishCount() - baselineMetrics.publishCount());
        result.addProperty("targetArtifactGenerations", targetArtifactGenerations);
        result.addProperty("targetArtifactPublishes", targetPublishes);
        result.addProperty("poiReconciliations",
                MosaicPhysicalPoiReconciler.metrics().invocationCount() - baselinePoi.invocationCount());
        result.addProperty("initializeLightCalls", delta(
                baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                .getOrDefault(ChunkStatus.INITIALIZE_LIGHT.getName(), 0L));
        result.addProperty("lightCalls", delta(
                baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                .getOrDefault(ChunkStatus.LIGHT.getName(), 0L));
        result.addProperty("spawnCalls", delta(
                baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                .getOrDefault(ChunkStatus.SPAWN.getName(), 0L));
        result.addProperty("fullCalls", delta(
                baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                .getOrDefault(ChunkStatus.FULL.getName(), 0L));
        result.addProperty("fullChunkClass", chunks.getFirst().getClass().getName());
        result.addProperty("fullStatus", chunks.getFirst().getPersistedStatus().getName());
        result.addProperty("lightCorrect", chunks.getFirst().isLightCorrect());
        result.addProperty("markerWritten", markerObservation && Boolean.getBoolean(PREFIX + "mutate"));
        result.addProperty("markerPresent", markerObservation && level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK));
        result.addProperty("globalInFlightAtObservation", finalMetrics.inFlightCount());
        result.add("lifecycleDuplicates", map(delta(
                baselineTrace.lifecycleDuplicates(), trace.lifecycleDuplicates())));
        result.add("runtimeTicks", map(delta(
                baselineTrace.runtimeTicks(), trace.runtimeTicks())));
        write(result);
        RandomNibble6Plus24Generator.LOGGER.info("Phase 3C2 ordinary production request PASS mode={} target={}",
                mode, target());
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) server.halt(false);
    }

    private static CompletableFuture<ChunkResult<ChunkAccess>> request(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true);
    }

    private static ServerLevel level(MinecraftServer server) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing Phase 3C2 dimension " + dimension.identifier());
        return level;
    }

    private static ChunkPos target() {
        return new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "125")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "-37")));
    }

    private static List<ChunkPos> positions(String mode) {
        if ("patch".equals(mode)) {
            ChunkPos center = target();
            List<ChunkPos> result = new ArrayList<>();
            for (int z = center.z() - 1; z <= center.z() + 1; z++) {
                for (int x = center.x() - 1; x <= center.x() + 1; x++) {
                    result.add(new ChunkPos(x, z));
                }
            }
            result.sort(Z_THEN_X);
            return result;
        }
        if ("pair".equals(mode)) {
            ChunkPos center = target();
            return List.of(center, new ChunkPos(center.x() + 1, center.z()));
        }
        return List.of(target());
    }

    private static BlockPos marker(ChunkPos pos, ServerLevel level) {
        return new BlockPos(pos.getMiddleBlockX(), level.getMinY() + 8, pos.getMiddleBlockZ());
    }

    private static JsonObject map(Map<String, Long> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    private static Map<String, Long> delta(Map<String, Long> before, Map<String, Long> after) {
        java.util.Map<String, Long> result = new java.util.TreeMap<>();
        after.forEach((key, value) -> {
            long difference = value - before.getOrDefault(key, 0L);
            if (difference != 0L) result.put(key, difference);
        });
        return Map.copyOf(result);
    }

    private static <T> List<T> appended(List<T> before, List<T> after) {
        if (after.size() < before.size()) {
            throw new IllegalStateException("Production trace regressed while observing ordinary requests");
        }
        return List.copyOf(after.subList(before.size(), after.size()));
    }

    private static void write(JsonObject result) {
        String output = System.getProperty(PREFIX + "output", "");
        if (output.isBlank()) return;
        Path path = Path.of(output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, result.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3C2 production result", exception);
        }
    }
}
