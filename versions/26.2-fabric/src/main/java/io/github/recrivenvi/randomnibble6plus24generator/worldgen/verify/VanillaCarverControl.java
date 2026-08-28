package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.CarverGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SurfaceGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SurfaceGenerationMode;

/** Direct-world-seed CARVERS control path that does not use MosaicSeedResolver. */
public final class VanillaCarverControl {

    public CarverGenerationRun generateCarvers(
            ServerLevel hostLevel,
            long vanillaWorldSeed,
            ChunkPos target) {
        try (SurfaceGenerationContext context = SurfaceGenerationContext.create(
                SurfaceGenerationMode.VANILLA_CONTROL,
                hostLevel,
                vanillaWorldSeed,
                target)) {
            return context.generateCarvers();
        }
    }
}
