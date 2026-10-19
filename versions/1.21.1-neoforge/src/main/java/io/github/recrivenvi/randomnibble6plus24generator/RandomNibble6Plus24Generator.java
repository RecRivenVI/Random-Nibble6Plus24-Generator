package io.github.recrivenvi.randomnibble6plus24generator;

import com.mojang.serialization.MapCodec;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.LoggerFactory;

@Mod(ModConstants.MOD_ID)
public final class RandomNibble6Plus24Generator {
    public RandomNibble6Plus24Generator(IEventBus bus) {
        DeferredRegister<MapCodec<? extends ChunkGenerator>> generators =
                DeferredRegister.create(Registries.CHUNK_GENERATOR, ModConstants.MOD_ID);
        generators.register(
                MosaicChunkGenerator.CODEC_ID.getPath(), () -> MosaicChunkGenerator.CODEC);
        generators.register(bus);
        LoggerFactory.getLogger(ModConstants.MOD_ID)
                .warn(
                        "Registered opt-in Mosaic B codec; production lifecycle acceptance is pending");
    }
}
