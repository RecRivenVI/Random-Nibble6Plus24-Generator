package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Same-input design comparison, not a new generation mode or production benchmark. */
public final class FeatureDesignComparison {
    private FeatureDesignComparison() {}

    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();
    private static final List<String> MODES = List.of("A", "B", "C");
    private static final List<FeatureBVerifier.Fixture> FIXTURES = fixtures();

    private static List<FeatureBVerifier.Fixture> fixtures() {
        var list = new ArrayList<FeatureBVerifier.Fixture>();
        for (var f : FeatureBVerifier.FIXTURES)
            if (Set.of(
                            "origin",
                            "nonorigin",
                            "village-edge",
                            "mineshaft",
                            "trial",
                            "monument",
                            "fortress",
                            "end-spike",
                            "stronghold")
                    .contains(f.id())) list.add(f);
        list.add(new FeatureBVerifier.Fixture("birch-0", "overworld", 0, -12, -12));
        list.add(new FeatureBVerifier.Fixture("birch-1", "overworld", 0, -8, -12));
        list.add(new FeatureBVerifier.Fixture("taiga-0", "overworld", 0, -72, 144));
        return List.copyOf(list);
    }

    private static final class Trace {
        final FeatureBVerifier.Fixture fixture;
        int writers, fullTerrainCalls;
        long observerNanos;
        String preFeaturesHash;

        Trace(FeatureBVerifier.Fixture fixture) {
            this.fixture = fixture;
        }
    }

    public static void writer(ChunkPos position) {
        var trace = CURRENT.get();
        if (trace == null) return;
        if (!position.equals(new ChunkPos(trace.fixture.x(), trace.fixture.z())))
            throw new IllegalStateException("Comparison ran a neighbor FEATURES writer");
        trace.writers++;
    }

    public static void terrain(IsolatedGenerationContext context, ProtoChunk target) {
        var trace = CURRENT.get();
        if (trace == null) return;
        trace.fullTerrainCalls++;
        if (!target.getPos().equals(new ChunkPos(trace.fixture.x(), trace.fixture.z()))) return;
        if (trace.preFeaturesHash != null)
            throw new IllegalStateException("Comparison rebuilt target terrain");
        long start = System.nanoTime();
        trace.preFeaturesHash =
                FeatureStableSnapshot.capture(
                                "minecraft:" + trace.fixture.dimension(),
                                target,
                                context.hostLevel().registryAccess())
                        .hash();
        trace.observerNanos += System.nanoTime() - start;
    }

    private record Run(
            FeatureStableSnapshot snapshot,
            String preHash,
            String baseHash,
            CompoundTag outgoing,
            Map<String, Object> evidence) {}

    private static ServerLevel host(MinecraftServer server, FeatureBVerifier.Fixture f) {
        return Objects.requireNonNull(
                server.getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION,
                                Identifier.withDefaultNamespace(f.dimension()))));
    }

    private static Run generate(MinecraftServer server, FeatureBVerifier.Fixture f, String mode) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested comparison");
        var trace = new Trace(f);
        CURRENT.set(trace);
        var host = host(server, f);
        long started = System.nanoTime();
        var row = new LinkedHashMap<String, Object>();
        var outgoing = new CompoundTag();
        var packets = new ListTag();
        outgoing.put("contributions", packets);
        String baseHash = "not-applicable";
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        host,
                        f.seed(),
                        new ChunkPos(f.x(), f.z()))) {
            ProtoChunk target;
            if (mode.equals("C")) {
                var c =
                        SelectiveFeatureGeneration.generate(
                                context,
                                new TreeContribution.Scope(
                                        "feature-design-comparison",
                                        "minecraft:" + f.dimension(),
                                        "26.3-c-slot-feedback-1"));
                target = c.target();
                row.put("input", input(c.inputs()));
                row.put("calls", c.calls());
                row.put("rejected", c.rejected());
                c.contributions().forEach(p -> packets.add(p.encode()));
                row.put(
                        "outgoing",
                        Map.of(
                                "slots", c.contributions().size(),
                                "cells",
                                        c.contributions().stream()
                                                .mapToInt(p -> p.cells().size())
                                                .sum(),
                                "blockEntities",
                                        c.contributions().stream()
                                                .flatMap(p -> p.cells().stream())
                                                .filter(p -> p.blockEntity().isPresent())
                                                .count(),
                                "blockTicks",
                                        c.contributions().stream()
                                                .mapToInt(p -> p.blockTicks().size())
                                                .sum(),
                                "fluidTicks",
                                        c.contributions().stream()
                                                .mapToInt(p -> p.fluidTicks().size())
                                                .sum(),
                                "postProcessing",
                                        c.contributions().stream()
                                                .mapToInt(p -> p.postProcessing().size())
                                                .sum()));
            } else {
                var b =
                        LightweightFeatureBaseline.generate(
                                context, new LocalFeatureInputs.Observer() {}, (r, i) -> {});
                target = b.target();
                row.put("input", input(b.inputs().metrics()));
                row.put("rejected", b.rejected());
                row.put("preparationNanos", b.preparationNanos() - trace.observerNanos);
                row.put("targetDecorationNanos", b.decorationNanos());
                if (mode.equals("A")) {
                    long snap = System.nanoTime();
                    baseHash =
                            FeatureStableSnapshot.capture(
                                            "minecraft:" + f.dimension(),
                                            target,
                                            host.registryAccess())
                                    .hash();
                    trace.observerNanos += System.nanoTime() - snap;
                    var inputs = b.contributionInputs(context);
                    var ore = NeighborOreContributions.apply(context, inputs, false, true);
                    var tree = NeighborTreeContributions.apply(context, inputs);
                    var missing = new TreeMap<>(ore.unsupportedFeatures());
                    missing.putAll(tree.unsupported());
                    var supported = new TreeSet<>(ore.supportedFeatures());
                    supported.addAll(tree.supported());
                    supported.forEach(missing::remove);
                    row.put(
                            "coverage",
                            Map.of(
                                    "status",
                                    "PARTIAL",
                                    "supported",
                                    supported,
                                    "unsupported",
                                    missing,
                                    "neighborStructures",
                                    "NOT_RECONSTRUCTED"));
                    row.put(
                            "ore",
                            Map.of(
                                    "nanos",
                                    ore.nanos(),
                                    "slots",
                                    ore.evaluatedSlots(),
                                    "attempts",
                                    ore.attempts(),
                                    "writes",
                                    ore.voxelWrites(),
                                    "fullFallbacks",
                                    ore.extraTerrain(),
                                    "localColumns",
                                    ore.localHeightQuery().columns()));
                    row.put(
                            "tree",
                            Map.of(
                                    "nanos",
                                    tree.nanos(),
                                    "slots",
                                    tree.slots(),
                                    "attempts",
                                    tree.attempts(),
                                    "writes",
                                    tree.state().targetBlockWrites(),
                                    "fullFallbacks",
                                    tree.state().extraTerrain(),
                                    "localColumns",
                                    tree.state().localTerrain().columns(),
                                    "scratchChunks",
                                    tree.state().scratchChunks(),
                                    "sparseBlocks",
                                    tree.state().sparseBlocks()));
                }
            }
            long generationNanos = System.nanoTime() - started - trace.observerNanos;
            long snap = System.nanoTime();
            var snapshot =
                    FeatureStableSnapshot.capture(
                            "minecraft:" + f.dimension(), target, host.registryAccess());
            row.put("snapshotNanos", System.nanoTime() - snap);
            if (!target.getPersistedStatus().equals(ChunkStatus.FEATURES)
                    || trace.writers != 1
                    || trace.preFeaturesHash == null
                    || trace.fullTerrainCalls < 1)
                throw new IllegalStateException(
                        "Incomplete comparison instrumentation / stage boundary");
            row.put("mode", mode);
            row.put("generationNanos", generationNanos);
            row.put("hash", snapshot.hash());
            row.put("preFeaturesHash", trace.preFeaturesHash);
            row.put("baseHash", mode.equals("B") ? snapshot.hash() : baseHash);
            row.put("writers", trace.writers);
            row.put("completeTerrainCalls", trace.fullTerrainCalls);
            row.put("completeNeighborTerrainCalls", trace.fullTerrainCalls - 1);
            row.put("targetData", counts(snapshot));
            return new Run(snapshot, trace.preFeaturesHash, baseHash, outgoing, row);
        } finally {
            CURRENT.remove();
            if (GenerationContextRegistry.bindingCount() != 0)
                throw new IllegalStateException("Comparison session leak");
        }
    }

    private static Map<String, Object> input(LocalFeatureInputs.Metrics m) {
        return Map.of(
                "fullNeighbors",
                m.fullNeighbors(),
                "localColumns",
                m.columns().columns(),
                "fallbackReasons",
                m.fallbackReasons(),
                "biomePalettes",
                m.biomePalettes(),
                "blockReads",
                m.blockReads(),
                "heightReads",
                m.heightReads());
    }

    private static Map<String, Integer> counts(FeatureStableSnapshot s) {
        return Map.of(
                "blockEntities",
                s.blockEntityCount(),
                "pendingBlockEntities",
                s.pendingBlockEntityNbtCount(),
                "entities",
                s.entityCount(),
                "blockTicks",
                s.blockTickCount(),
                "fluidTicks",
                s.fluidTickCount(),
                "postProcessing",
                s.postProcessingCount());
    }

    private static Object difference(FeatureStableSnapshot a, FeatureStableSnapshot b) {
        return Map.of(
                "diff",
                a.diff(b),
                "firstMetadataDifference",
                Objects.toString(a.deterministicMetadataDifference(b), "equal"));
    }

    private static void same(Run a, Run b, Path diff) throws Exception {
        if (!a.snapshot.hash().equals(b.snapshot.hash())
                || !a.preHash.equals(b.preHash)
                || !a.outgoing.equals(b.outgoing)) {
            write(diff, difference(a.snapshot, b.snapshot));
            save(a, diff.getParent(), "failed-first");
            save(b, diff.getParent(), "failed-repeat");
            throw new IllegalStateException(
                    "Same-policy repeat mismatch; no additional normalization: " + diff);
        }
    }

    private static void save(Run run, Path root, String mode) throws Exception {
        run.snapshot.write(root.resolve(mode + ".bin.gz"));
        write(root.resolve(mode + "-entities-raw.json"), run.snapshot.rawEntityNbt());
        NbtIo.writeCompressed(run.outgoing, root.resolve(mode + "-outgoing.nbt"));
    }

    private static List<Long> physical(MinecraftServer server) {
        var values = new ArrayList<Long>();
        for (var host : server.getAllLevels()) {
            values.add((long) host.getChunkSource().getLoadedChunksCount());
            values.add((long) host.getBlockTicks().count());
            values.add((long) host.getFluidTicks().count());
            values.add(
                    java.util.stream.StreamSupport.stream(
                                    host.getAllEntities().spliterator(), false)
                            .count());
        }
        return values;
    }

    private static void write(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }

    public static void run(MinecraftServer server) {
        Path base =
                Path.of(System.getProperty("validation.result"))
                        .resolve("comparison")
                        .resolve(System.getProperty("validation.target"));
        try {
            if (Boolean.getBoolean("featurea.export")) {
                var launch = new LinkedHashMap<String, Object>();
                launch.put("java", ProcessHandle.current().info().command().orElseThrow());
                launch.put("directory", System.getProperty("user.dir"));
                launch.put("classpath", System.getProperty("java.class.path"));
                launch.put(
                        "jvmArguments",
                        java.lang.management.ManagementFactory.getRuntimeMXBean()
                                .getInputArguments()
                                .stream()
                                .filter(s -> !s.startsWith("-Dfeaturea."))
                                .toList());
                var command = System.getProperty("sun.java.command").split(" ");
                launch.put("main", command[0]);
                launch.put("gameArguments", Arrays.copyOfRange(command, 1, command.length));
                write(base.resolve("launcher.json"), launch);
            } else compare(server, base.resolve(System.getProperty("featurea.variant", "host_0")));
        } catch (Exception e) {
            throw new IllegalStateException("FEATURES design comparison failed", e);
        }
        server.halt(false);
    }

    private static void compare(MinecraftServer server, Path root) throws Exception {
        if (Runtime.getRuntime().availableProcessors() != 4)
            throw new IllegalStateException("Comparison requires the shared four-CPU test limit");
        var physicalBefore = physical(server);
        // Excluded warm-up. All measured paths subsequently rotate execution position.
        for (String mode : MODES) generate(server, FIXTURES.getFirst(), mode);
        var rows = new ArrayList<Object>();
        boolean secondHost = root.getFileName().toString().equals("host_1");
        int aPositives = 0, cPositives = 0;
        for (var f : FIXTURES) {
            System.out.println("FEATURES COMPARISON " + f.id());
            var directory = root.resolve(f.id());
            Files.createDirectories(directory);
            Map<String, Run> first = new HashMap<>();
            var samples = new ArrayList<Object>();
            for (int round = 0; round < 3; round++) {
                var order = new ArrayList<>(MODES);
                if (secondHost) Collections.reverse(order);
                Collections.rotate(order, round);
                for (String mode : order) {
                    var run = generate(server, f, mode);
                    if (first.containsKey(mode))
                        same(first.get(mode), run, directory.resolve("repeat-failure.json"));
                    else {
                        first.put(mode, run);
                        save(run, directory, mode);
                    }
                    run.evidence.put("round", round);
                    run.evidence.put("order", List.copyOf(order));
                    samples.add(run.evidence);
                }
            }
            var a = first.get("A");
            var b = first.get("B");
            var c = first.get("C");
            if (!a.preHash.equals(b.preHash)
                    || !b.preHash.equals(c.preHash)
                    || !a.baseHash.equals(b.snapshot.hash()))
                throw new IllegalStateException(
                        "A/B/C do not share exact target inputs: " + f.id());
            if (a.snapshot.diff(b.snapshot).differingBlocks() > 0) aPositives++;
            if (((Number) ((Map<?, ?>) c.evidence.get("outgoing")).get("cells")).intValue() > 0)
                cPositives++;
            var referenceHost = host(server, f);
            FeatureStableSnapshot reference;
            long began = System.nanoTime();
            try (var context =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC,
                            referenceHost,
                            f.seed(),
                            new ChunkPos(f.x(), f.z()))) {
                var result = context.generateFeaturesStable();
                if (result.featureTrace().completedWriters().size() != 9)
                    throw new IllegalStateException("Reference is not canonical nine-writer");
                long referenceGenerationNanos = System.nanoTime() - began;
                reference =
                        FeatureStableSnapshot.capture(
                                "minecraft:" + f.dimension(),
                                result.targetChunk(),
                                referenceHost.registryAccess());
                reference.write(directory.resolve("reference-nine-writer.bin.gz"));
                write(
                        directory.resolve("reference.json"),
                        Map.of(
                                "generationNanos",
                                referenceGenerationNanos,
                                "kind",
                                "RETAINED_ISOLATED_V1_NOT_INDEPENDENT_NATIVE",
                                "hash",
                                reference.hash(),
                                "rawEntities",
                                reference.rawEntityNbt(),
                                "targetData",
                                counts(reference)));
            }
            if (secondHost)
                for (String mode : MODES) {
                    var previous = root.getParent().resolve("host_0").resolve(f.id());
                    if (!FeatureStableSnapshot.read(previous.resolve(mode + ".bin.gz"))
                                    .hash()
                                    .equals(first.get(mode).snapshot.hash())
                            || !NbtIo.readCompressed(
                                            previous.resolve(mode + "-outgoing.nbt"),
                                            NbtAccounter.unlimitedHeap())
                                    .equals(first.get(mode).outgoing))
                        throw new IllegalStateException(
                                "Cross-host design mismatch: " + f.id() + " " + mode);
                }
            var diffs =
                    Map.of(
                            "A_vs_B",
                            difference(a.snapshot, b.snapshot),
                            "C_vs_B",
                            difference(c.snapshot, b.snapshot),
                            "A_vs_reference",
                            difference(a.snapshot, reference),
                            "B_vs_reference",
                            difference(b.snapshot, reference),
                            "C_vs_reference",
                            difference(c.snapshot, reference));
            write(directory.resolve("differences.json"), diffs);
            rows.add(Map.of("fixture", f, "samples", samples, "differences", diffs));
        }
        if (aPositives == 0
                || cPositives == 0
                || !physicalBefore.equals(physical(server))
                || GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Missing positive/physical isolation/cleanup proof");
        var summary = new LinkedHashMap<String, Object>();
        summary.put("status", "PASS_COMPARISON_ONLY");
        summary.put("proofVersion", 1);
        summary.put("pid", ProcessHandle.current().pid());
        summary.put("hostSeed", server.overworld().getSeed());
        summary.put("logicalCpuLimit", Runtime.getRuntime().availableProcessors());
        summary.put("fixtures", rows);
        summary.put("aPositiveFixtures", aPositives);
        summary.put("cOutgoingFixtures", cPositives);
        summary.put("physicalUnchanged", true);
        summary.put("bindings", GenerationContextRegistry.bindingCount());
        summary.put(
                "measurement",
                "Single thread, 4 logical CPU, 3 order-rotated samples per mode per fixture after warm-up. Includes context/preparation/fallback/decorating; excludes snapshot observer and disk output. Not client/production throughput.");
        summary.put(
                "limits",
                List.of(
                        "A supported subset only; neighbor structures omitted",
                        "C outgoing is NOT physical receiving",
                        "Reference is retained V1, not fresh independent native",
                        "No visual acceptance",
                        "Drowned/Shulker nondeterministic entity gates remain unresolved"));
        write(root.resolve("summary.json"), summary);
    }
}
