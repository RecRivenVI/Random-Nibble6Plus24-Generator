package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureStableSnapshot;
import java.util.*;
import net.minecraft.nbt.*;

/** Probe-only acceptance, separate from the UUID-only canonical snapshot and its hash. */
public final class NativeEntityAcceptance {
    private NativeEntityAcceptance() {}

    public static final String RULE = "26.1.2-native-drowned-fields-1";
    public static final String SHULKER_RULE = "26.1.2-native-end-city-shulker-yaw-1";

    public record Entities(
            boolean accepted,
            List<NativeEntityDifference.Difference> rawDifferences,
            List<NativeEntityDifference.Difference> unexpectedDifferences) {}

    public record Snapshot(
            boolean accepted,
            boolean strictParity,
            String nonEntityDifference,
            Entities entities) {}

    public static Entities compare(ListTag before, ListTag after) {
        return compare(before, after, false);
    }

    static Entities compare(ListTag before, ListTag after, boolean endCity) {
        return compare(before, after, endCity, false);
    }

    static Entities compare(
            ListTag before, ListTag after, boolean endCity, boolean catAttributeOrder) {
        var left = view(before, endCity, catAttributeOrder);
        var right = view(after, endCity, catAttributeOrder);
        var unexpected = NativeEntityDifference.differences(left, right);
        return new Entities(
                unexpected.isEmpty(),
                NativeEntityDifference.differences(before, after),
                unexpected);
    }

    public static Snapshot compare(
            FeatureStableSnapshot before,
            ListTag beforeRaw,
            FeatureStableSnapshot after,
            ListTag afterRaw) {
        return compare(before, beforeRaw, after, afterRaw, false);
    }

    static Snapshot compare(
            FeatureStableSnapshot before,
            ListTag beforeRaw,
            FeatureStableSnapshot after,
            ListTag afterRaw,
            boolean endCity) {
        return compare(before, beforeRaw, after, afterRaw, endCity, false);
    }

    static Snapshot compare(
            FeatureStableSnapshot before,
            ListTag beforeRaw,
            FeatureStableSnapshot after,
            ListTag afterRaw,
            boolean endCity,
            boolean catAttributeOrder) {
        requireMatches(before, beforeRaw);
        requireMatches(after, afterRaw);
        var diff = before.diff(after);
        String metadata = before.deterministicMetadataDifference(after);
        String other =
                diff.differingBlocks() != 0
                        ? "BlockStates differ"
                        : metadata == null || metadata.equals("ProtoChunk entities differ")
                                ? null
                                : metadata;
        var entities = compare(beforeRaw, afterRaw, endCity, catAttributeOrder);
        return new Snapshot(
                other == null && entities.accepted(), diff.equivalent(), other, entities);
    }

    private static ListTag view(ListTag entities, boolean endCity, boolean catAttributeOrder) {
        var result = new ListTag();
        for (var tag : entities) {
            if (!(tag instanceof CompoundTag original))
                throw new IllegalArgumentException("Malformed entity");
            var entity = original.copy();
            // Root UUID only. Passenger, owner and attribute UUIDs are not implicitly exempt.
            if (entity.contains("UUID")) {
                if (!(entity.get("UUID") instanceof IntArrayTag uuid)
                        || uuid.getAsIntArray().length != 4)
                    throw new IllegalArgumentException("Malformed entity UUID");
                entity.putIntArray("UUID", new int[4]);
            }
            for (var key : List.of("UUIDMost", "UUIDLeast")) {
                if (entity.contains(key)) {
                    if (!(entity.get(key) instanceof LongTag))
                        throw new IllegalArgumentException("Malformed UUID component");
                    entity.putLong(key, 0);
                }
            }
            if (catAttributeOrder
                    && entity.getStringOr("id", "").equals("minecraft:cat")
                    && entity.contains("attributes")) {
                if (!(entity.get("attributes") instanceof ListTag))
                    throw new IllegalArgumentException("Malformed cat attributes");
                var byId = NativeEntityDifference.attributesById(entity);
                var sorted = new ListTag();
                for (var id : new TreeSet<>(byId.keySet())) sorted.add(byId.get(id));
                entity.put("attributes", sorted);
            }
            if (!endCity
                    && entity.getStringOr("id", "").equals("minecraft:drowned")
                    && entity.contains("attributes")) {
                if (!(entity.get("attributes") instanceof ListTag))
                    throw new IllegalArgumentException("Malformed attributes");
                var byId = NativeEntityDifference.attributesById(entity);
                for (var id : byId.keySet()) {
                    var attribute = byId.getCompoundOrEmpty(id);
                    if (id.equals("minecraft:spawn_reinforcements"))
                        boundedDouble(attribute, "base", (double) 0.1f);
                    if (id.equals("minecraft:knockback_resistance")
                            && attribute.contains("modifiers")) {
                        if (!(attribute.get("modifiers") instanceof ListTag modifiers))
                            throw new IllegalArgumentException("Malformed modifiers");
                        var identities = new HashSet<String>();
                        for (var modifier : modifiers) {
                            if (!(modifier instanceof CompoundTag m))
                                throw new IllegalArgumentException("Malformed modifier");
                            String key = m.getStringOr("id", "");
                            if (key.isEmpty() || !identities.add(key))
                                throw new IllegalArgumentException("Ambiguous modifier identity");
                            if (key.equals("minecraft:random_spawn_bonus")
                                    && m.getStringOr("operation", "").equals("add_value"))
                                boundedDouble(m, "amount", (double) 0.05f);
                        }
                    }
                }
                // All members, operations, types and presence remain compared, keyed by unique ID.
                entity.put("attributes", byId);
            }
            if (endCity
                    && entity.getStringOr("id", "").equals("minecraft:shulker")
                    && entity.contains("Rotation")) {
                // EndCity Sentry uses create + setPos, without finalizeSpawn's yaw reset.
                // LivingEntity's constructor consumes entity.random.nextFloat() * 6.2831855f.
                // Pitch, presence, entity order/count and every other field remain exact.
                if (!(entity.get("Rotation") instanceof ListTag rotation)
                        || rotation.size() != 2
                        || !(rotation.get(0) instanceof FloatTag yaw)
                        || !(rotation.get(1) instanceof FloatTag)
                        || !Float.isFinite(yaw.floatValue())
                        || yaw.floatValue() < 0
                        || yaw.floatValue() >= 6.2831855f)
                    throw new IllegalArgumentException("Outside evidenced End City Shulker yaw");
                rotation.set(0, FloatTag.valueOf(0));
            }
            result.add(entity);
        }
        return result;
    }

    private static void boundedDouble(CompoundTag tag, String key, double maximum) {
        if (!tag.contains(key))
            return; // Missing versus present still differs; never synthesize data.
        if (!(tag.get(key) instanceof DoubleTag number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0
                || number.doubleValue() >= maximum)
            throw new IllegalArgumentException("Outside evidenced native random field: " + key);
        tag.putDouble(key, 0);
    }

    /**
     * Bind raw evidence to the existing snapshot, rather than accepting a substitute entity list.
     */
    static void requireMatches(FeatureStableSnapshot snapshot, ListTag raw) {
        var encoded = new ArrayList<String>();
        for (var tag : raw) {
            if (!(tag instanceof CompoundTag entity))
                throw new IllegalArgumentException("Malformed raw evidence");
            var text = new StringJoiner(",", "{", "}");
            for (var key : new TreeSet<>(entity.keySet())) {
                text.add(
                        key
                                + ":"
                                + (List.of("UUID", "UUIDMost", "UUIDLeast").contains(key)
                                        ? "<excluded-entity-uuid>"
                                        : snapshotEncoding(entity.get(key))));
            }
            encoded.add(text.toString());
        }
        if (!encoded.equals(snapshot.canonicalEntityNbt()))
            throw new IllegalArgumentException(
                    "Raw entity evidence does not match UUID-only snapshot");
    }

    private static String snapshotEncoding(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var text = new StringJoiner(",", "{", "}");
            for (var key : new TreeSet<>(compound.keySet()))
                text.add(key + ":" + snapshotEncoding(compound.get(key)));
            return text.toString();
        }
        if (tag instanceof ListTag list) {
            var text = new StringJoiner(",", "[", "]");
            for (var item : list) text.add(snapshotEncoding(item));
            return text.toString();
        }
        return tag.getId() + ":" + tag;
    }
}
