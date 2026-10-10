package io.github.recrivenvi.randomnibble6plus24generator.lazy;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.*;

public final class LazyVerification {
    private static final MosaicSeedResolver RESOLVER =
            new MosaicSeedResolver(MosaicWorldProfile.current());

    private record Fixture(String id, ResourceKey<Level> dimension, ChunkPos pos, long seed) {}

    private static Path output() {
        return Path.of(System.getProperty("mosaic.lazy.output")).toAbsolutePath();
    }

    public static void run(MinecraftServer server) {
        JsonObject report = new JsonObject();
        JsonArray rows = new JsonArray();
        report.add("fixtures", rows);
        report.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        report.addProperty("lazyEnabled", Boolean.getBoolean("mosaic.lazy.enabled"));
        try {
            Files.createDirectories(output());
            if (Boolean.getBoolean("mosaic.lazy.ringAudit")) {
                report.add("rings", ringAudit(server));
            } else {
                List<Fixture> fixtures = fixtures(server);
                for (Fixture fixture : fixtures) {
                    JsonObject row =
                            Boolean.getBoolean("mosaic.lazy.lightweight")
                                    ? io.github.recrivenvi.randomnibble6plus24generator.research
                                            .LazyLightweightBridge.run(
                                            server.getLevel(fixture.dimension()),
                                            fixture.id(),
                                            fixture.pos(),
                                            fixture.seed(),
                                            Path.of(System.getProperty("mosaic.lazy.reference")))
                                    : generate(server, fixture);
                    rows.add(row);
                    Files.writeString(output().resolve("report.json"), report.toString());
                    System.out.println(
                            "LAZY ORACLE FIXTURE PASS " + fixture.id() + " " + row.get("metrics"));
                }
            }
            if (GenerationContextRegistry.bindingCount() != 0 || LazyScopes.count() != 0)
                throw new IllegalStateException("scope leak");
            report.addProperty("status", "PASS");
            report.addProperty(
                    "validationScope",
                    "Pre-FEATURES + structure inputs; full FEATURES entity parity is reported separately, never normalized further");
        } catch (Throwable failure) {
            report.addProperty("status", "FAIL");
            report.addProperty("failure", failure.toString());
            failure.printStackTrace();
        }
        try {
            Files.writeString(output().resolve("report.json"), report.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        System.out.println("LAZY VERIFICATION " + report.get("status"));
        server.halt(false);
    }

    private static List<Fixture> fixtures(MinecraftServer server) {
        if (Boolean.getBoolean("mosaic.candidate.positiveMatrix"))
            return candidatePositiveFixtures(server);
        if (Boolean.getBoolean("mosaic.lazy.strongholdMatrix")) return strongholdFixtures(server);
        List<Fixture> list = new ArrayList<>();
        long[] seeds = {
            0, 1, -1, 123456789L, -987654321L, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789ABCDEFL
        };
        int n = 0;
        for (long m : seeds) list.add(mapped("origin-" + n++, m, Level.OVERWORLD, ChunkPos.ZERO));
        for (long m : seeds)
            for (var p :
                    List.of(
                            new ChunkPos(1, 0),
                            new ChunkPos(-1, 1),
                            new ChunkPos(125, -37),
                            new ChunkPos(-20000, 30000)))
                list.add(mapped("overworld-" + n++, m, Level.OVERWORLD, p));
        for (var d : List.of(Level.NETHER, Level.END))
            for (long m : new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE})
                for (var p :
                        List.of(
                                ChunkPos.ZERO,
                                new ChunkPos(1, -1),
                                new ChunkPos(-125, 37),
                                new ChunkPos(10000, -20000)))
                    list.add(mapped("dimension-" + n++, m, d, p));
        if (Boolean.getBoolean("mosaic.lazy.smoke"))
            list =
                    new ArrayList<>(
                            List.of(
                                    mapped("origin", 123456789, Level.OVERWORLD, ChunkPos.ZERO),
                                    mapped(
                                            "blocker",
                                            123456789,
                                            Level.OVERWORLD,
                                            new ChunkPos(125, -37))));
        list.add(mapped("village", 123456789, Level.OVERWORLD, new ChunkPos(-10, 11)));
        list.add(mapped("mineshaft", 123456789, Level.OVERWORLD, new ChunkPos(-4, -2)));
        list.add(mapped("trial", 123456789, Level.OVERWORLD, new ChunkPos(-13, -14)));
        list.add(
                new Fixture(
                        "trial-adaptation",
                        Level.OVERWORLD,
                        new ChunkPos(-14, -14),
                        7995798377199686500L));
        list.add(mapped("monument", 123456789, Level.OVERWORLD, new ChunkPos(52, -247)));
        list.add(mapped("fortress", 123456789, Level.NETHER, new ChunkPos(-11, -6)));
        list.add(mapped("end-city", 123456789, Level.END, new ChunkPos(1004, 1006)));
        var host = server.overworld();
        var generator = (NoiseBasedChunkGenerator) host.getChunkSource().getGenerator();
        var random =
                RandomState.create(
                        generator.generatorSettings().value(),
                        host.registryAccess().lookupOrThrow(Registries.NOISE),
                        123456789);
        var state =
                generator.createState(
                        host.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                        random,
                        123456789);
        for (var set : state.possibleStructureSets())
            if (set.value().placement() instanceof ConcentricRingsStructurePlacement ring) {
                ChunkPos pos = state.getRingPositionsFor(ring).getFirst();
                list.add(new Fixture("stronghold-start", Level.OVERWORLD, pos, 123456789));
                list.add(
                        new Fixture(
                                "stronghold-neighbor",
                                Level.OVERWORLD,
                                new ChunkPos(pos.x() + 1, pos.z()),
                                123456789));
                break;
            }
        String only = System.getProperty("mosaic.lazy.only", "");
        return only.isBlank() ? list : list.stream().filter(f -> f.id().equals(only)).toList();
    }

    private static List<Fixture> strongholdFixtures(MinecraftServer server) {
        List<Fixture> list = new ArrayList<>();
        var host = server.overworld();
        var generator = (NoiseBasedChunkGenerator) host.getChunkSource().getGenerator();
        var definitions = host.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        java.util.Random seeds = new java.util.Random(0x4C415A5953544152L);
        for (int i = 0; i < 16; i++) {
            long seed =
                    i == 0
                            ? 0
                            : i == 1
                                    ? -1
                                    : i == 2
                                            ? Long.MIN_VALUE
                                            : i == 3 ? Long.MAX_VALUE : seeds.nextLong();
            var random =
                    RandomState.create(
                            generator.generatorSettings().value(),
                            host.registryAccess().lookupOrThrow(Registries.NOISE),
                            seed);
            var state = generator.createState(definitions, random, seed);
            var ring =
                    (ConcentricRingsStructurePlacement)
                            state.possibleStructureSets().stream()
                                    .map(h -> h.value().placement())
                                    .filter(p -> p instanceof ConcentricRingsStructurePlacement)
                                    .findFirst()
                                    .orElseThrow();
            // Deliberately target actual Oracle placements, not random mostly-negative locations.
            ChunkPos pos = state.getRingPositionsFor(ring).get(new int[] {0, 17, 63, 127}[i % 4]);
            list.add(new Fixture("ring-positive-" + i, Level.OVERWORLD, pos, seed));
            list.add(
                    new Fixture(
                            "ring-neighbor-" + i,
                            Level.OVERWORLD,
                            new ChunkPos(pos.x() + 1, pos.z()),
                            seed));
        }
        return list;
    }

    private static List<Fixture> candidatePositiveFixtures(MinecraftServer server) {
        List<Fixture> fixtures = new ArrayList<>();
        JsonArray evidence = new JsonArray();
        String reference = System.getProperty("mosaic.lazy.reference", "");
        try {
            if (!reference.isBlank()) {
                var saved =
                        JsonParser.parseString(
                                        Files.readString(
                                                Path.of(reference)
                                                        .resolve("positive-fixtures.json")))
                                .getAsJsonArray();
                for (var value : saved) {
                    var row = value.getAsJsonObject();
                    var dimension =
                            ResourceKey.create(
                                    Registries.DIMENSION,
                                    net.minecraft.resources.Identifier.parse(
                                            row.get("dimension").getAsString()));
                    fixtures.add(
                            new Fixture(
                                    row.get("id").getAsString(),
                                    dimension,
                                    new ChunkPos(row.get("x").getAsInt(), row.get("z").getAsInt()),
                                    row.get("seed").getAsLong()));
                }
                Files.writeString(output().resolve("positive-fixtures.json"), saved.toString());
                return fixtures;
            }
            if (!Boolean.getBoolean("mosaic.candidate.oracle"))
                throw new IllegalStateException(
                        "Positive fixtures must be discovered with the old path");
            java.util.Random random = new java.util.Random(0x4431504F53495449L);
            for (String type :
                    List.of(
                            "village",
                            "trial_chambers",
                            "monument",
                            "fortress",
                            "end_city",
                            "mineshaft")) {
                var dimension =
                        type.equals("fortress")
                                ? Level.NETHER
                                : type.equals("end_city") ? Level.END : Level.OVERWORLD;
                ServerLevel host = server.getLevel(dimension);
                var definitions = host.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
                var structures = host.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                java.util.function.Predicate<net.minecraft.world.level.levelgen.structure.Structure>
                        matches =
                                s -> {
                                    String key = structures.getKey(s).getPath();
                                    return type.equals("village")
                                            ? key.startsWith("village_")
                                            : type.equals("mineshaft")
                                                    ? key.startsWith("mineshaft")
                                                    : key.equals(type);
                                };
                var set =
                        definitions
                                .listElements()
                                .filter(
                                        h ->
                                                h.value().structures().stream()
                                                        .anyMatch(
                                                                e ->
                                                                        matches.test(
                                                                                e.structure()
                                                                                        .value())))
                                .findFirst()
                                .orElseThrow();
                var spread = (RandomSpreadStructurePlacement) set.value().placement();
                int found = 0;
                for (int attempt = 0; attempt < 6000 && found < 8; attempt++) {
                    long seed = random.nextLong();
                    ChunkPos source =
                            spread.getPotentialStructureChunk(
                                    seed, random.nextInt(8193) - 4096, random.nextInt(8193) - 4096);
                    if (!spread.applyAdditionalChunkRestrictions(source.x(), source.z(), seed))
                        continue;
                    try (var context =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, source)) {
                        Object run =
                                invoke(
                                        context,
                                        "generateTo",
                                        new Class<?>[] {ChunkStatus.class},
                                        ChunkStatus.STRUCTURE_STARTS);
                        ChunkAccess chunk = invoke(run, "targetChunk", new Class<?>[] {});
                        var actual =
                                chunk.getAllStarts().values().stream()
                                        .filter(
                                                start ->
                                                        start.isValid()
                                                                && matches.test(
                                                                        start.getStructure()))
                                        .findFirst();
                        if (actual.isEmpty()) continue;
                        var start = actual.get();
                        ChunkPos neighbor = null;
                        for (int dz = -1; dz <= 1 && neighbor == null; dz++)
                            for (int dx = -1; dx <= 1 && neighbor == null; dx++) {
                                if (dx == 0 && dz == 0) continue;
                                ChunkPos candidate = new ChunkPos(source.x() + dx, source.z() + dz);
                                if (start.getPieces().stream()
                                        .anyMatch(
                                                piece ->
                                                        piece.getBoundingBox()
                                                                .intersects(
                                                                        candidate.getMinBlockX(),
                                                                        candidate.getMinBlockZ(),
                                                                        candidate.getMaxBlockX(),
                                                                        candidate.getMaxBlockZ())))
                                    neighbor = candidate;
                            }
                        if (neighbor == null) continue;
                        for (String suffix : List.of("start", "neighbor")) {
                            ChunkPos target = suffix.equals("start") ? source : neighbor;
                            String id = "positive-" + type + "-" + found + "-" + suffix;
                            fixtures.add(new Fixture(id, dimension, target, seed));
                            JsonObject row = new JsonObject();
                            row.addProperty("id", id);
                            row.addProperty("dimension", dimension.identifier().toString());
                            row.addProperty("x", target.x());
                            row.addProperty("z", target.z());
                            row.addProperty("seed", Long.toString(seed));
                            row.addProperty("source", source.toString());
                            row.addProperty("pieces", start.getPieces().size());
                            evidence.add(row);
                        }
                        found++;
                        System.out.println(
                                "NATIVE STRUCTURE POSITIVE "
                                        + type
                                        + " "
                                        + found
                                        + " pieces="
                                        + start.getPieces().size()
                                        + " attempts="
                                        + (attempt + 1));
                    }
                }
                if (found != 8)
                    throw new IllegalStateException(
                            "Insufficient native positive fixtures for " + type + ": " + found);
            }
            Files.writeString(output().resolve("positive-fixtures.json"), evidence.toString());
            return fixtures;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static Fixture mapped(String id, long master, ResourceKey<Level> d, ChunkPos p) {
        return new Fixture(id, d, p, RESOLVER.resolveLocalWorldSeed(master, d, p));
    }

    private static JsonObject generate(MinecraftServer server, Fixture f) throws Exception {
        ServerLevel host = server.getLevel(f.dimension());
        JsonObject row = new JsonObject();
        row.addProperty("id", f.id());
        row.addProperty("seed", f.seed());
        row.addProperty("chunk", f.pos().toString());
        row.addProperty("dimension", f.dimension().identifier().toString());
        JsonObject hashes = new JsonObject();
        row.add("hashes", hashes);
        long active = 0;
        long start = System.nanoTime();
        try (var ctx =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC, host, f.seed(), f.pos())) {
            active += System.nanoTime() - start;
            for (var stage :
                    List.of(
                            ChunkStatus.BIOMES,
                            ChunkStatus.NOISE,
                            ChunkStatus.SURFACE,
                            ChunkStatus.CARVERS)) {
                start = System.nanoTime();
                Object run = invoke(ctx, "generateTo", new Class<?>[] {ChunkStatus.class}, stage);
                active += System.nanoTime() - start;
                ChunkAccess chunk = invoke(run, "targetChunk", new Class<?>[] {});
                var snap = CarverStageSnapshot.capture(chunk, host.registryAccess());
                hashes.addProperty(stage.getName(), snap.hash());
                if (stage == ChunkStatus.CARVERS) {
                    row.add("preStructures", starts(ctx, host));
                    row.add(
                            "preBeardInputs",
                            new Gson()
                                    .toJsonTree(
                                            new TreeMap<>(
                                                    ((LazyStateAccess) ctx.structureState())
                                                            .mosaicLazyScope()
                                                            .beardInputs)));
                    row.add("targetRelevance", relevance(ctx, chunk));
                }
                if (f.seed() == 123456789
                        && f.dimension() == Level.OVERWORLD
                        && f.pos().equals(ChunkPos.ZERO)) {
                    if (stage == ChunkStatus.SURFACE
                            && !snap.base()
                                    .hash()
                                    .equals(
                                            "cf8a46342321d8ec0baa089631debd3c38572b3dd8968fbbb66c76dd2e7d5b3f"))
                        throw new IllegalStateException("SURFACE golden");
                    if (stage == ChunkStatus.CARVERS
                            && !snap.hash()
                                    .equals(
                                            "8f0eb2a9d708ade9fba2dbfe3e21f1a6d3aa318d7c4052db5ea134bd42f0fa0a"))
                        throw new IllegalStateException("CARVERS golden");
                }
            }
            start = System.nanoTime();
            var stable = ctx.generateFeaturesStable();
            active += System.nanoTime() - start;
            FeatureStableSnapshot feature =
                    FeatureStableSnapshot.capture(
                            f.dimension().identifier().toString(),
                            stable.targetChunk(),
                            host.registryAccess());
            feature.write(output().resolve(f.id() + "-features.gz"));
            hashes.addProperty("features", feature.hash());
            row.addProperty("featureWorldgenDataHash", feature.worldgenDataHash());
            row.add("rawEntities", new Gson().toJsonTree(feature.rawEntityNbt()));
            row.add("canonicalEntities", new Gson().toJsonTree(feature.canonicalEntityNbt()));
            Object map = field(ctx, "chunkMap");
            Map<?, ?> holders = field(map, "holders");
            JsonObject structures = new JsonObject();
            for (Object value : holders.values()) {
                GenerationChunkHolder holder = (GenerationChunkHolder) value;
                ChunkAccess chunk = holder.getChunkIfPresentUnchecked(ChunkStatus.STRUCTURE_STARTS);
                if (chunk == null) continue;
                for (var entry : chunk.getAllStarts().entrySet()) {
                    String key =
                            chunk.getPos()
                                    + "/"
                                    + host.registryAccess()
                                            .lookupOrThrow(Registries.STRUCTURE)
                                            .getKey(entry.getKey());
                    structures.addProperty(
                            key,
                            entry.getValue()
                                    .createTag(
                                            StructurePieceSerializationContext.fromLevel(host),
                                            chunk.getPos())
                                    .toString());
                }
            }
            row.add("structures", structures);
            var scope = ((LazyStateAccess) ctx.structureState()).mosaicLazyScope();
            var productionScope =
                    ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                            .ConcentricRingStateAccess)
                                    ctx.structureState())
                            .randomnibble6plus24generator$ringScope();
            row.add(
                    "productionRings",
                    productionScope == null
                            ? JsonNull.INSTANCE
                            : new Gson().toJsonTree(productionScope.metrics()));
            var candidateScope =
                    ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                            .StructureCandidateStateAccess)
                                    ctx.structureState())
                            .randomnibble6plus24generator$candidateScope();
            row.add(
                    "productionCandidates",
                    candidateScope == null
                            ? JsonNull.INSTANCE
                            : new Gson().toJsonTree(candidateScope.metrics()));
            if (Boolean.getBoolean("mosaic.candidate.study")) {
                boolean baseline = Boolean.getBoolean("mosaic.candidate.oracle");
                if ((candidateScope == null) != baseline)
                    throw new IllegalStateException("Wrong candidate-pruning activation");
                if (!baseline && candidateScope.metrics().sourceQueries() == 0)
                    throw new IllegalStateException("Production candidate hook unused");
            }
            if (Boolean.getBoolean("mosaic.repairC")
                    && (productionScope == null) == !Boolean.getBoolean("mosaic.repairC.oracle"))
                throw new IllegalStateException("Wrong production/Oracle ring activation");
            row.add("beardInputs", new Gson().toJsonTree(new TreeMap<>(scope.beardInputs)));
            row.add("metrics", new Gson().toJsonTree(scope.metrics()));
            row.addProperty("generationMs", active / 1e6);
            row.addProperty("virtualHolders", holders.size());
            row.addProperty("targetReferenceTypes", stable.targetChunk().getAllReferences().size());
            Files.writeString(output().resolve(f.id() + ".json"), row.toString());
            String reference = System.getProperty("mosaic.lazy.reference", "");
            if (!reference.isBlank()) {
                JsonObject expected =
                        JsonParser.parseString(
                                        Files.readString(
                                                Path.of(reference).resolve(f.id() + ".json")))
                                .getAsJsonObject();
                for (String stage :
                        List.of(
                                "minecraft:biomes",
                                "minecraft:noise",
                                "minecraft:surface",
                                "minecraft:carvers"))
                    if (!Objects.equals(
                            expected.getAsJsonObject("hashes").get(stage), hashes.get(stage)))
                        throw new IllegalStateException(
                                "Pre-FEATURES mismatch " + f.id() + " " + stage);
                for (String part : List.of("preStructures", "structures"))
                    if (!expected.get(part).equals(row.get(part))) {
                        System.err.println(
                                feature.diff(
                                        FeatureStableSnapshot.read(
                                                Path.of(reference)
                                                        .resolve(f.id() + "-features.gz"))));
                        throw new IllegalStateException("Oracle mismatch " + f.id() + " " + part);
                    }
                row.add(
                        "preBeardComparison",
                        BeardInputComparison.compare(
                                expected.getAsJsonObject("preBeardInputs"),
                                row.getAsJsonObject("preBeardInputs"),
                                host));
                row.add(
                        "beardComparison",
                        BeardInputComparison.compare(
                                expected.getAsJsonObject("beardInputs"),
                                row.getAsJsonObject("beardInputs"),
                                host));
                boolean fullMatch =
                        expected.getAsJsonObject("hashes")
                                .get("features")
                                .equals(hashes.get("features"));
                row.addProperty("fullFeaturesIncludingEntitiesMatch", fullMatch);
                if (!fullMatch) {
                    var difference =
                            feature.diff(
                                    FeatureStableSnapshot.read(
                                            Path.of(reference).resolve(f.id() + "-features.gz")));
                    row.addProperty("fullFeaturesDifference", difference.toString());
                    // This is NOT canonical FEATURES parity. The existing diff checks
                    // all blocks and non-entity fields before reporting an entity mismatch.
                    if (difference.differingBlocks() != 0
                            || !"ProtoChunk entities differ".equals(difference.firstDifference()))
                        throw new IllegalStateException(
                                "FEATURES non-entity mismatch " + f.id() + " " + difference);
                    // Snapshot.diff checks every non-entity field before entities. worldgenDataHash
                    // excludes only ChunkStatus, not entities, so it is not an entity-free hash.
                }
                row.addProperty("oracleParity", true);
            }
        }
        Files.writeString(output().resolve(f.id() + ".json"), row.toString());
        return row;
    }

    private static JsonObject ringAudit(MinecraftServer server) throws Exception {
        var host = server.overworld();
        var generator = (NoiseBasedChunkGenerator) host.getChunkSource().getGenerator();
        var definitions = host.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var noises = host.registryAccess().lookupOrThrow(Registries.NOISE);
        java.util.Random inputs = new java.util.Random(0x6C617A7953545255L);
        int targets = Integer.getInteger("mosaic.lazy.ringTargets", 1000),
                searchTargets = 0,
                relevantTargets = 0,
                biased = 0,
                biasedRelevant = 0;
        long eagerSearches = 0,
                lazySearches = 0,
                biasedSearches = 0,
                eagerNanos = 0,
                lazyNanos = 0,
                layouts = 0;
        int fullFallback = 0, maxDisplacement = 0;
        JsonArray details = new JsonArray();
        for (int i = 0; i < targets; i++) {
            int span = i < targets * 4 / 10 ? 32 : i < targets * 8 / 10 ? 256 : 4096;
            ChunkPos target =
                    new ChunkPos(
                            inputs.nextInt(span * 2 + 1) - span,
                            inputs.nextInt(span * 2 + 1) - span);
            long master = inputs.nextLong(),
                    seed = RESOLVER.resolveLocalWorldSeed(master, Level.OVERWORLD, target);
            var random = RandomState.create(generator.generatorSettings().value(), noises, seed);
            var oracle = generator.createState(definitions, random, seed);
            var oracleScope =
                    new TargetStructureScope(target, TargetStructureScope.v2Radius(), seed, false);
            ((LazyStateAccess) oracle).mosaicLazyScope(oracleScope);
            ConcentricRingsStructurePlacement rings =
                    oracle.possibleStructureSets().stream()
                            .map(h -> h.value().placement())
                            .filter(p -> p instanceof ConcentricRingsStructurePlacement)
                            .map(p -> (ConcentricRingsStructurePlacement) p)
                            .findFirst()
                            .orElseThrow();
            long begin = System.nanoTime();
            List<ChunkPos> full = oracle.getRingPositionsFor(rings);
            eagerNanos += System.nanoTime() - begin;
            eagerSearches += full.size();
            for (int slot = 0; slot < full.size(); slot++) {
                var a = oracleScope.initialRingSlots.get(slot);
                var b = full.get(slot);
                int displacement = Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
                if (displacement > 7)
                    throw new IllegalStateException("Native biome adjustment exceeds proven bound");
                maxDisplacement = Math.max(maxDisplacement, displacement);
            }
            var scope =
                    new TargetStructureScope(target, TargetStructureScope.v2Radius(), seed, true);
            var candidate = generator.createState(definitions, random, seed);
            ((LazyStateAccess) candidate).mosaicLazyScope(scope);
            begin = System.nanoTime();
            List<ChunkPos> sparse = candidate.getRingPositionsFor(rings);
            lazyNanos += System.nanoTime() - begin;
            List<ChunkPos> expected =
                    full.stream().filter(p -> scope.contains(p.x(), p.z())).toList();
            if (!sparse.equals(expected))
                throw new IllegalStateException(
                        "False negative/order error seed="
                                + seed
                                + " target="
                                + target
                                + " expected="
                                + expected
                                + " actual="
                                + sparse);
            long searched =
                    ((Number) scope.metrics().getOrDefault("ringBiomeSearches", 0)).longValue();
            lazySearches += searched;
            layouts++;
            if (searched > 0) searchTargets++;
            if (!expected.isEmpty()) relevantTargets++;
            if (searched == full.size()) fullFallback++;
            if (i < 64)
                for (int slot : new int[] {0, 1, 17, 63, 127})
                    for (int dx : new int[] {-11, 0, 11}) {
                        ChunkPos center = new ChunkPos(full.get(slot).x() + dx, full.get(slot).z());
                        var bscope =
                                new TargetStructureScope(
                                        center, TargetStructureScope.v2Radius(), seed, true);
                        var bstate = generator.createState(definitions, random, seed);
                        ((LazyStateAccess) bstate).mosaicLazyScope(bscope);
                        var want =
                                full.stream().filter(p -> bscope.contains(p.x(), p.z())).toList();
                        var got = bstate.getRingPositionsFor(rings);
                        if (!want.equals(got))
                            throw new IllegalStateException(
                                    "Stronghold false-negative biased fixture "
                                            + center
                                            + " seed="
                                            + seed);
                        biased++;
                        if (!want.isEmpty()) biasedRelevant++;
                        biasedSearches +=
                                ((Number) bscope.metrics().getOrDefault("ringBiomeSearches", 0))
                                        .longValue();
                    }
            if (searched > 0) {
                JsonObject row = new JsonObject();
                row.addProperty("i", i);
                row.addProperty("master", master);
                row.addProperty("seed", seed);
                row.addProperty("target", target.toString());
                row.addProperty("searches", searched);
                row.addProperty("relevant", expected.size());
                details.add(row);
            }
            if (i % 100 == 0)
                System.out.println(
                        "LAZY RINGS " + i + " searches=" + lazySearches + " biased=" + biased);
        }
        JsonObject out = new JsonObject();
        out.addProperty("ordinaryTargets", targets);
        out.addProperty("targetsNeedingSearch", searchTargets);
        out.addProperty("oracleRelevantTargets", relevantTargets);
        out.addProperty("eagerSearches", eagerSearches);
        out.addProperty("lazySearches", lazySearches);
        out.addProperty("cheapLayouts", layouts);
        out.addProperty("fullFallbacks", fullFallback);
        out.addProperty("biasedTargets", biased);
        out.addProperty("biasedOracleRelevant", biasedRelevant);
        out.addProperty("biasedSearches", biasedSearches);
        out.addProperty("falseNegatives", 0);
        out.addProperty("maxNativeDisplacementChunks", maxDisplacement);
        out.addProperty("eagerMillis", eagerNanos / 1e6);
        out.addProperty("lazyMillis", lazyNanos / 1e6);
        out.add("positiveDetails", details);
        return out;
    }

    private static JsonObject starts(IsolatedGenerationContext ctx, ServerLevel host)
            throws Exception {
        Object map = field(ctx, "chunkMap");
        Map<?, ?> holders = field(map, "holders");
        JsonObject out = new JsonObject();
        for (Object value : holders.values()) {
            GenerationChunkHolder holder = (GenerationChunkHolder) value;
            ChunkAccess chunk = holder.getChunkIfPresentUnchecked(ChunkStatus.STRUCTURE_STARTS);
            if (chunk == null) continue;
            for (var entry : chunk.getAllStarts().entrySet())
                out.addProperty(
                        chunk.getPos()
                                + "/"
                                + host.registryAccess()
                                        .lookupOrThrow(Registries.STRUCTURE)
                                        .getKey(entry.getKey()),
                        entry.getValue()
                                .createTag(
                                        StructurePieceSerializationContext.fromLevel(host),
                                        chunk.getPos())
                                .toString());
        }
        return out;
    }

    private static JsonObject relevance(IsolatedGenerationContext ctx, ChunkAccess target)
            throws Exception {
        Object map = field(ctx, "chunkMap");
        Map<?, ?> holders = field(map, "holders");
        int referencedStarts = 0, referencedPieces = 0, intersectingPieces = 0;
        ChunkPos pos = target.getPos();
        for (Object value : holders.values()) {
            ChunkAccess source =
                    ((GenerationChunkHolder) value)
                            .getChunkIfPresentUnchecked(ChunkStatus.STRUCTURE_STARTS);
            if (source == null) continue;
            for (var entry : source.getAllStarts().entrySet()) {
                var refs = target.getAllReferences().get(entry.getKey());
                if (refs == null
                        || !refs.contains(source.getPos().pack())
                        || !entry.getValue().isValid()) continue;
                referencedStarts++;
                referencedPieces += entry.getValue().getPieces().size();
                for (var piece : entry.getValue().getPieces())
                    if (piece.getBoundingBox()
                            .intersects(
                                    pos.getMinBlockX(),
                                    pos.getMinBlockZ(),
                                    pos.getMaxBlockX(),
                                    pos.getMaxBlockZ())) intersectingPieces++;
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("referencedStarts", referencedStarts);
        out.addProperty("referencedPieces", referencedPieces);
        out.addProperty("piecesWithHorizontalTargetIntersection", intersectingPieces);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) throws Exception {
        var f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(owner);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Object owner, String name, Class<?>[] signature, Object... args)
            throws Exception {
        var m = owner.getClass().getDeclaredMethod(name, signature);
        m.setAccessible(true);
        return (T) m.invoke(owner, args);
    }
}
