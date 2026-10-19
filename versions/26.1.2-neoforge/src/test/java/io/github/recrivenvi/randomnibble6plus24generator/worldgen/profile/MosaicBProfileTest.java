package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

class MosaicBProfileTest {
    @Test
    void oldAndNewExplicitProfileRoundtripWithoutFormatUpgrade() {
        var old = profile(1);
        var explicit = profile(2);
        assertEquals(1, old.featureOrderingAlgorithmVersion());
        assertEquals(2, explicit.featureOrderingAlgorithmVersion());
        assertEquals(old.formatVersion(), explicit.formatVersion());
        assertEquals(
                old.seedDerivationAlgorithmVersion(), explicit.seedDerivationAlgorithmVersion());
        for (var profile : new MosaicWorldProfile[] {old, explicit}) {
            var json = MosaicWorldProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
            assertEquals(
                    profile, MosaicWorldProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }

    @Test
    void unsupportedOrderingStillFailsClosed() {
        var old = profile(1);
        var unknown =
                new MosaicWorldProfile(
                        old.formatVersion(),
                        old.seedDerivationAlgorithmVersion(),
                        3,
                        old.presentationAlgorithmVersion(),
                        old.primaryDimension());
        assertThrows(IllegalArgumentException.class, unknown::requireSupported);
    }

    private static MosaicWorldProfile profile(int ordering) {
        // Level's NeoForge attachment superclass requires an actual FML instance. Factories are
        // exercised by the game probe; this unit test checks the primitive format/Codec contract.
        return new MosaicWorldProfile(
                2,
                1,
                ordering,
                1,
                ResourceKey.create(
                        Registries.DIMENSION, Identifier.withDefaultNamespace("overworld")));
    }
}
