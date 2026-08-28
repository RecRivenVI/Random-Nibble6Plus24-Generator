package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.Level;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;

class MosaicWorldProfileTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void versionOneUsesTheFrozenSeedCoreVersionsAndOverworldPrimaryDimension() {
        MosaicWorldProfile profile = MosaicWorldProfile.version1();

        assertEquals(1, profile.formatVersion());
        assertEquals(1, profile.seedDerivationAlgorithmVersion());
        assertEquals(1, profile.presentationAlgorithmVersion());
        assertEquals(Level.OVERWORLD, profile.primaryDimension());
    }

    @Test
    void profileDoesNotContainASecondMasterSeedAuthority() {
        assertFalse(Arrays.stream(MosaicWorldProfile.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("masterSeed")));
    }

    @Test
    void profileRejectsNonPositiveVersionNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new MosaicWorldProfile(0, 1, 1, Level.OVERWORLD));
        assertThrows(IllegalArgumentException.class, () -> new MosaicWorldProfile(1, 0, 1, Level.OVERWORLD));
        assertThrows(IllegalArgumentException.class, () -> new MosaicWorldProfile(1, 1, 0, Level.OVERWORLD));
    }

    @Test
    void resolverRejectsUnsupportedVersions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MosaicSeedResolver(new MosaicWorldProfile(2, 1, 1, Level.OVERWORLD)));

        MosaicSeedResolver unsupportedSeedAlgorithm = new MosaicSeedResolver(
                new MosaicWorldProfile(1, 2, 1, Level.OVERWORLD));
        assertThrows(
                IllegalArgumentException.class,
                () -> unsupportedSeedAlgorithm.resolveLocalWorldSeed(0L, Level.OVERWORLD, new net.minecraft.world.level.ChunkPos(1, 0)));

        MosaicSeedResolver unsupportedPresentationAlgorithm = new MosaicSeedResolver(
                new MosaicWorldProfile(1, 1, 2, Level.OVERWORLD));
        assertThrows(
                IllegalArgumentException.class,
                () -> unsupportedPresentationAlgorithm.resolvePreviewSeed(0L, Level.OVERWORLD, net.minecraft.world.level.ChunkPos.ZERO, 0L));
    }
}
