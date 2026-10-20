package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureStableSnapshot;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;

/** Content/session isolation, not physical player or performance evidence. */
final class BSessionOrderVerifier {
    private record Fixture(String id, ResourceKey<Level> dimension, ChunkPos target) {}

    private record Evidence(
            FeatureStableSnapshot snapshot,
            List<net.minecraft.nbt.CompoundTag> starts,
            List<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> spawn,
            List<String> visible,
            long featureSeedSequence,
            long calls) {}

    private static final List<Fixture> FIXTURES =
            List.of(
                    new Fixture("origin", Level.OVERWORLD, new ChunkPos(0, 0)),
                    new Fixture("nonorigin", Level.OVERWORLD, new ChunkPos(125, -37)),
                    new Fixture("negative", Level.OVERWORLD, new ChunkPos(-10, 11)),
                    new Fixture("fortress", Level.NETHER, new ChunkPos(-11, -6)),
                    new Fixture("end_spike", Level.END, new ChunkPos(2, 0)));

    private BSessionOrderVerifier() {}

    static void run(MinecraftServer server) {
        var output = Path.of(System.getProperty("featureb.sessionOutput"));
        var report = new LinkedHashMap<String, Object>();
        report.put("schema", 1);
        report.put(
                "scope", "STANDALONE_B_CONTENT_ORDER_AND_FIVE_SESSIONS_NOT_PLAYER_OR_PERFORMANCE");
        report.put("inputHash", System.getProperty("featureb.inputHash"));
        report.put("pid", ProcessHandle.current().pid());
        var pool = Executors.newFixedThreadPool(5);
        try {
            var before = physicalState(server);
            Map<String, Evidence> serial = new LinkedHashMap<>();
            for (var fixture : FIXTURES) serial.put(fixture.id(), generate(server, fixture));
            var reversed = new ArrayList<>(FIXTURES);
            Collections.reverse(reversed);
            for (var fixture : reversed)
                equal(fixture, serial.get(fixture.id()), generate(server, fixture));
            var active = new AtomicInteger();
            var peak = new AtomicInteger();
            var barrier = new CyclicBarrier(5);
            List<CompletableFuture<Evidence>> futures = new ArrayList<>();
            for (var fixture : FIXTURES)
                futures.add(
                        CompletableFuture.supplyAsync(
                                () -> {
                                    int count = active.incrementAndGet();
                                    peak.accumulateAndGet(count, Math::max);
                                    try {
                                        barrier.await(30, TimeUnit.SECONDS);
                                        return generate(server, fixture);
                                    } catch (InterruptedException failure) {
                                        Thread.currentThread().interrupt();
                                        throw new CompletionException(failure);
                                    } catch (BrokenBarrierException | TimeoutException failure) {
                                        throw new CompletionException(failure);
                                    } finally {
                                        active.decrementAndGet();
                                    }
                                },
                                pool));
            server.managedBlock(() -> futures.stream().allMatch(CompletableFuture::isDone));
            for (int i = 0; i < FIXTURES.size(); i++)
                equal(FIXTURES.get(i), serial.get(FIXTURES.get(i).id()), futures.get(i).join());
            if (peak.get() != 5
                    || active.get() != 0
                    || GenerationContextRegistry.bindingCount() != 0
                    || MosaicSpawnContextRegistry.bindingCount() != 0
                    || !before.equals(physicalState(server)))
                throw new IllegalStateException("B parallel session/physical cleanup failed");
            var hashes = new LinkedHashMap<String, String>();
            serial.forEach((id, evidence) -> hashes.put(id, evidence.snapshot().hash()));
            report.put("status", "PASS");
            report.put("fixtures", hashes);
            report.put("serial", 5);
            report.put("reverseComparisons", 5);
            report.put("parallelComparisons", 5);
            report.put("peakSessions", peak.get());
            report.put("bindings", 0);
            report.put("physicalHostUnchanged", true);
        } catch (Throwable failure) {
            report.put("status", "FAIL");
            report.put("failure", failure.toString());
            throw new IllegalStateException("B content order/session verification failed", failure);
        } finally {
            pool.shutdownNow();
            try {
                Files.createDirectories(output.getParent());
                Files.writeString(
                        output,
                        new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
            server.halt(false);
        }
    }

    private static Evidence generate(MinecraftServer server, Fixture fixture) {
        var level = Objects.requireNonNull(server.getLevel(fixture.dimension()));
        var profile = MosaicWorldProfile.targetOnly();
        long seed =
                new MosaicSeedResolver(profile)
                        .resolveLocalWorldSeed(123456789, fixture.dimension(), fixture.target());
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        level,
                        seed,
                        fixture.target(),
                        profile)) {
            var prepared = BFeaturePreparation.generate(context, profile);
            var trace = prepared.run().featureTrace();
            if (trace.observedWorldSeed() != seed
                    || trace.maxConcurrentFeatureWriters() != 1
                    || !trace.requestedWriters().equals(List.of(fixture.target()))
                    || !trace.completedWriters().equals(trace.requestedWriters()))
                throw new IllegalStateException("Wrong B writer/seed in " + fixture.id());
            var spawn =
                    new ArrayList<
                            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>();
            for (var pos : SpawnBiomeSnapshot.neighbors(fixture.target())) {
                var biomes = prepared.spawnBiomes();
                int min = net.minecraft.core.QuartPos.fromBlock(biomes.minY(pos));
                int height = net.minecraft.core.QuartPos.fromBlock(biomes.height(pos));
                for (int y = min; y < min + height; y++)
                    for (int z = 0; z < 4; z++)
                        for (int x = 0; x < 4; x++)
                            spawn.add(biomes.biome(pos, pos.x() * 4 + x, y, pos.z() * 4 + z));
            }
            return new Evidence(
                    FeatureStableSnapshot.capture(
                            fixture.dimension().identifier().toString(),
                            prepared.run().targetChunk(),
                            level.registryAccess()),
                    prepared.externalStarts().stream()
                            .map(net.minecraft.nbt.CompoundTag::copy)
                            .toList(),
                    List.copyOf(spawn),
                    trace.featureVisibleBiomeSequence(),
                    trace.featureSeedSequenceHash(),
                    trace.featureSeedInvocationCount());
        }
    }

    private static void equal(Fixture fixture, Evidence before, Evidence after) {
        if (!before.snapshot().diff(after.snapshot()).equivalent()
                || !before.starts().equals(after.starts())
                || !before.spawn().equals(after.spawn())
                || !before.visible().equals(after.visible())
                || before.featureSeedSequence() != after.featureSeedSequence()
                || before.calls() != after.calls())
            throw new IllegalStateException(
                    "Order/session result differs at "
                            + fixture.id()
                            + ": "
                            + before.snapshot().diff(after.snapshot()));
    }

    private static Map<String, List<Long>> physicalState(MinecraftServer server) {
        var result = new LinkedHashMap<String, List<Long>>();
        for (var level : server.getAllLevels())
            result.put(
                    level.dimension().identifier().toString(),
                    List.of(
                            (long) level.getChunkSource().getLoadedChunksCount(),
                            (long) level.getBlockTicks().count(),
                            (long) level.getFluidTicks().count(),
                            java.util.stream.StreamSupport.stream(
                                            level.getAllEntities().spliterator(), false)
                                    .count()));
        return result;
    }
}
