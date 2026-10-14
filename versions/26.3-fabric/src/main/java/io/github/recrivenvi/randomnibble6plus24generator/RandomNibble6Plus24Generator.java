package io.github.recrivenvi.randomnibble6plus24generator;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RandomNibble6Plus24Generator implements ModInitializer {

    public static final String MOD_ID = ModConstants.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                MosaicChunkGenerator.CODEC_ID,
                MosaicChunkGenerator.CODEC);
        Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                MosaicChunkGenerator.DEVELOPMENT_B_CODEC_ID,
                MosaicChunkGenerator.DEVELOPMENT_B_CODEC);
        if (!MosaicChunkGenerator.CODEC_ID.equals(
                BuiltInRegistries.CHUNK_GENERATOR.getKey(MosaicChunkGenerator.CODEC))) {
            throw new IllegalStateException(
                    "Mosaic chunk generator codec registration did not persist");
        }
        LOGGER.info("Initialized Random Nibble6Plus24 Generator runtime identity foundation");
    }
}
