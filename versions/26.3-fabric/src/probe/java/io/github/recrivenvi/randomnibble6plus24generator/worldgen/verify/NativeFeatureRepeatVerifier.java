package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.storage.LevelResource;

/** Fresh physical Vanilla repeats. Detects nondeterminism; NEVER grants an equality exception. */
public final class NativeFeatureRepeatVerifier {
    private NativeFeatureRepeatVerifier() {}

    private static final ChunkPos TARGET = new ChunkPos(-14, -13);
    private static final Map<String, AtomicInteger> STAGES = new ConcurrentHashMap<>();
    private static volatile boolean observing;

    static FeatureBVerifier.Fixture shulkerFixture() {
        return FeatureBVerifier.FIXTURES.stream()
                .filter(f -> f.id().equals("end-city"))
                .findFirst()
                .orElseThrow();
    }

    public static Map<String, Object> fixture() {
        if (Boolean.getBoolean("featureb.nativeCat"))
            return Map.of(
                    "seed",
                    6493561816561378690L,
                    "dimension",
                    "minecraft:overworld",
                    "x",
                    0,
                    "z",
                    2);
        if (Boolean.getBoolean("featurec.nativeShulker")) {
            var f = shulkerFixture();
            return Map.of(
                    "seed",
                    f.seed(),
                    "dimension",
                    "minecraft:" + f.dimension(),
                    "x",
                    f.x(),
                    "z",
                    f.z());
        }
        return Map.of(
                "masterSeed",
                FeatureCFrontierVerifier.MASTER,
                "seed",
                FeatureCFrontierVerifier.seed(TARGET),
                "dimension",
                "minecraft:overworld",
                "x",
                TARGET.x(),
                "z",
                TARGET.z());
    }

    public static void stage(WorldGenContext context, ChunkStatus status) {
        if (!observing) return;
        require(
                GenerationContextRegistry.find(context).isEmpty(),
                "Native repeat entered isolation");
        require(!status.isAfter(ChunkStatus.FEATURES), "Native capture advanced to " + status);
        STAGES.computeIfAbsent(status.toString(), unused -> new AtomicInteger()).incrementAndGet();
    }

    public static void run(MinecraftServer server, Path root) throws Exception {
        Files.createDirectories(root);
        var f = fixture();
        var target = new ChunkPos((int) f.get("x"), (int) f.get("z"));
        String dimension = (String) f.get("dimension");
        var level =
                server.getLevel(
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
        long seed = (long) f.get("seed");
        require(server.getWorldGenSettings().options().seed() == seed, "Wrong physical world seed");
        require(!MosaicWorldIdentity.isMosaicWorld(server), "Native repeat is not Vanilla");
        require(
                level.getChunkSource().getGenerator().getClass()
                        == net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.class,
                "Native repeat requires the original NoiseBasedChunkGenerator");
        require(GenerationContextRegistry.bindingCount() == 0, "Unexpected isolated context");
        long generationCalls = FeatureCFlowVerifier.generationCount();
        var plan = FeatureOrderingPlan.targetLocalZxRowMajorV1(target);
        NativeFeatureExecutionTrace.begin(plan.writers());
        STAGES.clear();
        observing = true;
        long started = System.nanoTime();
        FeatureStableSnapshot snapshot;
        CompoundTag raw = new CompoundTag();
        NativeFeatureExecutionTrace.Result trace;
        try {
            for (var pos : plan.writers()) {
                var chunk = level.getChunk(pos.x(), pos.z(), ChunkStatus.FEATURES, true);
                require(
                        chunk instanceof ProtoChunk
                                && chunk.getPersistedStatus() == ChunkStatus.FEATURES,
                        "Native writer did not stop at FEATURES " + pos);
            }
            var chunk =
                    (ProtoChunk)
                            level.getChunk(target.x(), target.z(), ChunkStatus.FEATURES, false);
            snapshot = FeatureStableSnapshot.capture(dimension, chunk, level.registryAccess());
            var entities = new ListTag();
            chunk.getEntities().forEach(e -> entities.add(e.copy()));
            raw.put("entities", entities);
            trace = NativeFeatureExecutionTrace.finish();
        } finally {
            observing = false;
            NativeFeatureExecutionTrace.abort();
        }
        boolean shulker = Boolean.getBoolean("featurec.nativeShulker");
        boolean cat = Boolean.getBoolean("featureb.nativeCat");
        snapshot.write(root.resolve("target.bin.gz"));
        NbtIo.writeCompressed(raw, root.resolve("entities-raw.nbt"));
        write(root.resolve("entities-raw.json"), snapshot.rawEntityNbt());
        require(
                shulker || cat ? snapshot.entityCount() > 0 : snapshot.entityCount() == 1,
                "Native entity positive count changed: " + snapshot.entityCount());
        for (var tag : raw.getListOrEmpty("entities"))
            require(
                    tag instanceof CompoundTag entity
                            && entity.getStringOr("id", "")
                                    .equals(
                                            shulker
                                                    ? "minecraft:shulker"
                                                    : cat ? "minecraft:cat" : "minecraft:drowned"),
                    "Native entity positive was not reached");
        require(
                FeatureCFlowVerifier.generationCount() == generationCalls
                        && GenerationContextRegistry.bindingCount() == 0,
                "Native capture used the experimental generator");
        write(root.resolve("trace.json"), trace);
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("status", "CAPTURED_NATIVE_ONLY");
        evidence.put("proofVersion", 1);
        evidence.put("fixture", fixture());
        evidence.put("pid", ProcessHandle.current().pid());
        evidence.put(
                "worldPath",
                server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
        evidence.put("generator", level.getChunkSource().getGenerator().getClass().getName());
        evidence.put("chunkMap", level.getChunkSource().chunkMap.getClass().getName());
        evidence.put("featureHash", snapshot.hash());
        evidence.put("entities", snapshot.entityCount());
        evidence.put("rawEntities", snapshot.rawEntityNbt());
        evidence.put("trace", trace);
        var stages = new TreeMap<String, Integer>();
        STAGES.forEach((key, count) -> stages.put(key, count.get()));
        evidence.put("stages", stages);
        evidence.put(
                "experimentalGenerations",
                FeatureCFlowVerifier.generationCount() - generationCalls);
        evidence.put("bindings", GenerationContextRegistry.bindingCount());
        evidence.put("runtimeMs", (System.nanoTime() - started) / 1_000_000);
        if (!root.getFileName().toString().equals("host_0")) {
            Path first = root.resolveSibling("host_0");
            var previous = FeatureStableSnapshot.read(first.resolve("target.bin.gz"));
            var diff = previous.diff(snapshot);
            var comparison = new LinkedHashMap<String, Object>();
            comparison.put("fullParity", diff.equivalent() ? "PASS" : "FAIL");
            comparison.put("diff", diff);
            comparison.put(
                    "canonicalEntitiesEqual",
                    previous.canonicalEntityNbt().equals(snapshot.canonicalEntityNbt()));
            var previousRaw =
                    NbtIo.readCompressed(
                            first.resolve("entities-raw.nbt"), NbtAccounter.unlimitedHeap());
            comparison.put("rawEntityDiff", differences(previousRaw, raw));
            if (!shulker)
                comparison.put(
                        "attributeValuesByIdDiff",
                        differences(
                                attributesById(
                                        previousRaw
                                                .getListOrEmpty("entities")
                                                .getCompoundOrEmpty(0)),
                                attributesById(
                                        raw.getListOrEmpty("entities").getCompoundOrEmpty(0))));
            comparison.put(
                    "otherCapturedDataEqual",
                    diff.differingBlocks() == 0
                            && Objects.equals(
                                    previous.deterministicMetadataDifference(snapshot),
                                    "ProtoChunk entities differ"));
            write(root.resolve("comparison.json"), comparison);
            evidence.put("comparison", comparison);
            require(
                    Files.readString(first.resolve("trace.json"))
                            .equals(Files.readString(root.resolve("trace.json"))),
                    "Native repeats had different writer/biome/feature-seed procedure");
        }
        write(root.resolve("summary.json"), evidence);
    }

    public record Difference(String path, String before, String after) {}

    /** Auxiliary explanation of list-order differences; NEVER used by snapshot equality. */
    public static CompoundTag attributesById(CompoundTag entity) {
        var result = new CompoundTag();
        for (var attribute : entity.getListOrEmpty("attributes")) {
            require(attribute instanceof CompoundTag, "Malformed diagnostic attribute");
            var compound = (CompoundTag) attribute;
            String id = compound.getStringOr("id", "");
            require(!id.isEmpty() && !result.contains(id), "Missing/duplicate attribute identity");
            result.put(id, compound.copy());
        }
        return result;
    }

    /** Diagnostic only: no ignored keys, numeric tolerance, mutation or reordered lists. */
    public static List<Difference> differences(Tag before, Tag after) {
        var result = new ArrayList<Difference>();
        collect("$", before, after, result);
        return List.copyOf(result);
    }

    private static void collect(String path, Tag a, Tag b, List<Difference> result) {
        if (Objects.equals(a, b)) return;
        if (a instanceof CompoundTag left && b instanceof CompoundTag right) {
            var keys = new TreeSet<>(left.keySet());
            keys.addAll(right.keySet());
            for (var key : keys) collect(path + "." + key, left.get(key), right.get(key), result);
        } else if (a instanceof ListTag left && b instanceof ListTag right) {
            for (int i = 0; i < Math.max(left.size(), right.size()); i++)
                collect(
                        path + "[" + i + "]",
                        i < left.size() ? left.get(i) : null,
                        i < right.size() ? right.get(i) : null,
                        result);
        } else
            result.add(
                    new Difference(
                            path,
                            Objects.toString(a, "<absent>"),
                            Objects.toString(b, "<absent>")));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void write(Path path, Object data) throws Exception {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n");
    }
}
