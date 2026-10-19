package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.MultiNoiseBiomeSourceAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.NoiseGeneratorSettingsAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.fml.ModList;

/** Owns optional biome-library seed state without changing its algorithms or the host. */
public final class SeedLocalNoiseGenerator {
    private SeedLocalNoiseGenerator() {}

    public static NoiseBasedChunkGenerator copy(ServerLevel host, long seed) {
        var source = host.getChunkSource().getGenerator();
        BiomeSource biomes;
        Holder<NoiseGeneratorSettings> settings;
        if (source instanceof NoiseBasedChunkGenerator noise) {
            biomes = noise.getBiomeSource();
            settings = noise.generatorSettings();
        } else if (source instanceof MosaicChunkGenerator mosaic) {
            biomes = mosaic.getBiomeSource();
            settings = mosaic.generatorSettings();
        } else
            throw new IllegalArgumentException(
                    "Unsupported isolated generator " + source.getClass().getName());
        if (!ModList.get().isLoaded("terrablender"))
            return new NoiseBasedChunkGenerator(biomes, settings);

        // A codec round-trip of a preset can resolve the very same initialized parameter list.
        // Copy its native entries into a fresh list instead of cloning TerraBlender's seed state.
        if (biomes.getClass() == MultiNoiseBiomeSource.class) {
            var parameters =
                    ((MultiNoiseBiomeSourceAccessor) biomes)
                            .randomnibble6plus24generator$parameters();
            biomes =
                    MultiNoiseBiomeSource.createFromList(
                            new Climate.ParameterList<>(List.copyOf(parameters.values())));
        } else if (biomes.getClass() == TheEndBiomeSource.class) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, host.registryAccess());
            var encoded =
                    TheEndBiomeSource.CODEC
                            .codec()
                            .encodeStart(ops, (TheEndBiomeSource) biomes)
                            .getOrThrow();
            biomes = TheEndBiomeSource.CODEC.codec().parse(ops, encoded).getOrThrow();
        } else if (biomes.getClass() != FixedBiomeSource.class
                && biomes.getClass() != CheckerboardColumnBiomeSource.class)
            throw new IllegalArgumentException(
                    "Unaudited TerraBlender biome source " + biomes.getClass().getName());

        var s = settings.value();
        var access = (NoiseGeneratorSettingsAccessor) (Object) s;
        var ownedSettings =
                new NoiseGeneratorSettings(
                        s.noiseSettings(),
                        s.defaultBlock(),
                        s.defaultFluid(),
                        s.noiseRouter(),
                        access.randomnibble6plus24generator$rawMaterialRule(),
                        s.spawnTarget(),
                        s.seaLevel(),
                        access.randomnibble6plus24generator$rawDisableMobGeneration(),
                        s.aquifers(),
                        s.useLegacyRandomSource(),
                        s.debugFunctions());
        var generator = new NoiseBasedChunkGenerator(biomes, Holder.direct(ownedSettings));
        var stemKey = ResourceKey.create(Registries.LEVEL_STEM, host.dimension().identifier());
        var stem =
                host.registryAccess()
                        .lookupOrThrow(Registries.LEVEL_STEM)
                        .getOrThrow(stemKey)
                        .value();
        try {
            // Optional public TerraBlender lifecycle API; no classes are required without the mod.
            Class.forName("terrablender.util.LevelUtils")
                    .getMethod(
                            "initializeBiomes",
                            RegistryAccess.class,
                            Holder.class,
                            ResourceKey.class,
                            ChunkGenerator.class,
                            long.class)
                    .invoke(null, host.registryAccess(), stem.type(), stemKey, generator, seed);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(
                    "TerraBlender local-seed initialization failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported TerraBlender initialization API", failure);
        }
        return generator;
    }
}
