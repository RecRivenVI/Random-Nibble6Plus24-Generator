package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Native Mojang FEATURES request-order audit. Never participates in isolated generation. */
public final class NativeVanillaFeatureOrderProbe {

    private static final String PREFIX = "randomnibble6plus24generator.phase2c0.features.";
    private static final List<String> ORDERS = List.of(
            "center_first",
            "center_last",
            "row_major",
            "reverse",
            "shuffle_1",
            "shuffle_2");
    private static final Comparator<ChunkPos> ROW_MAJOR = Comparator
            .comparingInt(ChunkPos::z)
            .thenComparingInt(ChunkPos::x);
    private static final AtomicReference<Request> ACTIVE = new AtomicReference<>();

    private NativeVanillaFeatureOrderProbe() {
    }

    public static void armIfRequested(MinecraftServer server) {
        String order = System.getProperty(PREFIX + "order");
        if (order == null) {
            return;
        }
        if (!ORDERS.contains(order)) {
            throw new IllegalArgumentException("Unknown FEATURES order " + order);
        }
        long seed = Long.parseLong(requireProperty("seed"));
        long physicalSeed = server.getWorldGenSettings().options().seed();
        if (seed != physicalSeed) {
            throw new IllegalStateException(
                    "FEATURES order probe seed mismatch; requested=" + seed + ", WorldOptions.seed=" + physicalSeed);
        }
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(requireProperty("dimension")));
        Request request = new Request(
                seed,
                dimension,
                order,
                Path.of(requireProperty("outputRoot")).toAbsolutePath().normalize(),
                Boolean.parseBoolean(System.getProperty(PREFIX + "compare", "false")));
        if (!ACTIVE.compareAndSet(null, request)) {
            throw new IllegalStateException("FEATURES order probe already active");
        }
    }

    public static boolean shouldObserve(
            WorldGenContext context,
            ChunkStatus status,
            ChunkAccess chunk) {
        Request request = ACTIVE.get();
        return request != null
                && status == ChunkStatus.FEATURES
                && GenerationContextRegistry.find(context).isEmpty()
                && context.level().dimension().equals(request.dimension)
                && request.expects(chunk.getPos());
    }

    public static void observe(
            WorldGenContext context,
            ChunkStatus status,
            ChunkAccess chunk) {
        Request request = ACTIVE.get();
        if (request != null && shouldObserve(context, status, chunk)) {
            request.recordCompletion(chunk.getPos());
        }
    }

    public static void runIfRequested(MinecraftServer server) {
        Request request = ACTIVE.get();
        if (request == null) {
            return;
        }
        long started = System.nanoTime();
        long memoryBefore = usedMemory();
        try {
            if (MosaicWorldIdentity.isMosaicWorld(server)) {
                throw new IllegalStateException("FEATURES order probe requires an ordinary Vanilla world");
            }
            ServerLevel level = server.getLevel(request.dimension);
            if (level == null) {
                throw new IllegalStateException("Missing FEATURES probe dimension "
                        + request.dimension.identifier());
            }
            ChunkStep featureStep = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
            int writerRadius = featureStep.blockStateWriteRadius();
            int accumulatedRadius = featureStep.accumulatedDependencies().getRadius();
            if (writerRadius < 0) {
                throw new IllegalStateException("Invalid FEATURES block-state write radius " + writerRadius);
            }

            List<ChunkPos> targets = matrixTargets(level, request.dimension);
            long snapshotNanos = 0L;
            int writerTasks = 0;
            for (ChunkPos target : targets) {
                ProbeResult result = runTarget(level, request, target, writerRadius, "matrix");
                writerTasks += result.writerTasks();
                snapshotNanos += result.snapshotNanos();
            }

            int clusterTargets = 0;
            if (request.dimension.equals(Level.OVERWORLD)
                    && Set.of("row_major", "reverse", "shuffle_1").contains(request.order)) {
                List<ChunkPos> cluster = clusterTargets();
                ProbeResult result = runCluster(level, request, cluster, writerRadius);
                clusterTargets = cluster.size();
                writerTasks += result.writerTasks();
                snapshotNanos += result.snapshotNanos();
            }

            long elapsed = System.nanoTime() - started;
            long heapDelta = usedMemory() - memoryBefore;
            writeRunMetadata(
                    request,
                    writerRadius,
                    accumulatedRadius,
                    targets.size(),
                    clusterTargets,
                    writerTasks,
                    elapsed,
                    snapshotNanos,
                    heapDelta);
            if (request.compare) {
                compareOrders(request, targets);
            }
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Native FEATURES order probe PASS dimension={} order={} targets={} clusterTargets={} "
                            + "writerRadius={} accumulatedRadius={} writerTasks={} runtimeMs={} snapshotMs={} heapDeltaMiB={}",
                    request.dimension.identifier(),
                    request.order,
                    targets.size(),
                    clusterTargets,
                    writerRadius,
                    accumulatedRadius,
                    writerTasks,
                    elapsed / 1_000_000L,
                    snapshotNanos / 1_000_000L,
                    heapDelta / (1024L * 1024L));
        } finally {
            ACTIVE.compareAndSet(request, null);
        }
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) {
            server.execute(() -> server.halt(false));
        }
    }

    private static ProbeResult runTarget(
            ServerLevel level,
            Request request,
            ChunkPos target,
            int writerRadius,
            String group) {
        List<ChunkPos> writers = orderedWriters(target, writerRadius, request.order);
        request.beginWriters(writers);
        for (ChunkPos writer : writers) {
            ChunkAccess chunk = level.getChunk(writer.x(), writer.z(), ChunkStatus.FEATURES, true);
            if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.FEATURES) {
                throw new IllegalStateException("Writer did not stop at FEATURES: " + writer);
            }
        }
        request.verifyWriterOrder();
        ChunkAccess targetChunk = level.getChunk(target.x(), target.z(), ChunkStatus.FEATURES, false);
        if (targetChunk == null || targetChunk.getPersistedStatus() != ChunkStatus.FEATURES) {
            throw new IllegalStateException("Stable target unavailable at FEATURES: " + target);
        }
        long snapshotStarted = System.nanoTime();
        FeatureStageSnapshot snapshot = FeatureStageSnapshot.capture(
                request.dimension.identifier().toString(),
                targetChunk,
                level.registryAccess());
        snapshot.write(snapshotPath(request, group, target));
        return new ProbeResult(writers.size(), System.nanoTime() - snapshotStarted);
    }

    private static ProbeResult runCluster(
            ServerLevel level,
            Request request,
            List<ChunkPos> targets,
            int writerRadius) {
        Set<ChunkPos> writerSet = new TreeSet<>(ROW_MAJOR);
        for (ChunkPos target : targets) {
            writerSet.addAll(square(target, writerRadius));
        }
        List<ChunkPos> writers = applyGlobalOrder(new ArrayList<>(writerSet), request.order);
        request.beginWriters(writers);
        for (ChunkPos writer : writers) {
            ChunkAccess chunk = level.getChunk(writer.x(), writer.z(), ChunkStatus.FEATURES, true);
            if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.FEATURES) {
                throw new IllegalStateException("Cluster writer did not stop at FEATURES: " + writer);
            }
        }
        request.verifyWriterOrder();
        long snapshotStarted = System.nanoTime();
        for (ChunkPos target : targets) {
            ChunkAccess chunk = level.getChunk(target.x(), target.z(), ChunkStatus.FEATURES, false);
            FeatureStageSnapshot.capture(
                    request.dimension.identifier().toString(),
                    chunk,
                    level.registryAccess()).write(snapshotPath(request, "cluster", target));
        }
        return new ProbeResult(writers.size(), System.nanoTime() - snapshotStarted);
    }

    private static List<ChunkPos> matrixTargets(ServerLevel level, ResourceKey<Level> dimension) {
        int required = dimension.equals(Level.OVERWORLD) ? 16 : 8;
        LinkedHashSet<ChunkPos> targets = new LinkedHashSet<>();
        int base = dimension.equals(Level.OVERWORLD) ? 2_048 : dimension.equals(Level.NETHER) ? 3_072 : 4_096;
        if (dimension.equals(Level.OVERWORLD)) {
            BlockPos search = new BlockPos(base << 4, 64, base << 4);
            addStructureTarget(level, targets, StructureTags.VILLAGE, search);
            addStructureTarget(level, targets, StructureTags.MINESHAFT, search);
            addStructureTarget(level, targets, StructureTags.EYE_OF_ENDER_LOCATED, search);
        }
        for (int index = 0; targets.size() < required; index++) {
            int x = base + index % 4 * 64;
            int z = base + index / 4 * 64;
            targets.add(new ChunkPos(x, z));
        }
        return List.copyOf(targets);
    }

    private static void addStructureTarget(
            ServerLevel level,
            LinkedHashSet<ChunkPos> targets,
            net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag,
            BlockPos search) {
        BlockPos found = level.findNearestMapStructure(tag, search, 256, false);
        if (found != null) {
            targets.add(new ChunkPos(found.getX() >> 4, found.getZ() >> 4));
        }
    }

    private static List<ChunkPos> clusterTargets() {
        List<ChunkPos> targets = new ArrayList<>();
        for (int z = 6_000; z < 6_005; z++) {
            for (int x = 6_000; x < 6_005; x++) {
                targets.add(new ChunkPos(x, z));
            }
        }
        return targets;
    }

    private static List<ChunkPos> orderedWriters(ChunkPos target, int radius, String order) {
        List<ChunkPos> rowMajor = square(target, radius);
        rowMajor.sort(ROW_MAJOR);
        return switch (order) {
            case "center_first" -> {
                rowMajor.remove(target);
                rowMajor.addFirst(target);
                yield rowMajor;
            }
            case "center_last" -> {
                rowMajor.remove(target);
                rowMajor.addLast(target);
                yield rowMajor;
            }
            default -> applyGlobalOrder(rowMajor, order);
        };
    }

    private static List<ChunkPos> applyGlobalOrder(List<ChunkPos> writers, String order) {
        writers.sort(ROW_MAJOR);
        switch (order) {
            case "reverse" -> Collections.reverse(writers);
            case "shuffle_1" -> Collections.shuffle(writers, new Random(0x26_2C_0001L));
            case "shuffle_2" -> Collections.shuffle(writers, new Random(0x26_2C_0002L));
            default -> {
                // center variants have no single center for a union frontier;
                // row-major is used only where cluster probes request it explicitly.
            }
        }
        return writers;
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

    private static Path snapshotPath(Request request, String group, ChunkPos target) {
        return orderDirectory(request)
                .resolve(group)
                .resolve(target.x() + "_" + target.z() + ".bin.gz");
    }

    private static Path orderDirectory(Request request) {
        return request.outputRoot
                .resolve(request.dimension.identifier().getPath())
                .resolve(request.order);
    }

    private static void compareOrders(Request request, List<ChunkPos> targets) {
        String dimensionPath = request.dimension.identifier().getPath();
        int sensitiveTargets = 0;
        long differingBlocks = 0;
        Map<String, Integer> categories = new TreeMap<>();
        String representative = null;
        for (ChunkPos target : targets) {
            FeatureStageSnapshot baseline = FeatureStageSnapshot.read(
                    request.outputRoot.resolve(dimensionPath).resolve("row_major")
                            .resolve("matrix").resolve(target.x() + "_" + target.z() + ".bin.gz"));
            boolean targetSensitive = false;
            for (String order : ORDERS) {
                if (order.equals("row_major")) continue;
                FeatureStageSnapshot candidate = FeatureStageSnapshot.read(
                        request.outputRoot.resolve(dimensionPath).resolve(order)
                                .resolve("matrix").resolve(target.x() + "_" + target.z() + ".bin.gz"));
                FeatureStageSnapshot.Diff diff = baseline.diff(candidate);
                if (!diff.equivalent()) {
                    targetSensitive = true;
                    differingBlocks += diff.differingBlocks();
                    diff.blockCategories().forEach(
                            (key, value) -> categories.merge(key, value, Integer::sum));
                    if (representative == null) {
                        representative = "target=" + target
                                + ", row_major vs " + order
                                + ", first=" + diff.firstDifference()
                                + ", blocks=" + diff.differingBlocks()
                                + ", categories=" + diff.blockCategories();
                    }
                }
            }
            if (targetSensitive) sensitiveTargets++;
        }

        int sensitiveClusterChunks = 0;
        long clusterDifferingBlocks = 0;
        if (request.dimension.equals(Level.OVERWORLD)) {
            for (ChunkPos target : clusterTargets()) {
                FeatureStageSnapshot baseline = FeatureStageSnapshot.read(
                        request.outputRoot.resolve(dimensionPath).resolve("row_major")
                                .resolve("cluster").resolve(target.x() + "_" + target.z() + ".bin.gz"));
                boolean sensitive = false;
                for (String order : List.of("reverse", "shuffle_1")) {
                    FeatureStageSnapshot candidate = FeatureStageSnapshot.read(
                            request.outputRoot.resolve(dimensionPath).resolve(order)
                                    .resolve("cluster").resolve(target.x() + "_" + target.z() + ".bin.gz"));
                    FeatureStageSnapshot.Diff diff = baseline.diff(candidate);
                    if (!diff.equivalent()) {
                        sensitive = true;
                        clusterDifferingBlocks += diff.differingBlocks();
                        if (representative == null) {
                            representative = "cluster target=" + target
                                    + ", row_major vs " + order
                                    + ", first=" + diff.firstDifference()
                                    + ", blocks=" + diff.differingBlocks();
                        }
                    }
                }
                if (sensitive) sensitiveClusterChunks++;
            }
        }
        String outcome = sensitiveTargets > 0 || sensitiveClusterChunks > 0
                ? "ORDER_SENSITIVE"
                : "NO_SENSITIVITY_REPRODUCED";
        String summary = "outcome=" + outcome + System.lineSeparator()
                + "dimension=" + request.dimension.identifier() + System.lineSeparator()
                + "matrixTargets=" + targets.size() + System.lineSeparator()
                + "sensitiveTargets=" + sensitiveTargets + System.lineSeparator()
                + "differingBlocks=" + differingBlocks + System.lineSeparator()
                + "blockCategories=" + categories + System.lineSeparator()
                + "clusterTargets=" + (request.dimension.equals(Level.OVERWORLD) ? 25 : 0) + System.lineSeparator()
                + "sensitiveClusterChunks=" + sensitiveClusterChunks + System.lineSeparator()
                + "clusterDifferingBlocks=" + clusterDifferingBlocks + System.lineSeparator()
                + "representative=" + Objects.toString(representative, "none") + System.lineSeparator();
        writeText(request.outputRoot.resolve(dimensionPath).resolve("summary.txt"), summary);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Native FEATURES order comparison {} dimension={} sensitiveTargets={} differingBlocks={} "
                        + "clusterSensitiveChunks={} representative={}",
                outcome,
                request.dimension.identifier(),
                sensitiveTargets,
                differingBlocks,
                sensitiveClusterChunks,
                representative);
    }

    private static void writeRunMetadata(
            Request request,
            int writerRadius,
            int accumulatedRadius,
            int targets,
            int clusterTargets,
            int writerTasks,
            long elapsed,
            long snapshotNanos,
            long heapDelta) {
        String text = "seed=" + request.seed + System.lineSeparator()
                + "dimension=" + request.dimension.identifier() + System.lineSeparator()
                + "order=" + request.order + System.lineSeparator()
                + "writerRadius=" + writerRadius + System.lineSeparator()
                + "writerNeighborhood=" + ((writerRadius * 2 + 1) * (writerRadius * 2 + 1)) + System.lineSeparator()
                + "accumulatedRadius=" + accumulatedRadius + System.lineSeparator()
                + "targets=" + targets + System.lineSeparator()
                + "clusterTargets=" + clusterTargets + System.lineSeparator()
                + "writerTasks=" + writerTasks + System.lineSeparator()
                + "runtimeMs=" + elapsed / 1_000_000L + System.lineSeparator()
                + "snapshotMs=" + snapshotNanos / 1_000_000L + System.lineSeparator()
                + "heapDeltaMiB=" + heapDelta / (1024L * 1024L) + System.lineSeparator();
        writeText(orderDirectory(request).resolve("run.txt"), text);
    }

    private static void writeText(Path path, String text) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write FEATURES probe result " + path, exception);
        }
    }

    private static String requireProperty(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing FEATURES probe property " + PREFIX + suffix);
        }
        return value;
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private record ProbeResult(int writerTasks, long snapshotNanos) {
    }

    private static final class Request {
        private final long seed;
        private final ResourceKey<Level> dimension;
        private final String order;
        private final Path outputRoot;
        private final boolean compare;
        private Set<Long> expectedWriters = Set.of();
        private List<ChunkPos> requestedOrder = List.of();
        private final List<ChunkPos> completionOrder = new ArrayList<>();

        private Request(long seed, ResourceKey<Level> dimension, String order, Path outputRoot, boolean compare) {
            this.seed = seed;
            this.dimension = dimension;
            this.order = order;
            this.outputRoot = outputRoot;
            this.compare = compare;
        }

        private synchronized void beginWriters(List<ChunkPos> writers) {
            requestedOrder = List.copyOf(writers);
            expectedWriters = writers.stream()
                    .map(pos -> ChunkPos.pack(pos.x(), pos.z()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            completionOrder.clear();
        }

        private synchronized boolean expects(ChunkPos pos) {
            return expectedWriters.contains(ChunkPos.pack(pos.x(), pos.z()));
        }

        private synchronized void recordCompletion(ChunkPos pos) {
            completionOrder.add(pos);
        }

        private synchronized void verifyWriterOrder() {
            if (!completionOrder.equals(requestedOrder)) {
                throw new IllegalStateException(
                        "Vanilla FEATURES completion order differed from serial request order; requested="
                                + requestedOrder + ", completed=" + completionOrder);
            }
            expectedWriters = Set.of();
            requestedOrder = List.of();
            completionOrder.clear();
        }
    }
}
