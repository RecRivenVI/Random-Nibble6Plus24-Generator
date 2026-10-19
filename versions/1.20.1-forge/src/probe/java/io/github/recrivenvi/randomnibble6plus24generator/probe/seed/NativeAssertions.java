package io.github.recrivenvi.randomnibble6plus24generator.probe.seed;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Objects;

/** Exact checks in the real Forge registry lifecycle, without a mock registry or loader. */
final class NativeAssertions {
    private static final JsonArray OBSERVATIONS = new JsonArray();
    private static final Gson JSON = new Gson();

    private NativeAssertions() {}

    static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "exact equality");
    }

    static void assertEquals(Object expected, Object actual, String label) {
        JsonObject observation = new JsonObject();
        observation.addProperty("check", label);
        observation.add("expected", JSON.toJsonTree(expected));
        observation.add("actual", JSON.toJsonTree(actual));
        OBSERVATIONS.add(observation);
        if (!Objects.equals(expected, actual))
            throw new AssertionError(label + ": expected " + expected + ", actual " + actual);
    }

    static void assertTrue(boolean actual) {
        assertEquals(true, actual);
    }

    static void assertTrue(boolean actual, String label) {
        assertEquals(true, actual, label);
    }

    static void assertFalse(boolean actual) {
        assertEquals(false, actual);
    }

    static JsonArray observations() {
        return OBSERVATIONS.deepCopy();
    }
}
