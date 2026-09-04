package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayStore;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureIndexStore;

/** Test-only acceptance probe for the loaded-Chunk Structure Overlay V1. */
public final class Phase3C3AOverlayVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3c3a.overlay.";
    private static volatile boolean started;

    private Phase3C3AOverlayVerification() {
    }

    public static boolean skipInitialSpawnIfRequested() {
        return !System.getProperty(PREFIX + "mode", "").isBlank();
    }

    public static boolean skipPrepareLevelsIfRequested() {
        return skipInitialSpawnIfRequested();
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "mode", "");
        if (mode.isBlank() || started) return;
        started = true;
        if (MosaicWorldIdentity.runtimeContext(server).isEmpty()) {
            throw new IllegalStateException("Phase 3C3A overlay probe requires a Mosaic world");
        }
        MosaicStructureOverlayTrace.reset();
        MosaicPhysicalMaterializer.Metrics baselineMaterializer = MosaicPhysicalMaterializer.metrics();
        if (mode.equals("clear") || mode.equals("clear-reload")) {
            runProjectionReplacement(server, mode, baselineMaterializer);
            return;
        }
        boolean reload = "reload".equals(mode);
        List<Fixture> fixtures = fixtureSpec().isBlank()
                ? discoverFixtures(server)
                : loadFixtures(server, fixtureSpec());
        List<Query> queries = fixtures.stream().map(Phase3C3AOverlayVerification::query).toList();
        Query fortress = fixtures.stream()
                .filter(fixture -> fixture.structureId().equals("minecraft:fortress"))
                .findFirst()
                .map(Phase3C3AOverlayVerification::queryFortress)
                .orElse(null);
        if (fortress != null) queries = append(queries, fortress);

        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("mode", mode);
        result.addProperty("artifactGenerations",
                MosaicPhysicalMaterializer.metrics().artifactCaptureCount()
                        - baselineMaterializer.artifactCaptureCount());
        result.addProperty("worldSeed", server.getWorldGenSettings().options().seed());
        result.addProperty("fixtureCount", fixtures.size());
        result.addProperty("fixtureSpec", encodeFixtureSpec(fixtures));
        result.add("fixtures", fixturesJson(fixtures));
        result.add("queries", queriesJson(queries));
        result.add("crossSeedBoundary", crossSeedBoundary(server, fixtures));
        result.add("overlayCalls", map(MosaicStructureOverlayTrace.snapshot()));
        result.addProperty("locateResult", "indexed physical projection");
        if (reload && MosaicPhysicalMaterializer.metrics().artifactCaptureCount()
                != baselineMaterializer.artifactCaptureCount()) {
            throw new IllegalStateException("Overlay reload regenerated a canonical Artifact");
        }
        write(result);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3C3A Structure Overlay PASS fixtures={} mode={}", fixtures.size(), mode);
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) server.halt(false);
    }

    private static void runProjectionReplacement(
            MinecraftServer server, String mode, MosaicPhysicalMaterializer.Metrics baselineMaterializer) {
        String encoded = fixtureSpec();
        if (encoded.isBlank()) throw new IllegalStateException("Projection replacement requires fixtureSpec");
        if (mode.equals("clear")) {
            List<Fixture> fixtures = loadFixtures(server, encoded);
            for (Fixture fixture : fixtures) {
                long seed = MosaicWorldIdentity.runtimeContext(fixture.level()).orElseThrow()
                        .resolveLocalWorldSeed(fixture.level().dimension(), fixture.owner());
                MosaicStructureOverlayStore.publish(fixture.level(), fixture.owner(), seed, List.of());
                if (!MosaicStructureOverlayStore.externalStarts(fixture.level(), fixture.owner()).isEmpty()
                        || MosaicStructureIndexStore.indexedEntryCount(fixture.level()) != 0) {
                    throw new IllegalStateException("Empty Overlay replacement left a cached projection: "
                            + fixture.owner());
                }
            }
        } else {
            for (String entry : encoded.split(";")) {
                if (entry.isBlank()) continue;
                String[] parts = entry.split("\\|", -1);
                if (parts.length != 4) throw new IllegalStateException("Malformed projection fixture: " + entry);
                ServerLevel level = level(server, parts[1]);
                ChunkPos owner = new ChunkPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                requestFull(server, level, owner);
                if (!MosaicStructureOverlayStore.externalStarts(level, owner).isEmpty()
                        || MosaicStructureIndexStore.indexedEntryCount(level) != 0) {
                    throw new IllegalStateException("Cleared Overlay projection reappeared after restart: " + entry);
                }
            }
        }
        if (MosaicPhysicalMaterializer.metrics().artifactCaptureCount()
                != baselineMaterializer.artifactCaptureCount()) {
            throw new IllegalStateException("Projection replacement triggered canonical generation");
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("mode", mode);
        result.addProperty("fixtureSpec", encoded);
        result.addProperty("artifactGenerations", 0L);
        result.addProperty("externalStartsAfter", 0L);
        write(result);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3C3A Overlay projection replacement PASS mode={}", mode);
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) server.halt(false);
    }

    private static List<Fixture> discoverFixtures(MinecraftServer server) {
        List<Fixture> fixtures = new ArrayList<>();
        Registry<Structure> structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<String> requested = Set.of(System.getProperty(PREFIX + "structures",
                "village,mineshaft,trial_chambers,monument,fortress,end_city")
                .split(","));
        if (requested.contains("village")) fixtures.add(find(server, level(server, "minecraft:overworld"), structures,
                "village", List.of(new ChunkPos(-5092, -2474)), 96));
        if (requested.contains("mineshaft")) fixtures.add(find(server, level(server, "minecraft:overworld"), structures,
                "mineshaft", List.of(), 256));
        if (requested.contains("trial_chambers")) fixtures.add(find(server, level(server, "minecraft:overworld"), structures,
                "trial_chambers", List.of(new ChunkPos(3699, -6116)), 256));
        if (requested.contains("monument")) fixtures.add(find(server, level(server, "minecraft:overworld"), structures,
                "monument", List.of(new ChunkPos(52, -247)), 0));
        if (requested.contains("fortress")) fixtures.add(find(server, level(server, "minecraft:the_nether"), structures,
                "fortress", List.of(), 128));
        if (requested.contains("end_city")) fixtures.add(find(server, level(server, "minecraft:the_end"), structures,
                "end_city", List.of(), 128));
        if (fixtures.isEmpty()) throw new IllegalStateException("No Overlay structures requested");
        return List.copyOf(fixtures);
    }

    private static List<Fixture> loadFixtures(MinecraftServer server, String encoded) {
        List<Fixture> result = new ArrayList<>();
        for (String entry : encoded.split(";")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split("\\|", -1);
            if (parts.length != 4) {
                throw new IllegalStateException("Malformed Overlay fixture spec entry: " + entry);
            }
            ServerLevel level = level(server, parts[1]);
            ChunkPos owner = new ChunkPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = registry.get(Identifier.parse(parts[0])).orElseThrow(
                    () -> new IllegalStateException("Missing persisted Overlay structure " + parts[0])).value();
            requestFull(server, level, owner);
            StructureStart start = MosaicStructureOverlayStore.startsForOwner(level, owner).stream()
                    .filter(candidate -> candidate.getStructure() == structure)
                    .findFirst().orElse(null);
            if (start == null || !start.isValid()) {
                throw new IllegalStateException("Persisted Overlay fixture no longer has a valid start: " + entry);
            }
            BlockPos point = piecePoint(start, owner, level);
            if (point == null) throw new IllegalStateException("Persisted Overlay fixture has no piece: " + entry);
            result.add(new Fixture(level, owner, structure, parts[0], start, point));
        }
        if (result.isEmpty()) throw new IllegalStateException("Overlay fixture spec was empty");
        return List.copyOf(result);
    }

    private static Fixture find(
            MinecraftServer server,
            ServerLevel level,
            Registry<Structure> registry,
            String requestedId,
            List<ChunkPos> explicit,
            int radius) {
        List<Holder.Reference<Structure>> candidates = registry.entrySet().stream()
                .filter(entry -> requestedId.equals(entry.getKey().identifier().getPath())
                        || (requestedId.equals("village")
                                && entry.getKey().identifier().getPath().startsWith("village_")))
                .map(entry -> registry.get(entry.getKey()).orElseThrow())
                .toList();
        if (candidates.isEmpty()) throw new IllegalStateException("Missing structure registry entry for " + requestedId);
        Map<Holder.Reference<Structure>, List<StructurePlacement>> placements = candidates.stream()
                .collect(Collectors.toMap(candidate -> candidate, candidate -> placementsFor(level, candidate)));
        List<ChunkPos> positions = new ArrayList<>(explicit);
        int scanCenterX = Integer.getInteger(PREFIX + "scanCenterX", 0);
        int scanCenterZ = Integer.getInteger(PREFIX + "scanCenterZ", 0);
        positions.addAll(spiral(radius, scanCenterX, scanCenterZ));
        int maxCandidates = Math.max(1, Integer.getInteger(PREFIX + "maxCandidates", 2048));
        if (positions.size() > maxCandidates) positions = new ArrayList<>(positions.subList(0, maxCandidates));
        for (ChunkPos pos : positions) {
            for (Holder.Reference<Structure> holder : candidates) {
                if (explicit.contains(pos) && Boolean.getBoolean(PREFIX + "traceCandidates")) {
                    RandomNibble6Plus24Generator.LOGGER.info(
                            "Overlay explicit {} structure={} targetPotential={} neighborhoodPotential={}",
                            pos, requestedId, isPotentialStructureChunk(level, pos, holder, placements.get(holder)),
                            isPotentialInWriterNeighborhood(level, pos, holder, placements.get(holder)));
                }
                if (!explicit.contains(pos)
                        && !isPotentialInWriterNeighborhood(level, pos, holder, placements.get(holder))) continue;
                LevelChunk chunk;
                try {
                    chunk = requestFull(server, level, pos);
                } catch (RuntimeException exception) {
                    if (Boolean.getBoolean(PREFIX + "traceCandidates")) {
                        RandomNibble6Plus24Generator.LOGGER.info(
                                "Overlay candidate {} failed to load for {}: {}",
                                pos, requestedId, exception.getMessage());
                    }
                    continue;
                }
                if (Boolean.getBoolean(PREFIX + "traceCandidates")) {
                    String physicalStarts = chunk.getAllStarts().keySet().stream()
                            .map(structureValue -> String.valueOf(registry.getKey(structureValue)))
                            .collect(Collectors.joining(","));
                    String projectedStarts = MosaicStructureOverlayStore.externalStarts(level, pos).stream()
                            .map(startValue -> String.valueOf(registry.getKey(startValue.getStructure())))
                            .collect(Collectors.joining(","));
                    RandomNibble6Plus24Generator.LOGGER.info(
                            "Overlay candidate {} structure={} physicalStarts=[{}] projectedStarts=[{}]",
                            pos, requestedId, physicalStarts, projectedStarts);
                }
                for (StructureStart start : MosaicStructureOverlayStore.startsForOwner(level, pos)) {
                    if (start.getStructure() != holder.value()) continue;
                    if (start != null && start.isValid()) {
                        BlockPos point = piecePoint(start, pos, level);
                        if (point != null) {
                            return new Fixture(level, pos, holder.value(),
                                    registry.getKey(holder.value()).toString(), start, point);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("No loaded Mosaic structure fixture for " + requestedId
                + " after radius=" + radius + " explicit=" + explicit);
    }

    private static List<StructurePlacement> placementsFor(
            ServerLevel level, Holder<Structure> structure) {
        ChunkGenerator source = level.getChunkSource().getGenerator();
        if (!(source instanceof MosaicChunkGenerator mosaic)) return List.of();
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                mosaic.getBiomeSource(), mosaic.generatorSettings());
        RandomState randomState = RandomState.create(
                generator.generatorSettings().value(),
                level.registryAccess().lookupOrThrow(Registries.NOISE), 0L);
        ChunkGeneratorStructureState state = generator.createState(
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET), randomState, 0L);
        state.ensureStructuresGenerated();
        return state.getPlacementsForStructure(structure);
    }

    private static boolean isPotentialStructureChunk(
            ServerLevel level, ChunkPos pos, Holder<Structure> structure,
            List<StructurePlacement> placements) {
        ChunkGenerator source = level.getChunkSource().getGenerator();
        if (!(source instanceof MosaicChunkGenerator mosaic)) return false;
        long seed = MosaicWorldIdentity.runtimeContext(level).orElseThrow()
                .resolveLocalWorldSeed(level.dimension(), pos);
        if (placements.stream().allMatch(placement -> placement instanceof RandomSpreadStructurePlacement)) {
            return placements.stream().anyMatch(placement ->
                    ((RandomSpreadStructurePlacement) placement).getPotentialStructureChunk(seed, pos.x(), pos.z())
                            .equals(pos));
        }
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                mosaic.getBiomeSource(), mosaic.generatorSettings());
        RandomState randomState = RandomState.create(
                generator.generatorSettings().value(),
                level.registryAccess().lookupOrThrow(Registries.NOISE),
                seed);
        ChunkGeneratorStructureState state = generator.createState(
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed);
        state.ensureStructuresGenerated();
        return placements.stream()
                .anyMatch(placement -> placement instanceof RandomSpreadStructurePlacement spread
                        ? spread.getPotentialStructureChunk(seed, pos.x(), pos.z()).equals(pos)
                        : placement.isStructureChunk(state, pos.x(), pos.z()));
    }

    private static boolean isPotentialInWriterNeighborhood(
            ServerLevel level, ChunkPos target, Holder<Structure> structure,
            List<StructurePlacement> placements) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                ChunkPos candidate = new ChunkPos(target.x() + dx, target.z() + dz);
                boolean potential = isPotentialStructureChunk(level, candidate, structure, placements);
                if (potential) return true;
                if (Boolean.getBoolean(PREFIX + "traceCandidates")) {
                    RandomNibble6Plus24Generator.LOGGER.info(
                            "Overlay writer candidate {} for target {} structure={} potential={}",
                            candidate, target, structure.unwrapKey().map(key -> key.identifier()).orElse(null), potential);
                }
            }
        }
        return false;
    }

    private static LevelChunk requestFull(MinecraftServer server, ServerLevel level, ChunkPos pos) {
        var future = level.getChunkSource().getChunkFuture(pos.x(), pos.z(),
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
        server.managedBlock(future::isDone);
        var result = future.join();
        ChunkAccess chunk = result.orElseThrow(() ->
                new IllegalStateException("Mosaic structure fixture failed to load " + pos
                        + ": " + result.getError() + " result=" + result));
        if (!(chunk instanceof LevelChunk levelChunk)) {
            throw new IllegalStateException("Mosaic structure fixture did not reach LevelChunk " + pos);
        }
        return levelChunk;
    }

    private static String fixtureSpec() {
        return System.getProperty(PREFIX + "fixtureSpec", "");
    }

    private static String encodeFixtureSpec(List<Fixture> fixtures) {
        return fixtures.stream()
                .map(fixture -> fixture.structureId() + "|"
                        + fixture.level().dimension().identifier() + "|"
                        + fixture.owner().x() + "|" + fixture.owner().z())
                .collect(Collectors.joining(";"));
    }

    private static Query query(Fixture fixture) {
        StructureManager manager = fixture.level().structureManager();
        StructureStart piece = manager.getStructureWithPieceAt(fixture.point(), fixture.structure());
        if (!piece.isValid() || piece.getStructure() != fixture.structure()) {
            throw new IllegalStateException("Overlay piece query missed " + fixture);
        }
        StructureStart at = manager.getStructureAt(fixture.point(), fixture.structure());
        if (!at.isValid()) throw new IllegalStateException("Overlay structure-at query missed " + fixture);
        if (!manager.hasAnyStructureAt(fixture.point())) {
            throw new IllegalStateException("Overlay hasAnyStructureAt query missed " + fixture);
        }
        Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> all = manager.getAllStructuresAt(fixture.point());
        if (!all.containsKey(fixture.structure())) {
            throw new IllegalStateException("Overlay getAllStructuresAt query missed " + fixture);
        }
        List<StructureStart> starts = manager.startsForStructure(
                fixture.owner(), candidate -> candidate == fixture.structure());
        if (starts.stream().noneMatch(start -> start.getChunkPos().equals(fixture.start().getChunkPos()))) {
            throw new IllegalStateException("Overlay startsForStructure query missed " + fixture);
        }
        Registry<Structure> registry = fixture.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.wrapAsHolder(fixture.structure());
        LocationPredicate predicate = new LocationPredicate(
                Optional.empty(), Optional.empty(), Optional.of(HolderSet.direct(holder)),
                Optional.of(fixture.level().dimension()), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        if (!predicate.matches(fixture.level(), fixture.point().getX() + .5D,
                fixture.point().getY() + .5D, fixture.point().getZ() + .5D)) {
            throw new IllegalStateException("Overlay LocationPredicate query missed " + fixture);
        }
        HolderSet<Structure> direct = HolderSet.direct(registry.wrapAsHolder(fixture.structure()));
        var located = fixture.level().getChunkSource().getGenerator().findNearestMapStructure(
                fixture.level(), direct, fixture.point(), 32, false);
        if (located == null
                || located.getSecond().value() != fixture.structure()
                || !ChunkPos.containing(located.getFirst()).equals(fixture.owner())) {
            throw new IllegalStateException("Mosaic /locate index returned an invalid physical projection for "
                    + fixture + ": " + located);
        }
        long externalStarts = MosaicStructureOverlayStore.externalStarts(fixture.level(), fixture.owner()).stream()
                .filter(start -> start.getStructure() == fixture.structure()).count();
        String signature = fixture.structureId() + "|owner=" + fixture.owner()
                + "|start=" + fixture.start().getChunkPos()
                + "|box=" + fixture.start().getBoundingBox()
                + "|pieces=" + fixture.start().getPieces().size()
                + "|externalStarts=" + externalStarts
                + "|locator=" + located.getFirst();
        return new Query(fixture.structureId(), fixture.owner(), fixture.point(), signature, externalStarts);
    }

    private static Query queryFortress(Fixture fixture) {
        BlockPos fortressPoint = piecePoint(fixture.start(), fixture.owner(), fixture.level());
        if (fortressPoint == null) fortressPoint = findFortressBrickPoint(fixture);
        if (fortressPoint == null) throw new IllegalStateException("Fortress fixture has no nether-brick spawn point");
        boolean inside = net.minecraft.world.level.NaturalSpawner.isInNetherFortressBounds(
                fortressPoint, fixture.level(), MobCategory.MONSTER, fixture.level().structureManager());
        if (!inside) throw new IllegalStateException("NaturalSpawner did not observe Mosaic fortress overlay");
        var source = fixture.level().getChunkSource().getGenerator();
        if (!(source instanceof MosaicChunkGenerator mosaic)) {
            throw new IllegalStateException("Mosaic fortress fixture lost Mosaic generator identity");
        }
        Registry<Structure> registry = fixture.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        long localSeed = MosaicWorldIdentity.runtimeContext(fixture.level()).orElseThrow()
                .resolveLocalWorldSeed(fixture.level().dimension(), fixture.owner());
        NoiseBasedChunkGenerator localGenerator = new NoiseBasedChunkGenerator(
                mosaic.getBiomeSource(), mosaic.generatorSettings());
        RandomState localRandomState = RandomState.create(
                localGenerator.generatorSettings().value(),
                fixture.level().registryAccess().lookupOrThrow(Registries.NOISE), localSeed);
        var biome = localGenerator.getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(fortressPoint.getX()),
                QuartPos.fromBlock(fortressPoint.getY()),
                QuartPos.fromBlock(fortressPoint.getZ()), localRandomState.sampler());
        var mobs = mosaic.getMobsAt(biome, fixture.level().structureManager(), MobCategory.MONSTER, fortressPoint);
        if (mobs != NetherFortressStructure.FORTRESS_ENEMIES
                && !mobs.equals(NetherFortressStructure.FORTRESS_ENEMIES)) {
            throw new IllegalStateException("Mosaic fortress spawn override did not use Overlay V1: point="
                    + fortressPoint + ", structures=" + fixture.level().structureManager()
                    .getAllStructuresAt(fortressPoint).keySet() + ", mobs=" + mobs
                    + ", mobEntries=" + mobs.unwrap()
                    + ", expectedEntries=" + NetherFortressStructure.FORTRESS_ENEMIES.unwrap());
        }
        return new Query(fixture.structureId(), fixture.owner(), fortressPoint,
                "fortress-spawn-override|inside=" + inside + "|entries=" + mobs.unwrap().size(), 0L);
    }

    private static BlockPos findFortressBrickPoint(Fixture fixture) {
        int minX = Math.max(fixture.owner().getMinBlockX(), fixture.start().getBoundingBox().minX());
        int maxX = Math.min(fixture.owner().getMaxBlockX(), fixture.start().getBoundingBox().maxX());
        int minZ = Math.max(fixture.owner().getMinBlockZ(), fixture.start().getBoundingBox().minZ());
        int maxZ = Math.min(fixture.owner().getMaxBlockZ(), fixture.start().getBoundingBox().maxZ());
        int minY = Math.max(fixture.level().getMinY() + 1, fixture.start().getBoundingBox().minY() + 1);
        int maxY = Math.min(fixture.level().getMaxY() - 1, fixture.start().getBoundingBox().maxY());
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (fixture.level().getBlockState(candidate.below())
                            .is(net.minecraft.world.level.block.Blocks.NETHER_BRICKS)) return candidate;
                }
            }
        }
        return null;
    }

    private static BlockPos piecePoint(StructureStart start, ChunkPos owner, ServerLevel level) {
        for (var piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            int minX = Math.max(box.minX(), owner.getMinBlockX());
            int maxX = Math.min(box.maxX(), owner.getMaxBlockX());
            int minZ = Math.max(box.minZ(), owner.getMinBlockZ());
            int maxZ = Math.min(box.maxZ(), owner.getMaxBlockZ());
            int minY = Math.max(box.minY(), level.getMinY());
            int maxY = Math.min(box.maxY(), level.getMaxY() - 1);
            if (minX <= maxX && minZ <= maxZ && minY <= maxY) {
                return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
            }
        }
        return null;
    }

    private static ServerLevel level(MinecraftServer server, String id) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
        ServerLevel level = server.getLevel(key);
        if (level == null) throw new IllegalStateException("Missing Overlay fixture dimension " + id);
        return level;
    }

    private static List<ChunkPos> spiral(int radius, int centerX, int centerZ) {
        List<ChunkPos> result = new ArrayList<>();
        for (int r = 0; r <= radius; r++) {
            for (int z = -r; z <= r; z++) {
                for (int x = -r; x <= r; x++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == r) {
                        result.add(new ChunkPos(centerX + x, centerZ + z));
                    }
                }
            }
        }
        return result;
    }

    private static JsonArray fixturesJson(List<Fixture> fixtures) {
        JsonArray result = new JsonArray();
        for (Fixture fixture : fixtures) {
            JsonObject value = new JsonObject();
            value.addProperty("structure", fixture.structureId());
            value.addProperty("dimension", fixture.level().dimension().identifier().toString());
            value.addProperty("chunk", fixture.owner().toString());
            value.addProperty("point", fixture.point().toString());
            value.addProperty("startChunk", fixture.start().getChunkPos().toString());
            value.addProperty("pieces", fixture.start().getPieces().size());
            value.addProperty("box", fixture.start().getBoundingBox().toString());
            result.add(value);
        }
        return result;
    }

    private static JsonArray queriesJson(List<Query> queries) {
        JsonArray result = new JsonArray();
        for (Query query : queries) {
            JsonObject value = new JsonObject();
            value.addProperty("structure", query.structureId());
            value.addProperty("owner", query.owner().toString());
            value.addProperty("point", query.point().toString());
            value.addProperty("signature", query.signature());
            value.addProperty("externalStarts", query.externalStarts());
            result.add(value);
        }
        return result;
    }

    private static JsonObject crossSeedBoundary(MinecraftServer server, List<Fixture> fixtures) {
        JsonObject result = new JsonObject();
        for (Fixture fixture : fixtures) {
            if (!fixture.structureId().equals("minecraft:monument")) continue;
            ChunkPos adjacent = new ChunkPos(fixture.owner().x() + 1, fixture.owner().z());
            requestFull(server, fixture.level(), adjacent);
            int x = adjacent.getMiddleBlockX();
            int z = Math.max(adjacent.getMinBlockZ(), Math.min(adjacent.getMaxBlockZ(),
                    fixture.start().getBoundingBox().minZ()));
            int y = Math.max(fixture.level().getMinY(), Math.min(fixture.level().getMaxY() - 1,
                    fixture.point().getY()));
            BlockPos point = new BlockPos(x, y, z);
            StructureStart leaked = fixture.level().structureManager()
                    .getStructureWithPieceAt(point, fixture.structure());
            if (leaked.isValid() && leaked.getChunkPos().equals(fixture.start().getChunkPos())) {
                throw new IllegalStateException("Cross-seed Overlay leaked adjacent physical structure start at "
                        + point);
            }
            result.addProperty("tested", true);
            result.addProperty("owner", fixture.owner().toString());
            result.addProperty("adjacent", adjacent.toString());
            result.addProperty("point", point.toString());
            result.addProperty("leakedOwnerStart", leaked.isValid()
                    ? leaked.getChunkPos().toString() : "none");
            return result;
        }
        result.addProperty("tested", false);
        return result;
    }

    private static JsonObject map(Map<String, Long> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    private static List<Query> append(List<Query> values, Query value) {
        List<Query> result = new ArrayList<>(values);
        result.add(value);
        return List.copyOf(result);
    }

    private static void write(JsonObject result) {
        String output = System.getProperty(PREFIX + "output", "");
        if (output.isBlank()) return;
        Path path = Path.of(output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, result.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3C3A overlay result", exception);
        }
    }

    private record Fixture(
            ServerLevel level,
            ChunkPos owner,
            Structure structure,
            String structureId,
            StructureStart start,
            BlockPos point) {
    }

    private record Query(
            String structureId,
            ChunkPos owner,
            BlockPos point,
            String signature,
            long externalStarts) {
    }
}
