package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.Level;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;

class MosaicWorldProfileCodecTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void profileCodecRoundTripsThePersistedSchema() {
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        JsonElement encoded = MosaicWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
        JsonObject object = encoded.getAsJsonObject();

        assertEquals(2, object.get("format_version").getAsInt());
        assertEquals(1, object.get("seed_derivation_algorithm_version").getAsInt());
        assertEquals(1, object.get("feature_ordering_algorithm_version").getAsInt());
        assertEquals(1, object.get("presentation_algorithm_version").getAsInt());
        assertEquals("minecraft:overworld", object.get("primary_dimension").getAsString());
        assertEquals(profile, MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void savedDataCodecRoundTripsWithoutAMasterSeedField() {
        MosaicWorldProfileData data = new MosaicWorldProfileData(MosaicWorldProfile.current());
        JsonElement encoded = MosaicWorldProfileData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();

        assertEquals(data.profile(), MosaicWorldProfileData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().profile());
        org.junit.jupiter.api.Assertions.assertFalse(encoded.getAsJsonObject().has("master_seed"));
        org.junit.jupiter.api.Assertions.assertFalse(encoded.getAsJsonObject().has("masterSeed"));
    }

    @Test
    void unsupportedFormatVersionFailsDuringDecode() {
        JsonObject encoded = encodedProfile();
        encoded.addProperty("format_version", 1);

        assertThrows(
                IllegalStateException.class,
                () -> MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void unsupportedSeedAlgorithmFailsDuringDecode() {
        JsonObject encoded = encodedProfile();
        encoded.addProperty("seed_derivation_algorithm_version", 2);

        assertThrows(
                IllegalStateException.class,
                () -> MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void unsupportedPresentationAlgorithmFailsDuringDecode() {
        JsonObject encoded = encodedProfile();
        encoded.addProperty("presentation_algorithm_version", 2);

        assertThrows(
                IllegalStateException.class,
                () -> MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void unsupportedFeatureOrderingAlgorithmFailsDuringDecode() {
        JsonObject encoded = encodedProfile();
        encoded.addProperty("feature_ordering_algorithm_version", 2);

        assertThrows(
                IllegalStateException.class,
                () -> MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void legacyFormatOneWithoutFeatureOrderingFieldFailsDuringDecode() {
        JsonObject encoded = encodedProfile();
        encoded.addProperty("format_version", 1);
        encoded.remove("feature_ordering_algorithm_version");

        assertThrows(
                IllegalStateException.class,
                () -> MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    private static JsonObject encodedProfile() {
        return MosaicWorldProfile.CODEC
                .encodeStart(JsonOps.INSTANCE, MosaicWorldProfile.current())
                .getOrThrow()
                .getAsJsonObject();
    }
}
