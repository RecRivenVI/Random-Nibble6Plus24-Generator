package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalPoiReconciler;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Test-only observer for the ordinary ServerChunkCache/ChunkMap request path. The property selects
 * this observer; production generation itself never checks the property and is driven solely by the
 * serialized Mosaic world identity.
 */
public final class Phase3C2ProductionVerification {

    private static final String PREFIX =
            io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator.MOD_ID
                    + ".phase3c2.production.";
    private static final Comparator<ChunkPos> Z_THEN_X =
            Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x);
    private static volatile int ticksBeforeRun = 1;
    private static volatile boolean started;
    private static final java.util.concurrent.atomic.AtomicLong singleWriterRuns =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong clippedWrites =
            new java.util.concurrent.atomic.AtomicLong();

    private Phase3C2ProductionVerification() {}

    public static void observeFeatureRun(
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                            .FeatureStableGenerationRun
                    run,
            long discardedWrites) {
        if (System.getProperty(PREFIX + "mode", "").isBlank()
                || run.featureTrace().orderingAlgorithmVersion() != 2) return;
        if (run.featureTrace().requestedWriters().size() != 1
                || run.featureTrace().maxConcurrentFeatureWriters() != 1) {
            throw new IllegalStateException("Target-only production writer invariant failed");
        }
        singleWriterRuns.incrementAndGet();
        clippedWrites.addAndGet(discardedWrites);
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "mode", "");
        if (mode.isBlank() || started) return;
        if (ticksBeforeRun-- > 0) return;
        started = true;
        if (MosaicWorldIdentity.runtimeContext(server).isEmpty()) {
            throw new IllegalStateException(
                    "Phase 3C2 production observer requires a Mosaic world");
        }
        ServerLevel level = level(server);
        if (mode.startsWith("biomes-")) {
            verifyPartialBiomes(server, level, mode);
            return;
        }
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
        boolean requestProof = Boolean.getBoolean(PREFIX + "requestProof");
        long requestDeadline =
                System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(300);
        server.managedBlock(
                () ->
                        futures.stream().allMatch(CompletableFuture::isDone)
                                || (requestProof && System.nanoTime() >= requestDeadline));
        if (requestProof && futures.stream().anyMatch(future -> !future.isDone()))
            throw new IllegalStateException(
                    "Ordinary request proof exceeded its 300-second observation bound");

        List<LevelChunk> chunks = new ArrayList<>();
        for (CompletableFuture<ChunkResult<ChunkAccess>> future : futures) {
            ChunkAccess chunk =
                    future.join()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Ordinary production request returned no ChunkAccess"));
            if (!(chunk instanceof LevelChunk levelChunk)
                    || levelChunk.getPersistedStatus() != ChunkStatus.FULL
                    || !levelChunk.isLightCorrect()) {
                throw new IllegalStateException(
                        "Ordinary production request did not reach FULL LevelChunk: "
                                + chunk.getClass().getName()
                                + " status="
                                + chunk.getPersistedStatus());
            }
            chunks.add(levelChunk);
        }

        boolean reload = "reload".equals(mode);
        int physicalBiomeChecks = 0;
        for (ChunkPos pos : positions) {
            var plan =
                    io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                            .MosaicPhysicalGenerationPlan.derive(pos, ChunkStatus.FULL);
            for (var entry : plan.physicalStatusRequirements().entrySet()) {
                if (reload) continue;
                if (entry.getValue().isBefore(ChunkStatus.BIOMES)) continue;
                ChunkPos dependency = entry.getKey();
                ChunkAccess physical =
                        level.getChunkSource()
                                .getChunk(
                                        dependency.x(), dependency.z(), ChunkStatus.BIOMES, false);
                if (physical == null
                        || physical.getHighestGeneratedStatus().isBefore(ChunkStatus.BIOMES)) {
                    throw new IllegalStateException(
                            "Physical biome dependency is not ready: " + dependency);
                }
                level.getNoiseBiome(dependency.x() * 4 + 2, 16, dependency.z() * 4 + 2);
                physicalBiomeChecks++;
            }
            // Exercise the same environment-attribute path used by runtime mob-cap accounting,
            // including quart samples across each side of this FULL chunk.
            for (int z : new int[] {0, 15}) {
                for (int x : new int[] {0, 15}) {
                    level.environmentAttributes()
                            .getValue(
                                    EnvironmentAttributes.NATURAL_MOB_SPAWNS,
                                    new BlockPos(
                                            pos.getMinBlockX() + x, 64, pos.getMinBlockZ() + z));
                }
            }
        }
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        PhysicalMosaicTrace.Snapshot trace = PhysicalMosaicTrace.snapshot();
        long artifactGenerations =
                metrics.artifactCaptureCount() - baselineMetrics.artifactCaptureCount();
        List<PhysicalMosaicTrace.SpawnSeed> spawnSeeds =
                appended(baselineTrace.spawnSeeds(), trace.spawnSeeds());
        if (reload && (artifactGenerations != 0 || !spawnSeeds.isEmpty())) {
            throw new IllegalStateException(
                    "Saved Mosaic Chunk re-entered canonical generation on reload: "
                            + metrics
                            + " spawn="
                            + spawnSeeds);
        }
        long targetArtifactGenerations =
                MosaicPhysicalMaterializer.artifactCaptures(level, target());
        long targetPublishes = MosaicPhysicalMaterializer.publishes(level, target());
        if (!reload && "concurrent".equals(mode) && targetArtifactGenerations > 1) {
            throw new IllegalStateException(
                    "Concurrent ordinary requests generated duplicate target Artifacts: "
                            + metrics
                            + " targetCaptures="
                            + targetArtifactGenerations);
        }

        BlockPos marker = marker(target(), level);
        boolean markerObservation = "full".equals(mode) || "reload".equals(mode);
        boolean markerExpected = markerObservation && Boolean.getBoolean(PREFIX + "expectMarker");
        if (markerExpected && !level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)) {
            throw new IllegalStateException(
                    "Saved Mosaic BlockState was overwritten during production reload");
        }
        if (markerObservation && Boolean.getBoolean(PREFIX + "mutate")) {
            level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        }

        MosaicPhysicalMaterializer.Metrics finalMetrics = MosaicPhysicalMaterializer.metrics();
        List<ChunkPos> observedPositions =
                "concurrent".equals(mode) ? List.of(target()) : positions;
        if (observedPositions.stream()
                .anyMatch(pos -> !MosaicPhysicalMaterializer.isIdle(level, pos))) {
            throw new IllegalStateException(
                    "Production observer left a requested target in flight: " + finalMetrics);
        }
        if (GenerationContextRegistry.bindingCount() != 0
                || MosaicSpawnContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Production request leaked an isolated context");
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.add(
                "developmentB",
                map(
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.features
                                .BDevelopmentWorld.metrics(server)));
        result.addProperty("mode", mode);
        result.addProperty("dimension", level.dimension().identifier().toString());
        result.addProperty("target", target().toString());
        result.addProperty("worldSeed", level.getServer().getWorldGenSettings().options().seed());
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        long targetLocalSeed = runtime.resolveLocalWorldSeed(level.dimension(), target());
        result.addProperty("localWorldSeed", targetLocalSeed);
        result.addProperty(
                "featureOrderingVersion", runtime.profile().featureOrderingAlgorithmVersion());
        result.addProperty("singleWriterRuns", singleWriterRuns.get());
        result.addProperty("discardedFeatureWrites", clippedWrites.get());
        if ("pair".equals(mode)) {
            ChunkPos adjacent = positions.get(1);
            long adjacentLocalSeed = runtime.resolveLocalWorldSeed(level.dimension(), adjacent);
            if (adjacentLocalSeed == targetLocalSeed) {
                throw new IllegalStateException(
                        "Adjacent production pair unexpectedly reused local world seed: "
                                + target()
                                + " and "
                                + adjacent);
            }
            result.addProperty("adjacent", adjacent.toString());
            result.addProperty("adjacentLocalWorldSeed", adjacentLocalSeed);
        }
        result.addProperty("ordinaryRequestCount", futures.size());
        result.addProperty("physicalBiomeChecks", physicalBiomeChecks);
        result.addProperty("returnedFullChunks", chunks.size());
        result.addProperty("artifactGenerations", artifactGenerations);
        result.addProperty(
                "artifactPublishes", metrics.publishCount() - baselineMetrics.publishCount());
        result.addProperty("targetArtifactGenerations", targetArtifactGenerations);
        result.addProperty("targetArtifactPublishes", targetPublishes);
        result.addProperty(
                "poiReconciliations",
                MosaicPhysicalPoiReconciler.metrics().invocationCount()
                        - baselinePoi.invocationCount());
        result.addProperty(
                "initializeLightCalls",
                delta(baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                        .getOrDefault(ChunkStatus.INITIALIZE_LIGHT.getName(), 0L));
        result.addProperty(
                "lightCalls",
                delta(baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                        .getOrDefault(ChunkStatus.LIGHT.getName(), 0L));
        result.addProperty(
                "spawnCalls",
                delta(baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                        .getOrDefault(ChunkStatus.SPAWN.getName(), 0L));
        result.addProperty(
                "fullCalls",
                delta(baselineTrace.physicalStatusCalls(), trace.physicalStatusCalls())
                        .getOrDefault(ChunkStatus.FULL.getName(), 0L));
        result.addProperty("fullChunkClass", chunks.getFirst().getClass().getName());
        result.addProperty("fullStatus", chunks.getFirst().getPersistedStatus().getName());
        result.addProperty("lightCorrect", chunks.getFirst().isLightCorrect());
        result.addProperty(
                "markerWritten", markerObservation && Boolean.getBoolean(PREFIX + "mutate"));
        result.addProperty(
                "markerPresent",
                markerObservation && level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK));
        result.addProperty("globalInFlightAtObservation", finalMetrics.inFlightCount());
        result.add(
                "lifecycleDuplicates",
                map(delta(baselineTrace.lifecycleDuplicates(), trace.lifecycleDuplicates())));
        result.add("runtimeTicks", map(delta(baselineTrace.runtimeTicks(), trace.runtimeTicks())));
        if (Boolean.getBoolean(PREFIX + "requestProof"))
            result.add("requestProof", requestProof(level, mode, positions, chunks));
        write(result);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3C2 ordinary production request PASS mode={} target={}", mode, target());
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true")))
            server.halt(false);
    }

    private static void verifyPartialBiomes(
            MinecraftServer server, ServerLevel level, String mode) {
        boolean reload = mode.equals("biomes-reload");
        if (!reload && !mode.equals("biomes-first"))
            throw new IllegalArgumentException("Unknown partial biome lifecycle mode");
        var pos = target();
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                .PhysicalBiomePreparation.observeForVerification(level, pos);
        long artifactsBefore = MosaicPhysicalMaterializer.artifactCaptures(level, pos);
        List<CompletableFuture<ChunkResult<ChunkAccess>>> requests = new ArrayList<>();
        for (int i = 0; i < 16; i++)
            requests.add(
                    level.getChunkSource()
                            .getChunkFuture(pos.x(), pos.z(), ChunkStatus.BIOMES, true));
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
        if (chunk.getPersistedStatus() != ChunkStatus.BIOMES
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
                                .PhysicalBiomePreparation.builds(level, pos)
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
                                .PhysicalBiomePreparation.builds(level, pos)
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
                            .getChunkFuture(racePos.x(), racePos.z(), ChunkStatus.BIOMES, true);
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
                    .isBefore(ChunkStatus.BIOMES))
                throw new IllegalStateException("Upgrade race exposed an empty palette");
            result.addProperty("concurrentUpgradeExactlyOneArtifact", true);
        }
        result.add(
                "partialBiomes",
                map(
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                .PhysicalBiomePreparation.metrics()));
        write(result);
        server.halt(false);
    }

    private static CompletableFuture<ChunkResult<ChunkAccess>> request(
            ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true);
    }

    private static JsonObject requestProof(
            ServerLevel level, String mode, List<ChunkPos> positions, List<LevelChunk> chunks) {
        String input = System.getProperty(PREFIX + "inputHash", "");
        if (!input.matches("[A-F0-9]{64}"))
            throw new IllegalStateException("Production request proof requires its source input");
        var identities = new java.util.IdentityHashMap<LevelChunk, Boolean>();
        for (int i = 0; i < chunks.size(); i++) {
            var expected = mode.equals("concurrent") ? target() : positions.get(i);
            var chunk = chunks.get(i);
            if (!chunk.getPos().equals(expected))
                throw new IllegalStateException("Production request returned another position");
            identities.put(chunk, Boolean.TRUE);
        }
        if (identities.size() != positions.size())
            throw new IllegalStateException("Repeated FULL request did not return the same Chunk");
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        var owners = new com.google.gson.JsonArray();
        for (var position : positions) {
            long captures = MosaicPhysicalMaterializer.artifactCaptures(level, position);
            long publications = MosaicPhysicalMaterializer.publishes(level, position);
            if (captures != 1 || publications != 1)
                throw new IllegalStateException(
                        "Requested physical owner was not materialized once: " + position);
            var owner = new JsonObject();
            owner.addProperty("x", position.x());
            owner.addProperty("z", position.z());
            owner.addProperty(
                    "localSeed",
                    Long.toString(runtime.resolveLocalWorldSeed(level.dimension(), position)));
            owner.addProperty("artifactCaptures", captures);
            owner.addProperty("publications", publications);
            owners.add(owner);
        }
        var result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("scope", "ORDINARY_FULL_REQUESTS_NOT_TICKING_OR_PLAYER_ENTRY");
        result.addProperty("inputHash", input);
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("logicalProcessors", Runtime.getRuntime().availableProcessors());
        result.addProperty(
                "save",
                level.getServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize()
                        .toString());
        result.addProperty("distinctChunkObjects", identities.size());
        result.addProperty("bindings", GenerationContextRegistry.bindingCount());
        result.addProperty("spawnBindings", MosaicSpawnContextRegistry.bindingCount());
        result.add("owners", owners);
        return result;
    }

    private static ServerLevel level(MinecraftServer server) {
        ResourceKey<Level> dimension =
                ResourceKey.create(
                        Registries.DIMENSION,
                        Identifier.parse(
                                System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        ServerLevel level = server.getLevel(dimension);
        if (level == null)
            throw new IllegalStateException(
                    "Missing Phase 3C2 dimension " + dimension.identifier());
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
        after.forEach(
                (key, value) -> {
                    long difference = value - before.getOrDefault(key, 0L);
                    if (difference != 0L) result.put(key, difference);
                });
        return Map.copyOf(result);
    }

    private static <T> List<T> appended(List<T> before, List<T> after) {
        if (after.size() < before.size()) {
            throw new IllegalStateException(
                    "Production trace regressed while observing ordinary requests");
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
            throw new IllegalStateException(
                    "Unable to write Phase 3C2 production result", exception);
        }
    }
}
