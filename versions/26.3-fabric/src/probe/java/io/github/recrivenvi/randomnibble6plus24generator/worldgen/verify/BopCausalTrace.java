package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Explicit diagnostic only: observes natural orders and replays recorded sorter inputs, not RNG.
 */
public final class BopCausalTrace implements AutoCloseable {
    public static final String PROPERTY = "mosaic.bop.causal";
    private static final ThreadLocal<BopCausalTrace> CURRENT = new ThreadLocal<>();
    private static final Gson JSON = new Gson();
    private final ServerLevel level;
    private final Path output;
    private final JsonObject report = new JsonObject();
    private final Map<String, Long> counts = new TreeMap<>();
    private BufferedWriter events;
    private WorldGenRegion region;
    private ChunkAccess target;
    private WorldgenRandom random;
    private boolean decorating;
    private int step = -1, index = -1;
    private String feature = "prelude";
    private long ordinal;

    private BopCausalTrace(ServerLevel level, Path output) throws IOException {
        this.level = level;
        this.output = output;
        Files.createDirectories(output);
        events =
                new BufferedWriter(
                        new OutputStreamWriter(
                                new GZIPOutputStream(
                                        Files.newOutputStream(output.resolve("events.jsonl.gz"))),
                                StandardCharsets.UTF_8));
    }

    public static BopCausalTrace open(ServerLevel level, Path output) throws IOException {
        if (!Boolean.getBoolean(PROPERTY) || CURRENT.get() != null)
            throw new IllegalStateException("Invalid causal scope");
        var trace = new BopCausalTrace(level, output);
        CURRENT.set(trace);
        return trace;
    }

    /** Runs upstream enumeration unchanged before the optional diagnostic sorter-input replay. */
    public void prepare(ChunkGenerator generator) {
        try {
            var source = generator.getBiomeSource();
            if (source.getClass() != TheEndBiomeSource.class)
                throw new IllegalStateException("End-only causal proof");
            var actual = List.copyOf(source.possibleBiomes());
            var collect = TheEndBiomeSource.class.getDeclaredMethod("collectPossibleBiomes");
            collect.setAccessible(true);
            List<?> upstream;
            try (var stream = (java.util.stream.Stream<?>) collect.invoke(source)) {
                upstream = stream.toList();
            }
            if (!actual.equals(upstream))
                throw new IllegalStateException(
                        "Natural order differs from unmodified upstream enumerator");
            report.add("naturalBiomeOrder", keys(actual));
            report.addProperty("upstreamEnumeratorEqual", true);
            var hashes = new JsonObject();
            for (var holder : actual) hashes.addProperty(key(holder), holder.hashCode());
            report.add("holderHashes", hashes);
            var tb =
                    Arrays.stream(source.getClass().getDeclaredFields())
                            .filter(f -> f.getName().contains("tbPossibleBiomes"))
                            .findFirst()
                            .orElseThrow();
            tb.setAccessible(true);
            report.add("upstreamTbSetOrder", keys((Collection<?>) tb.get(source)));
            report.addProperty("upstreamTbSetClass", tb.get(source).getClass().getName());
            Field field = ChunkGenerator.class.getDeclaredField("featuresPerStep");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var original = (Supplier<List<FeatureSorter.StepFeatureData>>) field.get(generator);
            Field getterField = ChunkGenerator.class.getDeclaredField("generationSettingsGetter");
            getterField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var getter =
                    (Function<Holder<Biome>, BiomeGenerationSettings>) getterField.get(generator);
            var nativeSteps = original.get();
            if (!stepKeys(nativeSteps)
                    .equals(
                            stepKeys(
                                    FeatureSorter.buildFeaturesPerStep(
                                            actual, b -> getter.apply(b).features(), true))))
                throw new IllegalStateException(
                        "Natural feature order is not upstream FeatureSorter output");
            report.add("naturalFeatureOrder", stepKeys(nativeSteps));
            List<Holder<Biome>> effective = actual;
            String replay = System.getProperty(PROPERTY + ".order");
            if (replay != null) {
                var evidence =
                        JsonParser.parseString(Files.readString(Path.of(replay))).getAsJsonObject();
                if (!evidence.get("upstreamEnumeratorEqual").getAsBoolean())
                    throw new IllegalStateException("Unproven replay order");
                var byKey = new HashMap<String, Holder<Biome>>();
                actual.forEach(b -> byKey.put(key(b), b));
                var replacement = new ArrayList<Holder<Biome>>();
                for (var item : evidence.getAsJsonArray("naturalBiomeOrder")) {
                    var biome = byKey.remove(item.getAsString());
                    if (biome == null)
                        throw new IllegalStateException("Duplicate/foreign replay biome");
                    replacement.add(biome);
                }
                if (!byKey.isEmpty()) throw new IllegalStateException("Missing replay biome");
                effective = List.copyOf(replacement);
                var steps =
                        FeatureSorter.buildFeaturesPerStep(
                                effective, b -> getter.apply(b).features(), true);
                field.set(generator, (Supplier<List<FeatureSorter.StepFeatureData>>) () -> steps);
                report.addProperty(
                        "diagnosticOrderSource", Path.of(replay).toAbsolutePath().toString());
            }
            report.add("effectiveBiomeOrder", keys(effective));
            @SuppressWarnings("unchecked")
            var selected = (Supplier<List<FeatureSorter.StepFeatureData>>) field.get(generator);
            report.add("effectiveFeatureOrder", stepKeys(selected.get()));
            if (!List.copyOf(source.possibleBiomes()).equals(actual))
                throw new IllegalStateException("Diagnostic mutated source order");
        } catch (ReflectiveOperationException | IOException failure) {
            throw new IllegalStateException("Causal order audit failed", failure);
        }
    }

    private JsonArray keys(Collection<?> values) {
        var out = new JsonArray();
        values.forEach(v -> out.add(key((Holder<?>) v)));
        return out;
    }

    private static String key(Holder<?> biome) {
        return biome.unwrapKey().orElseThrow().identifier().toString();
    }

    private JsonArray stepKeys(List<FeatureSorter.StepFeatureData> steps) {
        var all = new JsonArray();
        var registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        for (var step : steps) {
            var list = new JsonArray();
            step.features()
                    .forEach(f -> list.add(Objects.requireNonNull(registry.getKey(f)).toString()));
            all.add(list);
        }
        return all;
    }

    public static void begin(Object level, ChunkAccess chunk) {
        var t = CURRENT.get();
        if (t == null) return;
        if (t.decorating || t.target != null)
            throw new IllegalStateException("Nested/repeated causal writer");
        t.region = (WorldGenRegion) level;
        t.target = chunk;
        t.report.addProperty(
                "terrainHash",
                FeatureStableSnapshot.capture("minecraft:the_end", chunk, t.level.registryAccess())
                        .hash());
        t.blocks("before.blocks.gz");
        t.decorating = true;
        t.event("begin", chunk.getPos().x(), chunk.getPos().z(), t.region.getSeed());
    }

    public static void end() {
        var t = CURRENT.get();
        if (t == null) return;
        t.event("end", t.state());
        t.decorating = false;
        t.blocks("after.blocks.gz");
    }

    public String terrainHash() {
        return report.get("terrainHash").getAsString();
    }

    public void verifyFinal(ChunkAccess chunk) throws IOException {
        if (chunk != target || decorating)
            throw new IllegalStateException("Wrong final causal target");
        blocks("final.blocks.gz");
        if (!Arrays.equals(
                Files.readAllBytes(output.resolve("after.blocks.gz")),
                Files.readAllBytes(output.resolve("final.blocks.gz"))))
            throw new IllegalStateException("Untraced post-decoration block mutation");
    }

    public static void decoration(Object rng, long seed, int x, int z) {
        var t = CURRENT.get();
        if (t == null || !t.decorating) return;
        if (t.random != null) throw new IllegalStateException("Multiple decoration random sources");
        t.random = (WorldgenRandom) rng;
        t.event("decoration", seed, x, z);
    }

    public static boolean traced(Object rng) {
        var t = CURRENT.get();
        return t != null && t.decorating && t.random == rng;
    }

    public static void seeded(Object rng, long seed) {
        if (traced(rng)) {
            var t = CURRENT.get();
            t.event("seed", seed, t.state());
        }
    }

    public static void slot(Object rng, long seed, int index, int step) {
        if (!traced(rng)) return;
        var t = CURRENT.get();
        t.step = step;
        t.index = index;
        t.feature = "structure";
        t.event("slot", seed, index, step);
    }

    public static void draw(Object rng, int bits, int value) {
        if (traced(rng)) {
            var t = CURRENT.get();
            t.event("draw", bits, value, t.state());
        }
    }

    public static void api(Object rng, String api, Object... values) {
        if (traced(rng)) {
            var t = CURRENT.get();
            t.event("api:" + api, values);
        }
    }

    public static void enter(PlacedFeature feature) {
        var t = CURRENT.get();
        if (t == null || !t.decorating) return;
        t.feature =
                Objects.requireNonNull(
                                t.level
                                        .registryAccess()
                                        .lookupOrThrow(Registries.PLACED_FEATURE)
                                        .getKey(feature))
                        .toString();
        t.event("feature", t.state());
    }

    public static void placed(boolean value) {
        var t = CURRENT.get();
        if (t != null && t.decorating) t.event("placed", value, t.state(), t.nextLongWitness());
    }

    public static void read(Object region, String api, BlockPos pos, Object value) {
        var t = CURRENT.get();
        if (t != null && t.decorating && t.region == region) {
            String state;
            if (value instanceof net.minecraft.world.level.material.FluidState fluid) {
                var properties = new TreeMap<String, String>();
                fluid.getValues()
                        .forEach(
                                entry ->
                                        properties.put(
                                                entry.property().getName(), entry.valueName()));
                state =
                        net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(
                                        fluid.getType())
                                + properties.toString();
            } else state = String.valueOf(value);
            t.event(api, pos.getX(), pos.getY(), pos.getZ(), state);
        }
    }

    public static void biome(Object manager, BlockPos pos, Holder<Biome> value) {
        var t = CURRENT.get();
        if (t != null && t.decorating && t.region.getBiomeManager() == manager)
            t.event("read:biome", pos.getX(), pos.getY(), pos.getZ(), key(value));
    }

    public static void write(
            Object chunk, BlockPos pos, BlockState before, BlockState after, int flags) {
        var t = CURRENT.get();
        if (t != null && t.decorating && t.target == chunk)
            t.event(
                    "write",
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    String.valueOf(before),
                    after.toString(),
                    flags);
    }

    private Object state() {
        if (random == null) return "unbound";
        try {
            var source = field(WorldgenRandom.class, "randomSource").get(random);
            if (!(source instanceof XoroshiroRandomSource))
                throw new IllegalStateException("Unaudited decoration RNG");
            var bits = field(XoroshiroRandomSource.class, "randomNumberGenerator").get(source);
            var gaussian = field(LegacyRandomSource.class, "gaussianSource").get(random);
            return List.of(
                    field(Xoroshiro128PlusPlus.class, "seedLo").getLong(bits),
                    field(Xoroshiro128PlusPlus.class, "seedHi").getLong(bits),
                    random.getCount(),
                    field(MarsagliaPolarGaussian.class, "haveNextNextGaussian")
                            .getBoolean(gaussian),
                    Double.doubleToRawLongBits(
                            field(MarsagliaPolarGaussian.class, "nextNextGaussian")
                                    .getDouble(gaussian)));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private long nextLongWitness() {
        @SuppressWarnings("unchecked")
        var s = (List<Object>) state();
        return new WorldgenRandom(new XoroshiroRandomSource((Long) s.get(0), (Long) s.get(1)))
                .nextLong();
    }

    private static final Map<String, Field> FIELDS = new HashMap<>();

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        String key = type.getName() + ":" + name;
        var f = FIELDS.get(key);
        if (f == null) {
            f = type.getDeclaredField(name);
            f.setAccessible(true);
            FIELDS.put(key, f);
        }
        return f;
    }

    private void event(String api, Object... values) {
        counts.merge(api, 1L, Long::sum);
        var item = new JsonArray();
        item.add(ordinal++);
        item.add(step);
        item.add(index);
        item.add(feature);
        item.add(api);
        for (var value : values) item.add(JSON.toJsonTree(value));
        try {
            events.write(JSON.toJson(item));
            events.newLine();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void blocks(String name) {
        try (var out =
                new DataOutputStream(
                        new GZIPOutputStream(Files.newOutputStream(output.resolve(name))))) {
            out.writeInt(target.getPos().x());
            out.writeInt(target.getPos().z());
            out.writeInt(target.getMinY());
            out.writeInt(target.getHeight());
            for (int y = target.getMinY(); y < target.getMinY() + target.getHeight(); y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        out.writeUTF(
                                target.getBlockState(
                                                new BlockPos(
                                                        target.getPos().getMinBlockX() + x,
                                                        y,
                                                        target.getPos().getMinBlockZ() + z))
                                        .toString());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (decorating) throw new IllegalStateException("Unfinished causal writer");
            report.add("counts", JSON.toJsonTree(counts));
            report.addProperty("eventCount", ordinal);
            PreFeaturesVerifier.write(output.resolve("trace.json"), report);
        } finally {
            CURRENT.remove();
            events.close();
        }
    }
}
