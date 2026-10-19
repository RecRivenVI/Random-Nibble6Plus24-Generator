package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.LegacyRingRandomAccess;
import java.util.function.Supplier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

/** Read-only parent stream observation; continuation is sampled on a detached copy only. */
public final class RingEntropyCapture {
    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();

    private RingEntropyCapture() {}

    public static long before(RandomSource random) {
        return CURRENT.get() == null ? 0 : state(random);
    }

    public static void drew(RandomSource parent, long before, double result) {
        var trace = CURRENT.get();
        if (trace != null)
            trace.add(parent, before, "nextDouble", Double.doubleToRawLongBits(result), null);
    }

    public static void forked(RandomSource parent, long before, RandomSource fork) {
        var trace = CURRENT.get();
        if (trace != null)
            trace.add(parent, before, "fork", state(fork), fork.getClass().getName());
    }

    public static <T> Captured<T> capture(Supplier<T> action) {
        if (CURRENT.get() != null) throw new IllegalStateException("Nested ring entropy proof");
        var trace = new Trace();
        CURRENT.set(trace);
        try {
            return new Captured<>(action.get(), trace.finish());
        } finally {
            CURRENT.remove();
        }
    }

    private static long state(RandomSource random) {
        if (random.getClass() != LegacyRandomSource.class)
            throw new IllegalStateException(
                    "Unproven native ring random source " + random.getClass());
        return ((LegacyRingRandomAccess) random).mosaic$ringRandomState().get();
    }

    public record Captured<T>(T value, JsonObject trace) {}

    private static final class Trace {
        final JsonArray calls = new JsonArray();
        RandomSource parent;

        void add(RandomSource random, long before, String api, long value, String forkType) {
            if (parent == null) parent = random;
            if (parent != random)
                throw new IllegalStateException("Native ring changed shared parent");
            var row = new JsonObject();
            row.addProperty("api", api);
            row.addProperty("before", Long.toHexString(before));
            row.addProperty("after", Long.toHexString(state(parent)));
            row.addProperty("valueBits", Long.toHexString(value));
            if (forkType != null) row.addProperty("forkType", forkType);
            calls.add(row);
        }

        JsonObject finish() {
            if (parent == null || calls.size() == 0)
                throw new IllegalStateException("Missing ring random calls");
            var copy = new LegacyRandomSource(0);
            ((LegacyRingRandomAccess) copy).mosaic$ringRandomState().set(state(parent));
            var result = new JsonObject();
            result.add("calls", calls);
            result.addProperty("exit", Long.toHexString(state(parent)));
            result.addProperty("nextLong", Long.toHexString(copy.nextLong()));
            result.addProperty(
                    "nextDoubleBits",
                    Long.toHexString(Double.doubleToRawLongBits(copy.nextDouble())));
            result.addProperty("nextInt97", copy.nextInt(97));
            return result;
        }
    }
}
