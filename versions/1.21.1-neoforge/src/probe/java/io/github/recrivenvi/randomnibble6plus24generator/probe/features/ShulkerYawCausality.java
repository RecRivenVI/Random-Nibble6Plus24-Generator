package io.github.recrivenvi.randomnibble6plus24generator.probe.features;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Shulker;

/** Probe-only intervention at the native constructor's Math.random inputs, never final NBT. */
public final class ShulkerYawCausality {
    public static final String ACCEPTANCE_RULE = "1.21.1-native-end-city-sentry-constructor-yaw-1";
    private static final String PREFIX = ModConstants.MOD_ID + ".featureBInputs.";
    private static final ThreadLocal<Frame> MARKER = new ThreadLocal<>();
    private static final List<Frame> observations = new ArrayList<>();
    private static JsonArray tape;
    private static int consumed;

    private ShulkerYawCausality() {}

    public static boolean enabled() {
        return NativeFeatureVerifier.enabled() && !mode().equals("off");
    }

    public static String mode() {
        String mode = System.getProperty(PREFIX + "yawMode", "off");
        if (!Set.of("off", "capture", "replay").contains(mode))
            throw new IllegalArgumentException("Unknown shulker yaw intervention");
        return mode;
    }

    public static void begin() {
        if (!NativeFeatureVerifier.enabled()) return;
        if (MARKER.get() != null) throw new IllegalStateException("Leaked sentry scope");
        observations.clear();
        consumed = 0;
        tape = null;
        if (mode().equals("replay")) {
            try {
                tape =
                        JsonParser.parseString(
                                        Files.readString(
                                                Path.of(System.getProperty(PREFIX + "yawTape"))))
                                .getAsJsonObject()
                                .getAsJsonArray("constructors");
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
    }

    public static AutoCloseable marker(String name, BlockPos position) {
        if (!NativeFeatureVerifier.enabled() || !name.startsWith("Sentry")) return () -> {};
        if (MARKER.get() != null) throw new IllegalStateException("Nested sentry constructor");
        var frame = new Frame(name, position.immutable());
        MARKER.set(frame);
        return () -> {
            if (MARKER.get() != frame) throw new IllegalStateException("Sentry scope mismatch");
            MARKER.remove();
            if (frame.created) {
                if (frame.actual.size() != 3 || frame.used.size() != 3)
                    throw new IllegalStateException(
                            "Native LivingEntity Math.random layout changed");
                observations.add(frame);
            } else if (!frame.actual.isEmpty())
                throw new IllegalStateException("Unfinished shulker constructor");
        };
    }

    public static double draw(Entity entity, double actual) {
        var frame = MARKER.get();
        if (!NativeFeatureVerifier.enabled() || frame == null || !(entity instanceof Shulker))
            return actual;
        if (!(actual >= 0 && actual < 1))
            throw new IllegalStateException("Invalid native Math.random value");
        int ordinal = frame.actual.size();
        double used = actual;
        if (tape != null) {
            if (consumed >= tape.size() || ordinal >= 3)
                throw new IllegalStateException("Yaw tape exhausted");
            var entry = tape.get(consumed).getAsJsonObject();
            if (!entry.get("marker").getAsString().equals(frame.name)
                    || entry.get("x").getAsInt() != frame.position.getX()
                    || entry.get("y").getAsInt() != frame.position.getY()
                    || entry.get("z").getAsInt() != frame.position.getZ())
                throw new IllegalStateException("Native marker order/input differs from replay");
            String raw = entry.getAsJsonArray("usedMathBits").get(ordinal).getAsString();
            used = Double.longBitsToDouble(Long.parseUnsignedLong(raw, 16));
            if (!(used >= 0 && used < 1))
                throw new IllegalStateException("Malformed constructor tape");
        }
        frame.actual.add(Long.toUnsignedString(Double.doubleToRawLongBits(actual), 16));
        frame.used.add(Long.toUnsignedString(Double.doubleToRawLongBits(used), 16));
        return used;
    }

    public static void created(Entity entity) {
        var frame = MARKER.get();
        if (!NativeFeatureVerifier.enabled() || frame == null || entity == null) return;
        if (!(entity instanceof Shulker) || frame.created || frame.used.size() != 3)
            throw new IllegalStateException("Unexpected sentry creation path");
        double raw = Double.longBitsToDouble(Long.parseUnsignedLong(frame.used.get(2), 16));
        // Exact constant and dmul/d2f path audited in this version's LivingEntity constructor.
        float expected = (float) (raw * 6.2831854820251465D);
        if (Float.floatToRawIntBits(entity.getYRot()) != Float.floatToRawIntBits(expected))
            throw new IllegalStateException("Constructor yaw does not follow observed Math.random");
        frame.yaw = entity.getYRot();
        frame.created = true;
        if (tape != null) consumed++;
    }

    public static void finish(CompoundTag raw, Path output) {
        if (!NativeFeatureVerifier.enabled()) return;
        if (MARKER.get() != null
                || (enabled() && observations.isEmpty())
                || (tape != null && consumed != tape.size()))
            throw new IllegalStateException("Incomplete constructor causal observations");
        var entities = raw.getList("entities", Tag.TAG_COMPOUND);
        int matched = 0;
        var seen = new HashSet<Frame>();
        for (var item : entities) {
            var entity = (CompoundTag) item;
            if (!entity.getString("id").equals("minecraft:shulker")) continue;
            var pos = entity.getList("Pos", Tag.TAG_DOUBLE);
            var frame =
                    observations.stream()
                            .filter(
                                    f ->
                                            pos.getDouble(0) == f.position.getX() + .5D
                                                    && pos.getDouble(1) == f.position.getY()
                                                    && pos.getDouble(2) == f.position.getZ() + .5D)
                            .findFirst()
                            .orElseThrow();
            var rotation = entity.getList("Rotation", Tag.TAG_FLOAT);
            if (rotation.size() != 2
                    || Float.floatToRawIntBits(rotation.getFloat(0))
                            != Float.floatToRawIntBits(frame.yaw)
                    || !seen.add(frame))
                throw new IllegalStateException("Final entity yaw/marker association differs");
            matched++;
        }
        if (enabled() && matched == 0)
            throw new IllegalStateException("No real target shulker observed");
        var result = new JsonObject();
        result.addProperty(
                "source", "LivingEntity.<init>: Math.random x3; ordinal 2 dmul then d2f");
        result.addProperty("mode", mode());
        result.addProperty("matchedTargetEntities", matched);
        var entries = new JsonArray();
        for (var frame : observations) {
            var row = new JsonObject();
            row.addProperty("marker", frame.name);
            row.addProperty("x", frame.position.getX());
            row.addProperty("y", frame.position.getY());
            row.addProperty("z", frame.position.getZ());
            row.add("actualMathBits", new Gson().toJsonTree(frame.actual));
            row.add("usedMathBits", new Gson().toJsonTree(frame.used));
            row.addProperty(
                    "constructorYawBits",
                    Integer.toUnsignedString(Float.floatToRawIntBits(frame.yaw), 16));
            entries.add(row);
        }
        result.add("constructors", entries);
        NativeStructureVerifier.write(output.resolve("shulker-yaw.json"), result);
    }

    /** Validation only: each accepted field must match an observed native Sentry constructor. */
    public static int acceptObservedYaw(CompoundTag canonical) {
        if (enabled()) throw new IllegalStateException("Causal replay must remain UUID-only");
        int count = 0;
        for (var item : canonical.getList("entities", Tag.TAG_COMPOUND)) {
            var entity = (CompoundTag) item;
            if (!entity.getString("id").equals("minecraft:shulker")) continue;
            requireObserved(entity);
            var check = entity.copy();
            var rawYaw = check.getList("Rotation", Tag.TAG_FLOAT).get(0).copy();
            zeroObservedYaw(check);
            check.getList("Rotation", Tag.TAG_FLOAT).set(0, rawYaw);
            if (!check.equals(entity))
                throw new IllegalStateException("Yaw policy changed other fields");
            var wrong = entity.copy();
            wrong.getList("Rotation", Tag.TAG_FLOAT).set(0, FloatTag.valueOf(Float.NaN));
            boolean rejected = false;
            try {
                zeroObservedYaw(wrong);
            } catch (IllegalStateException expected) {
                rejected = true;
            }
            if (!rejected)
                throw new IllegalStateException("Yaw policy accepted an unobserved value");
            zeroObservedYaw(entity);
            count++;
        }
        return count;
    }

    private static void zeroObservedYaw(CompoundTag entity) {
        requireObserved(entity);
        entity.getList("Rotation", Tag.TAG_FLOAT).set(0, FloatTag.valueOf(0));
    }

    private static void requireObserved(CompoundTag entity) {
        var pos = entity.getList("Pos", Tag.TAG_DOUBLE);
        var rotation = entity.getList("Rotation", Tag.TAG_FLOAT);
        if (!entity.getString("id").equals("minecraft:shulker")
                || pos.size() != 3
                || rotation.size() != 2)
            throw new IllegalStateException("Malformed native Sentry evidence");
        var frame =
                observations.stream()
                        .filter(
                                f ->
                                        pos.getDouble(0) == f.position.getX() + .5D
                                                && pos.getDouble(1) == f.position.getY()
                                                && pos.getDouble(2) == f.position.getZ() + .5D)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No observed native Sentry at entity position"));
        if (Float.floatToRawIntBits(rotation.getFloat(0)) != Float.floatToRawIntBits(frame.yaw))
            throw new IllegalStateException(
                    "Entity yaw differs from its observed native constructor");
    }

    public static void verifyOutput(CompoundTag canonical, JsonObject proof, Path output)
            throws java.io.IOException {
        String expectedPath = System.getProperty(PREFIX + "yawExpected");
        if (expectedPath == null) {
            if (!mode().equals("capture"))
                throw new IllegalStateException("Replay needs a control");
            return;
        }
        Path expected = Path.of(expectedPath);
        var control =
                NbtIo.readCompressed(
                        expected.resolve("canonical.nbt.gz"), NbtAccounter.unlimitedHeap());
        var before =
                JsonParser.parseString(Files.readString(expected.resolve("summary.json")))
                        .getAsJsonObject();
        for (String field :
                List.of(
                        "fixture",
                        "dimension",
                        "x",
                        "z",
                        "localSeed",
                        "environment",
                        "inputHash",
                        "structures",
                        "trace",
                        "preInputs",
                        "outerInputs",
                        "rejected")) {
            if (!NativeStructureVerifier.canonical(before.get(field))
                    .toString()
                    .equals(NativeStructureVerifier.canonical(proof.get(field)).toString()))
                throw new IllegalStateException("Causal run changed deterministic input " + field);
        }
        NativeStructureVerifier.write(
                output.resolve("causal-difference.json"),
                new Gson().toJsonTree(NativeFeatureNbtDiff.compare(control, canonical)));
        if (mode().equals("replay")) {
            if (!control.equals(canonical))
                throw new IllegalStateException(
                        "Constructor replay did not reproduce full UUID-only NBT");
            proof.addProperty("fullReplayEqual", true);
        } else {
            if (control.equals(canonical))
                throw new IllegalStateException("Native repeats did not expose yaw divergence");
            var left = control.copy();
            var right = canonical.copy();
            diagnosticZeroYaw(left);
            diagnosticZeroYaw(right);
            if (!left.equals(right))
                throw new IllegalStateException(
                        "Native difference includes fields other than observed shulker yaw");
            proof.addProperty("nativeDivergenceOnlyObservedYaw", true);
        }
    }

    private static void diagnosticZeroYaw(CompoundTag root) {
        for (var item : root.getList("entities", Tag.TAG_COMPOUND)) {
            var entity = (CompoundTag) item;
            if (!entity.getString("id").equals("minecraft:shulker"))
                throw new IllegalStateException("Unexpected entity in finite yaw experiment");
            var rotation = entity.getList("Rotation", Tag.TAG_FLOAT);
            if (rotation.size() != 2) throw new IllegalStateException("Malformed shulker rotation");
            rotation.set(0, FloatTag.valueOf(0));
        }
    }

    private static final class Frame {
        final String name;
        final BlockPos position;
        final List<String> actual = new ArrayList<>(), used = new ArrayList<>();
        boolean created;
        float yaw;

        Frame(String name, BlockPos position) {
            this.name = name;
            this.position = position;
        }
    }
}
