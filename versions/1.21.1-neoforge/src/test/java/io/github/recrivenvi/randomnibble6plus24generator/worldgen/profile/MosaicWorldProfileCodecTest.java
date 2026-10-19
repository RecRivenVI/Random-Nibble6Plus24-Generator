package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MosaicWorldProfileCodecTest {
    @Test
    void bothPersistedOrderingsRoundTripWithoutChangingTheDefault() {
        for (var profile :
                new MosaicWorldProfile[] {
                    MosaicWorldProfile.current(), MosaicWorldProfile.targetOnly()
                }) {
            var encoded =
                    MosaicWorldProfile.CODEC
                            .encodeStart(JsonOps.INSTANCE, profile)
                            .result()
                            .orElseThrow();
            assertEquals(
                    profile,
                    MosaicWorldProfile.CODEC
                            .parse(JsonOps.INSTANCE, encoded)
                            .result()
                            .orElseThrow());
            var object = encoded.getAsJsonObject();
            assertEquals(2, object.get("format_version").getAsInt());
            assertEquals(1, object.get("seed_derivation_algorithm_version").getAsInt());
            assertEquals(1, object.get("presentation_algorithm_version").getAsInt());
            assertFalse(object.has("master_seed"));
            assertFalse(object.has("masterSeed"));
        }
        assertEquals(1, MosaicWorldProfile.current().featureOrderingAlgorithmVersion());
        assertEquals(2, MosaicWorldProfile.targetOnly().featureOrderingAlgorithmVersion());
    }

    @Test
    void unsupportedVersionsAndMissingOrderingRemainFailClosed() {
        for (var entry :
                Map.of(
                                "format_version",
                                1,
                                "seed_derivation_algorithm_version",
                                2,
                                "feature_ordering_algorithm_version",
                                3,
                                "presentation_algorithm_version",
                                2)
                        .entrySet()) {
            JsonObject object = encoded();
            object.addProperty(entry.getKey(), entry.getValue());
            assertTrue(
                    MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, object).error().isPresent(),
                    entry.getKey());
        }
        JsonObject object = encoded();
        object.remove("feature_ordering_algorithm_version");
        assertTrue(MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, object).error().isPresent());
    }

    private static JsonObject encoded() {
        return MosaicWorldProfile.CODEC
                .encodeStart(JsonOps.INSTANCE, MosaicWorldProfile.current())
                .result()
                .orElseThrow()
                .getAsJsonObject();
    }
}
