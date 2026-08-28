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
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        JsonElement encoded = MosaicWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
        JsonObject object = encoded.getAsJsonObject();

        assertEquals(1, object.get("format_version").getAsInt());
        assertEquals(1, object.get("seed_derivation_algorithm_version").getAsInt());
        assertEquals(1, object.get("presentation_algorithm_version").getAsInt());
        assertEquals("minecraft:overworld", object.get("primary_dimension").getAsString());
        assertEquals(profile, MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void savedDataCodecRoundTripsWithoutAMasterSeedField() {
        MosaicWorldProfileData data = new MosaicWorldProfileData(MosaicWorldProfile.version1());
        JsonElement encoded = MosaicWorldProfileData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();

        assertEquals(data.profile(), MosaicWorldProfileData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().profile());
        org.junit.jupiter.api.Assertions.assertFalse(encoded.getAsJsonObject().has("master_seed"));
        org.junit.jupiter.api.Assertions.assertFalse(encoded.getAsJsonObject().has("masterSeed"));
    }

    @Test
    void unsupportedFormatVersionFailsDuringDecode() {
        JsonObject encoded = encodedProfile();
        encoded.addProperty("format_version", 2);

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

    private static JsonObject encodedProfile() {
        return MosaicWorldProfile.CODEC
                .encodeStart(JsonOps.INSTANCE, MosaicWorldProfile.version1())
                .getOrThrow()
                .getAsJsonObject();
    }
}
