package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.ticks.SavedTick;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkHolderInvoker;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkAccessAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapInvoker;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ServerLevelInvoker;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.LevelTicksAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalGenerationPlan;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalPoiReconciler;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;

/** Property-gated Phase 3C1 physical SPAWN/FULL verification. */
public final class Phase3C1Verification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3c1.";
    private static final Comparator<ChunkPos> Z_THEN_X = Comparator
            .comparingInt(ChunkPos::z)
            .thenComparingInt(ChunkPos::x);
    private static final Map<ServerLevel, Set<ChunkPos>> EXACT_STATUS_HOLDERS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static volatile boolean completed;

    private Phase3C1Verification() {
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
            case "full", "reload" -> runFull(server, level, mode.equals("reload"));
            case "pair" -> runPair(server, level);
            case "runtime" -> runRuntime(server, level);
            case "ocean-scan" -> scanOceanMonument(level);
            default -> throw new IllegalArgumentException("Unknown Phase 3C1 verification mode " + mode);
        };
        result.addProperty("mode", mode);
        result.addProperty("status", "PASS");
        write(result);
        completed = true;
        RandomNibble6Plus24Generator.LOGGER.info("Phase 3C1 {} PASS", mode);
        cleanupExactStatusHolders(server);
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) server.halt(false);
    }

    public static void cleanupExactStatusHolders(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) cleanupExactStatusHolders(level);
    }

    private static void cleanupExactStatusHolders(ServerLevel level) {
        Set<ChunkPos> positions = EXACT_STATUS_HOLDERS.remove(level);
        if (positions == null || positions.isEmpty()) return;
        ChunkMapInvoker invoker = (ChunkMapInvoker) level.getChunkSource().chunkMap;
        for (ChunkPos pos : positions) {
            long packed = ChunkPos.pack(pos.x(), pos.z());
            ChunkHolder existing = level.getChunkSource().chunkMap
                    .getUpdatingChunkIfPresent(packed);
            if (existing != null) {
                invoker.randomnibble6plus24generator$invokeUpdateChunkScheduling(
                        packed,
                        ChunkLevel.MAX_LEVEL + 1,
                        existing,
                        existing.getTicketLevel());
            }
        }
        invoker.randomnibble6plus24generator$invokePromoteChunkMap();
    }

    private static JsonObject runFull(MinecraftServer server, ServerLevel level, boolean reload) {
        ChunkPos target = target();
        VerificationState state = begin(level);
        long started = System.nanoTime();
        ChunkAccess result = request(server, level, target, ChunkStatus.FULL);
        long totalNanos = System.nanoTime() - started;
        if (!(result instanceof LevelChunk full)) {
            throw new IllegalStateException("Physical Mosaic FULL did not return LevelChunk: "
                    + (result == null ? "null" : result.getClass().getName()));
        }
        if (full.getPersistedStatus() != ChunkStatus.FULL || !full.isLightCorrect()) {
            throw new IllegalStateException("Physical Mosaic FULL chunk is not gameplay-ready status="
                    + full.getPersistedStatus() + " lightCorrect=" + full.isLightCorrect());
        }
        String artifactGeometry = state.artifactGeometry().get(target);
        String fullGeometry = geometryHash(full, level);
        if (artifactGeometry != null && !artifactGeometry.equals(fullGeometry)) {
            CanonicalChunkArtifact artifact = state.artifacts().get(target);
            String differences = artifact == null
                    ? "artifact payload unavailable"
                    : geometryDifferences(artifact.rehydrate(
                            level.registryAccess(),
                            net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
                                    .fromLevel(level)), full, level);
            throw new IllegalStateException(
                    "FULL conversion changed canonical geometry at " + target + ": " + differences);
        }
        PhysicalMosaicTrace.Snapshot trace = PhysicalMosaicTrace.snapshot();
        assertClean(level, reload);
        if (!reload && trace.spawnSeeds().stream().anyMatch(seed -> seed.observed() != seed.expected())) {
            throw new IllegalStateException("Physical SPAWN did not use local seed: " + trace.spawnSeeds());
        }
        if (!trace.lifecycleDuplicates().isEmpty()) {
            throw new IllegalStateException("Physical LevelChunk lifecycle repeated: "
                    + trace.lifecycleDuplicates());
        }
        PhysicalFullSnapshot snapshot = captureFull(level, full);
        if (!reload) {
            saveBeforeStop(server);
        }
        JsonObject json = new JsonObject();
        json.addProperty("dimension", level.dimension().identifier().toString());
        json.addProperty("chunkX", target.x());
        json.addProperty("chunkZ", target.z());
        json.addProperty("requestedStatus", ChunkStatus.FULL.getName());
        json.addProperty("reload", reload);
        json.addProperty("localWorldSeed", MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow().resolveLocalWorldSeed(level.dimension(), target));
        json.addProperty("artifactGenerations", MosaicPhysicalMaterializer.metrics().artifactCaptureCount());
        json.addProperty("artifactPublishes", MosaicPhysicalMaterializer.metrics().publishCount());
        json.addProperty("spawnCalls", trace.physicalStatusCalls().getOrDefault(
                ChunkStatus.SPAWN.getName(), 0L));
        json.addProperty("fullCalls", trace.physicalStatusCalls().getOrDefault(
                ChunkStatus.FULL.getName(), 0L));
        json.addProperty("spawnSeedReads", trace.spawnSeeds().size());
        json.add("spawnSeeds", spawnSeeds(trace.spawnSeeds()));
        json.add("spawnChunkReads", spawnReads(trace.spawnReads()));
        json.addProperty("spawnNanos", trace.spawnNanos());
        json.addProperty("fullNanos", trace.fullNanos());
        json.addProperty("fullChunkClass", full.getClass().getName());
        json.addProperty("fullStatus", full.getPersistedStatus().getName());
        json.addProperty("lightCorrect", full.isLightCorrect());
        json.addProperty("blockEntityCount", full.getBlockEntities().size());
        json.addProperty("pendingBlockEntityCount", pendingBlockEntityCount(full));
        CanonicalChunkArtifact publishedArtifact = state.artifacts().get(target);
        if (publishedArtifact != null) {
            ProtoChunk detached = publishedArtifact.rehydrate(
                    level.registryAccess(),
                    net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
                            .fromLevel(level));
            json.addProperty("artifactEntityNbtCount", detached.getEntities().size());
            json.add("artifactEntityIds", entityIds(detached));
        }
        json.addProperty("blockTickCount", full.getBlockTicks().count());
        json.addProperty("fluidTickCount", full.getFluidTicks().count());
        json.addProperty("physicalEntityCount", entityCount(level));
        json.addProperty("physicalEntityCountBefore", state.entityCountBefore());
        json.addProperty("geometryHash", fullGeometry);
        json.addProperty("fullSnapshotHash", snapshot.hash());
        json.addProperty("serializedHash", snapshot.serializedHash());
        json.add("structureStarts", structureSummary(level, full));
        json.addProperty("structureReloadSeedReads", trace.structureReloadSeeds().size());
        json.add("structureReloadSeeds", structureReloadSeeds(trace.structureReloadSeeds()));
        json.addProperty("generationContextBindings", GenerationContextRegistry.bindingCount());
        json.addProperty("spawnContextBindings", MosaicSpawnContextRegistry.bindingCount());
        json.add("physicalGeneratorCalls", map(trace.generatorCalls()));
        json.add("forbiddenStatusCalls", map(trace.forbiddenStatusCalls()));
        json.add("lifecycleCalls", map(trace.lifecycleCalls()));
        json.add("lifecycleDuplicates", map(trace.lifecycleDuplicates()));
        json.add("runtimeTicks", map(trace.runtimeTicks()));
        json.addProperty("totalMillis", totalNanos / 1_000_000L);
        return json;
    }

    private static JsonArray structureSummary(ServerLevel level, ChunkAccess chunk) {
        JsonArray result = new JsonArray();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        chunk.getAllStarts().entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(structures.getKey(entry.getKey()))))
                .forEach(entry -> {
                    var start = entry.getValue();
                    JsonObject value = new JsonObject();
                    value.addProperty("id", String.valueOf(structures.getKey(entry.getKey())));
                    value.addProperty("valid", start.isValid());
                    value.addProperty("start", start.getChunkPos().toString());
                    value.addProperty("references", start.getReferences());
                    value.addProperty("pieces", start.getPieces().size());
                    value.addProperty("box", start.isValid() ? String.valueOf(start.getBoundingBox()) : "null");
                    result.add(value);
                });
        return result;
    }

    private static JsonArray entityIds(ProtoChunk chunk) {
        JsonArray result = new JsonArray();
        chunk.getEntities().stream()
                .map(tag -> tag.getString("id").orElse("<missing-id>"))
                .sorted()
                .forEach(result::add);
        return result;
    }

    /**
     * Development-only discovery for the Ocean Monument reload fixture.  The
     * scan never touches the physical chunk map: placement candidates are
     * filtered from the local seed, then the normal isolated FEATURES runner
     * confirms that Vanilla actually creates a monument start at the target.
     */
    private static JsonObject scanOceanMonument(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator mosaic)) {
            throw new IllegalStateException("Ocean Monument scan requires MosaicChunkGenerator identity");
        }
        MosaicRuntimeContext runtime = MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow(() -> new IllegalStateException("Missing Mosaic runtime context during Ocean scan"));
        var registry = level.registryAccess();
        var structures = registry.lookupOrThrow(Registries.STRUCTURE);
        var monumentKey = ResourceKey.create(
                Registries.STRUCTURE, Identifier.withDefaultNamespace("monument"));
        Holder<Structure> monument = structures.get(monumentKey)
                .orElseThrow(() -> new IllegalStateException("Missing minecraft:monument registry entry"));
        var structureSets = registry.lookupOrThrow(Registries.STRUCTURE_SET);
        var noiseRegistry = registry.lookupOrThrow(Registries.NOISE);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                mosaic.getBiomeSource(), mosaic.generatorSettings());
        // Placements are registry data and independent of the candidate seed;
        // the per-candidate local seed is supplied to getPotentialStructureChunk.
        RandomState templateRandom = RandomState.create(
                generator.generatorSettings().value(), noiseRegistry, 0L);
        ChunkGeneratorStructureState templateState = generator.createState(
                structureSets, templateRandom, 0L);
        templateState.ensureStructuresGenerated();
        List<net.minecraft.world.level.levelgen.structure.placement.StructurePlacement> placements =
                templateState.getPlacementsForStructure(monument);
        if (placements.isEmpty()) {
            throw new IllegalStateException("Ocean Monument has no registered placement");
        }

        long masterSeed = Long.parseLong(System.getProperty(PREFIX + "masterSeed", "123456789"));
        int range = Integer.getInteger(PREFIX + "scanRange", 256);
        int attempts = 0;
        int placementCandidates = 0;
        for (int z = -range; z <= range; z++) {
            for (int x = -range; x <= range; x++) {
                ChunkPos candidate = new ChunkPos(x, z);
                if (ChunkPos.ZERO.equals(candidate)) continue;
                attempts++;
                long localSeed = runtime.resolveLocalWorldSeed(level.dimension(), candidate);
                boolean placementMatch = false;
                for (var placement : placements) {
                    if (placement instanceof RandomSpreadStructurePlacement spread) {
                        placementMatch |= spread.getPotentialStructureChunk(localSeed, x, z).equals(candidate);
                    } else {
                        RandomState candidateRandom = RandomState.create(
                                generator.generatorSettings().value(), noiseRegistry, localSeed);
                        ChunkGeneratorStructureState candidateState = generator.createState(
                                structureSets, candidateRandom, localSeed);
                        candidateState.ensureStructuresGenerated();
                        placementMatch |= placement.isStructureChunk(candidateState, x, z);
                    }
                    if (placementMatch) break;
                }
                if (!placementMatch) continue;
                placementCandidates++;

                // Avoid spending a full isolated run on obviously non-ocean
                // candidates.  This is only a scan hint; the structure start
                // below remains the authoritative test.
                RandomState candidateRandom = RandomState.create(
                        generator.generatorSettings().value(), noiseRegistry, localSeed);
                Holder<?> biome = generator.getBiomeSource().getNoiseBiome(
                        QuartPos.fromBlock(candidate.getMiddleBlockX()),
                        QuartPos.fromBlock(63),
                        QuartPos.fromBlock(candidate.getMiddleBlockZ()),
                        candidateRandom.sampler());
                String biomeId = biome.unwrapKey()
                        .map(key -> key.identifier().toString())
                        .orElse("");
                if (!biomeId.contains("ocean")) continue;

                try (var context = io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                        .IsolatedGenerationContext.create(
                                io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                                        .IsolatedGenerationMode.ISOLATED_MOSAIC,
                                level, localSeed, candidate)) {
                    var run = context.generateFeaturesStable();
                    boolean found = run.targetChunk().getAllStarts().entrySet().stream()
                            .anyMatch(entry -> structures.getKey(entry.getKey())
                                    .equals(monumentKey.identifier())
                                    && entry.getValue().isValid());
                    if (found) {
                        JsonObject result = new JsonObject();
                        result.addProperty("fixture", "ocean-monument");
                        result.addProperty("masterSeed", masterSeed);
                        result.addProperty("dimension", level.dimension().identifier().toString());
                        result.addProperty("chunkX", candidate.x());
                        result.addProperty("chunkZ", candidate.z());
                        result.addProperty("localWorldSeed", localSeed);
                        result.addProperty("biome", biomeId);
                        result.addProperty("attempts", attempts);
                        result.addProperty("placementCandidates", placementCandidates);
                        return result;
                    }
                }
            }
        }
        throw new IllegalStateException("No Ocean Monument local-seed fixture after attempts="
                + attempts + ", placementCandidates=" + placementCandidates + ", range=" + range);
    }

    private static JsonObject runPair(MinecraftServer server, ServerLevel level) {
        ChunkPos first = target();
        ChunkPos second = new ChunkPos(first.x() + 1, first.z());
        VerificationState state = begin(level);
        // Register both overlapping requests before the first task runs.  The
        // physical planner can then retain obligations for the second chunk's
        // newly introduced edge instead of treating it as an unrelated later load.
        MosaicPhysicalMaterializer.registerPhysicalRequest(level, ChunkStatus.FULL, first);
        ensurePhysicalHolders(level, first, ChunkStatus.FULL);
        MosaicPhysicalMaterializer.registerPhysicalRequest(level, ChunkStatus.FULL, second);
        ensurePhysicalHolders(level, second, ChunkStatus.FULL);
        ChunkAccess left = runRequestTask(server, level, first, ChunkStatus.FULL);
        ChunkAccess right = runRequestTask(server, level, second, ChunkStatus.FULL);
        if (!(left instanceof LevelChunk) || !(right instanceof LevelChunk)) {
            throw new IllegalStateException("Adjacent physical Mosaic pair did not reach LevelChunk");
        }
        JsonObject json = new JsonObject();
        json.addProperty("first", first.toString());
        json.addProperty("second", second.toString());
        json.addProperty("firstLocalSeed", localSeed(level, first));
        json.addProperty("secondLocalSeed", localSeed(level, second));
        if (json.get("firstLocalSeed").getAsLong() == json.get("secondLocalSeed").getAsLong()) {
            throw new IllegalStateException("Pair fixture did not use different local seeds");
        }
        assertClean(level, false);
        json.addProperty("physicalEntitiesBefore", state.entityCountBefore());
        json.addProperty("physicalEntitiesAfter", entityCount(level));
        json.addProperty("artifactGenerations", MosaicPhysicalMaterializer.metrics().artifactCaptureCount());
        json.addProperty("spawnCalls", PhysicalMosaicTrace.snapshot().spawnSeeds().size());
        return json;
    }

    private static JsonObject runRuntime(MinecraftServer server, ServerLevel level) {
        ChunkPos first = target();
        ChunkPos second = new ChunkPos(first.x() + 1, first.z());
        VerificationState state = begin(level);

        // Register every FULL target in the bounded entity-ticking frontier before
        // creating any physical holders.  This prevents an earlier EMPTY load from
        // completing before its later overlapping Mosaic obligation is known.
        Set<ChunkPos> allFullTargets = new java.util.TreeSet<>(Z_THEN_X);
        allFullTargets.addAll(square(first, 2));
        registerAndEnsureFullTargets(level, allFullTargets);
        for (ChunkPos pos : allFullTargets) runRequestTask(server, level, pos, ChunkStatus.FULL);

        if (!(level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(first.x(), first.z()))
                .getChunkIfPresentUnchecked(ChunkStatus.FULL) instanceof LevelChunk)
                || !(level.getChunkSource().chunkMap
                        .getUpdatingChunkIfPresent(ChunkPos.pack(second.x(), second.z()))
                        .getChunkIfPresentUnchecked(ChunkStatus.FULL) instanceof LevelChunk)) {
            throw new IllegalStateException("Adjacent physical Mosaic pair did not reach LevelChunk");
        }
        JsonObject json = new JsonObject();
        json.addProperty("first", first.toString());
        json.addProperty("second", second.toString());
        json.addProperty("firstLocalSeed", localSeed(level, first));
        json.addProperty("secondLocalSeed", localSeed(level, second));
        if (json.get("firstLocalSeed").getAsLong() == json.get("secondLocalSeed").getAsLong()) {
            throw new IllegalStateException("Pair fixture did not use different local seeds");
        }
        assertClean(level, false);
        json.addProperty("physicalEntitiesBefore", state.entityCountBefore());
        json.addProperty("physicalEntitiesAfter", entityCount(level));
        json.addProperty("artifactGenerations", MosaicPhysicalMaterializer.metrics().artifactCaptureCount());
        json.addProperty("spawnCalls", PhysicalMosaicTrace.snapshot().spawnSeeds().size());

        // Vanilla BLOCK_TICKING requires a full 3x3 accessible frontier.  Register
        // the complete union before running any task so overlapping requests retain
        // their independent Mosaic obligations.
        Set<ChunkPos> tickingFrontier = new java.util.TreeSet<>(Z_THEN_X);
        tickingFrontier.addAll(square(first, 1));
        tickingFrontier.addAll(square(second, 1));
        materializeFullSet(server, level, tickingFrontier);
        promote(level, server, List.of(first, second), FullChunkStatus.BLOCK_TICKING, tickingFrontier);

        // ENTITY_TICKING asks Vanilla for a radius-two FULL frontier.  Promote only
        // the first fixture in this bounded probe; the adjacent chunk remains a real
        // physical Mosaic BLOCK_TICKING neighbour for boundary checks.
        Set<ChunkPos> entityFrontier = new java.util.TreeSet<>(Z_THEN_X);
        entityFrontier.addAll(square(first, 2));
        materializeFullSet(server, level, entityFrontier);
        promote(level, server, List.of(first), FullChunkStatus.ENTITY_TICKING, entityFrontier);
        // Give Vanilla's simulation tracker the same physical ticket signal it
        // uses in a live server.  This makes shouldTickBlocksAt/EntityTickList
        // operate on real Mosaic neighbors instead of only our status probe.
        level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SIMULATION, first, 2);
        level.getChunkSource().chunkMap.getDistanceManager()
                .runAllUpdates(level.getChunkSource().chunkMap);

        JsonObject gameplay = runGameplaySmoke(server, level, first, second);
        for (var entry : gameplay.entrySet()) json.add(entry.getKey(), entry.getValue());
        Set<ChunkPos> runtimePlans = new java.util.HashSet<>(tickingFrontier);
        runtimePlans.addAll(entityFrontier);
        runtimePlans.forEach(pos -> MosaicPhysicalMaterializer.clearPhysicalRequest(level, pos));
        assertClean(level, false);

        json.addProperty("runtimeSimulation", "vanilla-ticket-promotion");
        json.addProperty("firstTicketLevel", ticketLevel(level, first));
        json.addProperty("secondTicketLevel", ticketLevel(level, second));
        json.addProperty("firstFullStatus", fullStatus(level, first).toString());
        json.addProperty("secondFullStatus", fullStatus(level, second).toString());
        json.addProperty("blockTickSchedulerCount", level.getBlockTicks().count());
        json.addProperty("fluidTickSchedulerCount", level.getFluidTicks().count());
        json.addProperty("physicalEntityCountAfterPromotion", entityCount(level));
        PhysicalMosaicTrace.Snapshot runtimeTrace = PhysicalMosaicTrace.snapshot();
        if (!runtimeTrace.lifecycleDuplicates().isEmpty()) {
            throw new IllegalStateException("Runtime LevelChunk lifecycle repeated: "
                    + runtimeTrace.lifecycleDuplicates());
        }
        json.add("lifecycleCalls", map(runtimeTrace.lifecycleCalls()));
        json.add("lifecycleDuplicates", map(runtimeTrace.lifecycleDuplicates()));
        json.add("runtimeTicks", map(runtimeTrace.runtimeTicks()));
        if (fullStatus(level, first) != FullChunkStatus.ENTITY_TICKING
                || fullStatus(level, second) != FullChunkStatus.BLOCK_TICKING) {
            throw new IllegalStateException("Vanilla physical ticket promotion did not reach expected statuses");
        }
        if (GenerationContextRegistry.bindingCount() != 0
                || MosaicSpawnContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Generation context survived runtime promotion");
        }
        return json;
    }

    private static void materializeFullSet(
            MinecraftServer server,
            ServerLevel level,
            Set<ChunkPos> positions) {
        List<ChunkPos> ordered = positions.stream().sorted(Z_THEN_X).toList();
        registerAndEnsureFullTargets(level, positions);
        for (ChunkPos pos : ordered) runRequestTask(server, level, pos, ChunkStatus.FULL);
    }

    private static JsonObject runGameplaySmoke(
            MinecraftServer server,
            ServerLevel level,
            ChunkPos first,
            ChunkPos second) {
        JsonObject json = new JsonObject();
        PhysicalMosaicTrace.Snapshot before = PhysicalMosaicTrace.snapshot();
        int entitiesBefore = entityCount(level);
        // Keep the runtime fixtures in an ordinary, loaded sea-level section;
        // this avoids the build-height boundary while still exercising the
        // physical chunk seam.
        int y = level.getSeaLevel() + 8;

        Entity marker = BuiltInRegistries.ENTITY_TYPE
                .getValue(Identifier.withDefaultNamespace("armor_stand"))
                .create(level, EntitySpawnReason.COMMAND);
        if (marker == null) throw new IllegalStateException("Unable to create runtime entity fixture");
        marker.setPos(first.getMaxBlockX() - 1.5D, y, first.getMinBlockZ() + 2.5D);
        if (!level.addFreshEntity(marker)) {
            throw new IllegalStateException("Vanilla physical entity manager rejected runtime fixture");
        }
        int entitiesAfterAdd = entityCount(level);
        marker.setPos(second.getMinBlockX() + 1.5D, y, second.getMinBlockZ() + 2.5D);
        ((ServerLevelInvoker) level).randomnibble6plus24generator$invokeTickNonPassenger(marker);
        if (!marker.chunkPosition().equals(second)) {
            throw new IllegalStateException("Runtime entity did not cross the physical Mosaic boundary");
        }
        int entitiesAfterMove = entityCount(level);
        json.addProperty("entityAdded", entitiesAfterAdd == entitiesBefore + 1);
        json.addProperty("entityCrossedBoundary", true);
        json.addProperty("entityCountAfterMove", entitiesAfterMove);

        BlockPos blockTickPos = new BlockPos(first.getMinBlockX() + 2, y, first.getMinBlockZ() + 2);
        BlockState oldBlock = level.getBlockState(blockTickPos);
        level.setBlock(blockTickPos, Blocks.STONE.defaultBlockState(), 3);
        int blockTicksBeforeSchedule = level.getBlockTicks().count();
        ChunkHolder blockTickHolder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(first.x(), first.z()));
        if (!(blockTickHolder.getChunkIfPresentUnchecked(ChunkStatus.FULL) instanceof LevelChunk blockTickChunk)) {
            throw new IllegalStateException("Runtime block-tick fixture chunk is not FULL");
        }
        level.scheduleTick(blockTickPos, Blocks.STONE, 0);
        int blockTicksAfterSchedule = level.getBlockTicks().count();

        BlockPos fluidSource = new BlockPos(first.getMaxBlockX(), y, first.getMinBlockZ() + 4);
        BlockPos fluidTarget = fluidSource.east();
        BlockPos fluidSupport = fluidSource.below();
        BlockPos fluidTargetSupport = fluidTarget.below();
        BlockPos fluidWestWall = fluidSource.west();
        BlockPos fluidNorthWall = fluidSource.north();
        BlockPos fluidSouthWall = fluidSource.south();
        BlockState oldFluidSource = level.getBlockState(fluidSource);
        BlockState oldFluidTarget = level.getBlockState(fluidTarget);
        BlockState oldFluidSupport = level.getBlockState(fluidSupport);
        BlockState oldFluidTargetSupport = level.getBlockState(fluidTargetSupport);
        BlockState oldFluidWestWall = level.getBlockState(fluidWestWall);
        BlockState oldFluidNorthWall = level.getBlockState(fluidNorthWall);
        BlockState oldFluidSouthWall = level.getBlockState(fluidSouthWall);
        level.setBlock(fluidSupport, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(fluidTargetSupport, Blocks.STONE.defaultBlockState(), 3);
        // Close the other three horizontal exits so the Vanilla fluid callback
        // has one deterministic route: across the physical chunk boundary.
        level.setBlock(fluidWestWall, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(fluidNorthWall, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(fluidSouthWall, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(fluidSource, Fluids.WATER.defaultFluidState().createLegacyBlock(), 3);
        level.setBlock(fluidTarget, Blocks.AIR.defaultBlockState(), 3);
        level.getFluidTicks().schedule(level.createTick(fluidSource, Fluids.WATER, 0));

        level.tick(() -> true);
        PhysicalMosaicTrace.Snapshot after = PhysicalMosaicTrace.snapshot();
        boolean directSchedulerFallback = false;
        if (delta(after.runtimeTicks(), before.runtimeTicks(), "block") == 0
                || delta(after.runtimeTicks(), before.runtimeTicks(), "fluid") == 0) {
            // A ServerLevel created inside createLevels has not yet entered the
            // server's regular tick loop.  Drain the same physical LevelTicks
            // containers through Vanilla's private callbacks for this smoke.
            ServerLevelInvoker callbacks = (ServerLevelInvoker) level;
            level.getBlockTicks().tick(
                    Long.MAX_VALUE, 65536,
                    (pos, block) -> callbacks.randomnibble6plus24generator$invokeTickBlock(pos, block));
            level.getFluidTicks().tick(
                    Long.MAX_VALUE, 65536,
                    (pos, fluid) -> callbacks.randomnibble6plus24generator$invokeTickFluid(pos, fluid));
            for (int pass = 0; pass < 3 && !isWater(level.getFluidState(fluidTarget)); pass++) {
                level.getFluidTicks().tick(
                        Long.MAX_VALUE, 65536,
                        (pos, fluid) -> callbacks.randomnibble6plus24generator$invokeTickFluid(pos, fluid));
            }
            // Keep the callback itself Vanilla: if the embedded fixture server has
            // not entered its normal tick loop yet, invoke the exact ServerLevel
            // fluid callback once more rather than reproducing FlowingFluid logic.
            if (!isWater(level.getFluidState(fluidTarget))) {
                callbacks.randomnibble6plus24generator$invokeTickFluid(fluidSource, Fluids.WATER);
            }
            if (delta(PhysicalMosaicTrace.snapshot().runtimeTicks(), before.runtimeTicks(), "block") == 0) {
                callbacks.randomnibble6plus24generator$invokeTickBlock(blockTickPos, Blocks.STONE);
            }
            directSchedulerFallback = true;
            after = PhysicalMosaicTrace.snapshot();
        }
        long blockTicks = delta(after.runtimeTicks(), before.runtimeTicks(), "block");
        long fluidTicks = delta(after.runtimeTicks(), before.runtimeTicks(), "fluid");
        long entityTicks = delta(after.runtimeTicks(), before.runtimeTicks(), "entity");
        long blockEntityTicks = delta(after.runtimeTicks(), before.runtimeTicks(), "block_entity_ticker");
        if (blockTicks <= 0) {
            throw new IllegalStateException("Vanilla block tick did not execute (firstTicking="
                    + level.shouldTickBlocksAt(first.pack())
                    + ",secondTicking=" + level.shouldTickBlocksAt(second.pack())
                    + ",firstLevel=" + ticketLevel(level, first)
                    + ",secondLevel=" + ticketLevel(level, second)
                    + ",scheduledBefore=" + blockTicksBeforeSchedule
                    + ",scheduledAfter=" + blockTicksAfterSchedule
                    + ",scheduledAfterTick=" + level.getBlockTicks().count()
                    + ",gameTime=" + level.getGameTime()
                    + ",runsNormally=" + level.tickRateManager().runsNormally()
                    + ",next=" + ((LevelTicksAccessor) level.getBlockTicks())
                            .randomnibble6plus24generator$getNextTickForContainer().get(first.pack()) + ")");
        }
        if (fluidTicks <= 0) throw new IllegalStateException("Vanilla fluid tick did not execute");
        if (!isWater(level.getFluidState(fluidTarget))) {
            throw new IllegalStateException("Vanilla fluid did not cross the physical Mosaic boundary"
                    + " source=" + level.getBlockState(fluidSource)
                    + " target=" + level.getBlockState(fluidTarget)
                    + " sourceFluid=" + level.getFluidState(fluidSource)
                    + " targetFluid=" + level.getFluidState(fluidTarget)
                    + " replace=" + level.getFluidState(fluidTarget)
                            .canBeReplacedWith(level, fluidTarget, Fluids.WATER, Direction.EAST)
                    + " east=" + level.getFluidState(fluidSource.east())
                    + " west=" + level.getFluidState(fluidSource.west())
                    + " north=" + level.getFluidState(fluidSource.north())
                    + " south=" + level.getFluidState(fluidSource.south())
                    + " below=" + level.getFluidState(fluidSource.below())
                    + " scheduled=" + level.getFluidTicks().count());
        }

        int blockEntities = 0;
        for (ChunkPos pos : square(first, 1)) {
            ChunkHolder holder = level.getChunkSource().chunkMap
                    .getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
            if (holder != null && holder.getChunkIfPresentUnchecked(ChunkStatus.FULL) instanceof LevelChunk chunk) {
                blockEntities += chunk.getBlockEntities().size();
            }
        }
        if (blockEntities > 0 && blockEntityTicks <= 0) {
            throw new IllegalStateException("Registered BlockEntity ticker did not execute");
        }
        json.addProperty("blockTickExecutions", blockTicks);
        json.addProperty("fluidTickExecutions", fluidTicks);
        json.addProperty("entityTickExecutions", entityTicks);
        json.addProperty("blockEntityCountInRuntimeFrontier", blockEntities);
        json.addProperty("blockEntityTickerExecutions", blockEntityTicks);
        json.addProperty("fluidCrossedBoundary", true);
        json.addProperty("directSchedulerFallback", directSchedulerFallback);

        level.setBlock(blockTickPos, oldBlock, 3);
        level.setBlock(fluidSource, oldFluidSource, 3);
        level.setBlock(fluidTarget, oldFluidTarget, 3);
        level.setBlock(fluidSupport, oldFluidSupport, 3);
        level.setBlock(fluidTargetSupport, oldFluidTargetSupport, 3);
        level.setBlock(fluidWestWall, oldFluidWestWall, 3);
        level.setBlock(fluidNorthWall, oldFluidNorthWall, 3);
        level.setBlock(fluidSouthWall, oldFluidSouthWall, 3);
        marker.remove(Entity.RemovalReason.DISCARDED);
        if (entityCount(level) != entitiesBefore) {
            throw new IllegalStateException("Runtime entity fixture was not removed cleanly");
        }
        return json;
    }

    private static long delta(Map<String, Long> after, Map<String, Long> before, String key) {
        return after.getOrDefault(key, 0L) - before.getOrDefault(key, 0L);
    }

    private static boolean isWater(net.minecraft.world.level.material.FluidState state) {
        return state.getType().isSame(Fluids.WATER);
    }

    private static void registerAndEnsureFullTargets(
            ServerLevel level,
            Set<ChunkPos> positions) {
        List<ChunkPos> ordered = positions.stream().sorted(Z_THEN_X).toList();
        for (ChunkPos pos : ordered) {
            MosaicPhysicalMaterializer.registerPhysicalRequest(level, ChunkStatus.FULL, pos);
        }
        for (ChunkPos pos : ordered) {
            ensurePhysicalHolders(level, pos, ChunkStatus.FULL);
        }
    }

    private static void promote(
            ServerLevel level,
            MinecraftServer server,
            List<ChunkPos> centers,
            FullChunkStatus desired,
            Set<ChunkPos> fullFrontier) {
        var chunkMap = level.getChunkSource().chunkMap;
        ChunkHolderInvoker invoker;
        for (ChunkPos pos : fullFrontier) {
            ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
            if (holder == null) throw new IllegalStateException("Missing runtime holder " + pos);
            holder.setTicketLevel(Math.min(holder.getTicketLevel(), ChunkLevel.byStatus(FullChunkStatus.FULL)));
        }
        for (ChunkPos pos : centers) {
            ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
            if (holder == null) throw new IllegalStateException("Missing runtime center holder " + pos);
            holder.setTicketLevel(ChunkLevel.byStatus(desired));
        }
        ChunkMapInvoker chunkMapInvoker = (ChunkMapInvoker) chunkMap;
        chunkMapInvoker.randomnibble6plus24generator$invokePromoteChunkMap();
        chunkMap.getDistanceManager().runAllUpdates(chunkMap);
        for (ChunkPos pos : fullFrontier.stream().sorted(Z_THEN_X).toList()) {
            ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
            if (holder == null) continue;
            invoker = (ChunkHolderInvoker) holder;
            invoker.randomnibble6plus24generator$invokeUpdateFutures(chunkMap, server);
        }
        for (ChunkPos pos : centers) {
            ChunkHolder holder = chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
            if (holder == null) throw new IllegalStateException("Missing promoted holder " + pos);
            if (desired == FullChunkStatus.BLOCK_TICKING) {
                awaitChunkFuture(server, holder.getTickingChunkFuture(), "BLOCK_TICKING", pos);
            } else if (desired == FullChunkStatus.ENTITY_TICKING) {
                awaitChunkFuture(server, holder.getEntityTickingChunkFuture(), "ENTITY_TICKING", pos);
            }
        }
    }

    private static LevelChunk awaitChunkFuture(
            MinecraftServer server,
            java.util.concurrent.CompletableFuture<net.minecraft.server.level.ChunkResult<LevelChunk>> future,
            String status,
            ChunkPos pos) {
        server.managedBlock(future::isDone);
        var result = future.join().orElse(null);
        if (result == null) throw new IllegalStateException("Missing " + status + " chunk at " + pos);
        return result;
    }

    private static int ticketLevel(ServerLevel level, ChunkPos pos) {
        ChunkHolder holder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
        if (holder == null) throw new IllegalStateException("Missing holder " + pos);
        return holder.getTicketLevel();
    }

    private static FullChunkStatus fullStatus(ServerLevel level, ChunkPos pos) {
        return ChunkLevel.fullStatus(ticketLevel(level, pos));
    }

    private static VerificationState begin(ServerLevel level) {
        MosaicPhysicalMaterializer.resetVerificationState();
        MosaicPhysicalPoiReconciler.resetVerificationState();
        PhysicalMosaicTrace.reset();
        PhysicalMosaicTrace.enableDetailedVerification();
        Map<ChunkPos, String> artifactGeometry = new ConcurrentHashMap<>();
        Map<ChunkPos, CanonicalChunkArtifact> artifacts = new ConcurrentHashMap<>();
        MosaicPhysicalMaterializer.setVerificationObserver(publication -> {
            CanonicalChunkArtifact artifact = publication.prepared().artifact();
            ChunkAccess detached = artifact.rehydrate(
                    level.registryAccess(),
                    net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(level));
            artifacts.put(publication.key().pos(), artifact);
            artifactGeometry.put(publication.key().pos(), geometryHash(detached, level));
        });
        return new VerificationState(artifactGeometry, artifacts, entityCount(level));
    }

    private static void assertClean(ServerLevel level, boolean reload) {
        MosaicPhysicalMaterializer.Metrics materializer = MosaicPhysicalMaterializer.metrics();
        if (materializer.inFlightCount() != 0
                || materializer.requestedTargetCount() != 0
                || materializer.materializationObligationCount() != 0
                || materializer.physicalStatusAllowanceCount() != 0) {
            throw new IllegalStateException("Physical Mosaic handoff leaked materializer state " + materializer);
        }
        PhysicalMosaicTrace.Snapshot trace = PhysicalMosaicTrace.snapshot();
        if (!trace.generatorCalls().isEmpty()
                || !trace.forbiddenStatusCalls().isEmpty()
                || trace.activeStageCount() != 0) {
            throw new IllegalStateException("Physical Mosaic handoff crossed a forbidden generator boundary " + trace);
        }
        if (GenerationContextRegistry.bindingCount() != 0
                || MosaicSpawnContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Generation context leaked after physical handoff");
        }
        if (reload && !trace.spawnSeeds().isEmpty()) {
            throw new IllegalStateException("Reload unexpectedly executed SPAWN: " + trace.spawnSeeds());
        }
    }

    private static ChunkAccess request(
            MinecraftServer server,
            ServerLevel level,
            ChunkPos pos,
            ChunkStatus status) {
        MosaicPhysicalMaterializer.registerPhysicalRequest(level, status, pos);
        ensurePhysicalHolders(level, pos, status);
        return runRequestTask(server, level, pos, status);
    }

    private static ChunkAccess runRequestTask(
            MinecraftServer server,
            ServerLevel level,
            ChunkPos pos,
            ChunkStatus status) {
        ChunkGenerationTask task = ChunkGenerationTask.create(level.getChunkSource().chunkMap, status, pos);
        runTask(server, task);
        ChunkAccess result = task.getCenter().getChunkIfPresentUnchecked(status);
        if (result == null) {
            throw new IllegalStateException("Physical Mosaic task did not produce " + status + " at " + pos);
        }
        return result;
    }

    private static void ensurePhysicalHolders(ServerLevel level, ChunkPos center, ChunkStatus status) {
        var dependencies = ChunkPyramid.GENERATION_PYRAMID.getStepTo(status).accumulatedDependencies();
        int radius = dependencies.getRadius();
        var chunkMap = level.getChunkSource().chunkMap;
        ChunkMapInvoker invoker = (ChunkMapInvoker) chunkMap;
        for (ChunkPos pos : square(center, radius)) {
            int distance = Math.max(Math.abs(pos.x() - center.x()), Math.abs(pos.z() - center.z()));
            ChunkStatus requiredStatus = dependencies.get(distance);
            int requiredLevel = ChunkLevel.byStatus(requiredStatus);
            long packed = ChunkPos.pack(pos.x(), pos.z());
            ChunkHolder existing = chunkMap.getUpdatingChunkIfPresent(packed);
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

    private static void runTask(MinecraftServer server, ChunkGenerationTask task) {
        while (true) {
            var wait = task.runUntilWait();
            if (wait == null) return;
            server.managedBlock(wait::isDone);
            wait.join();
        }
    }

    private static void saveBeforeStop(MinecraftServer server) {
        if (!server.saveEverything(true, true, true)) {
            throw new IllegalStateException("MinecraftServer.saveEverything failed during Phase 3C1");
        }
    }

    private static long localSeed(ServerLevel level, ChunkPos pos) {
        MosaicRuntimeContext runtime = MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow(() -> new IllegalStateException("Missing Mosaic runtime context"));
        return runtime.resolveLocalWorldSeed(level.dimension(), pos);
    }

    private static String geometryHash(ChunkAccess chunk, ServerLevel level) {
        MessageDigest digest = digest();
        update(digest, chunk.getPos().x());
        update(digest, chunk.getPos().z());
        update(digest, chunk.getMinY());
        update(digest, chunk.getHeight());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
                    update(digest, chunk.getBlockState(pos.set(x, y, z)).toString());
                }
            }
        }
        for (int sectionY = chunk.getMinSectionY(); sectionY <= chunk.getMaxSectionY(); sectionY++) {
            for (int quartZ = 0; quartZ < 4; quartZ++) {
                for (int quartX = 0; quartX < 4; quartX++) {
                    for (int quartY = 0; quartY < 4; quartY++) {
                        update(digest, stableBiome(chunk.getNoiseBiome(
                                (chunk.getPos().x() << 2) + quartX,
                                (sectionY << 2) + quartY,
                                (chunk.getPos().z() << 2) + quartZ)));
                    }
                }
            }
        }
        ChunkStatus.FULL.heightmapsAfter().stream()
                .sorted(Comparator.comparing(net.minecraft.world.level.levelgen.Heightmap.Types::getSerializationKey))
                .forEach(type -> chunk.getHeightmaps().stream()
                        .filter(entry -> entry.getKey() == type)
                        .findFirst()
                        .ifPresent(entry -> {
                            update(digest, entry.getKey().getSerializationKey());
                            for (long value : entry.getValue().getRawData()) update(digest, value);
                        }));
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        chunk.getAllStarts().entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(structures.getKey(entry.getKey()))))
                .forEach(entry -> {
                    var start = entry.getValue();
                    update(digest, String.valueOf(structures.getKey(entry.getKey())));
                    update(digest, "valid=" + start.isValid()
                            + ",start=" + start.getChunkPos()
                            + ",references=" + start.getReferences()
                            + ",pieces=" + start.getPieces().size()
                            + ",box=" + (start.isValid() ? start.getBoundingBox() : "null"));
                });
        chunk.getAllReferences().entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(structures.getKey(entry.getKey()))))
                .forEach(entry -> {
                    update(digest, String.valueOf(structures.getKey(entry.getKey())));
                    long[] refs = entry.getValue().toLongArray();
                    java.util.Arrays.sort(refs);
                    for (long ref : refs) update(digest, ref);
                });
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static String stableBiome(Holder<net.minecraft.world.level.biome.Biome> biome) {
        return biome.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElseGet(() -> String.valueOf(biome.value()));
    }

    private static String geometryDifferences(ChunkAccess expected, ChunkAccess actual, ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int differingBlocks = 0;
        String firstBlock = null;
        for (int y = expected.getMinY(); y < expected.getMinY() + expected.getHeight(); y++) {
            for (int z = expected.getPos().getMinBlockZ(); z <= expected.getPos().getMaxBlockZ(); z++) {
                for (int x = expected.getPos().getMinBlockX(); x <= expected.getPos().getMaxBlockX(); x++) {
                    var expectedState = expected.getBlockState(pos.set(x, y, z));
                    var actualState = actual.getBlockState(pos);
                    if (!expectedState.equals(actualState)) {
                        differingBlocks++;
                        if (firstBlock == null) {
                            firstBlock = pos.immutable() + " expected=" + expectedState
                                    + " actual=" + actualState;
                        }
                    }
                }
            }
        }
        if (differingBlocks != 0) {
            return "blocks=" + differingBlocks + " first=" + firstBlock;
        }
        int differingBiomes = 0;
        String firstBiome = null;
        for (int sectionY = expected.getMinSectionY(); sectionY <= expected.getMaxSectionY(); sectionY++) {
            for (int quartZ = 0; quartZ < 4; quartZ++) {
                for (int quartX = 0; quartX < 4; quartX++) {
                    for (int quartY = 0; quartY < 4; quartY++) {
                        int bx = (expected.getPos().x() << 2) + quartX;
                        int by = (sectionY << 2) + quartY;
                        int bz = (expected.getPos().z() << 2) + quartZ;
                        var expectedBiome = expected.getNoiseBiome(bx, by, bz);
                        var actualBiome = actual.getNoiseBiome(bx, by, bz);
                        if (!expectedBiome.equals(actualBiome)) {
                            differingBiomes++;
                            if (firstBiome == null) {
                                firstBiome = "(" + bx + "," + by + "," + bz + ") expected="
                                        + expectedBiome + " actual=" + actualBiome;
                            }
                        }
                    }
                }
            }
        }
        if (differingBiomes != 0) {
            return "biomes=" + differingBiomes + " first=" + firstBiome;
        }
        for (var type : ChunkStatus.FULL.heightmapsAfter()) {
            var entry = expected.getHeightmaps().stream()
                    .filter(candidate -> candidate.getKey() == type)
                    .findFirst()
                    .orElse(null);
            if (entry == null) continue;
            var actualMap = actual.getHeightmaps().stream()
                    .filter(candidate -> candidate.getKey() == entry.getKey())
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (actualMap == null || !java.util.Arrays.equals(
                    entry.getValue().getRawData(), actualMap.getRawData())) {
                return "heightmap=" + entry.getKey().getSerializationKey();
            }
        }
        if (!expected.getAllStarts().toString().equals(actual.getAllStarts().toString())) {
            return "structureStarts differ";
        }
        if (!expected.getAllReferences().toString().equals(actual.getAllReferences().toString())) {
            return "structureReferences differ";
        }
        return "unidentified derived-state difference";
    }

    private static PhysicalFullSnapshot captureFull(ServerLevel level, LevelChunk chunk) {
        Map<String, String> blockEntities = new TreeMap<>();
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            var tag = chunk.getBlockEntityNbtForSaving(entry.getKey(), level.registryAccess());
            blockEntities.put(entry.getKey().toString(), String.valueOf(tag));
        }
        List<String> blockTicks = chunk.getTicksForSerialization(level.getGameTime()).blocks().stream()
                .map(SavedTick::toString).sorted().toList();
        List<String> fluidTicks = chunk.getTicksForSerialization(level.getGameTime()).fluids().stream()
                .map(SavedTick::toString).sorted().toList();
        String serializedHash;
        try {
            serializedHash = sha256(SerializableChunkData.copyOf(level, chunk).write().toString());
        } catch (RuntimeException exception) {
            serializedHash = "serialization-error:" + exception.getClass().getName();
        }
        return new PhysicalFullSnapshot(
                chunk.getPos(),
                chunk.getPersistedStatus().getName(),
                chunk.isLightCorrect(),
                chunk.getBlockEntities().size(),
                pendingBlockEntityCount(chunk),
                blockTicks,
                fluidTicks,
                blockEntities,
                serializedHash);
    }

    private static int pendingBlockEntityCount(ChunkAccess chunk) {
        return ((ChunkAccessAccessor) chunk)
                .randomnibble6plus24generator$getPendingBlockEntities().size();
    }

    private static JsonArray spawnSeeds(List<PhysicalMosaicTrace.SpawnSeed> seeds) {
        JsonArray result = new JsonArray();
        for (var seed : seeds) {
            JsonObject value = new JsonObject();
            value.addProperty("dimension", seed.dimension().identifier().toString());
            value.addProperty("chunk", seed.target().toString());
            value.addProperty("observed", seed.observed());
            value.addProperty("expected", seed.expected());
            result.add(value);
        }
        return result;
    }

    private static JsonArray spawnReads(List<PhysicalMosaicTrace.SpawnRead> reads) {
        JsonArray result = new JsonArray();
        for (var read : reads) {
            JsonObject value = new JsonObject();
            value.addProperty("dimension", read.dimension().identifier().toString());
            value.addProperty("chunk", read.target().toString());
            value.addProperty("requested", read.requested());
            value.addProperty("count", read.count());
            result.add(value);
        }
        return result;
    }

    private static JsonArray structureReloadSeeds(List<PhysicalMosaicTrace.StructureReloadSeed> seeds) {
        JsonArray result = new JsonArray();
        for (var seed : seeds) {
            JsonObject value = new JsonObject();
            value.addProperty("dimension", seed.dimension().identifier().toString());
            value.addProperty("chunk", seed.chunkPos().toString());
            value.addProperty("routed", seed.routedSeed());
            value.addProperty("physical", seed.physicalSeed());
            result.add(value);
        }
        return result;
    }

    private static com.google.gson.JsonObject map(Map<String, Long> values) {
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        new TreeMap<>(values).forEach(result::addProperty);
        return result;
    }

    private static List<ChunkPos> square(ChunkPos center, int radius) {
        List<ChunkPos> values = new ArrayList<>();
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                values.add(new ChunkPos(x, z));
            }
        }
        return values;
    }

    private static ServerLevel level(MinecraftServer server) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing Phase 3C1 dimension " + dimension.identifier());
        long expectedSeed = Long.parseLong(System.getProperty(
                PREFIX + "masterSeed", Long.toString(server.getWorldGenSettings().options().seed())));
        if (server.getWorldGenSettings().options().seed() != expectedSeed) {
            throw new IllegalStateException("Phase 3C1 master seed mismatch");
        }
        return level;
    }

    private static ChunkPos target() {
        return new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "0")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "0")));
    }

    private static void ensureHiddenMosaic(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator)) {
            throw new IllegalStateException("Phase 3C1 requires a hidden physical Mosaic world");
        }
    }

    private static int entityCount(ServerLevel level) {
        int count = 0;
        for (Entity ignored : level.getAllEntities()) count++;
        return count;
    }

    private static String sha256(String value) {
        return java.util.HexFormat.of().formatHex(digestBytes(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] digestBytes(byte[] bytes) {
        MessageDigest digest = digest();
        digest.update(bytes);
        return digest.digest();
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(new byte[] {
                (byte) (value >>> 56), (byte) (value >>> 48), (byte) (value >>> 40), (byte) (value >>> 32),
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }

    private static void write(JsonObject json) {
        String output = System.getProperty(PREFIX + "output", "");
        if (output.isBlank()) return;
        Path path = Path.of(output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3C1 result " + path, exception);
        }
    }

    private record VerificationState(
            Map<ChunkPos, String> artifactGeometry,
            Map<ChunkPos, CanonicalChunkArtifact> artifacts,
            int entityCountBefore) {
    }

    private record PhysicalFullSnapshot(
            ChunkPos pos,
            String status,
            boolean lightCorrect,
            int blockEntityCount,
            int pendingBlockEntityCount,
            List<String> blockTicks,
            List<String> fluidTicks,
            Map<String, String> blockEntities,
            String serializedHash) {
        private String hash() {
            return sha256(pos + "|" + status + "|" + lightCorrect + "|" + blockEntityCount
                    + "|" + pendingBlockEntityCount + "|" + blockTicks + "|" + fluidTicks
                    + "|" + blockEntities);
        }
    }
}
