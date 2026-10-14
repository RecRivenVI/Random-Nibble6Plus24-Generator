package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.TreeContribution;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;

/** End City exception requires independent physical evidence; no new canonical hash rule. */
final class FeatureCShulkerVerifier {
    private FeatureCShulkerVerifier() {}

    static void run(MinecraftServer server, Path root) throws Exception {
        var audit = new FeatureAcceptanceAudit(root, true);
        var physical = FeatureCFlowVerifier.physical(server);
        long before = FeatureCFlowVerifier.generationCount();
        var main = NativeFeatureRepeatVerifier.shulkerFixture();
        var alternate =
                new FeatureBVerifier.Fixture(
                        "end-city-alt", main.dimension(), 1, main.x(), main.z());
        var fixtures = List.of(main, alternate);
        var rows = new ArrayList<Object>();
        var serial = new LinkedHashMap<String, FeatureCFlowVerifier.Result>();
        for (var fixture : fixtures) {
            var result = generate(server, fixture, "light");
            serial.put(fixture.id(), result);
            audit.checkpoint(fixture.id(), result);
            audit.compare("dense-" + fixture.id(), result, generate(server, fixture, "dense"));
            var b = generate(server, fixture, "B");
            var originalB = generate(server, fixture, "B-dense");
            audit.checkpoint("b-" + fixture.id(), b);
            audit.compare("original-B-" + fixture.id(), b, originalB);
            var cTrace = new HashMap<>(result.trace());
            var bTrace = new HashMap<>(b.trace());
            cTrace.remove("selective");
            bTrace.remove("selective");
            require(
                    cTrace.equals(bTrace) && result.batches().isEmpty(),
                    "C changed End decoration RNG/outgoing");
            audit.compare(
                    "no-tree-B-C-" + fixture.id(),
                    b.snapshot(),
                    FeatureAcceptanceAudit.raw(b),
                    result.snapshot(),
                    FeatureAcceptanceAudit.raw(result));
            if (fixture.equals(main)) {
                require(
                        result.snapshot().entityCount() == 3,
                        "Missing three actual End City Shulkers");
                var nativeRoot =
                        root.getParent()
                                .getParent()
                                .getParent()
                                .resolve("native_shulker")
                                .resolve(root.getParent().getFileName())
                                .resolve("host_0");
                var nativeEntities =
                        NbtIo.readCompressed(
                                        nativeRoot.resolve("entities-raw.nbt"),
                                        NbtAccounter.unlimitedHeap())
                                .getListOrEmpty("entities");
                var nativeComparison =
                        NativeEntityAcceptance.compare(
                                nativeEntities, FeatureAcceptanceAudit.raw(result), true);
                write(root.resolve("native-entity-comparison.json"), nativeComparison);
                require(
                        nativeComparison.accepted(),
                        "Native End City entity count/positions/data changed");
            }
            rows.add(
                    Map.of(
                            "fixture",
                            fixture,
                            "hash",
                            result.snapshot().hash(),
                            "bHash",
                            b.snapshot().hash(),
                            "entities",
                            result.snapshot().entityCount(),
                            "trace",
                            result.trace(),
                            "calls",
                            result.calls(),
                            "rejected",
                            result.rejected()));
        }
        for (var fixture : fixtures.reversed())
            audit.compare(
                    "reverse-" + fixture.id(),
                    serial.get(fixture.id()),
                    generate(server, fixture, "light"));
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(3)) {
            var barrier = new CyclicBarrier(3);
            var futures = new ArrayList<CompletableFuture<Void>>();
            for (var fixture : List.of(main, alternate, main)) {
                futures.add(
                        CompletableFuture.runAsync(
                                () -> {
                                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                                    try {
                                        barrier.await();
                                        audit.compare(
                                                "parallel-" + fixture.id(),
                                                serial.get(fixture.id()),
                                                generate(server, fixture, "light"));
                                    } catch (Exception e) {
                                        throw new CompletionException(e);
                                    } finally {
                                        active.decrementAndGet();
                                    }
                                },
                                pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        require(
                peak.get() == 3
                        && GenerationContextRegistry.bindingCount() == 0
                        && physical.equals(FeatureCFlowVerifier.physical(server)),
                "C entity isolation/cleanup failed");
        var summary = new LinkedHashMap<String, Object>();
        summary.put("status", "PASS_END_CITY_FINITE_ACCEPTANCE");
        summary.put("proofVersion", 1);
        summary.put("pid", ProcessHandle.current().pid());
        summary.put("hostSeed", server.overworld().getSeed());
        summary.put("fixtures", rows);
        summary.put("acceptance", audit.summary());
        summary.put("generations", FeatureCFlowVerifier.generationCount() - before);
        summary.put("peakSessions", peak.get());
        summary.put("bindings", GenerationContextRegistry.bindingCount());
        summary.put("physicalUnchanged", true);
        write(root.resolve("summary.json"), summary);
    }

    private static FeatureCFlowVerifier.Result generate(
            MinecraftServer server, FeatureBVerifier.Fixture f, String mode) {
        return FeatureCFlowVerifier.generate(
                server,
                f,
                mode,
                new TreeContribution.Scope(
                        "feature-c-end-city", "minecraft:the_end", "26.3-c-slot-feedback-2"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void write(Path path, Object value) throws Exception {
        Files.writeString(
                path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }
}
