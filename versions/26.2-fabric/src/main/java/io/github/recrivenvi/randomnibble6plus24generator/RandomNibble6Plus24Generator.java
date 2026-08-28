package io.github.recrivenvi.randomnibble6plus24generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;

public final class RandomNibble6Plus24Generator implements ModInitializer {

    public static final String MOD_ID = "randomnibble6plus24generator";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                MosaicChunkGenerator.CODEC_ID,
                MosaicChunkGenerator.CODEC);
        if (!MosaicChunkGenerator.CODEC_ID.equals(
                BuiltInRegistries.CHUNK_GENERATOR.getKey(MosaicChunkGenerator.CODEC))) {
            throw new IllegalStateException("Mosaic chunk generator codec registration did not persist");
        }
        LOGGER.info("Initialized Random Nibble6Plus24 Generator runtime identity foundation");
    }
}
