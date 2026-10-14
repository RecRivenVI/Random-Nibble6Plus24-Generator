package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;

/** Complete C sources and complete C receivers. Detached replay, never physical installation. */
public final class FeatureCFrontierVerifier {
    private FeatureCFrontierVerifier() {}

    static final long MASTER = 123456789L;
    private static final MosaicSeedResolver RESOLVER =
            new MosaicSeedResolver(MosaicWorldProfile.current());
    static final TreeContribution.Scope SCOPE =
            new TreeContribution.Scope(
                    "feature-c-frontier-123456789",
                    "minecraft:overworld",
                    "26.3-c-slot-feedback-1");
    private static final Comparator<ChunkPos> ORDER =
            Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x);

    static long seed(ChunkPos pos) {
        return RESOLVER.resolveLocalWorldSeed(MASTER, Level.OVERWORLD, pos);
    }

    private static FeatureBVerifier.Fixture fixture(ChunkPos pos) {
        return new FeatureBVerifier.Fixture(
                "chunk-" + pos.x() + "-" + pos.z(), "overworld", seed(pos), pos.x(), pos.z());
    }

    private static FeatureCFlowVerifier.Result generate(
            MinecraftServer server, ChunkPos pos, String mode) {
        return FeatureCFlowVerifier.generate(server, fixture(pos), mode, SCOPE);
    }

    private static List<TreeContribution> outgoing(FeatureCFlowVerifier.Result result) {
        return result.batches().stream().filter(b -> !b.cells().isEmpty()).toList();
    }

    private static FeatureContributionJournal journal(ChunkPos pos, int minY, int height) {
        return new FeatureContributionJournal(SCOPE, pos, seed(pos), minY, height);
    }

    record Prepared(
            NavigableMap<ChunkPos, FeatureCFlowVerifier.Result> sources,
            List<Object> scan,
            NavigableMap<ChunkPos, FeatureCFlowVerifier.Result> generated,
            List<TreeContribution> packages,
            CompoundTag raw) {}

    static Prepared prepare(MinecraftServer server, Path root) throws Exception {
        return prepare(server, root, null);
    }

    private static Prepared prepare(MinecraftServer server, Path root, FeatureAcceptanceAudit audit)
            throws Exception {
        Files.createDirectories(root);
        var sources = new TreeMap<ChunkPos, FeatureCFlowVerifier.Result>(ORDER);
        var scan = new ArrayList<Object>();
        // A bounded deterministic search, not a generated world or an automatic receiving frontier.
        for (int i = 0; i < 128 && sources.size() < 4; i++) {
            var pos = new ChunkPos(-16 + i % 16, -16 + i / 16);
            System.out.println("C FRONTIER scan " + pos);
            long before = System.nanoTime();
            var result = generate(server, pos, "light");
            if (audit != null) audit.checkpoint("scan-" + i, result);
            int cells = outgoing(result).stream().mapToInt(b -> b.cells().size()).sum();
            scan.add(
                    Map.of(
                            "fixture",
                            fixture(pos),
                            "hash",
                            result.snapshot().hash(),
                            "cells",
                            cells,
                            "nanos",
                            System.nanoTime() - before));
            if (cells > 0) sources.put(pos, result);
        }
        require(
                sources.size() == 4,
                "Bounded scan did not find four natural C cross-boundary sources");
        var initiallyNeeded = new TreeSet<ChunkPos>(ORDER);
        sources.values()
                .forEach(
                        r ->
                                outgoing(r)
                                        .forEach(
                                                b ->
                                                        b.cells()
                                                                .forEach(
                                                                        c ->
                                                                                initiallyNeeded.add(
                                                                                        ChunkPos
                                                                                                .containing(
                                                                                                        c
                                                                                                                .pos())))));
        var generated = new TreeMap<ChunkPos, FeatureCFlowVerifier.Result>(ORDER);
        generated.putAll(sources);
        // Natural fossil placement previously exposed a missing, template-only getLevel allowance.
        var fossil = new ChunkPos(-8, -13);
        generated.computeIfAbsent(fossil, p -> generate(server, p, "light"));
        require(
                generated
                                .get(fossil)
                                .levelEscapes()
                                .getOrDefault(
                                        "net.minecraft.world.level.levelgen.feature.FossilFeature.place",
                                        0L)
                        > 0,
                "Fossil template resource route did not execute");
        for (var pos : initiallyNeeded) {
            if (!generated.containsKey(pos)) {
                System.out.println("C FRONTIER receiver " + pos);
                generated.put(pos, generate(server, pos, "light"));
            }
        }
        // Outgoing packages of these receivers are retained, but NEVER recursively generate their
        // receivers.
        var packages =
                generated.values().stream()
                        .flatMap(r -> outgoing(r).stream())
                        .sorted(Comparator.comparing(TreeContribution::key))
                        .toList();
        for (var b : packages)
            require(
                    b.key().seed() == seed(b.key().source()) && b.key().scope().equals(SCOPE),
                    "Wrong local seed provenance");
        var raw = new CompoundTag();
        var list = new ListTag();
        packages.forEach(b -> list.add(b.encode()));
        raw.put("contributions", list);
        NbtIo.writeCompressed(raw, root.resolve("contributions.nbt"));
        return new Prepared(sources, scan, generated, packages, raw);
    }

    public static void run(MinecraftServer server, Path root) throws Exception {
        run(server, root, false);
    }

    public static void run(MinecraftServer server, Path root, boolean finiteAcceptance)
            throws Exception {
        var host = server.overworld();
        var physical = physical(host);
        long start = System.nanoTime();
        var audit = finiteAcceptance ? new FeatureAcceptanceAudit(root) : null;
        var prepared = prepare(server, root, audit);
        var sources = prepared.sources();
        var scan = prepared.scan();
        var generated = prepared.generated();
        var packages = prepared.packages();
        var raw = prepared.raw();
        var generationEvidence = new ArrayList<Object>();
        for (var entry : generated.entrySet()) {
            var pos = entry.getKey();
            var result = entry.getValue();
            // Independent complete TERRAIN representation, same explicit C policy. Not nine-writer
            // parity.
            var control = generate(server, pos, "dense");
            if (audit == null && !result.snapshot().hash().equals(control.snapshot().hash())) {
                var diagnostic = new LinkedHashMap<String, FeatureCFlowVerifier.Result>();
                diagnostic.put("light", result);
                diagnostic.put("dense", control);
                diagnostic.put("denseRepeat", generate(server, pos, "dense"));
                diagnostic.put("b", generate(server, pos, "B-dense"));
                diagnostic.put("bRepeat", generate(server, pos, "B-dense"));
                var evidence = new LinkedHashMap<String, Object>();
                evidence.put("fixture", fixture(pos));
                evidence.put("status", "FAIL_FULL_PARITY");
                evidence.put("scanned", scan.size());
                evidence.put("positiveSources", sources.size());
                evidence.put("preparedTargets", generated.size());
                evidence.put("bindings", GenerationContextRegistry.bindingCount());
                evidence.put("physicalUnchanged", physical.equals(physical(host)));
                var entities = new CompoundTag();
                for (var e : diagnostic.entrySet()) {
                    var r = e.getValue();
                    evidence.put(
                            e.getKey(),
                            Map.of(
                                    "rawEntities",
                                    r.snapshot().rawEntityNbt(),
                                    "hash",
                                    r.snapshot().hash(),
                                    "diffFromLight",
                                    result.snapshot().diff(r.snapshot()),
                                    "trace",
                                    r.trace(),
                                    "calls",
                                    r.calls(),
                                    "outgoing",
                                    r.batches().size()));
                    var nbt = new ListTag();
                    r.target().getEntities().forEach(entity -> nbt.add(entity.copy()));
                    entities.put(e.getKey(), nbt);
                }
                write(root.resolve("mismatch-" + fixture(pos).id() + ".json"), evidence);
                NbtIo.writeCompressed(entities, root.resolve("mismatch-entities-raw.nbt"));
                result.snapshot().write(root.resolve("mismatch-light.bin.gz"));
                control.snapshot().write(root.resolve("mismatch-dense.bin.gz"));
            }
            compare(result, control, "frontier-dense " + pos, audit);
            if (audit != null) audit.checkpoint(fixture(pos).id(), result);
            result.snapshot().write(root.resolve(fixture(pos).id() + ".bin.gz"));
            write(
                    root.resolve(fixture(pos).id() + "-entities-raw.json"),
                    result.snapshot().rawEntityNbt());
            generationEvidence.add(
                    Map.of(
                            "fixture",
                            fixture(pos),
                            "hash",
                            result.snapshot().hash(),
                            "calls",
                            result.calls(),
                            "trace",
                            result.trace(),
                            "outgoing",
                            outgoing(result).size()));
        }
        // Repeat actual generation in reverse order. Inbound projection is never an input to
        // generation.
        for (var pos : generated.descendingKeySet())
            compare(
                    generated.get(pos),
                    generate(server, pos, "light"),
                    "frontier-reverse " + pos,
                    audit);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var jobs = new ArrayList<Future<?>>();
            for (var pos : sources.keySet())
                jobs.add(
                        pool.submit(
                                () ->
                                        compare(
                                                generated.get(pos),
                                                generate(server, pos, "light"),
                                                "frontier-concurrent " + pos,
                                                audit)));
            for (var job : jobs) job.get();
        }

        var receivers = new TreeSet<ChunkPos>(ORDER);
        packages.forEach(b -> b.cells().forEach(c -> receivers.add(ChunkPos.containing(c.pos()))));
        int accepted = 0,
                rejected = 0,
                pending = 0,
                protectedCount = 0,
                blockTicks = 0,
                fluidTicks = 0,
                post = 0,
                priorReloads = 0,
                pendingReceivers = 0;
        var receiverEvidence = new ArrayList<Object>();
        for (var pos : receivers) {
            var relevant =
                    packages.stream()
                            .filter(
                                    b ->
                                            b.cells().stream()
                                                    .anyMatch(
                                                            c ->
                                                                    ChunkPos.containing(c.pos())
                                                                            .equals(pos)))
                            .toList();
            require(
                    relevant.stream().noneMatch(b -> b.key().seed() == seed(pos)),
                    "Receiver must use a different local seed");
            int minY = relevant.getFirst().minY(), height = relevant.getFirst().height();
            var j = journal(pos, minY, height);
            relevant.forEach(j::publish);
            require(
                    j.project().incoming().isEmpty() && j.project().pendingPositions() > 0,
                    "Absent base must remain pending");
            // Stop/restart while the receiver is absent is a separate checkpoint.
            NbtIo.writeCompressed(
                    j.encode(), root.resolve("pending-" + pos.x() + "-" + pos.z() + ".nbt"));
            j =
                    FeatureContributionJournal.decode(
                            NbtIo.readCompressed(
                                    root.resolve("pending-" + pos.x() + "-" + pos.z() + ".nbt"),
                                    NbtAccounter.unlimitedHeap()),
                            SCOPE,
                            pos,
                            seed(pos));
            Map<BlockPos, TreeContribution.Cell> base = new HashMap<>();
            var own = generated.get(pos);
            if (own != null) {
                relevant.forEach(
                        b ->
                                b.cells().stream()
                                        .filter(c -> ChunkPos.containing(c.pos()).equals(pos))
                                        .forEach(
                                                c -> {
                                                    var p = c.pos();
                                                    base.put(
                                                            p,
                                                            new TreeContribution.Cell(
                                                                    p,
                                                                    own.target().getBlockState(p),
                                                                    Optional.ofNullable(
                                                                            own.target()
                                                                                    .getBlockEntityNbtForSaving(
                                                                                            p,
                                                                                            host
                                                                                                    .registryAccess()))));
                                                }));
            } else pendingReceivers++;
            // Delayed base arrival in individual entries exercises partial readiness without
            // placeholders.
            var baseOrder =
                    base.values().stream()
                            .sorted(Comparator.comparingLong(c -> c.pos().asLong()))
                            .toList();
            for (var c : baseOrder) j.supplyBase(List.of(c));
            var expected = j.project();
            verifyIndependentProjection(expected, relevant, base, Map.of(), pos);
            accepted += expected.incoming().size();
            rejected += expected.rejectedPositions();
            pending += expected.pendingPositions();
            blockTicks += expected.blockTicks().size();
            fluidTicks += expected.fluidTicks().size();
            post += expected.postProcessing().size();
            var reverse = journal(pos, minY, height);
            reverse.supplyBase(baseOrder);
            var backwards = new ArrayList<>(relevant);
            Collections.reverse(backwards);
            backwards.forEach(reverse::publish);
            require(
                    reverse.encode().equals(j.encode()),
                    "Source arrival/base readiness order changed journal");
            try (var pool = Executors.newFixedThreadPool(3)) {
                var parallel = journal(pos, minY, height);
                parallel.supplyBase(baseOrder);
                var jobs = new ArrayList<Future<?>>();
                for (int i = 0; i < 4; i++)
                    for (var b : backwards) jobs.add(pool.submit(() -> parallel.publish(b)));
                for (var job : jobs) job.get();
                require(parallel.encode().equals(j.encode()), "Duplicate delivery not idempotent");
            }
            if (!expected.incoming().isEmpty()) {
                var p =
                        expected.incoming().keySet().stream()
                                .min(Comparator.comparingLong(BlockPos::asLong))
                                .orElseThrow();
                var edit =
                        new TreeContribution.Cell(
                                p, Blocks.AIR.defaultBlockState(), Optional.empty());
                j.protect(edit);
                protectedCount++;
                verifyIndependentProjection(j.project(), relevant, base, Map.of(p, edit), pos);
                require(!j.project().incoming().containsKey(p), "Protected dug air refilled");
            }
            Path file = root.resolve("receiver-" + pos.x() + "-" + pos.z() + ".nbt");
            NbtIo.writeCompressed(j.encode(), file);
            var restored =
                    FeatureContributionJournal.decode(
                            NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()),
                            SCOPE,
                            pos,
                            seed(pos));
            require(restored.project().equals(j.project()), "Reload changed projection");
            for (var b : relevant) require(!restored.publish(b), "Reload lost dedup state");
            for (var b : relevant) restored.remove(b.key());
            var removed = restored.project();
            require(
                    removed.incoming().isEmpty()
                            && removed.blockTicks().isEmpty()
                            && removed.fluidTicks().isEmpty()
                            && removed.postProcessing().isEmpty(),
                    "Removed contribution left side effects");
            require(
                    removed.protectedEdits().equals(j.project().protectedEdits()),
                    "Removal erased user protection");
            restored = FeatureContributionJournal.decode(restored.encode(), SCOPE, pos, seed(pos));
            for (var b : relevant) restored.publish(b);
            require(
                    restored.project().equals(j.project()),
                    "Removal/republication changed protection");
            if (root.getFileName().toString().equals("host_1")) {
                var previous =
                        NbtIo.readCompressed(
                                root.resolveSibling("host_0").resolve(file.getFileName()),
                                NbtAccounter.unlimitedHeap());
                require(
                        previous.equals(j.encode())
                                && FeatureContributionJournal.decode(
                                                previous, SCOPE, pos, seed(pos))
                                        .project()
                                        .equals(j.project()),
                        "Other JVM receiver state differs");
                priorReloads++;
            }
            receiverEvidence.add(
                    Map.of(
                            "x",
                            pos.x(),
                            "z",
                            pos.z(),
                            "seed",
                            seed(pos),
                            "generated",
                            own != null,
                            "incoming",
                            expected.incoming().size(),
                            "rejected",
                            expected.rejectedPositions(),
                            "pending",
                            expected.pendingPositions(),
                            "protected",
                            j.project().protectedEdits().size(),
                            "baseHash",
                            own == null ? "ABSENT" : own.snapshot().hash()));
        }
        // Even after replay, every byte represented by the full target snapshot remains native C.
        for (var result : generated.values())
            require(
                    FeatureStableSnapshot.capture(
                                    SCOPE.dimension(), result.target(), host.registryAccess())
                            .hash()
                            .equals(result.snapshot().hash()),
                    "Receiving record mutated full C base/metadata");
        require(
                accepted > 0 && protectedCount > 0 && rejected > 0,
                "Missing natural acceptance/rejection/protection gates");
        require(
                GenerationContextRegistry.bindingCount() == 0 && physical.equals(physical(host)),
                "C frontier leaked context or physical state");
        if (root.getFileName().toString().equals("host_1"))
            require(
                    raw.equals(
                            NbtIo.readCompressed(
                                    root.resolveSibling("host_0/contributions.nbt"),
                                    NbtAccounter.unlimitedHeap())),
                    "Raw source contributions differ across JVMs");
        var summary = new LinkedHashMap<String, Object>();
        summary.put("status", audit == null ? "PASS" : "PASS_FINITE_ENTITY_ACCEPTANCE");
        summary.put("proofVersion", audit == null ? 1 : 2);
        if (audit != null) summary.put("acceptance", audit.summary());
        summary.put("pid", ProcessHandle.current().pid());
        summary.put("hostSeed", host.getSeed());
        summary.put("masterSeed", MASTER);
        summary.put("scan", scan);
        summary.put("positiveSources", sources.size());
        summary.put("generated", generationEvidence);
        summary.put("receivers", receiverEvidence);
        summary.put("pendingReceivers", pendingReceivers);
        summary.put("packages", packages.size());
        summary.put("accepted", accepted);
        summary.put("rejected", rejected);
        summary.put("pending", pending);
        summary.put("protected", protectedCount);
        summary.put("priorJvmReloads", priorReloads);
        summary.put(
                "effects",
                Map.of("blockTicks", blockTicks, "fluidTicks", fluidTicks, "postProcessing", post));
        summary.put("physicalApplications", 0);
        summary.put("physicalUnchanged", true);
        summary.put("bindings", 0);
        summary.put("elapsedNanos", System.nanoTime() - start);
        write(root.resolve("summary.json"), summary);
    }

    private static void compare(
            FeatureCFlowVerifier.Result a,
            FeatureCFlowVerifier.Result b,
            String id,
            FeatureAcceptanceAudit audit) {
        if (audit == null) FeatureCFlowVerifier.compare(a, b, id);
        else audit.compare(id, a, b);
    }

    /**
     * Position-first reference, independent of the journal's package-first replay implementation.
     */
    static void verifyIndependentProjection(
            FeatureContributionJournal.Projection actual,
            List<TreeContribution> packages,
            Map<BlockPos, TreeContribution.Cell> base,
            Map<BlockPos, TreeContribution.Cell> edits,
            ChunkPos receiver) {
        Map<BlockPos, TreeContribution.Cell> expected = new HashMap<>();
        Map<BlockPos, TreeContribution> owners = new HashMap<>();
        var positions = new HashSet<BlockPos>();
        packages.forEach(
                b ->
                        b.cells().stream()
                                .filter(c -> ChunkPos.containing(c.pos()).equals(receiver))
                                .forEach(c -> positions.add(c.pos())));
        for (var pos : positions) {
            var original = base.get(pos);
            if (original == null
                    || edits.containsKey(pos)
                    || !original.state().isAir()
                    || original.blockEntity().isPresent()) continue;
            var winner =
                    packages.stream()
                            .filter(b -> b.cells().stream().anyMatch(c -> c.pos().equals(pos)))
                            .min(Comparator.comparing(TreeContribution::key))
                            .orElseThrow();
            expected.put(
                    pos,
                    winner.cells().stream()
                            .filter(c -> c.pos().equals(pos))
                            .findFirst()
                            .orElseThrow());
            owners.put(pos, winner);
        }
        var sorted = packages.stream().sorted(Comparator.comparing(TreeContribution::key)).toList();
        var blocks =
                sorted.stream()
                        .flatMap(
                                b ->
                                        b.blockTicks().stream()
                                                .filter(t -> b.equals(owners.get(t.pos()))))
                        .toList();
        var fluids =
                sorted.stream()
                        .flatMap(
                                b ->
                                        b.fluidTicks().stream()
                                                .filter(t -> b.equals(owners.get(t.pos()))))
                        .toList();
        var post =
                sorted.stream()
                        .flatMap(
                                b ->
                                        b.postProcessing().stream()
                                                .filter(p -> b.equals(owners.get(p))))
                        .toList();
        require(
                expected.equals(actual.incoming())
                        && edits.equals(actual.protectedEdits())
                        && blocks.equals(actual.blockTicks())
                        && fluids.equals(actual.fluidTicks())
                        && post.equals(actual.postProcessing()),
                "Position-first receiving reference differs");
    }

    static Map<String, Long> physical(ServerLevel host) {
        return Map.of(
                "chunks",
                (long) host.getChunkSource().getLoadedChunksCount(),
                "blockTicks",
                (long) host.getBlockTicks().count(),
                "fluidTicks",
                (long) host.getFluidTicks().count(),
                "entities",
                java.util.stream.StreamSupport.stream(host.getAllEntities().spliterator(), false)
                        .count());
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static void write(Path path, Object value) throws Exception {
        Files.writeString(
                path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }
}
