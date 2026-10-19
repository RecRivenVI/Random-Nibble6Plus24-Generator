package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OracleEnvironmentEncodingTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void exactCodecEncodingDoesNotCompareFloatObjectsWithParsedDoubleObjects() {
        var encoded = new JsonObject();
        encoded.addProperty("value", .1f);
        var parsed = JsonParser.parseString(encoded.toString());
        assertNotEquals(encoded, parsed);
        assertEquals(
                PreFeaturesVerifier.canonicalJson(encoded).toString(),
                PreFeaturesVerifier.canonicalJson(parsed).toString());
    }

    @Test
    void canonicalEncodingDoesNotRoundAdjacentLongsOrFloatValuesTogether() {
        var left = new JsonObject();
        var right = new JsonObject();
        left.addProperty("seed", Long.MAX_VALUE);
        right.addProperty("seed", Long.MAX_VALUE - 1);
        assertNotEquals(
                PreFeaturesVerifier.canonicalJson(left).toString(),
                PreFeaturesVerifier.canonicalJson(right).toString());
        left.addProperty("seed", .1f);
        right.addProperty("seed", Math.nextUp(.1f));
        assertNotEquals(
                PreFeaturesVerifier.canonicalJson(left).toString(),
                PreFeaturesVerifier.canonicalJson(right).toString());
    }
}
