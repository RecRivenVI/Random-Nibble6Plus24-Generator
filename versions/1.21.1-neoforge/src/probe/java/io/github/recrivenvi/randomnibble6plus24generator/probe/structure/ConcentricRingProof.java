package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingStateAccess;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/** Component proof against this JVM's unmodified native ring algorithm, not physical parity. */
public final class ConcentricRingProof {
    private ConcentricRingProof() {}

    public static void verify(ServerLevel host, Path output) {
        if (!Boolean.getBoolean(ModConstants.MOD_ID + ".structure.lazyRings"))
            throw new IllegalStateException("Ring candidate was not enabled");
        if (ConcentricRingScope.v2QueryRadius() != 11)
            throw new IllegalStateException("Unaudited 1.21.1 query frontier");
        long began = System.nanoTime();
        int cases = 0;
        long slots = 0, searches = 0;
        var seeds = new JsonArray();
        for (long seed : new long[] {0, 1, -1, 123456789}) {
            // A separately constructed, unscoped native state owns the full Vanilla result.
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
                if (((ConcentricRingStateAccess) nativeState)
                                .randomnibble6plus24generator$ringScope()
                        != null) throw new IllegalStateException("Oracle acquired a finite scope");
                Holder<StructureSet> ringSet =
                        nativeState.possibleStructureSets().stream()
                                .filter(
                                        set ->
                                                set.value().placement()
                                                        instanceof
                                                        ConcentricRingsStructurePlacement)
                                .findFirst()
                                .orElseThrow();
                var placement = (ConcentricRingsStructurePlacement) ringSet.value().placement();
                List<ChunkPos> nativePositions =
                        List.copyOf(nativeState.getRingPositionsFor(placement));
                if (nativePositions.size() != 128)
                    throw new IllegalStateException("Unexpected native ring count");
                var raw = new JsonArray();
                nativePositions.forEach(pos -> raw.add(pos.toString()));
                var entry = new JsonObject();
                entry.addProperty("seed", Long.toString(seed));
                entry.add("nativePositions", raw);
                for (ChunkPos start : nativePositions) {
                    for (int offset : new int[] {-11, 0, 11}) {
                        var target = new ChunkPos(start.x + offset, start.z - offset);
                        try (var lazy = new StructureGenerationContext(host, seed, target)) {
                            ChunkGeneratorStructureState state = lazy.state();
                            var scope =
                                    ((ConcentricRingStateAccess) state)
                                            .randomnibble6plus24generator$ringScope();
                            if (scope == null
                                    || scope.metrics().fallback()
                                            != ConcentricRingScope.Fallback.NONE)
                                throw new IllegalStateException(
                                        "Ring proof fell back instead of testing lazy");
                            var expected =
                                    nativePositions.stream()
                                            .filter(pos -> scope.contains(pos.x, pos.z))
                                            .toList();
                            var actual = state.getRingPositionsFor(placement);
                            if (!expected.equals(actual) || !actual.contains(start))
                                throw new IllegalStateException(
                                        "Positive ring mismatch: seed="
                                                + seed
                                                + " target="
                                                + target);
                            for (ChunkPos pos : expected)
                                if (!placement.isStructureChunk(state, pos.x, pos.z))
                                    throw new IllegalStateException(
                                            "Native placement lookup lost a positive");
                            cases++;
                            slots += scope.metrics().slotsConsidered();
                            searches += scope.metrics().biomeSearches();
                        }
                    }
                }
                seeds.add(entry);
            }
        }
        var result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty(
                "inputHash",
                java.util.Objects.requireNonNull(
                        System.getProperty(ModConstants.MOD_ID + ".terrainInputs.inputHash"),
                        "Ring proof must be bound to its actual source fingerprint"));
        result.addProperty(
                "scope", "same-JVM component proof; independent terrain Oracle is separate");
        result.addProperty("positiveFocusedCases", cases);
        result.addProperty("falseNegatives", 0);
        result.addProperty("slots", slots);
        result.addProperty("biomeSearches", searches);
        result.addProperty("queryRadius", 11);
        result.addProperty("elapsedMillis", (System.nanoTime() - began) / 1e6);
        result.add("nativeSeeds", seeds);
        NativeStructureVerifier.write(output.resolve("ring-proof.json"), result);
    }
}
