package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapInvoker;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalGenerationPlan;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalPoiReconciler;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;

/** Property-gated Phase 3B verification against the real physical scheduler and light engine. */
public final class Phase3BVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3b.";
    private static final Comparator<ChunkPos> Z_THEN_X = Comparator
            .comparingInt(ChunkPos::z)
            .thenComparingInt(ChunkPos::x);
    private static final Map<ServerLevel, Set<ChunkPos>> EXACT_STATUS_HOLDERS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static volatile boolean completed;

    private Phase3BVerification() {
    }

    public static boolean requested() {
        return !System.getProperty(PREFIX + "verify", "").isBlank();
    }

    public static void bootstrapProfileIfRequested(MinecraftServer server) {
        if (!requested()) return;
        server.getDataStorage().set(
                MosaicWorldProfileData.TYPE,
                new MosaicWorldProfileData(MosaicWorldProfile.current()));
    }

    public static boolean skipInitialSpawnIfRequested() {
        return requested();
    }

    public static boolean skipPrepareLevelsIfCompleted() {
        return requested() && completed;
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "verify", "");
        if (mode.isBlank()) return;
        ServerLevel level = level(server);
        ensureHiddenMosaic(level);
        JsonObject result = switch (mode) {
            case "single" -> runSingle(server, level);
            case "patch" -> runPatch(server, level);
            case "border" -> runBorder(server, level);
            case "fault" -> runFault(server, level);
            case "reload" -> runReload(server, level);
            case "initialize-save" -> runInitializeSave(server, level);
            case "poi-scan" -> runPoiScan(level);
            case "poi-scan-bee" -> runBeePoiScan(level);
            default -> throw new IllegalArgumentException("Unknown Phase 3B verification mode " + mode);
        };
        result.addProperty("mode", mode);
        result.addProperty("status", "PASS");
        write(result);
        completed = true;
        Phase3APhysicalMaterializationVerification.markCompleted();
        RandomNibble6Plus24Generator.LOGGER.info("Phase 3B {} PASS", mode);
        cleanupExactStatusHolders(server);
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) server.halt(false);
    }

    public static void cleanupExactStatusHolders(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) cleanupExactStatusHolders(level);
    }

    private static void cleanupExactStatusHolders(ServerLevel level) {
        Set<ChunkPos> positions = EXACT_STATUS_HOLDERS.remove(level);
        if (positions == null || positions.isEmpty()) return;
        var chunkMap = level.getChunkSource().chunkMap;
        ChunkMapInvoker invoker = (ChunkMapInvoker) chunkMap;
        for (ChunkPos pos : positions) {
            long packed = ChunkPos.pack(pos.x(), pos.z());
            var existing = chunkMap.getUpdatingChunkIfPresent(packed);
            if (existing != null) {
                invoker.randomnibble6plus24generator$invokeUpdateChunkScheduling(
                        packed, ChunkLevel.MAX_LEVEL + 1, existing, existing.getTicketLevel());
            }
        }
        invoker.randomnibble6plus24generator$invokePromoteChunkMap();
    }

    private static JsonObject runSingle(MinecraftServer server, ServerLevel level) {
        ChunkPos target = target();
        ChunkStatus status = requestedStatus();
        VerificationState state = begin(level);
        long started = System.nanoTime();
        ChunkAccess result = request(server, level, target, status);
        long totalNanos = System.nanoTime() - started;
        assertStatus(result, status);

        MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(target, status);
        List<ChunkAccess> physicalFootprint = chunks(level, plan.materializationObligations().keySet());
        PoiEvidence poi = verifyPoi(level, physicalFootprint);
        boolean poiIdempotenceVerified = false;
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "requirePoi", "false")) && poi.count() > 0) {
            ChunkAccess positive = physicalFootprint.stream()
                    .filter(chunk -> !MosaicPhysicalPoiReconciler.expectedEntries(chunk).isEmpty())
                    .findFirst()
                    .orElseThrow();
            List<MosaicPhysicalPoiReconciler.PoiEntry> beforeSecondReconcile =
                    MosaicPhysicalPoiReconciler.actualEntries(level, positive);
            MosaicPhysicalPoiReconciler.reconcile(level, positive);
            List<MosaicPhysicalPoiReconciler.PoiEntry> afterSecondReconcile =
                    MosaicPhysicalPoiReconciler.actualEntries(level, positive);
            if (!beforeSecondReconcile.equals(afterSecondReconcile)) {
                throw new IllegalStateException("Repeated Vanilla POI consistency scan was not idempotent");
            }
            poiIdempotenceVerified = true;
        }
        GeometryEvidence geometry = verifyGeometry(level, state, physicalFootprint);
        PhysicalLightSnapshot light = status == ChunkStatus.LIGHT
                ? PhysicalLightSnapshot.capture(level, result, geometry.targetPreLightHash(target))
                : null;
        assertClean(level, state, status, plan.materializationObligations().size());

        JsonObject json = commonResult(level, target, status, state, totalNanos, poi, geometry);
        json.addProperty("poiIdempotenceVerified", poiIdempotenceVerified);
        if (light != null) addLight(json, level, result, light, target);
        json.add("outerStatusDistribution", statusDistribution(level, target, plan.accumulatedRadius()));
        return json;
    }

    private static JsonObject runPatch(MinecraftServer server, ServerLevel level) {
        ChunkPos center = target();
        String orderName = System.getProperty(PREFIX + "order", "row-major");
        List<ChunkPos> targets = square(center, 1);
        order(targets, orderName);
        VerificationState state = begin(level);
        long started = System.nanoTime();
        Set<ChunkPos> allGeometry = new TreeSet<>(Z_THEN_X);
        for (ChunkPos pos : targets) {
            allGeometry.addAll(MosaicPhysicalGenerationPlan.derive(pos, ChunkStatus.LIGHT)
                    .materializationObligations().keySet());
        }
        if (!orderName.equals("parallel")) {
            for (ChunkPos pos : allGeometry) request(server, level, pos, ChunkStatus.FEATURES);
        }
        long artifactsBeforeLight = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
        if (orderName.equals("parallel")) {
            requestParallel(server, level, targets, ChunkStatus.LIGHT).forEach(chunk ->
                    assertStatus(chunk, ChunkStatus.LIGHT));
        } else {
            for (ChunkPos pos : targets) assertStatus(request(server, level, pos, ChunkStatus.LIGHT), ChunkStatus.LIGHT);
        }
        long totalNanos = System.nanoTime() - started;
        if (!orderName.equals("parallel")
                && MosaicPhysicalMaterializer.metrics().artifactCaptureCount() != artifactsBeforeLight) {
            throw new IllegalStateException("Patch LIGHT re-canonicalized preassembled physical geometry");
        }

        JsonObject hashes = new JsonObject();
        List<ChunkAccess> physical = chunks(level, allGeometry);
        PoiEvidence poi = verifyPoi(level, physical);
        GeometryEvidence geometry = verifyGeometry(level, state, physical);
        for (ChunkPos pos : square(center, 1)) {
            ChunkAccess chunk = physicalChunk(level, pos, ChunkStatus.LIGHT);
            assertStatus(chunk, ChunkStatus.LIGHT);
            PhysicalLightSnapshot snapshot = PhysicalLightSnapshot.capture(
                    level, chunk, geometry.targetPreLightHash(pos));
            hashes.addProperty(pos.x() + "," + pos.z(), snapshot.lightDataHash());
        }
        assertClean(level, state, ChunkStatus.LIGHT, allGeometry.size());

        JsonObject json = commonResult(level, center, ChunkStatus.LIGHT, state, totalNanos, poi, geometry);
        json.addProperty("order", orderName);
        json.addProperty("lightTargets", 9);
        json.addProperty("materializedGeometry", allGeometry.size());
        json.add("lightHashes", hashes);
        return json;
    }

    private static JsonObject runBorder(MinecraftServer server, ServerLevel level) {
        ChunkPos target = target();
        ChunkPos east = new ChunkPos(target.x() + 1, target.z());
        boolean source = Boolean.parseBoolean(System.getProperty(PREFIX + "borderSource", "true"));
        VerificationState state = begin(level);
        Set<ChunkPos> footprintSet = new TreeSet<>(Z_THEN_X);
        footprintSet.addAll(square(target, 1));
        footprintSet.addAll(square(east, 1));
        List<ChunkPos> footprint = List.copyOf(footprintSet);
        for (ChunkPos pos : footprint) request(server, level, pos, ChunkStatus.FEATURES);
        long artifactsBeforeLight = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();

        int y = Math.max(level.getSeaLevel() + 16, level.getMinY() + 32);
        int z = target.getMiddleBlockZ();
        for (int x = target.getMaxBlockX() - 4; x <= east.getMinBlockX() + 2; x++) {
            ChunkPos owner = ChunkPos.containing(new BlockPos(x, y, z));
            ChunkAccess chunk = physicalChunk(level, owner, ChunkStatus.FEATURES);
            chunk.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 0);
        }
        BlockPos sourcePos = new BlockPos(east.getMinBlockX(), y, z);
        if (source) {
            physicalChunk(level, east, ChunkStatus.FEATURES)
                    .setBlockState(sourcePos, Blocks.GLOWSTONE.defaultBlockState(), 0);
        }
        // The diagnostic mutation defines the assembled physical pre-light geometry for this
        // fixture. Lighting must preserve this state exactly; it is not written back to Artifact.
        for (ChunkPos pos : footprint) {
            ChunkAccess physical = physicalChunk(level, pos, ChunkStatus.FEATURES);
            FeatureStableSnapshot snapshot = FeatureStableSnapshot.capture(
                    level.dimension().identifier().toString(), physical, level.registryAccess());
            state.before().put(pos, BeforeChunk.from(snapshot));
        }
        BlockPos samplePos = new BlockPos(target.getMaxBlockX(), y, z);
        long started = System.nanoTime();
        request(server, level, east, ChunkStatus.LIGHT);
        ChunkAccess lit = request(server, level, target, ChunkStatus.LIGHT);
        long totalNanos = System.nanoTime() - started;
        if (MosaicPhysicalMaterializer.metrics().artifactCaptureCount() != artifactsBeforeLight) {
            throw new IllegalStateException("Border LIGHT re-canonicalized existing physical geometry");
        }
        int blockLight = level.getChunkSource().getLightEngine()
                .getLayerListener(LightLayer.BLOCK).getLightValue(samplePos);
        if (source && blockLight <= 0) {
            throw new IllegalStateException("Physical east-neighbor block light did not cross into target");
        }
        if (!source && blockLight != 0) {
            throw new IllegalStateException("Negative border control unexpectedly received block light " + blockLight);
        }
        PhysicalLightSnapshot snapshot = PhysicalLightSnapshot.capture(
                level, lit, FeatureStableSnapshot.capture(
                        level.dimension().identifier().toString(), lit, level.registryAccess()).worldgenDataHash());
        PoiEvidence poi = verifyPoi(level, chunks(level, footprint));
        GeometryEvidence geometry = verifyGeometry(level, state, chunks(level, footprint));
        assertClean(level, state, ChunkStatus.LIGHT, footprint.size());

        JsonObject json = commonResult(level, target, ChunkStatus.LIGHT, state, totalNanos, poi, geometry);
        json.addProperty("borderSource", source);
        json.addProperty("sourceX", sourcePos.getX());
        json.addProperty("sourceY", sourcePos.getY());
        json.addProperty("sourceZ", sourcePos.getZ());
        json.addProperty("sampleBlockLight", blockLight);
        json.addProperty("lightHash", snapshot.hash());
        json.addProperty("lightDataHash", snapshot.lightDataHash());
        addLightComponents(json, snapshot);
        json.addProperty("artifactCountBeforeLight", artifactsBeforeLight);
        json.addProperty("artifactCountAfterLight", MosaicPhysicalMaterializer.metrics().artifactCaptureCount());
        return json;
    }

    private static JsonObject runFault(MinecraftServer server, ServerLevel level) {
        ChunkPos target = target();
        VerificationState state = begin(level);
        for (ChunkPos pos : square(target, 1)) request(server, level, pos, ChunkStatus.FEATURES);
        ChunkAccess features = physicalChunk(level, target, ChunkStatus.FEATURES);
        long artifacts = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
        MosaicPhysicalPoiReconciler.setVerificationFault(
                MosaicPhysicalPoiReconciler.FaultPoint.AFTER_RECONCILIATION_BEFORE_INITIALIZE_LIGHT);
        try {
            MosaicPhysicalPoiReconciler.reconcile(level, features);
            throw new IllegalStateException("Expected injected POI failure");
        } catch (MosaicPhysicalPoiReconciler.InjectedPoiFailure expectedFailure) {
            // Publication remains authoritative FEATURES; retry starts with POI already consistent.
        }
        if (features.getPersistedStatus() != ChunkStatus.FEATURES) {
            throw new IllegalStateException("POI fault advanced physical status");
        }
        List<MosaicPhysicalPoiReconciler.PoiEntry> expected = MosaicPhysicalPoiReconciler.expectedEntries(features);
        if (!expected.equals(MosaicPhysicalPoiReconciler.actualEntries(level, features))) {
            throw new IllegalStateException("POI fault did not leave idempotently reconciled state");
        }
        request(server, level, target, ChunkStatus.INITIALIZE_LIGHT);
        if (MosaicPhysicalMaterializer.metrics().artifactCaptureCount() != artifacts) {
            throw new IllegalStateException("POI retry regenerated Artifact");
        }

        PhysicalMosaicTrace.setVerificationFault(
                PhysicalMosaicTrace.FaultPoint.AFTER_INITIALIZE_LIGHT_BEFORE_LIGHT);
        GenerationChunkHolder initializedHolder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(target.x(), target.z()));
        try {
            PhysicalMosaicTrace.beforeLightStep(level, initializedHolder);
            throw new IllegalStateException("Expected injected post-INITIALIZE_LIGHT failure");
        } catch (PhysicalMosaicTrace.InjectedDerivedStateFailure expectedFailure) {
            // The initialized future remains successful and can feed the later LIGHT retry.
        }
        if (features.getPersistedStatus() != ChunkStatus.INITIALIZE_LIGHT) {
            throw new IllegalStateException("Post-initialize fault lost INITIALIZE_LIGHT state");
        }
        request(server, level, target, ChunkStatus.LIGHT);
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        if (metrics.inFlightCount() != 0
                || metrics.requestedTargetCount() != 0
                || metrics.materializationObligationCount() != 0
                || metrics.physicalStatusAllowanceCount() != 0
                || GenerationContextRegistry.bindingCount() != 0
                || PhysicalMosaicTrace.snapshot().activeStageCount() != 0) {
            throw new IllegalStateException("Phase 3B fault cleanup leaked state " + metrics);
        }
        JsonObject json = new JsonObject();
        json.addProperty("poiFaultRetry", true);
        json.addProperty("postInitializeFaultRetry", true);
        json.addProperty("artifactCountBeforeFaults", artifacts);
        json.addProperty("artifactCountAfterFaults", metrics.artifactCaptureCount());
        json.addProperty("finalStatus", features.getPersistedStatus().getName());
        json.addProperty("finalLightCorrect", features.isLightCorrect());
        json.addProperty("physicalEntitiesBefore", state.entitiesBefore());
        json.addProperty("physicalEntitiesAfter", entityCount(level));
        return json;
    }

    private static JsonObject runReload(MinecraftServer server, ServerLevel level) {
        ChunkPos target = target();
        VerificationState state = begin(level);
        ChunkAccess loadedAtFeatures = request(server, level, target, ChunkStatus.FEATURES);
        String preLight = FeatureStableSnapshot.capture(
                level.dimension().identifier().toString(), loadedAtFeatures, level.registryAccess()).worldgenDataHash();
        ChunkAccess lit = request(server, level, target, ChunkStatus.LIGHT);
        PhysicalLightSnapshot snapshot = PhysicalLightSnapshot.capture(level, lit, preLight);
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "expectNoArtifacts", "true"))
                && metrics.artifactCaptureCount() != 0) {
            throw new IllegalStateException("Saved physical Chunk regenerated Artifact before LIGHT");
        }
        PoiEvidence poi = verifyPoi(level, List.of(lit));
        JsonObject json = commonResult(
                level, target, ChunkStatus.LIGHT, state, 0L, poi,
                new GeometryEvidence(Map.of(target, preLight), 1, 0));
        json.addProperty("lightHash", snapshot.hash());
        json.addProperty("lightDataHash", snapshot.lightDataHash());
        addLightComponents(json, snapshot);
        json.addProperty("artifactRegeneration", metrics.artifactCaptureCount());
        json.addProperty("reloadedStatus", lit.getPersistedStatus().getName());
        json.addProperty("reloadedLightCorrect", lit.isLightCorrect());
        return json;
    }

    private static JsonObject runInitializeSave(MinecraftServer server, ServerLevel level) {
        ChunkPos target = target();
        VerificationState state = begin(level);
        List<ChunkPos> footprint = square(target, 1);
        for (ChunkPos pos : footprint) request(server, level, pos, ChunkStatus.FEATURES);
        long artifacts = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
        ChunkAccess initialized = request(server, level, target, ChunkStatus.INITIALIZE_LIGHT);
        assertStatus(initialized, ChunkStatus.INITIALIZE_LIGHT);
        if (MosaicPhysicalMaterializer.metrics().artifactCaptureCount() != artifacts) {
            throw new IllegalStateException("INITIALIZE_LIGHT re-canonicalized preassembled geometry");
        }
        PoiEvidence poi = verifyPoi(level, List.of(initialized));
        GeometryEvidence geometry = verifyGeometry(level, state, chunks(level, footprint));
        assertClean(level, state, ChunkStatus.INITIALIZE_LIGHT, footprint.size());
        JsonObject json = commonResult(
                level, target, ChunkStatus.INITIALIZE_LIGHT, state, 0L, poi, geometry);
        json.addProperty("persistedIntermediateStatus", initialized.getPersistedStatus().getName());
        json.addProperty("lightCorrect", initialized.isLightCorrect());
        json.addProperty("materializedFootprint", footprint.size());
        return json;
    }

    private static JsonObject runPoiScan(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            throw new IllegalArgumentException("Phase 3B POI scan currently targets Overworld village fixtures");
        }
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        MosaicSeedResolver resolver = new MosaicSeedResolver(profile);
        MosaicChunkGenerator identityGenerator = (MosaicChunkGenerator) level.getChunkSource().getGenerator();
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                identityGenerator.getBiomeSource(), identityGenerator.generatorSettings());
        var structureRegistry = level.registryAccess().lookupOrThrow(
                net.minecraft.core.registries.Registries.STRUCTURE);
        List<? extends net.minecraft.core.Holder<Structure>> villages = List.of(
                "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga")
                .stream()
                .map(name -> ResourceKey.create(
                        net.minecraft.core.registries.Registries.STRUCTURE,
                        Identifier.withDefaultNamespace(name)))
                .map(key -> structureRegistry.get(key).orElseThrow())
                .toList();
        var structureSets = level.registryAccess().lookupOrThrow(
                net.minecraft.core.registries.Registries.STRUCTURE_SET);
        var noises = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE);
        long[] masters = {0L, 1L, -1L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE};
        int limit = Integer.getInteger(PREFIX + "scanLimit", 2000);
        int startIndex = Integer.getInteger(PREFIX + "scanStart", 0);
        int desired = Integer.getInteger(PREFIX + "scanMatches", 12);
        long random = 0x243f6a8885a308d3L;
        int placementCandidates = 0;
        int generatedCandidates = 0;
        JsonArray matches = new JsonArray();
        Set<String> coveredTypes = new TreeSet<>();
        for (int skipped = 0; skipped < startIndex; skipped++) {
            random = random * 6364136223846793005L + 1442695040888963407L;
            random = random * 6364136223846793005L + 1442695040888963407L;
        }
        for (int index = startIndex; index < limit && matches.size() < desired; index++) {
            random = random * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((random >>> 16) % 20001L) - 10000;
            random = random * 6364136223846793005L + 1442695040888963407L;
            int z = (int) ((random >>> 16) % 20001L) - 10000;
            long master = masters[index % masters.length];
            ChunkPos target = new ChunkPos(x, z);
            long localSeed = resolver.resolveLocalWorldSeed(master, Level.OVERWORLD, target);
            RandomState randomState = RandomState.create(generator.generatorSettings().value(), noises, localSeed);
            var structureState = generator.createState(structureSets, randomState, localSeed);
            structureState.ensureStructuresGenerated();
            boolean nearbyVillage = false;
            for (var village : villages) {
                for (var placement : structureState.getPlacementsForStructure(village)) {
                    for (int dz = -8; dz <= 8 && !nearbyVillage; dz++) {
                        for (int dx = -8; dx <= 8; dx++) {
                            if (placement.isStructureChunk(structureState, x + dx, z + dz)) {
                                nearbyVillage = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!nearbyVillage) continue;
            placementCandidates++;
            var run = new IsolatedGenerationSession(profile).generateFeaturesStable(level, master, target);
            generatedCandidates++;
            List<MosaicPhysicalPoiReconciler.PoiEntry> poi =
                    MosaicPhysicalPoiReconciler.expectedEntries(run.targetChunk());
            if (poi.isEmpty()) continue;
            JsonObject match = new JsonObject();
            match.addProperty("masterSeed", Long.toString(master));
            match.addProperty("localSeed", Long.toString(localSeed));
            match.addProperty("chunkX", x);
            match.addProperty("chunkZ", z);
            match.addProperty("poiCount", poi.size());
            Set<String> types = new TreeSet<>();
            poi.forEach(entry -> types.add(entry.type()));
            coveredTypes.addAll(types);
            match.add("poiTypes", strings(types));
            matches.add(match);
        }
        if (matches.size() < desired) {
            throw new IllegalStateException("POI scan found " + matches.size() + "/" + desired
                    + " fixtures after targets=" + limit
                    + " placementCandidates=" + placementCandidates
                    + " generatedCandidates=" + generatedCandidates);
        }
        JsonObject result = new JsonObject();
        result.addProperty("tested", limit);
        result.addProperty("startIndex", startIndex);
        result.addProperty("placementCandidates", placementCandidates);
        result.addProperty("generatedCandidates", generatedCandidates);
        result.addProperty("matches", matches.size());
        result.add("coveredTypes", strings(coveredTypes));
        result.add("fixtures", matches);
        return result;
    }

    private static JsonObject runBeePoiScan(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            throw new IllegalArgumentException("Phase 3B bee POI scan requires Overworld");
        }
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        MosaicSeedResolver resolver = new MosaicSeedResolver(profile);
        MosaicChunkGenerator identityGenerator = (MosaicChunkGenerator) level.getChunkSource().getGenerator();
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                identityGenerator.getBiomeSource(), identityGenerator.generatorSettings());
        var noises = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE);
        long[] masters = {0L, 1L, -1L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE};
        Set<String> beeBiomes = Set.of(
                "plains", "sunflower_plains", "meadow", "flower_forest", "forest",
                "birch_forest", "old_growth_birch_forest", "cherry_grove");
        int limit = Integer.getInteger(PREFIX + "scanLimit", 1000);
        int startIndex = Integer.getInteger(PREFIX + "scanStart", 0);
        int desired = Integer.getInteger(PREFIX + "scanMatches", 8);
        long random = 0x13198a2e03707344L;
        int biomeCandidates = 0;
        int generatedCandidates = 0;
        JsonArray matches = new JsonArray();
        for (int skipped = 0; skipped < startIndex; skipped++) {
            random = random * 6364136223846793005L + 1442695040888963407L;
            random = random * 6364136223846793005L + 1442695040888963407L;
        }
        for (int index = startIndex; index < limit && matches.size() < desired; index++) {
            random = random * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((random >>> 16) % 20001L) - 10000;
            random = random * 6364136223846793005L + 1442695040888963407L;
            int z = (int) ((random >>> 16) % 20001L) - 10000;
            long master = masters[index % masters.length];
            ChunkPos target = new ChunkPos(x, z);
            long localSeed = resolver.resolveLocalWorldSeed(master, Level.OVERWORLD, target);
            RandomState randomState = RandomState.create(generator.generatorSettings().value(), noises, localSeed);
            var biome = generator.getBiomeSource().getNoiseBiome(
                    QuartPos.fromBlock(target.getMiddleBlockX()),
                    QuartPos.fromBlock(100),
                    QuartPos.fromBlock(target.getMiddleBlockZ()),
                    randomState.sampler());
            String biomePath = biome.unwrapKey()
                    .map(key -> key.identifier().getPath())
                    .orElse("");
            if (!beeBiomes.contains(biomePath)) continue;
            biomeCandidates++;
            var run = new IsolatedGenerationSession(profile).generateFeaturesStable(level, master, target);
            generatedCandidates++;
            List<MosaicPhysicalPoiReconciler.PoiEntry> poi = MosaicPhysicalPoiReconciler
                    .expectedEntries(run.targetChunk()).stream()
                    .filter(entry -> entry.type().equals("minecraft:bee_nest")
                            || entry.type().equals("minecraft:beehive"))
                    .toList();
            if (poi.isEmpty()) continue;
            JsonObject match = new JsonObject();
            match.addProperty("masterSeed", Long.toString(master));
            match.addProperty("localSeed", Long.toString(localSeed));
            match.addProperty("chunkX", x);
            match.addProperty("chunkZ", z);
            match.addProperty("biome", biome.unwrapKey()
                    .map(key -> key.identifier().toString()).orElse("direct"));
            match.addProperty("poiCount", poi.size());
            Set<String> types = new TreeSet<>();
            poi.forEach(entry -> types.add(entry.type()));
            match.add("poiTypes", strings(types));
            matches.add(match);
        }
        if (matches.size() < desired) {
            throw new IllegalStateException("Bee POI scan found " + matches.size() + "/" + desired
                    + " after targets=" + limit
                    + " biomeCandidates=" + biomeCandidates
                    + " generatedCandidates=" + generatedCandidates);
        }
        JsonObject result = new JsonObject();
        result.addProperty("tested", limit);
        result.addProperty("startIndex", startIndex);
        result.addProperty("biomeCandidates", biomeCandidates);
        result.addProperty("generatedCandidates", generatedCandidates);
        result.addProperty("matches", matches.size());
        result.add("fixtures", matches);
        return result;
    }

    private static VerificationState begin(ServerLevel level) {
        MosaicPhysicalMaterializer.resetVerificationState();
        MosaicPhysicalPoiReconciler.resetVerificationState();
        PhysicalMosaicTrace.reset();
        PhysicalMosaicTrace.enableDetailedVerification();
        Map<ChunkPos, BeforeChunk> before = new ConcurrentHashMap<>();
        List<MosaicPhysicalMaterializer.Publication> publications = new CopyOnWriteArrayList<>();
        MosaicPhysicalMaterializer.setVerificationObserver(publication -> {
            ProtoChunk staging = publication.prepared().artifact().rehydrate(
                    level.registryAccess(), StructurePieceSerializationContext.fromLevel(level));
            FeatureStableSnapshot snapshot = FeatureStableSnapshot.capture(
                    level.dimension().identifier().toString(), staging, level.registryAccess());
            before.put(publication.key().pos(), BeforeChunk.from(snapshot));
            publications.add(publication);
        });
        return new VerificationState(
                before,
                publications,
                entityCount(level),
                level.getBlockTicks().count(),
                level.getFluidTicks().count());
    }

    private static GeometryEvidence verifyGeometry(
            ServerLevel level,
            VerificationState state,
            List<ChunkAccess> chunks) {
        Map<ChunkPos, String> preLightHashes = new HashMap<>();
        int compared = 0;
        for (ChunkAccess physical : chunks) {
            BeforeChunk before = state.before().get(physical.getPos());
            if (before == null) continue;
            FeatureStableSnapshot after = FeatureStableSnapshot.capture(
                    level.dimension().identifier().toString(), physical, level.registryAccess());
            if (!before.worldgenHash().equals(after.worldgenDataHash())
                    || !before.rawEntityNbt().equals(after.rawEntityNbt())
                    || !before.blockEntityNbt().equals(after.blockEntityNbt())
                    || !before.blockTicks().equals(after.blockTickData())
                    || !before.fluidTicks().equals(after.fluidTickData())
                    || !before.structures().equals(after.structureStartData())) {
                throw new IllegalStateException("Physical lighting changed canonical geometry at " + physical.getPos());
            }
            preLightHashes.put(physical.getPos(), before.worldgenHash());
            compared++;
        }
        return new GeometryEvidence(Map.copyOf(preLightHashes), chunks.size(), compared);
    }

    private static PoiEvidence verifyPoi(ServerLevel level, List<ChunkAccess> chunks) {
        int total = 0;
        Set<String> types = new TreeSet<>();
        Map<String, List<MosaicPhysicalPoiReconciler.PoiEntry>> positiveChunks = new TreeMap<>();
        for (ChunkAccess chunk : chunks) {
            List<MosaicPhysicalPoiReconciler.PoiEntry> expected = MosaicPhysicalPoiReconciler.expectedEntries(chunk);
            List<MosaicPhysicalPoiReconciler.PoiEntry> actual = MosaicPhysicalPoiReconciler.actualEntries(level, chunk);
            if (!expected.equals(actual)) {
                throw new IllegalStateException("Physical POI mismatch at " + chunk.getPos()
                        + " expected=" + expected + " actual=" + actual);
            }
            total += expected.size();
            expected.forEach(entry -> types.add(entry.type()));
            if (!expected.isEmpty()) {
                positiveChunks.put(chunk.getPos().x() + "," + chunk.getPos().z(), expected);
            }
        }
        boolean requirePositive = Boolean.parseBoolean(System.getProperty(PREFIX + "requirePoi", "false"));
        if (requirePositive && total == 0) throw new IllegalStateException("Fixture did not contain a real POI");
        return new PoiEvidence(total, Set.copyOf(types), Map.copyOf(positiveChunks));
    }

    private static void assertClean(
            ServerLevel level,
            VerificationState state,
            ChunkStatus requestedStatus,
            int expectedArtifacts) {
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        if (metrics.inFlightCount() != 0
                || metrics.requestedTargetCount() != 0
                || metrics.materializationObligationCount() != 0
                || metrics.physicalStatusAllowanceCount() != 0
                || metrics.artifactCaptureCount() != expectedArtifacts
                || metrics.publishCount() != expectedArtifacts
                || GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Invalid Phase 3B cleanup/materialization metrics " + metrics);
        }
        PhysicalMosaicTrace.Snapshot trace = PhysicalMosaicTrace.snapshot();
        if (trace.totalGeneratorCalls() != 0L
                || !trace.forbiddenStatusCalls().isEmpty()
                || trace.activeStageCount() != 0) {
            throw new IllegalStateException("Physical Mosaic generator/derived-state escape " + trace);
        }
        if (entityCount(level) != state.entitiesBefore()
                || level.getBlockTicks().count() != state.blockTicksBefore()
                || level.getFluidTicks().count() != state.fluidTicksBefore()) {
            throw new IllegalStateException("Physical gameplay state changed before FULL");
        }
        if (requestedStatus == ChunkStatus.LIGHT && trace.lightQueries().stream()
                .anyMatch(query -> query.plannedMaterializedGeometry() && !query.returnedPhysicalChunk())) {
            throw new IllegalStateException("Physical light query missed planned geometry");
        }
    }

    private static JsonObject commonResult(
            ServerLevel level,
            ChunkPos target,
            ChunkStatus status,
            VerificationState state,
            long totalNanos,
            PoiEvidence poi,
            GeometryEvidence geometry) {
        MosaicPhysicalMaterializer.Metrics materializer = MosaicPhysicalMaterializer.metrics();
        MosaicPhysicalPoiReconciler.Metrics poiMetrics = MosaicPhysicalPoiReconciler.metrics();
        PhysicalMosaicTrace.Snapshot trace = PhysicalMosaicTrace.snapshot();
        JsonObject json = new JsonObject();
        json.addProperty("dimension", level.dimension().identifier().toString());
        json.addProperty("chunkX", target.x());
        json.addProperty("chunkZ", target.z());
        json.addProperty("requestedStatus", status.getName());
        json.addProperty("artifactGenerations", materializer.artifactCaptureCount());
        json.addProperty("artifactPublishes", materializer.publishCount());
        json.addProperty("artifactDedupHits", materializer.dedupHits());
        json.addProperty("planCount", materializer.planCount());
        json.addProperty("poiCount", poi.count());
        json.add("poiTypes", strings(poi.types()));
        json.addProperty("poiPositiveChunks", poi.positiveChunks().size());
        JsonObject poiByChunk = new JsonObject();
        poi.positiveChunks().forEach((pos, entries) -> {
            JsonObject evidence = new JsonObject();
            evidence.addProperty("count", entries.size());
            Set<String> entryTypes = new TreeSet<>();
            JsonArray records = new JsonArray();
            for (var entry : entries) {
                entryTypes.add(entry.type());
                records.add(entry.type() + "@" + entry.pos() + ":free=" + entry.freeTickets());
            }
            evidence.add("types", strings(entryTypes));
            evidence.add("records", records);
            poiByChunk.add(pos, evidence);
        });
        json.add("poiByChunk", poiByChunk);
        json.addProperty("poiReconciliations", poiMetrics.invocationCount());
        json.addProperty("poiSections", poiMetrics.sectionCount());
        json.addProperty("poiMicros", poiMetrics.totalNanos() / 1_000L);
        json.addProperty("initializeLightCalls", trace.physicalStatusCalls()
                .getOrDefault(ChunkStatus.INITIALIZE_LIGHT.getName(), 0L));
        json.addProperty("lightCalls", trace.physicalStatusCalls()
                .getOrDefault(ChunkStatus.LIGHT.getName(), 0L));
        json.addProperty("initializeLightMicros", trace.initializeLightNanos() / 1_000L);
        json.addProperty("lightMicros", trace.lightNanos() / 1_000L);
        json.addProperty("lightQueryCount", trace.lightQueries().size());
        json.add("lightQueryFootprint", lightQueryFootprint(trace.lightQueries(), target));
        json.addProperty("physicalEntitiesBefore", state.entitiesBefore());
        json.addProperty("physicalEntitiesAfter", entityCount(level));
        json.addProperty("physicalBlockTicksBefore", state.blockTicksBefore());
        json.addProperty("physicalBlockTicksAfter", level.getBlockTicks().count());
        json.addProperty("physicalFluidTicksBefore", state.fluidTicksBefore());
        json.addProperty("physicalFluidTicksAfter", level.getFluidTicks().count());
        json.addProperty("geometryChunks", geometry.totalChunks());
        json.addProperty("geometryCompared", geometry.comparedChunks());
        json.addProperty("totalMillis", totalNanos / 1_000_000L);
        json.addProperty("physicalLightEngineClass", level.getChunkSource().getLightEngine().getClass().getName());
        json.addProperty("dimensionHasSkyLight", level.dimensionType().hasSkyLight());
        json.add("physicalGeneratorCalls", map(trace.generatorCalls()));
        json.add("forbiddenStatusCalls", map(trace.forbiddenStatusCalls()));
        return json;
    }

    private static void addLight(
            JsonObject json,
            ServerLevel level,
            ChunkAccess chunk,
            PhysicalLightSnapshot snapshot,
            ChunkPos target) {
        int maxBlock = 0;
        int maxSky = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
            for (int z = target.getMinBlockZ(); z <= target.getMaxBlockZ(); z++) {
                for (int x = target.getMinBlockX(); x <= target.getMaxBlockX(); x++) {
                    cursor.set(x, y, z);
                    maxBlock = Math.max(maxBlock, level.getChunkSource().getLightEngine()
                            .getLayerListener(LightLayer.BLOCK).getLightValue(cursor));
                    maxSky = Math.max(maxSky, level.getChunkSource().getLightEngine()
                            .getLayerListener(LightLayer.SKY).getLightValue(cursor));
                }
            }
        }
        json.addProperty("lightHash", snapshot.hash());
        json.addProperty("lightDataHash", snapshot.lightDataHash());
        addLightComponents(json, snapshot);
        json.addProperty("lightCorrect", snapshot.lightCorrect());
        json.addProperty("finalStatus", snapshot.status());
        json.addProperty("maxBlockLight", maxBlock);
        json.addProperty("maxSkyLight", maxSky);
    }

    private static void addLightComponents(JsonObject json, PhysicalLightSnapshot snapshot) {
        json.addProperty("blockLightHash", snapshot.blockLightHash());
        json.addProperty("skyLightHash", snapshot.skyLightHash());
        json.addProperty("skyLightSourcesHash", snapshot.skyLightSourcesHash());
        json.addProperty("nonNullBlockLightSections", snapshot.nonNullBlockLightSections());
        json.addProperty("nonNullSkyLightSections", snapshot.nonNullSkyLightSections());
        JsonObject blockLayers = new JsonObject();
        snapshot.blockLightLayerHashes().forEach((section, hash) ->
                blockLayers.addProperty(Integer.toString(section), hash));
        JsonObject skyLayers = new JsonObject();
        snapshot.skyLightLayerHashes().forEach((section, hash) ->
                skyLayers.addProperty(Integer.toString(section), hash));
        json.add("blockLightLayers", blockLayers);
        json.add("skyLightLayers", skyLayers);
    }

    private static ChunkAccess request(
            MinecraftServer server, ServerLevel level, ChunkPos pos, ChunkStatus status) {
        MosaicPhysicalMaterializer.registerPhysicalRequest(level, status, pos);
        ensurePhysicalHolders(level, pos, status);
        ChunkGenerationTask task = ChunkGenerationTask.create(level.getChunkSource().chunkMap, status, pos);
        runTask(server, task);
        ChunkAccess result = task.getCenter().getChunkIfPresentUnchecked(status);
        if (result == null) {
            String futures = task.getCenter().getAllFutures().stream()
                    .map(pair -> pair.getFirst() + "=" + (pair.getSecond() == null
                            ? "<not-created>"
                            : pair.getSecond().getNow(null)))
                    .toList().toString();
            throw new IllegalStateException(
                    "Exact physical task canceled target=" + pos
                            + " requested=" + status
                            + " persisted=" + task.getCenter().getPersistedStatus()
                            + " latestStatus=" + task.getCenter().getLatestStatus()
                            + " futures=" + futures
                            + " materializer=" + MosaicPhysicalMaterializer.metrics()
                            + " poi=" + MosaicPhysicalPoiReconciler.metrics()
                            + " trace=" + PhysicalMosaicTrace.snapshot());
        }
        return result;
    }

    private static List<ChunkAccess> requestParallel(
            MinecraftServer server, ServerLevel level, List<ChunkPos> positions, ChunkStatus status) {
        List<ChunkGenerationTask> tasks = new ArrayList<>();
        for (ChunkPos pos : positions) {
            MosaicPhysicalMaterializer.registerPhysicalRequest(level, status, pos);
            ensurePhysicalHolders(level, pos, status);
            tasks.add(ChunkGenerationTask.create(level.getChunkSource().chunkMap, status, pos));
        }
        List<ChunkGenerationTask> remaining = new ArrayList<>(tasks);
        while (!remaining.isEmpty()) {
            List<CompletableFuture<?>> waits = new ArrayList<>();
            var iterator = remaining.iterator();
            while (iterator.hasNext()) {
                CompletableFuture<?> wait = iterator.next().runUntilWait();
                if (wait == null) iterator.remove();
                else waits.add(wait);
            }
            if (!waits.isEmpty()) {
                CompletableFuture<Void> all = CompletableFuture.allOf(waits.toArray(CompletableFuture[]::new));
                server.managedBlock(all::isDone);
                all.join();
            }
        }
        return tasks.stream()
                .map(task -> task.getCenter().getChunkIfPresentUnchecked(status))
                .toList();
    }

    private static void runTask(MinecraftServer server, ChunkGenerationTask task) {
        while (true) {
            CompletableFuture<?> wait = task.runUntilWait();
            if (wait == null) return;
            server.managedBlock(wait::isDone);
            wait.join();
        }
    }

    private static void ensurePhysicalHolders(ServerLevel level, ChunkPos center, ChunkStatus status) {
        var dependencies = net.minecraft.world.level.chunk.status.ChunkPyramid.GENERATION_PYRAMID
                .getStepTo(status).accumulatedDependencies();
        int radius = dependencies.getRadius();
        var chunkMap = level.getChunkSource().chunkMap;
        ChunkMapInvoker invoker = (ChunkMapInvoker) chunkMap;
        for (ChunkPos pos : square(center, radius)) {
            long packed = ChunkPos.pack(pos.x(), pos.z());
            int distance = Math.max(Math.abs(pos.x() - center.x()), Math.abs(pos.z() - center.z()));
            int requiredLevel = ChunkLevel.byStatus(dependencies.get(distance));
            var existing = chunkMap.getUpdatingChunkIfPresent(packed);
            if (existing == null) {
                existing = invoker.randomnibble6plus24generator$invokeUpdateChunkScheduling(
                        packed, requiredLevel, null, ChunkLevel.MAX_LEVEL + 1);
                EXACT_STATUS_HOLDERS.computeIfAbsent(level, ignored -> ConcurrentHashMap.newKeySet()).add(pos);
            } else if (existing.getTicketLevel() > requiredLevel) {
                invoker.randomnibble6plus24generator$invokeUpdateChunkScheduling(
                        packed, requiredLevel, existing, existing.getTicketLevel());
            }
        }
        invoker.randomnibble6plus24generator$invokePromoteChunkMap();
    }

    private static void assertStatus(ChunkAccess chunk, ChunkStatus expected) {
        if (chunk == null || chunk.getPersistedStatus() != expected) {
            throw new IllegalStateException("Expected physical status " + expected
                    + " found " + (chunk == null ? "null" : chunk.getPersistedStatus()));
        }
        if (expected == ChunkStatus.LIGHT && !chunk.isLightCorrect()) {
            throw new IllegalStateException("Physical LIGHT Chunk is not light-correct");
        }
    }

    private static List<ChunkAccess> chunks(ServerLevel level, Iterable<ChunkPos> positions) {
        List<ChunkAccess> result = new ArrayList<>();
        for (ChunkPos pos : positions) {
            ChunkAccess chunk = physicalChunk(level, pos, ChunkStatus.FEATURES);
            if (chunk == null || chunk.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
                throw new IllegalStateException("Missing materialized physical geometry at " + pos);
            }
            result.add(chunk);
        }
        return List.copyOf(result);
    }

    private static ChunkAccess physicalChunk(
            ServerLevel level, ChunkPos pos, ChunkStatus status) {
        GenerationChunkHolder holder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresentUnchecked(status);
        if (chunk == null) {
            throw new IllegalStateException("Missing exact physical " + status + " future at " + pos);
        }
        return chunk;
    }

    private static List<ChunkPos> square(ChunkPos center, int radius) {
        List<ChunkPos> result = new ArrayList<>();
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                result.add(new ChunkPos(x, z));
            }
        }
        return result;
    }

    private static void order(List<ChunkPos> values, String order) {
        switch (order) {
            case "row-major" -> values.sort(Z_THEN_X);
            case "reverse" -> values.sort(Z_THEN_X.reversed());
            case "shuffle" -> Collections.shuffle(values, new Random(0x706879736963616cL));
            case "parallel" -> values.sort(Z_THEN_X);
            default -> throw new IllegalArgumentException("Unknown Phase 3B order " + order);
        }
    }

    private static JsonObject statusDistribution(ServerLevel level, ChunkPos center, int radius) {
        JsonObject distribution = new JsonObject();
        for (int distance = 0; distance <= radius; distance++) {
            Map<String, Integer> counts = new TreeMap<>();
            for (ChunkPos pos : square(center, distance)) {
                if (Math.max(Math.abs(pos.x() - center.x()), Math.abs(pos.z() - center.z())) != distance) continue;
                GenerationChunkHolder holder = level.getChunkSource().chunkMap
                        .getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
                ChunkStatus latest = holder == null ? null : holder.getLatestStatus();
                String status = latest == null ? "absent" : latest.getName();
                counts.merge(status, 1, Integer::sum);
            }
            distribution.add(Integer.toString(distance), intMap(counts));
        }
        return distribution;
    }

    private static JsonObject lightQueryFootprint(
            List<PhysicalMosaicTrace.LightQuery> queries, ChunkPos center) {
        JsonObject json = new JsonObject();
        if (queries.isEmpty()) {
            json.addProperty("count", 0);
            return json;
        }
        int minDx = Integer.MAX_VALUE;
        int maxDx = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE;
        int maxDz = Integer.MIN_VALUE;
        long nulls = 0;
        Set<String> statuses = new TreeSet<>();
        for (PhysicalMosaicTrace.LightQuery query : queries) {
            minDx = Math.min(minDx, query.requestedPos().x() - center.x());
            maxDx = Math.max(maxDx, query.requestedPos().x() - center.x());
            minDz = Math.min(minDz, query.requestedPos().z() - center.z());
            maxDz = Math.max(maxDz, query.requestedPos().z() - center.z());
            if (!query.returnedPhysicalChunk()) nulls++;
            if (query.returnedStatus() != null) statuses.add(query.returnedStatus().getName());
        }
        json.addProperty("count", queries.size());
        json.addProperty("minDx", minDx);
        json.addProperty("maxDx", maxDx);
        json.addProperty("minDz", minDz);
        json.addProperty("maxDz", maxDz);
        json.addProperty("nullQueries", nulls);
        json.add("statuses", strings(statuses));
        return json;
    }

    private static ServerLevel level(MinecraftServer server) {
        long expectedMasterSeed = Long.parseLong(System.getProperty(
                PREFIX + "masterSeed", Long.toString(server.getWorldGenSettings().options().seed())));
        if (server.getWorldGenSettings().options().seed() != expectedMasterSeed) {
            throw new IllegalStateException("Phase 3B fixture master seed mismatch");
        }
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing Phase 3B dimension " + dimension.identifier());
        return level;
    }

    private static ChunkPos target() {
        return new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "0")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "0")));
    }

    private static ChunkStatus requestedStatus() {
        return switch (System.getProperty(PREFIX + "targetStatus", "light")) {
            case "initialize_light" -> ChunkStatus.INITIALIZE_LIGHT;
            case "light" -> ChunkStatus.LIGHT;
            default -> throw new IllegalArgumentException("Unknown Phase 3B target status");
        };
    }

    private static void ensureHiddenMosaic(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator)) {
            throw new IllegalStateException("Phase 3B requires a hidden physical Mosaic world");
        }
    }

    private static int entityCount(ServerLevel level) {
        int count = 0;
        for (Entity ignored : level.getAllEntities()) count++;
        return count;
    }

    private static JsonObject map(Map<String, Long> values) {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    private static JsonObject intMap(Map<String, Integer> values) {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static void write(JsonObject json) {
        String output = System.getProperty(PREFIX + "output", "");
        if (output.isBlank()) return;
        Path path = Path.of(output);
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3B result " + path, exception);
        }
    }

    private record BeforeChunk(
            String worldgenHash,
            List<String> rawEntityNbt,
            Map<String, String> blockEntityNbt,
            List<String> blockTicks,
            List<String> fluidTicks,
            Map<String, String> structures) {
        private static BeforeChunk from(FeatureStableSnapshot snapshot) {
            return new BeforeChunk(
                    snapshot.worldgenDataHash(),
                    snapshot.rawEntityNbt(),
                    snapshot.blockEntityNbt(),
                    snapshot.blockTickData(),
                    snapshot.fluidTickData(),
                    snapshot.structureStartData());
        }
    }

    private record VerificationState(
            Map<ChunkPos, BeforeChunk> before,
            List<MosaicPhysicalMaterializer.Publication> publications,
            int entitiesBefore,
            int blockTicksBefore,
            int fluidTicksBefore) {
    }

    private record PoiEvidence(
            int count,
            Set<String> types,
            Map<String, List<MosaicPhysicalPoiReconciler.PoiEntry>> positiveChunks) {
    }

    private record GeometryEvidence(
            Map<ChunkPos, String> preLightHashes,
            int totalChunks,
            int comparedChunks) {
        private String targetPreLightHash(ChunkPos pos) {
            return preLightHashes.getOrDefault(pos, "<loaded-physical-world-authority>");
        }
    }
}
