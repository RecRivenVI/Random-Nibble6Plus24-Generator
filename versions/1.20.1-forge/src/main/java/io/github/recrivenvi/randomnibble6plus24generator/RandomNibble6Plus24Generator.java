package io.github.recrivenvi.randomnibble6plus24generator;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.LoggerFactory;

@Mod(ModConstants.MOD_ID)
public final class RandomNibble6Plus24Generator {
    private static final net.minecraftforge.registries.DeferredRegister<
                    com.mojang.serialization.Codec<
                            ? extends net.minecraft.world.level.chunk.ChunkGenerator>>
            GENERATORS =
                    net.minecraftforge.registries.DeferredRegister.create(
                            net.minecraft.core.registries.Registries.CHUNK_GENERATOR,
                            ModConstants.MOD_ID);

    static {
        GENERATORS.register(
                "development_b",
                () ->
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                                .MosaicChunkGenerator.CODEC);
    }

    public RandomNibble6Plus24Generator() {
        GENERATORS.register(
                net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        // Native Forge posts this after stopServer, including its exceptional runServer paths.
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.HIGHEST, RandomNibble6Plus24Generator::stopped);
        LoggerFactory.getLogger(ModConstants.MOD_ID)
                .warn(
                        "Mosaic B codec registered; only matching hidden development identity is accepted");
    }

    private static void stopped(ServerStoppedEvent event) {
        try {
            // A real native failure can leave stopServer before its RETURN injection. Forge
            // still posts this event; drain owned work before invalidating its world authority.
            for (var level : event.getServer().getAllLevels()) {
                var owner =
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                .MosaicPhysicalLevelOwner.forLevel(level);
                var lifecycle = owner.randomnibble6plus24generator$generationLifecycle();
                if (lifecycle.active()) {
                    owner.mosaic$physicalState().beginClosing();
                    lifecycle.awaitClosedWorkers();
                }
            }
        } finally {
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity
                    .closeServerIdentity(event.getServer());
        }
    }
}
