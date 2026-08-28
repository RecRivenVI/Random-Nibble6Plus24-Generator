package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Property-gated real ChunkMap/GenerationChunkHolder pre-light materialization verification. */
public final class Phase3APhysicalMaterializationVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3a.";
    private static volatile boolean completed;

    private Phase3APhysicalMaterializationVerification() {
    }

    public static boolean requested() {
        return !System.getProperty(PREFIX + "verify", "").isBlank()
                || !System.getProperty(PREFIX + "patch.verify", "").isBlank()
                || !System.getProperty(PREFIX + "fault.verify", "").isBlank()
                || !System.getProperty(PREFIX + "reload.verify", "").isBlank();
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
        if (System.getProperty(PREFIX + "verify", "").isBlank()) return;
        String mode = System.getProperty(PREFIX + "verify");
        if (!mode.equals("smoke") && !mode.equals("fixture")) {
            throw new IllegalArgumentException("Unknown Phase 3A verification mode " + mode);
        }
        long expectedMasterSeed = Long.parseLong(System.getProperty(
                PREFIX + "masterSeed", Long.toString(server.getWorldGenSettings().options().seed())));
        if (server.getWorldGenSettings().options().seed() != expectedMasterSeed) {
            throw new IllegalStateException("Phase 3A fixture master seed mismatch");
        }
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        ChunkPos target = new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "0")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "0")));
        int duplicateRequests = Integer.parseInt(System.getProperty(PREFIX + "duplicateRequests", "1"));
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing Phase 3A fixture dimension " + dimension.identifier());
        if (!(level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator)) {
            throw new IllegalStateException("Phase 3A requires a hidden physical Mosaic world");
        }

        MosaicPhysicalMaterializer.resetVerificationState();
        PhysicalMosaicTrace.reset();
        AtomicReference<MosaicPhysicalMaterializer.Publication> publication = new AtomicReference<>();
        MosaicPhysicalMaterializer.setVerificationObserver(value -> {
            if (!publication.compareAndSet(null, value)) {
                throw new IllegalStateException("Physical Mosaic target published more than once");
            }
        });

        SideEffects before = SideEffects.capture(level, target);
        long requestStarted = System.nanoTime();
        if (duplicateRequests > 1) {
            List<CompletableFuture<MosaicPhysicalMaterializer.PreparedMaterialization>> preparedRequests =
                    new ArrayList<>();
            for (int index = 0; index < duplicateRequests; index++) {
                preparedRequests.add(MosaicPhysicalMaterializer.requestPrepared(level, target));
            }
            CompletableFuture<MosaicPhysicalMaterializer.PreparedMaterialization> first = preparedRequests.getFirst();
            if (preparedRequests.stream().anyMatch(future -> future != first)) {
                throw new IllegalStateException("In-flight materializer did not return one shared future");
            }
        }
        List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new ArrayList<>();
        for (int index = 0; index < duplicateRequests; index++) {
            futures.add(level.getChunkSource().getChunkFuture(
                    target.x(), target.z(), ChunkStatus.FEATURES, true));
        }
        server.managedBlock(() -> futures.stream().allMatch(CompletableFuture::isDone));
        List<ChunkAccess> observed = futures.stream()
                .map(CompletableFuture::join)
                .map(result -> result.orElse(null))
                .toList();
        ChunkAccess physical = observed.getFirst();
        if (physical == null || observed.stream().anyMatch(chunk -> chunk != physical)) {
            throw new IllegalStateException("Duplicate physical requests did not observe one Chunk object");
        }
        MosaicPhysicalMaterializer.Publication published = publication.get();
        if (published == null || published.prepared().chunk() != physical) {
            throw new IllegalStateException("Scheduler did not publish the prepared fresh ProtoChunk");
        }
        GenerationChunkHolder holder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(target.x(), target.z()));
        if (holder == null
                || holder.getLatestChunk() != physical
                || holder.getPersistedStatus() != ChunkStatus.FEATURES
                || physical.getPersistedStatus() != ChunkStatus.FEATURES
                || physical.isLightCorrect()) {
            throw new IllegalStateException("Physical holder did not adopt the pre-light FEATURES ProtoChunk");
        }

        ProtoChunk staging = published.prepared().artifact().rehydrate(
                level.registryAccess(), StructurePieceSerializationContext.fromLevel(level));
        FeatureStableSnapshot stagingSnapshot = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), staging, level.registryAccess());
        FeatureStableSnapshot physicalSnapshot = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), physical, level.registryAccess());
        FeatureStageSnapshot.Diff diff = stagingSnapshot.diff(physicalSnapshot);
        String metadata = stagingSnapshot.deterministicMetadataDifference(physicalSnapshot);
        if (!diff.equivalent() || metadata != null) {
            throw new IllegalStateException("Artifact/physical mismatch: " + diff + " metadata=" + metadata);
        }
        if (!stagingSnapshot.rawEntityNbt().equals(physicalSnapshot.rawEntityNbt())
                || !stagingSnapshot.blockEntityNbt().equals(physicalSnapshot.blockEntityNbt())) {
            throw new IllegalStateException("Artifact/physical raw NBT mismatch");
        }
        String expectedHash = System.getProperty(PREFIX + "expectedHash", "");
        if (!expectedHash.isBlank() && !expectedHash.equals(physicalSnapshot.hash())) {
            throw new IllegalStateException(
                    "Physical canonical hash mismatch expected=" + expectedHash + " actual=" + physicalSnapshot.hash());
        }
        PhysicalMosaicTrace.Snapshot physicalTrace = PhysicalMosaicTrace.snapshot();
        if (physicalTrace.totalGeneratorCalls() != 0L || !physicalTrace.forbiddenStatusCalls().isEmpty()) {
            throw new IllegalStateException("Physical Mosaic worldgen/light task escaped: " + physicalTrace);
        }
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        if (metrics.isolatedGenerationCount() != 1L
                || metrics.artifactCaptureCount() != 1L
                || metrics.publishCount() != 1L
                || metrics.inFlightCount() != 0
                || metrics.requestedTargetCount() != 0
                || GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Invalid Phase 3A materialization metrics " + metrics);
        }
        SideEffects after = SideEffects.capture(level, target);
        if (before.entities() != after.entities()
                || before.blockTicks() != after.blockTicks()
                || before.fluidTicks() != after.fluidTicks()
                || before.poiCount() != after.poiCount()) {
            throw new IllegalStateException("Physical gameplay side effect escaped pre-light publish before="
                    + before + " after=" + after);
        }
        MosaicPhysicalMaterializer.Metrics beforeRepeat = MosaicPhysicalMaterializer.metrics();
        ChunkAccess repeated = level.getChunkSource().getChunk(
                target.x(), target.z(), ChunkStatus.FEATURES, false);
        if (repeated != physical || !beforeRepeat.equals(MosaicPhysicalMaterializer.metrics())) {
            throw new IllegalStateException("Already materialized physical target regenerated");
        }
        boolean mutatedBeforeStop = Boolean.parseBoolean(System.getProperty(PREFIX + "mutateBeforeStop", "false"));
        if (mutatedBeforeStop) {
            BlockPos marker = new BlockPos(target.getMiddleBlockX(), physical.getMinY() + 8, target.getMiddleBlockZ());
            physical.setBlockState(marker, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(), 0);
            physical.markUnsaved();
        }
        String persistedHash = mutatedBeforeStop
                ? FeatureStableSnapshot.capture(
                        dimension.identifier().toString(), physical, level.registryAccess()).hash()
                : physicalSnapshot.hash();
        String snapshotOutput = System.getProperty(PREFIX + "snapshotOutput", "");
        if (!snapshotOutput.isBlank()) {
            FeatureStableSnapshot.capture(
                    dimension.identifier().toString(), physical, level.registryAccess())
                    .write(Path.of(snapshotOutput));
        }

        JsonObject json = new JsonObject();
        json.addProperty("status", "PASS");
        json.addProperty("mode", mode);
        json.addProperty("masterSeed", Long.toString(expectedMasterSeed));
        json.addProperty("dimension", dimension.identifier().toString());
        json.addProperty("chunkX", target.x());
        json.addProperty("chunkZ", target.z());
        json.addProperty("localWorldSeed", Long.toString(published.prepared().artifact().localWorldSeed()));
        json.addProperty("semanticHash", physicalSnapshot.hash());
        json.addProperty("rawFingerprint", published.prepared().artifact().rawFingerprint());
        json.addProperty("duplicateRequests", duplicateRequests);
        json.addProperty("sameChunkObject", true);
        json.addProperty("persistedStatus", physical.getPersistedStatus().getName());
        json.addProperty("lightCorrect", physical.isLightCorrect());
        json.addProperty("isolatedGenerations", metrics.isolatedGenerationCount());
        json.addProperty("artifactCaptures", metrics.artifactCaptureCount());
        json.addProperty("publishes", metrics.publishCount());
        json.addProperty("dedupHits", metrics.dedupHits());
        json.addProperty("virtualChunks", published.prepared().isolatedMetrics().virtualChunkCount());
        json.addProperty("writers", 9);
        json.addProperty("isolatedMs", published.prepared().isolatedNanos() / 1_000_000L);
        json.addProperty("artifactMicros", published.prepared().artifactNanos() / 1_000L);
        json.addProperty("rehydrateMicros", published.prepared().rehydrateNanos() / 1_000L);
        json.addProperty("totalRequestMs", (System.nanoTime() - requestStarted) / 1_000_000L);
        json.addProperty("physicalEntitiesBefore", before.entities());
        json.addProperty("physicalEntitiesAfter", after.entities());
        json.addProperty("physicalBlockTicksBefore", before.blockTicks());
        json.addProperty("physicalBlockTicksAfter", after.blockTicks());
        json.addProperty("physicalFluidTicksBefore", before.fluidTicks());
        json.addProperty("physicalFluidTicksAfter", after.fluidTicks());
        json.addProperty("physicalPoiBefore", before.poiCount());
        json.addProperty("physicalPoiAfter", after.poiCount());
        json.addProperty("loadedChunksBefore", before.loadedChunks());
        json.addProperty("loadedChunksAfter", after.loadedChunks());
        json.addProperty("physicalStorageScans", published.prepared().isolatedMetrics().virtualStorageScanCount());
        json.addProperty("reRequestReused", true);
        json.addProperty("mutatedBeforeStop", mutatedBeforeStop);
        json.addProperty("persistedHash", persistedHash);
        json.add("physicalGeneratorCalls", map(physicalTrace.generatorCalls()));
        json.add("forbiddenStatusCalls", map(physicalTrace.forbiddenStatusCalls()));
        writeIfRequested(json);
        completed = true;
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3A physical materialization PASS dimension={} target={} localSeed={} hash={} duplicates={}",
                dimension.identifier(), target, published.prepared().artifact().localWorldSeed(),
                physicalSnapshot.hash(), duplicateRequests);
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) server.halt(false);
    }

    public static void markCompleted() {
        completed = true;
    }

    private static JsonObject map(java.util.Map<String, Long> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    private static void writeIfRequested(JsonObject json) {
        String output = System.getProperty(PREFIX + "output", "");
        if (output.isBlank()) return;
        Path path = Path.of(output);
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3A result " + path, exception);
        }
    }

    private record SideEffects(
            int loadedChunks,
            int entities,
            int blockTicks,
            int fluidTicks,
            long poiCount) {

        private static SideEffects capture(ServerLevel level, ChunkPos target) {
            int entities = 0;
            for (Entity ignored : level.getAllEntities()) entities++;
            BlockPos center = target.getMiddleBlockPosition(level.getSeaLevel());
            long poi = level.getPoiManager().getCountInRange(
                    ignored -> true, center, 32, PoiManager.Occupancy.ANY);
            return new SideEffects(
                    level.getChunkSource().getLoadedChunksCount(),
                    entities,
                    level.getBlockTicks().count(),
                    level.getFluidTicks().count(),
                    poi);
        }
    }
}
