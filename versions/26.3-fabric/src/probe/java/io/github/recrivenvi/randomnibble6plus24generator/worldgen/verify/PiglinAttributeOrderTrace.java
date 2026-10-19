package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;

/** Diagnostic serialization-order tape; never changes attributes, RNG or acceptance rules. */
public final class PiglinAttributeOrderTrace {
    private static volatile State active;

    private PiglinAttributeOrderTrace() {}

    public static void begin() throws Exception {
        if (!Boolean.getBoolean("featureb.attributeOrder")) return;
        require(active == null, "Attribute trace already active");
        var replay = System.getProperty("featureb.attributeReplay", "");
        Map<String, List<String>> tape = new TreeMap<>();
        if (!replay.isEmpty()) {
            var path = Path.of(replay).toAbsolutePath().normalize();
            var root =
                    Path.of(System.getProperty("validation.result")).toAbsolutePath().normalize();
            require(path.startsWith(root.getParent()), "Foreign replay tape");
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            require(
                    json.get("mode").getAsString().equals("capture"),
                    "Replay needs natural capture");
            for (var entry : json.getAsJsonArray("records")) {
                var object = entry.getAsJsonObject();
                var order = new ArrayList<String>();
                object.getAsJsonArray("nativeOrder").forEach(id -> order.add(id.getAsString()));
                require(
                        tape.put(object.get("entity").getAsString(), List.copyOf(order)) == null,
                        "Duplicate tape entity");
            }
            require(tape.size() == 8, "Need all eight natural piglins");
        }
        active = new State(replay, Map.copyOf(tape));
    }

    public static List<AttributeInstance.Packed> observe(
            LivingEntity entity, AttributeMap owner, List<AttributeInstance.Packed> packed) {
        var state = active;
        if (state == null
                || !entity.getType()
                        .builtInRegistryHolder()
                        .key()
                        .identifier()
                        .toString()
                        .equals("minecraft:piglin")) return packed;
        try {
            require(entity.getAttributes() == owner, "Wrong entity/map association");
            var field = AttributeMap.class.getDeclaredField("attributes");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (Map<Holder<Attribute>, AttributeInstance>) field.get(owner);
            require(
                    map.getClass()
                            .getName()
                            .equals("it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap"),
                    "Unexpected native map");
            var maskField = map.getClass().getDeclaredField("mask");
            maskField.setAccessible(true);
            int mask = maskField.getInt(map);
            String key = key(entity.getX(), entity.getY(), entity.getZ());
            var keys = new ArrayList<Map<String, Object>>();
            for (var entry : map.entrySet()) {
                var holder = entry.getKey();
                require(
                        holder.hashCode() == System.identityHashCode(holder),
                        "Non-identity key hash");
                keys.add(
                        Map.of(
                                "id",
                                id(holder),
                                "hashCode",
                                holder.hashCode(),
                                "identityHashCode",
                                System.identityHashCode(holder),
                                "slot",
                                it.unimi.dsi.fastutil.HashCommon.mix(holder.hashCode()) & mask));
            }
            // Fastutil's spliterator need not follow its iterator's table scan direction.
            var iteration = new ArrayList<String>();
            for (var value : map.values()) iteration.add(id(value.getAttribute()));
            var nativeOrder = packed.stream().map(p -> id(p.attribute())).toList();
            require(iteration.equals(nativeOrder), "Native values iteration/pack differs");
            var used = packed;
            if (!state.replay.isEmpty()) {
                var wanted =
                        Objects.requireNonNull(
                                state.tape.get(key), "No matching natural entity tape");
                var byId = new HashMap<String, AttributeInstance.Packed>();
                for (var p : packed)
                    require(byId.put(id(p.attribute()), p) == null, "Duplicate attribute");
                require(
                        wanted.size() == byId.size() && new HashSet<>(wanted).equals(byId.keySet()),
                        "Replay would alter attribute members");
                used = wanted.stream().map(byId::get).toList();
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("entity", key);
            row.put("mapClass", map.getClass().getName());
            row.put("mask", mask);
            row.put("keysInIterationOrder", keys);
            row.put("nativeOrder", nativeOrder);
            row.put("usedOrder", used.stream().map(p -> id(p.attribute())).toList());
            row.put(
                    "packedValues",
                    packed.stream()
                            .map(
                                    p ->
                                            Map.of(
                                                    "id",
                                                    id(p.attribute()),
                                                    "baseBits",
                                                    Long.toHexString(
                                                            Double.doubleToRawLongBits(
                                                                    p.baseValue())),
                                                    "modifiers",
                                                    p.modifiers().toString()))
                            .toList());
            require(
                    state.records.putIfAbsent(key, row) == null,
                    "Entity serialized twice in capture");
            return used;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot audit native attribute map", failure);
        }
    }

    public static void finish(Path output, ListTag raw, FeatureStableSnapshot snapshot)
            throws Exception {
        var state = active;
        if (state == null) return;
        require(state.records.size() == 8, "Natural piglin serialization positive missing");
        int matched = 0;
        for (var tag : raw) {
            var entity = (CompoundTag) tag;
            if (!entity.getStringOr("id", "").equals("minecraft:piglin")) continue;
            var pos = entity.getListOrEmpty("Pos");
            var key =
                    key(
                            ((NumericTag) pos.get(0)).doubleValue(),
                            ((NumericTag) pos.get(1)).doubleValue(),
                            ((NumericTag) pos.get(2)).doubleValue());
            var row =
                    Objects.requireNonNull(
                            state.records.get(key), "Raw NBT lost entity association");
            var ids = new ArrayList<String>();
            entity.getListOrEmpty("attributes")
                    .forEach(a -> ids.add(((CompoundTag) a).getStringOr("id", "")));
            require(row.get("usedOrder").equals(ids), "Codec/raw NBT changed pack order");
            matched++;
        }
        require(matched == 8, "Raw piglin count differs");
        var report = new LinkedHashMap<String, Object>();
        report.put("schema", 1);
        report.put("mode", state.replay.isEmpty() ? "capture" : "replay");
        report.put("sourceTape", state.replay);
        report.put("scope", "DIAGNOSTIC_CAUSAL_TAPE_NOT_ACCEPTANCE_OR_PRODUCTION_SORT");
        report.put("records", List.copyOf(state.records.values()));
        if (!state.replay.isEmpty()) {
            var reference = Path.of(state.replay).getParent();
            var before = FeatureStableSnapshot.read(reference.resolve("features.bin.gz"));
            var beforeRaw =
                    NbtIo.readCompressed(
                                    reference.resolve("entities-raw.nbt"),
                                    NbtAccounter.unlimitedHeap())
                            .getListOrEmpty("entities");
            var comparison = NativeEntityAcceptance.compare(before, beforeRaw, snapshot, raw);
            report.put("fullReplayComparison", comparison);
            write(output.resolve("attribute-order.json"), report);
            require(
                    comparison.strictParity()
                            && comparison.entities().unexpectedDifferences().isEmpty(),
                    "Replay failed full UUID-only equality");
        } else write(output.resolve("attribute-order.json"), report);
    }

    public static void abort() {
        active = null;
    }

    private static String id(Holder<Attribute> holder) {
        return holder.unwrapKey().orElseThrow().identifier().toString();
    }

    private static String key(double x, double y, double z) {
        return Long.toHexString(Double.doubleToRawLongBits(x))
                + ":"
                + Long.toHexString(Double.doubleToRawLongBits(y))
                + ":"
                + Long.toHexString(Double.doubleToRawLongBits(z));
    }

    private static void write(Path path, Object object) throws Exception {
        Files.writeString(
                path, new GsonBuilder().setPrettyPrinting().create().toJson(object) + "\n");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class State {
        final String replay;
        final Map<String, List<String>> tape;
        final Map<String, Map<String, Object>> records = new ConcurrentHashMap<>();

        State(String replay, Map<String, List<String>> tape) {
            this.replay = replay;
            this.tape = tape;
        }
    }
}
