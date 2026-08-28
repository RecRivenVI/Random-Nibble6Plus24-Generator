package io.github.recrivenvi.randomnibble6plus24generator.test;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

public final class MosaicTestWorlds {

    private static HolderLookup.Provider registries;

    private MosaicTestWorlds() {
    }

    public static synchronized HolderLookup.Provider registries() {
        MinecraftTestBootstrap.ensureBootstrapped();
        if (registries == null) {
            registries = VanillaRegistries.createLookup();
        }
        return registries;
    }

    public static WorldDimensions normalDimensions() {
        return WorldPresets.createNormalWorldDimensions(registries());
    }

    public static WorldDimensions dimensionsForPreset(ResourceKey<WorldPreset> presetKey) {
        return registries()
                .lookupOrThrow(Registries.WORLD_PRESET)
                .getOrThrow(presetKey)
                .value()
                .createWorldDimensions();
    }

    public static NoiseBasedChunkGenerator normalOverworldGenerator() {
        return (NoiseBasedChunkGenerator) normalDimensions().overworld();
    }

    public static MosaicChunkGenerator mosaicGenerator(MosaicWorldProfile profile) {
        NoiseBasedChunkGenerator normalGenerator = normalOverworldGenerator();
        return mosaicGenerator(normalGenerator, profile);
    }

    public static MosaicChunkGenerator mosaicGenerator(
            NoiseBasedChunkGenerator normalGenerator,
            MosaicWorldProfile profile) {
        return new MosaicChunkGenerator(
                normalGenerator.getBiomeSource(),
                normalGenerator.generatorSettings(),
                profile);
    }

    public static MosaicChunkGenerator serializableMosaicGenerator(MosaicWorldProfile profile) {
        Biome inlineBiome = new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.5F)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects(
                        0x3F76E4,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        BiomeSpecialEffects.GrassColorModifier.NONE))
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        return new MosaicChunkGenerator(
                new FixedBiomeSource(Holder.direct(inlineBiome)),
                Holder.direct(NoiseGeneratorSettings.dummy()),
                profile);
    }

    public static WorldGenSettings mosaicSettings(long masterSeed, MosaicWorldProfile profile) {
        java.util.Map<ResourceKey<LevelStem>, LevelStem> mosaicDimensions = new java.util.LinkedHashMap<>();
        for (var entry : normalDimensions().dimensions().entrySet()) {
            ChunkGenerator generator = entry.getValue().generator();
            if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
                throw new IllegalStateException("Expected vanilla noise generator for " + entry.getKey());
            }
            mosaicDimensions.put(
                    entry.getKey(),
                    new LevelStem(entry.getValue().type(), mosaicGenerator(noiseGenerator, profile)));
        }
        return new WorldGenSettings(
                new WorldOptions(masterSeed, true, false),
                new WorldDimensions(mosaicDimensions));
    }
}
