package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Run before serial fixtures warm the shared native template/pool caches. */
final class StructureLockVerifier {
    private StructureLockVerifier() {}

    static Map<String, Object> run(MinecraftServer server) {
        var fixtures =
                List.of(2, 4, 6, 7, 8, 10, 12).stream()
                        .map(FeatureBVerifier.FIXTURES::get)
                        .toList();
        var executor = Executors.newFixedThreadPool(5);
        var gate = new CountDownLatch(1);
        var results = new ArrayList<CompletableFuture<Projection>>();
        try {
            for (int repeat = 0; repeat < 3; repeat++) {
                for (var fixture : fixtures) {
                    results.add(
                            CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            gate.await();
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new CompletionException(e);
                                        }
                                        return capture(server, fixture, true);
                                    },
                                    executor));
                }
            }
            gate.countDown();
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();
            var reference = fixtures.stream().map(f -> capture(server, f, false)).toList();
            for (int i = 0; i < results.size(); i++) {
                if (!reference.get(i % fixtures.size()).equals(results.get(i).join()))
                    throw new IllegalStateException(
                            "Concurrent native pieces/references changed "
                                    + fixtures.get(i % fixtures.size()).id());
            }
        } finally {
            executor.shutdown();
        }
        if (GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Structure lock regression leaked contexts");
        return Map.of(
                "status",
                "PASS",
                "structures",
                fixtures.size(),
                "parallelComparisons",
                results.size(),
                "threads",
                5,
                "nativeAlgorithm",
                true);
    }

    private static Projection capture(
            MinecraftServer server, FeatureBVerifier.Fixture f, boolean narrow) {
        var host =
                Objects.requireNonNull(
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION,
                                        Identifier.withDefaultNamespace(f.dimension()))));
        try (var context =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                host,
                                f.seed(),
                                new ChunkPos(f.x(), f.z()));
                var reuse = new ScopedStructureSources(context)) {
            var inputs = CompactStructureInputs.resolve(context, narrow ? reuse : null);
            if (inputs.metrics().narrowTemplateLocks() != narrow)
                throw new IllegalStateException("Audited structure lock path not exercised");
            var carrier =
                    new io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain
                            .BiomePaletteChunk(
                            context.target(), host, host.palettedContainerFactory());
            inputs.copyReferencesTo(carrier);
            var references = new TreeMap<String, List<Long>>();
            carrier.getAllReferences()
                    .forEach(
                            (structure, positions) ->
                                    references.put(
                                            host.registryAccess()
                                                    .lookupOrThrow(Registries.STRUCTURE)
                                                    .getKey(structure)
                                                    .toString(),
                                            positions.longStream().sorted().boxed().toList()));
            var tags = inputs.captureStartTags(StructurePieceSerializationContext.fromLevel(host));
            if (tags.isEmpty())
                throw new IllegalStateException(
                        "Structure fixture lacked a positive start " + f.id());
            return new Projection(tags, references);
        }
    }

    private record Projection(List<CompoundTag> starts, Map<String, List<Long>> references) {}
}
