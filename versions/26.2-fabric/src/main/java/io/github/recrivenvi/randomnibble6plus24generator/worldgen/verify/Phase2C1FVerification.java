package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;

/** Explicit Phase 2C1F plan export and unrelated-host isolated batch consumer. */
public final class Phase2C1FVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase2c1f.";

    private Phase2C1FVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String planOutput = System.getProperty(PREFIX + "exportPlan");
        if (planOutput != null && !planOutput.isBlank()) {
            exportPlan(Path.of(planOutput));
            server.execute(() -> server.halt(false));
            return;
        }
        String manifest = System.getProperty(PREFIX + "manifest");
        if (manifest == null || manifest.isBlank()) return;
        runManifest(server, Path.of(manifest), Path.of(require("output")));
        server.execute(() -> server.halt(false));
    }

    private static void exportPlan(Path output) {
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        MosaicSeedResolver resolver = new MosaicSeedResolver(profile);
        JsonArray fixtures = new JsonArray();
        List<Phase2C1Verification.Fixture> matrix = Phase2C1Verification.matrixFixtures();
        for (int index = 0; index < matrix.size(); index++) {
            Phase2C1Verification.Fixture fixture = matrix.get(index);
            JsonObject json = new JsonObject();
            json.addProperty("id", fixtureId(index));
            json.addProperty("masterSeed", Long.toString(fixture.masterSeed()));
            json.addProperty("localSeed", Long.toString(resolver.resolveLocalWorldSeed(
                    fixture.masterSeed(), fixture.dimension(), fixture.target())));
            json.addProperty("dimension", fixture.dimension().identifier().toString());
            json.addProperty("chunkX", fixture.target().x());
            json.addProperty("chunkZ", fixture.target().z());
            fixtures.add(json);
        }
        write(output, fixtures.toString());
    }

    private static String fixtureId(int index) {
        String group = index < 8 ? "ow-origin" : index < 40 ? "ow-nonorigin" : index < 56 ? "nether" : "end";
        return String.format(java.util.Locale.ROOT, "%s-%02d", group, index);
    }

    private static void runManifest(MinecraftServer server, Path manifest, Path output) {
        if (MosaicWorldIdentity.isMosaicWorld(server)) {
            throw new IllegalStateException("Phase 2C1F consumer requires an ordinary unrelated host world");
        }
        long hostSeed = server.getWorldGenSettings().options().seed();
        JsonArray fixtures;
        try {
            fixtures = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Phase 2C1F manifest " + manifest, exception);
        }
        JsonArray results = new JsonArray();
        long totalRuntimeMs = 0L;
        Runtime runtime = Runtime.getRuntime();
        long heapBefore = runtime.totalMemory() - runtime.freeMemory();
        long coarsePeakHeap = heapBefore;
        for (JsonElement element : fixtures) {
            JsonObject fixture = element.getAsJsonObject();
            JsonObject result = runFixture(server, hostSeed, fixture);
            results.add(result);
            totalRuntimeMs += result.get("runtimeMs").getAsLong();
            coarsePeakHeap = Math.max(coarsePeakHeap, runtime.totalMemory() - runtime.freeMemory());
        }
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Generation context leak after Phase 2C1F batch: "
                    + GenerationContextRegistry.bindingCount());
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("status", "PASS");
        summary.addProperty("hostSeed", Long.toString(hostSeed));
        summary.addProperty("fixtures", results.size());
        summary.addProperty("totalRuntimeMs", totalRuntimeMs);
        summary.addProperty("heapBeforeMiB", heapBefore / 1024L / 1024L);
        summary.addProperty("coarsePeakHeapMiB", coarsePeakHeap / 1024L / 1024L);
        summary.addProperty("heapAfterMiB", (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L);
        summary.add("results", results);
        write(output, summary.toString());
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1F independent isolated batch PASS fixtures={} hostSeed={} runtimeMs={}",
                results.size(), hostSeed, totalRuntimeMs);
    }

    private static JsonObject runFixture(MinecraftServer server, long hostSeed, JsonObject fixture) {
        String id = fixture.get("id").getAsString();
        long masterSeed = Long.parseLong(fixture.get("masterSeed").getAsString());
        long expectedLocalSeed = Long.parseLong(fixture.get("localSeed").getAsString());
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse(fixture.get("dimension").getAsString()));
        ChunkPos target = new ChunkPos(fixture.get("chunkX").getAsInt(), fixture.get("chunkZ").getAsInt());
        if (hostSeed == expectedLocalSeed) {
            throw new IllegalStateException("Unrelated host seed equals local seed for " + id);
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing Phase 2C1F dimension " + dimension.identifier());
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        long actualLocalSeed = new MosaicSeedResolver(profile)
                .resolveLocalWorldSeed(masterSeed, dimension, target);
        if (actualLocalSeed != expectedLocalSeed) {
            throw new IllegalStateException("Manifest local seed mismatch for " + id);
        }

        PhysicalHostSnapshot before = PhysicalHostSnapshot.capture(level, target);
        long started = System.nanoTime();
        FeatureStableGenerationRun run = new IsolatedGenerationSession(profile)
                .generateFeaturesStable(level, masterSeed, target);
        long generationFinished = System.nanoTime();
        FeatureStableSnapshot isolated = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), run.targetChunk(), level.registryAccess());
        long snapshotFinished = System.nanoTime();
        long runtimeMs = (snapshotFinished - started) / 1_000_000L;
        PhysicalHostSnapshot after = PhysicalHostSnapshot.capture(level, target);
        if (!before.equals(after)) {
            throw new IllegalStateException("Physical host side effect for " + id + ": before=" + before + ", after=" + after);
        }
        if (run.metrics().virtualStorageScanCount() != 0L) {
            throw new IllegalStateException("Physical storage scan during Phase 2C1F " + id);
        }
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Generation context leak after " + id);
        }
        assertKnownLevelEscapes(run, id);

        FeatureStableSnapshot nativeSnapshot = FeatureStableSnapshot.read(
                Path.of(fixture.get("nativeSnapshot").getAsString()));
        FeatureStageSnapshot.Diff diff = nativeSnapshot.diff(isolated);
        if (!diff.equivalent()) {
            throw new IllegalStateException(
                    "Independent Native FEATURES mismatch " + id + ": first=" + diff.firstDifference()
                            + ", differingBlocks=" + diff.differingBlocks()
                            + ", metadata=" + nativeSnapshot.deterministicMetadataDifference(isolated));
        }
        long nativeInvocationCount = fixture.get("nativeFeatureSeedInvocationCount").getAsLong();
        String nativeSequenceHash = fixture.get("nativeFeatureSeedSequenceHash").getAsString();
        long nativeDecorationReads = fixture.get("nativeDecorationSeedReads").getAsLong();
        String nativeVisibleBiomes = fixture.get("nativeFeatureVisibleBiomeSequence").getAsString();
        if (nativeInvocationCount != run.featureTrace().featureSeedInvocationCount()
                || !nativeSequenceHash.equals(Long.toUnsignedString(
                        run.featureTrace().featureSeedSequenceHash(), 16))
                || nativeDecorationReads != run.featureTrace().decorationSeedReads()
                || !nativeVisibleBiomes.equals(run.featureTrace().featureVisibleBiomeSequence().toString())) {
            throw new IllegalStateException(
                    "Native/isolated FEATURES RNG trace mismatch " + id
                            + " native=" + nativeInvocationCount + "/" + nativeSequenceHash + "/" + nativeDecorationReads
                            + " isolated=" + run.featureTrace().featureSeedInvocationCount() + "/"
                            + Long.toUnsignedString(run.featureTrace().featureSeedSequenceHash(), 16) + "/"
                            + run.featureTrace().decorationSeedReads());
        }

        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("dimension", dimension.identifier().toString());
        result.addProperty("masterSeed", Long.toString(masterSeed));
        result.addProperty("localSeed", Long.toString(actualLocalSeed));
        result.addProperty("chunkX", target.x());
        result.addProperty("chunkZ", target.z());
        result.addProperty("hash", isolated.hash());
        result.addProperty("runtimeMs", runtimeMs);
        result.addProperty("generationRuntimeMs", (generationFinished - started) / 1_000_000L);
        result.addProperty("snapshotCostMs", (snapshotFinished - generationFinished) / 1_000_000L);
        result.addProperty("virtualChunks", run.metrics().virtualChunkCount());
        result.addProperty("virtualStatusDistribution", run.featureTrace().virtualStatusDistribution().toString());
        result.addProperty("featureSeedInvocationCount", run.featureTrace().featureSeedInvocationCount());
        result.addProperty("featureSeedSequenceHash", Long.toUnsignedString(
                run.featureTrace().featureSeedSequenceHash(), 16));
        result.addProperty("decorationSeedReads", run.featureTrace().decorationSeedReads());
        result.addProperty("featureVisibleBiomeSequence", run.featureTrace().featureVisibleBiomeSequence().toString());
        result.addProperty("writers", run.featureTrace().requestedWriters().size());
        result.addProperty("maxConcurrentFeatureWriters", run.featureTrace().maxConcurrentFeatureWriters());
        result.addProperty("blockEntities", isolated.blockEntityCount());
        result.addProperty("instantiatedBlockEntities", isolated.instantiatedBlockEntityCount());
        result.addProperty("pendingBlockEntityNbt", isolated.pendingBlockEntityNbtCount());
        result.addProperty("blockTicks", isolated.blockTickCount());
        result.addProperty("fluidTicks", isolated.fluidTickCount());
        result.addProperty("postProcessing", isolated.postProcessingCount());
        result.addProperty("entities", isolated.entityCount());
        result.addProperty("rawEntityNbt", isolated.rawEntityNbt().toString());
        result.addProperty("canonicalEntityNbt", isolated.canonicalEntityNbt().toString());
        result.addProperty("blockEntityNbt", isolated.blockEntityNbt().toString());
        result.addProperty("blockTicksData", isolated.blockTickData().toString());
        result.addProperty("fluidTicksData", isolated.fluidTickData().toString());
        result.addProperty("structureStarts", isolated.structureStartData().toString());
        result.addProperty("paleMossRouteHits", run.featureTrace().paleMossGeneratorRedirects());
        result.addProperty("cappedProcessorRouteHits", run.featureTrace().cappedProcessorSeedRedirects());
        result.addProperty("suppressedPoiUpdates", run.metrics().suppressedPhysicalPoiUpdates());
        result.addProperty("physicalStorageScans", run.metrics().virtualStorageScanCount());
        result.addProperty("physicalLevelEscapes", run.featureTrace().physicalLevelEscapeSummary().toString());
        result.addProperty("featureWrites", run.featureTrace().featureWriteSummary().toString());
        return result;
    }

    private static void assertKnownLevelEscapes(FeatureStableGenerationRun run, String id) {
        for (String caller : run.featureTrace().physicalLevelEscapeSummary().keySet()) {
            if (caller.equals("net.minecraft.world.level.StructureManager.forWorldGenRegion")
                    || caller.startsWith("net.minecraft.world.level.levelgen.feature.EndSpikeFeature.")
                    || caller.startsWith("net.minecraft.world.level.levelgen.feature.treedecorators.PaleMossDecorator.")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor.")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.structures.EndCityPieces$")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces$")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece.")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$")
                    || caller.startsWith("net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.")) {
                continue;
            }
            throw new IllegalStateException("Unknown physical getLevel escape in " + id + ": " + caller);
        }
    }

    static record PhysicalHostSnapshot(
            int loadedChunks,
            int entities,
            int blockTicks,
            int fluidTicks,
            long poiCount,
            String loadedFrontierHash) {

        static PhysicalHostSnapshot capture(ServerLevel level, ChunkPos target) {
            int entities = 0;
            for (Entity ignored : level.getAllEntities()) entities++;
            BlockPos center = target.getMiddleBlockPosition(level.getSeaLevel());
            long poiCount = level.getPoiManager().getCountInRange(
                    ignored -> true, center, 32, PoiManager.Occupancy.ANY);
            return new PhysicalHostSnapshot(
                    level.getChunkSource().getLoadedChunksCount(),
                    entities,
                    level.getBlockTicks().count(),
                    level.getFluidTicks().count(),
                    poiCount,
                    loadedFrontierHash(level, target));
        }

        private static String loadedFrontierHash(ServerLevel level, ChunkPos target) {
            MessageDigest digest = digest();
            ServerChunkCache source = level.getChunkSource();
            for (int z = target.z() - 1; z <= target.z() + 1; z++) {
                for (int x = target.x() - 1; x <= target.x() + 1; x++) {
                    LevelChunk chunk = source.getChunkNow(x, z);
                    update(digest, x); update(digest, z);
                    if (chunk == null) {
                        update(digest, "absent");
                        continue;
                    }
                    update(digest, chunk.getPersistedStatus().toString());
                    update(digest, chunk.isUnsaved() ? "dirty" : "clean");
                    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                    for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                            for (int localX = 0; localX < 16; localX++) {
                                update(digest, chunk.getBlockState(cursor.set(
                                        chunk.getPos().getMinBlockX() + localX, y,
                                        chunk.getPos().getMinBlockZ() + localZ)).toString());
                            }
                        }
                    }
                    int minQuartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
                    int minQuartY = QuartPos.fromBlock(chunk.getMinY());
                    int minQuartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
                    int quartHeight = chunk.getHeight() / QuartPos.SIZE;
                    for (int qy = 0; qy < quartHeight; qy++) {
                        for (int qz = 0; qz < 4; qz++) {
                            for (int qx = 0; qx < 4; qx++) {
                                Holder<Biome> biome = chunk.getNoiseBiome(
                                        minQuartX + qx, minQuartY + qy, minQuartZ + qz);
                                update(digest, biome.unwrapKey()
                                        .map(key -> key.identifier().toString()).orElse(biome.toString()));
                            }
                        }
                    }
                    for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
                        update(digest, entry.getKey().getSerializationKey());
                        for (long value : entry.getValue().getRawData()) update(digest, value);
                    }
                    TreeMap<String, String> starts = new TreeMap<>();
                    var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                    for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                        starts.put(String.valueOf(structures.getKey(entry.getKey())), entry.getValue().toString());
                    }
                    update(digest, starts.toString());
                    update(digest, chunk.getAllReferences().toString());
                    TreeMap<String, String> blockEntities = new TreeMap<>();
                    for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                        blockEntities.put(entry.getKey().toShortString(),
                                entry.getValue().saveWithFullMetadata(level.registryAccess()).toString());
                    }
                    update(digest, blockEntities.toString());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(new byte[] {
                (byte) (value >>> 56), (byte) (value >>> 48), (byte) (value >>> 40), (byte) (value >>> 32),
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }

    private static String require(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + PREFIX + suffix);
        return value;
    }

    private static void write(Path output, String value) {
        try {
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Files.writeString(output, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 2C1F output " + output, exception);
        }
    }
}
