package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

class MosaicIdentityValidatorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void matchingGeneratorAndPersistedProfileProduceMosaicIdentity() {
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        WorldDimensions dimensions = MosaicTestWorlds.mosaicSettings(123L, profile).dimensions();

        assertEquals(
                Optional.of(profile),
                MosaicIdentityValidator.validate(dimensions, Optional.of(profile), true));
    }

    @Test
    void generatorWithoutProfileFailsClosed() {
        WorldDimensions dimensions = MosaicTestWorlds.mosaicSettings(
                123L,
                MosaicWorldProfile.version1()).dimensions();

        MosaicIdentityValidationException exception = assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicIdentityValidator.validate(dimensions, Optional.empty(), false));
        assertTrue(exception.getMessage().contains("metadata is missing"));
        assertTrue(exception.getMessage().contains("refusing Vanilla fallback"));
    }

    @Test
    void profileWithoutGeneratorFailsClosed() {
        MosaicIdentityValidationException exception = assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicIdentityValidator.validate(
                        MosaicTestWorlds.normalDimensions(),
                        Optional.of(MosaicWorldProfile.version1()),
                        true));
        assertTrue(exception.getMessage().contains("no serialized Mosaic generator"));
    }

    @Test
    void unreadableProfileFileMarkerWithoutDecodedProfileFailsClosed() {
        MosaicIdentityValidationException exception = assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicIdentityValidator.validate(
                        MosaicTestWorlds.normalDimensions(),
                        Optional.empty(),
                        true));
        assertTrue(exception.getMessage().contains("profile metadata exists"));
    }

    @Test
    void mismatchedGeneratorAndPersistedProfilesFailClosed() {
        MosaicWorldProfile generatorProfile = MosaicWorldProfile.version1();
        MosaicWorldProfile persistedProfile = new MosaicWorldProfile(1, 1, 1, Level.NETHER);
        WorldDimensions dimensions = MosaicTestWorlds.mosaicSettings(123L, generatorProfile).dimensions();

        MosaicIdentityValidationException exception = assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicIdentityValidator.validate(dimensions, Optional.of(persistedProfile), true));
        assertTrue(exception.getMessage().contains("generator/profile mismatch"));
    }

    @Test
    void primaryDimensionMustContainAMosaicGenerator() {
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        WorldDimensions normal = MosaicTestWorlds.normalDimensions();
        Map<ResourceKey<LevelStem>, LevelStem> dimensions = new LinkedHashMap<>(normal.dimensions());
        LevelStem nether = dimensions.get(LevelStem.NETHER);
        dimensions.put(
                LevelStem.NETHER,
                new LevelStem(nether.type(), MosaicTestWorlds.mosaicGenerator(profile)));

        MosaicIdentityValidationException exception = assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicIdentityValidator.validate(
                        new WorldDimensions(dimensions),
                        Optional.of(profile),
                        true));
        assertTrue(exception.getMessage().contains("ALL_DIMENSIONS_MOSAIC"));
    }

    @Test
    void serializedMosaicGeneratorsMustAgreeOnProfile() {
        MosaicWorldProfile overworldProfile = MosaicWorldProfile.version1();
        MosaicWorldProfile netherProfile = new MosaicWorldProfile(1, 1, 1, Level.NETHER);
        WorldDimensions normal = MosaicTestWorlds.normalDimensions();
        Map<ResourceKey<LevelStem>, LevelStem> dimensions = new LinkedHashMap<>(normal.dimensions());
        LevelStem overworld = dimensions.get(LevelStem.OVERWORLD);
        LevelStem nether = dimensions.get(LevelStem.NETHER);
        LevelStem end = dimensions.get(LevelStem.END);
        dimensions.put(
                LevelStem.OVERWORLD,
                new LevelStem(overworld.type(), MosaicTestWorlds.mosaicGenerator(overworldProfile)));
        dimensions.put(
                LevelStem.NETHER,
                new LevelStem(nether.type(), MosaicTestWorlds.mosaicGenerator(netherProfile)));
        dimensions.put(
                LevelStem.END,
                new LevelStem(end.type(), MosaicTestWorlds.mosaicGenerator(overworldProfile)));

        MosaicIdentityValidationException exception = assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicIdentityValidator.validate(
                        new WorldDimensions(dimensions),
                        Optional.of(overworldProfile),
                        true));
        assertTrue(exception.getMessage().contains("disagree"));
    }

    @Test
    void anyMixedGeneratorMosaicWorldFailsClosed() {
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        WorldDimensions normal = MosaicTestWorlds.normalDimensions();
        Map<ResourceKey<LevelStem>, LevelStem> dimensions = new LinkedHashMap<>(normal.dimensions());
        LevelStem overworld = dimensions.get(LevelStem.OVERWORLD);
        dimensions.put(
                LevelStem.OVERWORLD,
                new LevelStem(overworld.type(), MosaicTestWorlds.mosaicGenerator(profile)));

        MosaicIdentityValidationException exception = assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicIdentityValidator.validate(
                        new WorldDimensions(dimensions),
                        Optional.of(profile),
                        true));
        assertTrue(exception.getMessage().contains("ALL_DIMENSIONS_MOSAIC"));
        assertTrue(exception.getMessage().contains("minecraft:the_nether"));
        assertTrue(exception.getMessage().contains("minecraft:the_end"));
    }

    @Test
    void vanillaGeneratorsAreNeverDetectedAsMosaic() {
        assertFalse(MosaicIdentityValidator.validate(
                MosaicTestWorlds.normalDimensions(), Optional.empty(), false).isPresent());
        assertFalse(MosaicIdentityValidator.validate(
                MosaicTestWorlds.dimensionsForPreset(WorldPresets.FLAT), Optional.empty(), false).isPresent());
        assertFalse(MosaicIdentityValidator.validate(
                MosaicTestWorlds.dimensionsForPreset(WorldPresets.DEBUG), Optional.empty(), false).isPresent());
    }
}
