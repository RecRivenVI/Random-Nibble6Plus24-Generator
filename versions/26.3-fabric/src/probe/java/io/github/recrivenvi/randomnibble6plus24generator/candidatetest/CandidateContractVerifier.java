package io.github.recrivenvi.randomnibble6plus24generator.candidatetest;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.*;

/** Candidate truth is evaluated by native isStructureChunk, not by another copied predicate. */
public final class CandidateContractVerifier {
    private static long checks, positives, scopes, checksBefore, checksAfter;
    private static final Map<String, Long> positiveBySet = new TreeMap<>();
    private static final JsonObject RESULT = new JsonObject();

    private CandidateContractVerifier() {}

    public static void run(MinecraftServer server) {
        long began = System.nanoTime();
        try {
            Random inputs = new Random(0x443143414E444944L);
            int seeds = Integer.getInteger("mosaic.candidate.test.seeds", 64);
            for (var dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
                ServerLevel level = server.getLevel(dimension);
                var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
                for (int i = 0; i < seeds; i++) {
                    long seed =
                            switch (i) {
                                case 0 -> 0;
                                case 1 -> -1;
                                case 2 -> Long.MIN_VALUE;
                                case 3 -> Long.MAX_VALUE;
                                default -> inputs.nextLong();
                            };
                    var random =
                            RandomState.create(
                                    level.registryAccess().lookupOrThrow(Registries.NOISE),
                                    seed,
                                    generator.generatorSettings().value());
                    var state =
                            generator.createState(
                                    level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                                    random,
                                    seed);
                    if (((StructureCandidateStateAccess) state)
                                    .randomnibble6plus24generator$candidateScope()
                            != null) throw new AssertionError("Normal Vanilla state was scoped");
                    for (var set : state.possibleStructureSets()) {
                        if (!(set.value().placement()
                                instanceof RandomSpreadStructurePlacement spread)) continue;
                        ChunkPos start =
                                spread.getPotentialStructureChunk(
                                        seed,
                                        inputs.nextInt(8193) - 4096,
                                        inputs.nextInt(8193) - 4096);
                        // Biased at actual native placement candidates and radius boundaries.
                        int offset = new int[] {0, 1, -1, 11, -11}[i % 5];
                        verify(
                                level,
                                state,
                                new ChunkPos(start.x() + offset, start.z() - offset),
                                null);
                    }
                    if (i % 16 == 0)
                        System.out.println(
                                "CANDIDATE CONTRACT "
                                        + dimension.identifier()
                                        + " seeds="
                                        + (i + 1)
                                        + " positives="
                                        + positives);
                }
            }
            configs(server.overworld());
            RESULT.addProperty("status", "PASS");
            RESULT.addProperty("falseNegatives", 0);
        } catch (Throwable error) {
            RESULT.addProperty("status", "FAIL");
            RESULT.addProperty("failure", error.toString());
            error.printStackTrace();
        }
        RESULT.addProperty("scopes", scopes);
        RESULT.addProperty("nativeChecks", checks);
        RESULT.addProperty("positiveComparisons", positives);
        RESULT.addProperty("setChecksBefore", checksBefore);
        RESULT.addProperty("setChecksAfter", checksAfter);
        RESULT.add("positiveBySet", new Gson().toJsonTree(positiveBySet));
        RESULT.addProperty("seconds", (System.nanoTime() - began) / 1e9);
        RESULT.addProperty("processors", Runtime.getRuntime().availableProcessors());
        try {
            Path path = Path.of(System.getProperty("mosaic.candidate.test.output"));
            Files.createDirectories(path.getParent());
            Files.writeString(path, RESULT.toString());
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        System.out.println("CANDIDATE CONTRACT " + RESULT);
        server.halt(false);
    }

    private static StructureCandidateScope verify(
            ServerLevel level,
            ChunkGeneratorStructureState state,
            ChunkPos target,
            StructureCandidateScope.Fallback requiredFallback) {
        var sets = state.possibleStructureSets();
        var scope = StructureCandidateScope.forV2Target(target, state.getLevelSeed(), sets);
        scopes++;
        if (requiredFallback != null
                && !scope.metrics().fallbackSets().containsKey(requiredFallback))
            throw new AssertionError("Missing conservative fallback " + requiredFallback);
        for (int dz = -11; dz <= 11; dz++)
            for (int dx = -11; dx <= 11; dx++) {
                long x = (long) target.x() + dx, z = (long) target.z() + dz;
                if (x < Integer.MIN_VALUE
                        || x > Integer.MAX_VALUE
                        || z < Integer.MIN_VALUE
                        || z > Integer.MAX_VALUE) continue;
                ChunkPos source = new ChunkPos((int) x, (int) z);
                var kept = scope.forSource(source, sets);
                List<Holder<StructureSet>> before = new ArrayList<>(), after = new ArrayList<>();
                for (var set : sets) {
                    if (set.value().placement() instanceof ConcentricRingsStructurePlacement) {
                        if (!kept.contains(set)) throw new AssertionError("Stronghold path pruned");
                        continue;
                    }
                    checks++;
                    if (set.value().placement().isStructureChunk(state, source.x(), source.z())) {
                        before.add(set);
                        positives++;
                        positiveBySet.merge(
                                set.unwrapKey()
                                        .map(k -> k.identifier().toString())
                                        .orElse("custom"),
                                1L,
                                Long::sum);
                    }
                }
                for (var set : kept)
                    if (!(set.value().placement() instanceof ConcentricRingsStructurePlacement)
                            && set.value()
                                    .placement()
                                    .isStructureChunk(state, source.x(), source.z()))
                        after.add(set);
                if (!before.equals(after))
                    throw new AssertionError(
                            "Candidate truth/order mismatch seed="
                                    + state.getLevelSeed()
                                    + " target="
                                    + target
                                    + " source="
                                    + source);
            }
        checksBefore += scope.metrics().setChecksBefore();
        checksAfter += scope.metrics().setChecksAfter();
        return scope;
    }

    private static void configs(ServerLevel level) throws Exception {
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var village =
                registry.getOrThrow(
                        net.minecraft.resources.ResourceKey.create(
                                Registries.STRUCTURE_SET,
                                net.minecraft.resources.Identifier.withDefaultNamespace(
                                        "villages")));
        var entries = village.value().structures();
        var random =
                RandomState.create(
                        level.registryAccess().lookupOrThrow(Registries.NOISE),
                        123456789,
                        generator.generatorSettings().value());
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        int variants = 0;
        for (int spacing : new int[] {2, 3, 16, 34, 4096})
            for (int separation : new int[] {0, spacing - 1})
                for (var spread : RandomSpreadType.values())
                    for (var reduction :
                            net.minecraft.world.level.levelgen.structure.placement
                                    .AbstractSpreadingStructurePlacement.FrequencyReductionMethod
                                    .values())
                        for (float frequency : new float[] {0F, 0.01F, 1F}) {
                            var placement =
                                    new RandomSpreadStructurePlacement(
                                            Vec3i.ZERO,
                                            reduction,
                                            frequency,
                                            variants % 2 == 0
                                                    ? Integer.MAX_VALUE
                                                    : 10387320 + variants,
                                            Optional.empty(),
                                            spacing,
                                            separation,
                                            spread);
                            // Exercise the same placement codec that datapacks use; do not dispatch
                            // by registry key.
                            var json =
                                    StructurePlacement.CODEC
                                            .encodeStart(ops, placement)
                                            .getOrThrow();
                            var decoded = StructurePlacement.CODEC.parse(ops, json).getOrThrow();
                            var sets = List.of(Holder.direct(new StructureSet(entries, decoded)));
                            var state = state(random, generator, 123456789, sets);
                            ChunkPos candidate =
                                    placement.getPotentialStructureChunk(123456789, -4097, 4095);
                            verify(level, state, candidate, null);
                            variants++;
                        }
        RESULT.addProperty("datapackCodecVariants", variants);
        var excluded =
                new RandomSpreadStructurePlacement(
                        Vec3i.ZERO,
                        net.minecraft.world.level.levelgen.structure.placement
                                .AbstractSpreadingStructurePlacement.FrequencyReductionMethod
                                .DEFAULT,
                        1,
                        7,
                        Optional.of(
                                new net.minecraft.world.level.levelgen.structure.placement
                                        .AbstractSpreadingStructurePlacement.ExclusionZone(
                                        village, 16)),
                        17,
                        4,
                        RandomSpreadType.TRIANGULAR);
        var excludedState =
                state(
                        random,
                        generator,
                        123456789,
                        List.of(Holder.direct(new StructureSet(entries, excluded)), village));
        int excludedCount = 0;
        for (int i = 0; i < 24; i++) {
            ChunkPos candidate = excluded.getPotentialStructureChunk(123456789, i * 17 - 200, -123);
            if (!excluded.applyInteractionsWithOtherStructures(
                    excludedState, candidate.x(), candidate.z())) excludedCount++;
            verify(level, excludedState, candidate, null);
        }
        if (excludedCount == 0) throw new AssertionError("No actual exclusion rejection covered");
        RESULT.addProperty("nativeExclusionCases", 24);
        RESULT.addProperty("nativeExclusionRejections", excludedCount);
        var stronghold =
                registry.getOrThrow(
                        net.minecraft.resources.ResourceKey.create(
                                Registries.STRUCTURE_SET,
                                net.minecraft.resources.Identifier.withDefaultNamespace(
                                        "strongholds")));
        var nearRings =
                new RandomSpreadStructurePlacement(
                        Vec3i.ZERO,
                        net.minecraft.world.level.levelgen.structure.placement
                                .AbstractSpreadingStructurePlacement.FrequencyReductionMethod
                                .DEFAULT,
                        1,
                        7,
                        Optional.of(
                                new net.minecraft.world.level.levelgen.structure.placement
                                        .AbstractSpreadingStructurePlacement.ExclusionZone(
                                        stronghold, 16)),
                        17,
                        4,
                        RandomSpreadType.LINEAR);
        verify(
                level,
                state(
                        random,
                        generator,
                        123456789,
                        List.of(Holder.direct(new StructureSet(entries, nearRings)), stronghold)),
                ChunkPos.ZERO,
                StructureCandidateScope.Fallback.SPECIAL_EXCLUSION);
        RESULT.addProperty("concentricExclusionFallback", true);
        var foreign =
                new RandomSpreadStructurePlacement(20, 5, RandomSpreadType.LINEAR, 2) {
                    @Override
                    protected boolean isPlacementChunk(
                            ChunkGeneratorStructureState state, int x, int z) {
                        return true;
                    }
                };
        verify(
                level,
                state(
                        random,
                        generator,
                        3,
                        List.of(Holder.direct(new StructureSet(entries, foreign)))),
                new ChunkPos(-9, 10),
                StructureCandidateScope.Fallback.NOT_EXACT_RANDOM_SPREAD);
        var foreignSet = Holder.direct(new StructureSet(entries, foreign));
        var linked =
                new RandomSpreadStructurePlacement(
                        Vec3i.ZERO,
                        net.minecraft.world.level.levelgen.structure.placement
                                .AbstractSpreadingStructurePlacement.FrequencyReductionMethod
                                .DEFAULT,
                        1,
                        7,
                        Optional.of(
                                new net.minecraft.world.level.levelgen.structure.placement
                                        .AbstractSpreadingStructurePlacement.ExclusionZone(
                                        foreignSet, 1)),
                        17,
                        4,
                        RandomSpreadType.LINEAR);
        verify(
                level,
                state(
                        random,
                        generator,
                        3,
                        List.of(Holder.direct(new StructureSet(entries, linked)))),
                ChunkPos.ZERO,
                StructureCandidateScope.Fallback.SPECIAL_EXCLUSION);
        var dense = new RandomSpreadStructurePlacement(1, 0, RandomSpreadType.LINEAR, 1);
        verify(
                level,
                state(
                        random,
                        generator,
                        3,
                        List.of(Holder.direct(new StructureSet(entries, dense)))),
                ChunkPos.ZERO,
                StructureCandidateScope.Fallback.DENSE_GRID);
        var normal =
                List.of(
                        Holder.direct(
                                new StructureSet(
                                        entries,
                                        new RandomSpreadStructurePlacement(
                                                34, 8, RandomSpreadType.LINEAR, 1))));
        verify(
                level,
                state(random, generator, 3, normal),
                new ChunkPos(Integer.MIN_VALUE, 0),
                StructureCandidateScope.Fallback.UNSAFE_COORDINATES);
        var first = StructureCandidateScope.forV2Target(ChunkPos.ZERO, 1, normal);
        var second = StructureCandidateScope.forV2Target(ChunkPos.ZERO, 2, normal);
        if (first.metrics().localSeed() == second.metrics().localSeed())
            throw new AssertionError("Seed authority shared");
        RESULT.addProperty("unknownAndDenseFallbacks", true);
        RESULT.addProperty("overflowFallback", true);
        for (int spacing : new int[] {0, 4097}) {
            var invalid =
                    List.of(
                            Holder.direct(
                                    new StructureSet(
                                            entries,
                                            new RandomSpreadStructurePlacement(
                                                    spacing, 0, RandomSpreadType.LINEAR, 1))));
            var scope = StructureCandidateScope.forV2Target(ChunkPos.ZERO, 3, invalid);
            if (!scope.forSource(ChunkPos.ZERO, invalid).equals(invalid)
                    || !scope.metrics()
                            .fallbackSets()
                            .containsKey(StructureCandidateScope.Fallback.INVALID_GRID))
                throw new AssertionError("Unsupported grid guessed");
        }
    }

    private static ChunkGeneratorStructureState state(
            RandomState random,
            NoiseBasedChunkGenerator generator,
            long seed,
            List<Holder<StructureSet>> sets)
            throws Exception {
        var ctor =
                ChunkGeneratorStructureState.class.getDeclaredConstructor(
                        RandomState.class,
                        net.minecraft.world.level.biome.BiomeSource.class,
                        long.class,
                        ChunkPos.class,
                        long.class,
                        List.class);
        ctor.setAccessible(true);
        return ctor.newInstance(
                random, generator.getBiomeSource(), seed, generator.getOrigin(random), seed, sets);
    }
}
