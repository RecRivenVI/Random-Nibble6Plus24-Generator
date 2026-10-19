package io.github.recrivenvi.randomnibble6plus24generator;

import com.mojang.serialization.MapCodec;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ModConstants.MOD_ID)
public final class RandomNibble6Plus24Generator {

    public static final String MOD_ID = ModConstants.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RandomNibble6Plus24Generator(IEventBus modBus) {
        DeferredRegister<MapCodec<? extends ChunkGenerator>> generators =
                DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);
        generators.register(
                MosaicChunkGenerator.CODEC_ID.getPath(), () -> MosaicChunkGenerator.CODEC);
        generators.register(
                MosaicChunkGenerator.DEVELOPMENT_B_CODEC_ID.getPath(),
                () -> MosaicChunkGenerator.DEVELOPMENT_B_CODEC);
        generators.register(modBus);
        LOGGER.info("Registered Mosaic reference codec for NeoForge; target port is unverified");
    }
}
