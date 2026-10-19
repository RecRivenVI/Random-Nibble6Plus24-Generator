package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletionException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/** Native slot/parent-stream component proof, independent world terrain remains a separate gate. */
public final class ConcentricRingProof {
    private ConcentricRingProof() {}

    public static void verify(ServerLevel host, Path output) {
        if (!Boolean.getBoolean(ModConstants.MOD_ID + ".structure.lazyRings"))
            throw new IllegalStateException("Ring candidate was not enabled");
        int radius = ConcentricRingScope.v2QueryRadius();
        if (radius != 11) throw new IllegalStateException("Native V2 closure differs: " + radius);
        long began = System.nanoTime();
        RingClimateProof.verify(host, output);
        long[] totals = new long[4];
        int positives = 0, ordinary = 0;
        var seeds = new JsonArray();
        for (long seed : new long[] {0, 1, -1, 123456789}) {
            try (var base = new StructureGenerationContext(host, seed, ChunkPos.ZERO)) {
                var nativeState =
                        base.generator()
                                .createState(
                                        host.registryAccess()
                                                .lookupOrThrow(
                                                        net.minecraft.core.registries.Registries
                                                                .STRUCTURE_SET),
                                        base.randomState(),
                                        seed);
                require(
                        ((ConcentricRingStateAccess) nativeState).mosaic$ringScope() == null,
                        "Oracle acquired a ring scope");
                var set =
                        nativeState.possibleStructureSets().stream()
                                .filter(
                                        s ->
                                                s.value().placement()
                                                        instanceof
                                                        ConcentricRingsStructurePlacement)
                                .findFirst()
                                .orElseThrow();
                var placement = (ConcentricRingsStructurePlacement) set.value().placement();
                var nativeResult =
                        RingEntropyCapture.capture(
                                () -> List.copyOf(nativeState.getRingPositionsFor(placement)));
                require(nativeResult.value().size() == 128, "Native ring count differs");
                var entry = new JsonObject();
                entry.addProperty("seed", Long.toString(seed));
                entry.add("nativePositions", new Gson().toJsonTree(nativeResult.value()));
                entry.add("parentStream", nativeResult.trace());
                seeds.add(entry);
                String expectedEntropy = nativeResult.trace().toString();
                for (var start : nativeResult.value())
                    for (int offset : new int[] {-radius, 0, radius}) {
                        require(
                                System.nanoTime() - began < 180_000_000_000L,
                                "Ring component proof exceeded fixed 180-second budget");
                        var target = new ChunkPos(start.x + offset, start.z - offset);
                        check(
                                host,
                                seed,
                                target,
                                placement,
                                nativeResult.value(),
                                expectedEntropy,
                                start,
                                totals,
                                0);
                        positives++;
                    }
                for (int index = 0; index < 250; index++) {
                    require(
                            System.nanoTime() - began < 180_000_000_000L,
                            "Ring component proof exceeded fixed 180-second budget");
                    var target = new ChunkPos((index % 25 - 12) * 17, (index / 25 - 5) * 19);
                    check(
                            host,
                            seed,
                            target,
                            placement,
                            nativeResult.value(),
                            expectedEntropy,
                            null,
                            totals,
                            2);
                    ordinary++;
                }
                var progress = new JsonObject();
                progress.addProperty("status", "RUNNING");
                progress.addProperty("positiveFocusedCases", positives);
                progress.addProperty("ordinaryTargets", ordinary);
                progress.addProperty(
                        "inputHash",
                        System.getProperty(ModConstants.MOD_ID + ".terrainInputs.inputHash"));
                NativeStructureVerifier.write(output.resolve("ring-progress.json"), progress);
            }
        }
        boundaries(host);
        var result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty(
                "inputHash",
                Objects.requireNonNull(
                        System.getProperty(ModConstants.MOD_ID + ".terrainInputs.inputHash")));
        result.addProperty(
                "scope",
                "same-JVM component/parent entropy; independent terrain/B/physical gates are separate");
        result.addProperty("positiveFocusedCases", positives);
        result.addProperty("ordinaryTargets", ordinary);
        result.addProperty("falseNegatives", 0);
        result.addProperty("queryRadius", radius);
        result.addProperty("positiveSlots", totals[0]);
        result.addProperty("positiveSearches", totals[1]);
        result.addProperty("ordinarySlots", totals[2]);
        result.addProperty("ordinarySearches", totals[3]);
        result.addProperty("parentRngContinuationIdentical", true);
        result.addProperty("fallbackGuardsPassed", true);
        result.addProperty("climateTailContinuationIdentical", true);
        result.addProperty("activeSuppliers", 0);
        result.addProperty("elapsedMillis", (System.nanoTime() - began) / 1e6);
        result.add("nativeSeeds", seeds);
        NativeStructureVerifier.write(output.resolve("ring-proof.json"), result);
    }

    private static void check(
            ServerLevel host,
            long seed,
            ChunkPos target,
            ConcentricRingsStructurePlacement placement,
            List<ChunkPos> nativePositions,
            String entropy,
            ChunkPos required,
            long[] totals,
            int base) {
        try (var context = new StructureGenerationContext(host, seed, target)) {
            var scope = ((ConcentricRingStateAccess) context.state()).mosaic$ringScope();
            require(
                    scope != null
                            && scope.metrics().fallback() == ConcentricRingScope.Fallback.NONE,
                    "Candidate did not activate");
            var expected = nativePositions.stream().filter(p -> scope.contains(p.x, p.z)).toList();
            var actual =
                    RingEntropyCapture.capture(
                            () -> context.state().getRingPositionsFor(placement));
            require(
                    expected.equals(actual.value())
                            && (required == null || actual.value().contains(required)),
                    "Ring projection differs at " + target + " seed=" + seed);
            require(
                    entropy.equals(actual.trace().toString()),
                    "Native parent random API/state/continuation differs");
            for (var pos : expected)
                require(
                        placement.isStructureChunk(context.state(), pos.x, pos.z),
                        "Native placement lookup lost positive");
            var metrics = scope.metrics();
            require(
                    metrics.slotsConsidered() == 128
                            && metrics.suppliers() == 128
                            && metrics.activeSuppliers() == 0,
                    "Incomplete native search or leaked scope");
            totals[base] += metrics.slotsConsidered();
            totals[base + 1] += metrics.biomeSearches();
        }
    }

    private static void boundaries(ServerLevel host) {
        try (var context = new StructureGenerationContext(host, 0, new ChunkPos(-120, 73))) {
            fallbacks(context);
            var scope = ((ConcentricRingStateAccess) context.state()).mosaic$ringScope();
            require(scope != null, "Missing boundary scope");
            for (int x = -140; x <= -100; x++)
                for (int z = 53; z <= 93; z++) {
                    boolean possible = false;
                    for (int dx = -7; dx <= 7; dx++)
                        for (int dz = -7; dz <= 7; dz++) possible |= scope.contains(x + dx, z + dz);
                    require(
                            possible == scope.mayReach(x, z),
                            "Incorrect seven-chunk search envelope");
                }
            try {
                scope.requireQuery(1000, 1000);
                throw new AssertionError("Out-of-scope query accepted");
            } catch (IllegalStateException expected) {
            }
            scope.beginRing(1);
            var injected = new IllegalStateException("Injected ring supplier failure");
            try {
                java.util.concurrent.CompletableFuture.supplyAsync(
                                scope.wrapSearch(
                                        () -> {
                                            throw injected;
                                        }),
                                Runnable::run)
                        .join();
                throw new AssertionError("Supplier failure was swallowed");
            } catch (CompletionException failure) {
                require(failure.getCause() == injected, "Supplier cause changed");
            }
            require(
                    scope.metrics().activeSuppliers() == 0
                            && ConcentricRingScope.currentSearch() == null,
                    "Supplier binding leaked");
        }
        try (var context =
                new StructureGenerationContext(
                        host, 0, new ChunkPos(Integer.MIN_VALUE, Integer.MAX_VALUE))) {
            var scope = ((ConcentricRingStateAccess) context.state()).mosaic$ringScope();
            require(
                    scope != null
                            && scope.mayReach(Integer.MIN_VALUE + 18, Integer.MAX_VALUE - 18)
                            && !scope.mayReach(Integer.MAX_VALUE, Integer.MIN_VALUE),
                    "Ring subtraction overflow");
        }
    }

    private static void fallbacks(StructureGenerationContext context) {
        var source = context.generator().getBiomeSource();
        var ringSet =
                context.state().possibleStructureSets().stream()
                        .filter(
                                s ->
                                        s.value().placement()
                                                instanceof ConcentricRingsStructurePlacement)
                        .findFirst()
                        .orElseThrow();
        var ring = (ConcentricRingsStructurePlacement) ringSet.value().placement();
        java.util.function.Function<
                        net.minecraft.world.level.levelgen.structure.placement.StructurePlacement,
                        net.minecraft.core.Holder<
                                net.minecraft.world.level.levelgen.structure.StructureSet>>
                set =
                        placement ->
                                net.minecraft.core.Holder.direct(
                                        new net.minecraft.world.level.levelgen.structure
                                                .StructureSet(
                                                ringSet.value().structures(), placement));
        var unknownSource =
                new net.minecraft.world.level.biome.FixedBiomeSource(
                        source.possibleBiomes().iterator().next()) {};
        require(
                ConcentricRingScope.forV2Target(
                                        context.target(),
                                        context.seed(),
                                        unknownSource,
                                        List.of(ringSet))
                                .metrics()
                                .fallback()
                        == ConcentricRingScope.Fallback.UNPROVEN_BIOME_SOURCE,
                "Unknown source did not fall back");
        var unknownPlacement = new UnknownPlacement();
        require(
                ConcentricRingScope.forV2Target(
                                        context.target(),
                                        context.seed(),
                                        source,
                                        List.of(set.apply(unknownPlacement)))
                                .metrics()
                                .fallback()
                        == ConcentricRingScope.Fallback.UNPROVEN_PLACEMENT,
                "Unknown placement did not fall back");
        var excessive =
                new ConcentricRingsStructurePlacement(
                        Integer.MAX_VALUE, 3, 128, ring.preferredBiomes());
        require(
                ConcentricRingScope.forV2Target(
                                        context.target(),
                                        context.seed(),
                                        source,
                                        List.of(set.apply(excessive)))
                                .metrics()
                                .fallback()
                        == ConcentricRingScope.Fallback.UNPROVEN_RING_GEOMETRY,
                "Wrapping geometry did not fall back");
        require(
                ConcentricRingScope.forV2Target(
                                        context.target(),
                                        context.seed(),
                                        source,
                                        List.of(ringSet, set.apply(ring)))
                                .metrics()
                                .fallback()
                        == ConcentricRingScope.Fallback.MULTIPLE_RING_SETS,
                "Multiple ring sets did not fall back");
        var excluded =
                new net.minecraft.world.level.levelgen.structure.placement
                        .RandomSpreadStructurePlacement(
                        net.minecraft.core.Vec3i.ZERO,
                        net.minecraft.world.level.levelgen.structure.placement.StructurePlacement
                                .FrequencyReductionMethod.DEFAULT,
                        1.0F,
                        1,
                        Optional.of(
                                new net.minecraft.world.level.levelgen.structure.placement
                                        .StructurePlacement.ExclusionZone(ringSet, 1)),
                        32,
                        8,
                        net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType
                                .LINEAR);
        require(
                ConcentricRingScope.forV2Target(
                                        context.target(),
                                        context.seed(),
                                        source,
                                        List.of(set.apply(excluded)))
                                .metrics()
                                .fallback()
                        == ConcentricRingScope.Fallback.CONCENTRIC_EXCLUSION,
                "Concentric exclusion did not fall back");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class UnknownPlacement
            extends net.minecraft.world.level.levelgen.structure.placement
                    .RandomSpreadStructurePlacement {
        UnknownPlacement() {
            super(
                    32,
                    8,
                    net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType.LINEAR,
                    1);
        }
    }
}
