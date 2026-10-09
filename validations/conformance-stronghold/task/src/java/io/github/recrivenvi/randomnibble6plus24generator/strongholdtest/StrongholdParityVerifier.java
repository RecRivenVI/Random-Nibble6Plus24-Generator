package io.github.recrivenvi.randomnibble6plus24generator.strongholdtest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;

/** Full native ring positions are the independent Oracle; only the production scope is under test. */
public final class StrongholdParityVerifier {
    private StrongholdParityVerifier() {}

    public static void run(MinecraftServer server) {
        JsonObject result = new JsonObject();
        long started = System.nanoTime();
        try {
            var level = server.overworld();
            if (MosaicWorldIdentity.isMosaic(level)) throw new AssertionError("Oracle host is not Vanilla");
            var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
            var definitions = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
            var noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
            Random inputs = new Random(0x4C415A5952494E47L);
            int seedCount = Integer.getInteger("mosaic.stronghold.test.seeds", 128);
            int positives = 0, ordinary = 0, ordinarySearched = 0;
            long nativeSlots = 0, lazySlots = 0, lazySearches = 0, ordinarySearches = 0;
            for (int i = 0; i < seedCount; i++) {
                long seed = switch (i) {
                    case 0 -> 0L;
                    case 1 -> -1L;
                    case 2 -> Long.MIN_VALUE;
                    case 3 -> Long.MAX_VALUE;
                    default -> inputs.nextLong();
                };
                var random = RandomState.create(generator.generatorSettings().value(), noises, seed);
                var oracle = generator.createState(definitions, random, seed);
                if (access(oracle).randomnibble6plus24generator$ringScope() != null) {
                    throw new AssertionError("Normal Vanilla structure state was scoped");
                }
                Holder<StructureSet> strongholds = oracle.possibleStructureSets().stream()
                        .filter(set -> set.value().placement() instanceof ConcentricRingsStructurePlacement)
                        .findFirst().orElseThrow();
                var rings = (ConcentricRingsStructurePlacement) strongholds.value().placement();
                List<ChunkPos> complete = oracle.getRingPositionsFor(rings);
                nativeSlots += complete.size();
                if (complete.size() != 128) throw new AssertionError("Unexpected native Stronghold count");
                for (int slot : new int[]{0, 1, 2, 17, 31, 63, 95, 127}) {
                    ChunkPos nativePosition = complete.get(slot);
                    for (int[] offset : new int[][]{{0, 0}, {-11, 0}, {11, 0}, {0, -11}, {0, 11}}) {
                        ChunkPos target = new ChunkPos(nativePosition.x() + offset[0], nativePosition.z() + offset[1]);
                        var candidate = generator.createState(definitions, random, seed);
                        var scope = ConcentricRingScope.forV2Target(target, seed, generator.getBiomeSource(), candidate.possibleStructureSets());
                        access(candidate).randomnibble6plus24generator$setRingScope(scope);
                        List<ChunkPos> expected = complete.stream().filter(p -> scope.contains(p.x(), p.z())).toList();
                        if (expected.isEmpty()) throw new AssertionError("Fixture did not target a real placement");
                        List<ChunkPos> actual = candidate.getRingPositionsFor(rings);
                        if (!actual.equals(expected) || !actual.contains(nativePosition)) {
                            throw new AssertionError("Stronghold false negative/order mismatch seed=" + seed + " target=" + target);
                        }
                        if (!rings.isStructureChunk(candidate, nativePosition.x(), nativePosition.z())) {
                            throw new AssertionError("Native placement lookup lost positive Stronghold");
                        }
                        if (scope.metrics().fallback() != ConcentricRingScope.Fallback.NONE) throw new AssertionError("Unexpected default fallback");
                        lazySlots += scope.metrics().slotsConsidered();
                        lazySearches += scope.metrics().biomeSearches();
                        positives++;
                    }
                }
                for (int j = 0; j < 16; j++) {
                    int span = j < 6 ? 32 : j < 12 ? 256 : 4096;
                    ChunkPos target = new ChunkPos(inputs.nextInt(span * 2 + 1) - span, inputs.nextInt(span * 2 + 1) - span);
                    var candidate = generator.createState(definitions, random, seed);
                    var scope = ConcentricRingScope.forV2Target(target, seed, generator.getBiomeSource(), candidate.possibleStructureSets());
                    access(candidate).randomnibble6plus24generator$setRingScope(scope);
                    if (!candidate.getRingPositionsFor(rings).equals(complete.stream().filter(p -> scope.contains(p.x(), p.z())).toList())) {
                        throw new AssertionError("Ordinary target mismatch seed=" + seed + " target=" + target);
                    }
                    ordinary++;
                    ordinarySearches += scope.metrics().biomeSearches();
                    if (scope.metrics().biomeSearches() > 0) ordinarySearched++;
                }
                if (i == 0) {
                    var placement = new RandomSpreadStructurePlacement(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT,
                            1.0f, 1, java.util.Optional.of(new StructurePlacement.ExclusionZone(strongholds, 16)),
                            32, 8, RandomSpreadType.LINEAR);
                    var root = Holder.direct(new StructureSet(strongholds.value().structures(), placement));
                    var fallback = ConcentricRingScope.forV2Target(ChunkPos.ZERO, seed, generator.getBiomeSource(), List.of(root, strongholds));
                    if (fallback.metrics().fallback() != ConcentricRingScope.Fallback.CONCENTRIC_EXCLUSION) throw new AssertionError("Unsafe exclusion was clipped");
                    var state = generator.createState(definitions, random, seed);
                    access(state).randomnibble6plus24generator$setRingScope(fallback);
                    if (!state.getRingPositionsFor(rings).equals(complete) || fallback.metrics().fullRingFallbacks() != 1) {
                        throw new AssertionError("Full-ring fallback differs from Vanilla");
                    }
                    result.addProperty("concentricExclusionFallback", true);
                }
                if (i % 8 == 0) System.out.println("STRONGHOLD ORACLE seeds=" + (i + 1) + "/" + seedCount + " positives=" + positives);
            }
            result.addProperty("status", "PASS");
            result.addProperty("seeds", seedCount);
            result.addProperty("positiveTargets", positives);
            result.addProperty("falseNegatives", 0);
            result.addProperty("ordinaryTargets", ordinary);
            result.addProperty("ordinaryTargetsNeedingSearch", ordinarySearched);
            result.addProperty("ordinaryBiomeSearches", ordinarySearches);
            result.addProperty("eagerOrdinaryEquivalentSearches", ordinary * 128L);
            result.addProperty("nativeOracleSlots", nativeSlots);
            result.addProperty("positiveSlotsConsidered", lazySlots);
            result.addProperty("positiveBiomeSearches", lazySearches);
            result.addProperty("vanillaStateUnscoped", true);
        } catch (Throwable failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("failure", failure.toString());
            failure.printStackTrace();
        }
        result.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        result.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1e9);
        try {
            Path output = Path.of(System.getProperty("mosaic.stronghold.test.output"));
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, result.toString());
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        System.out.println("STRONGHOLD ORACLE " + result);
        server.halt(false);
    }

    private static ConcentricRingStateAccess access(ChunkGeneratorStructureState state) {
        return (ConcentricRingStateAccess) state;
    }
}
