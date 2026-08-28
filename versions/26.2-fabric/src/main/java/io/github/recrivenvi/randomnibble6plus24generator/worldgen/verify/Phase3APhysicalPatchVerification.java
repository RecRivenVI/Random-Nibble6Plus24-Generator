package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonArray;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Adjacent/3x3/order/modified-neighbor physical Mosaic acceptance. */
public final class Phase3APhysicalPatchVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3a.patch.";
    private static final Comparator<ChunkPos> Z_THEN_X = Comparator
            .comparingInt(ChunkPos::z)
            .thenComparingInt(ChunkPos::x);

    private Phase3APhysicalPatchVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String shape = System.getProperty(PREFIX + "verify", "");
        if (shape.isBlank()) return;
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(System.getProperty(PREFIX + "dimension", "minecraft:overworld")));
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing Phase 3A patch dimension");
        ChunkPos center = new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "32")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "32")));
        String order = System.getProperty(PREFIX + "order", "row-major");
        List<ChunkPos> positions = positions(shape, center);
        if (!shape.equals("modified-neighbor")) order(positions, order);

        MosaicPhysicalMaterializer.resetVerificationState();
        PhysicalMosaicTrace.reset();
        List<MosaicPhysicalMaterializer.Publication> publications = new CopyOnWriteArrayList<>();
        MosaicPhysicalMaterializer.setVerificationObserver(publications::add);
        long started = System.nanoTime();
        BlockPos markerPos = null;
        BlockState markerState = null;

        if (shape.equals("modified-neighbor")) {
            ChunkPos neighbor = positions.getFirst();
            ChunkAccess neighborChunk = requestOne(server, level, neighbor);
            MosaicPhysicalMaterializer.Publication neighborPublication = publications.stream()
                    .filter(value -> value.key().pos().equals(neighbor))
                    .findFirst()
                    .orElseThrow();
            assertArtifactParity(level, dimension, neighborPublication, neighborChunk);
            markerPos = new BlockPos(neighbor.getMiddleBlockX(), neighborChunk.getMinY() + 8, neighbor.getMiddleBlockZ());
            neighborChunk.setBlockState(markerPos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 0);
            markerState = neighborChunk.getBlockState(markerPos);
            requestOne(server, level, center);
        } else if (order.equals("parallel")) {
            List<CompletableFuture<ChunkResult<ChunkAccess>>> futures = positions.stream()
                    .map(pos -> level.getChunkSource().getChunkFuture(
                            pos.x(), pos.z(), ChunkStatus.FEATURES, true))
                    .toList();
            server.managedBlock(() -> futures.stream().allMatch(CompletableFuture::isDone));
            futures.forEach(future -> {
                if (future.join().orElse(null) == null) throw new IllegalStateException("Missing parallel patch Chunk");
            });
        } else {
            for (ChunkPos pos : positions) requestOne(server, level, pos);
        }

        if (markerPos != null && level.getChunkSource().getChunk(
                positions.getFirst().x(), positions.getFirst().z(), ChunkStatus.FEATURES, false)
                .getBlockState(markerPos) != markerState) {
            throw new IllegalStateException("Target materialization overwrote modified physical neighbor");
        }
        int expectedPublications = positions.size();
        if (publications.size() != expectedPublications) {
            throw new IllegalStateException(
                    "Patch publication count mismatch expected=" + expectedPublications + " actual=" + publications.size());
        }
        publications.sort(Comparator.comparing(value -> value.key().pos(), Z_THEN_X));
        JsonArray chunks = new JsonArray();
        Long firstSeed = null;
        for (MosaicPhysicalMaterializer.Publication publication : publications) {
            ChunkPos pos = publication.key().pos();
            ChunkAccess physical = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FEATURES, false);
            FeatureStableSnapshot right;
            if (shape.equals("modified-neighbor") && pos.equals(positions.getFirst())) {
                right = FeatureStableSnapshot.capture(
                        dimension.identifier().toString(), physical, level.registryAccess());
            } else {
                right = assertArtifactParity(level, dimension, publication, physical);
            }
            if (firstSeed == null) firstSeed = publication.prepared().artifact().localWorldSeed();
            JsonObject chunk = new JsonObject();
            chunk.addProperty("x", pos.x());
            chunk.addProperty("z", pos.z());
            chunk.addProperty("localSeed", Long.toString(publication.prepared().artifact().localWorldSeed()));
            chunk.addProperty("hash", right.hash());
            chunk.addProperty("status", physical.getPersistedStatus().getName());
            chunk.addProperty("lightCorrect", physical.isLightCorrect());
            chunks.add(chunk);
        }
        if (shape.equals("adjacent") && publications.size() == 2
                && publications.get(0).prepared().artifact().localWorldSeed()
                        == publications.get(1).prepared().artifact().localWorldSeed()) {
            throw new IllegalStateException("Adjacent Mosaic fixtures unexpectedly share their local seed");
        }
        MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
        if (metrics.isolatedGenerationCount() != expectedPublications
                || metrics.artifactCaptureCount() != expectedPublications
                || metrics.publishCount() != expectedPublications
                || metrics.inFlightCount() != 0
                || metrics.requestedTargetCount() != 0
                || PhysicalMosaicTrace.snapshot().totalGeneratorCalls() != 0
                || GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Invalid physical patch metrics " + metrics);
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("shape", shape);
        result.addProperty("order", order);
        result.addProperty("dimension", dimension.identifier().toString());
        result.addProperty("centerX", center.x());
        result.addProperty("centerZ", center.z());
        result.addProperty("chunks", publications.size());
        result.addProperty("isolatedGenerations", metrics.isolatedGenerationCount());
        result.addProperty("publishes", metrics.publishCount());
        result.addProperty("markerPreserved", markerPos != null);
        result.addProperty("runtimeMs", (System.nanoTime() - started) / 1_000_000L);
        result.add("results", chunks);
        write(result);
        Phase3APhysicalMaterializationVerification.markCompleted();
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3A physical patch PASS shape={} order={} chunks={} runtimeMs={}",
                shape, order, publications.size(), (System.nanoTime() - started) / 1_000_000L);
        server.halt(false);
    }

    private static ChunkAccess requestOne(MinecraftServer server, ServerLevel level, ChunkPos pos) {
        CompletableFuture<ChunkResult<ChunkAccess>> future = level.getChunkSource().getChunkFuture(
                pos.x(), pos.z(), ChunkStatus.FEATURES, true);
        server.managedBlock(future::isDone);
        ChunkAccess chunk = future.join().orElse(null);
        if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.FEATURES || chunk.isLightCorrect()) {
            throw new IllegalStateException("Physical patch Chunk did not stop at FEATURES: " + pos);
        }
        return chunk;
    }

    private static FeatureStableSnapshot assertArtifactParity(
            ServerLevel level,
            ResourceKey<Level> dimension,
            MosaicPhysicalMaterializer.Publication publication,
            ChunkAccess physical) {
        ProtoChunk staging = publication.prepared().artifact().rehydrate(
                level.registryAccess(), StructurePieceSerializationContext.fromLevel(level));
        FeatureStableSnapshot left = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), staging, level.registryAccess());
        FeatureStableSnapshot right = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), physical, level.registryAccess());
        if (!left.diff(right).equivalent()
                || left.deterministicMetadataDifference(right) != null
                || !left.rawEntityNbt().equals(right.rawEntityNbt())
                || !left.blockEntityNbt().equals(right.blockEntityNbt())) {
            throw new IllegalStateException("Patch Artifact/physical mismatch at " + physical.getPos());
        }
        return right;
    }

    private static List<ChunkPos> positions(String shape, ChunkPos center) {
        List<ChunkPos> positions = new ArrayList<>();
        switch (shape) {
            case "adjacent" -> {
                positions.add(center);
                positions.add(new ChunkPos(center.x() + 1, center.z()));
            }
            case "modified-neighbor" -> {
                positions.add(new ChunkPos(center.x() + 1, center.z()));
                positions.add(center);
            }
            case "patch" -> {
                for (int z = center.z() - 1; z <= center.z() + 1; z++) {
                    for (int x = center.x() - 1; x <= center.x() + 1; x++) {
                        positions.add(new ChunkPos(x, z));
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown Phase 3A patch shape " + shape);
        }
        return positions;
    }

    private static void order(List<ChunkPos> positions, String order) {
        switch (order) {
            case "row-major" -> positions.sort(Z_THEN_X);
            case "reverse" -> positions.sort(Z_THEN_X.reversed());
            case "shuffle" -> Collections.shuffle(positions, new Random(0x6e6962626c653234L));
            case "parallel" -> positions.sort(Z_THEN_X);
            default -> throw new IllegalArgumentException("Unknown Phase 3A patch order " + order);
        }
    }

    private static void write(JsonObject result) {
        Path output = Path.of(System.getProperty(PREFIX + "output"));
        try {
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Files.writeString(output, result.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3A patch result", exception);
        }
    }
}
