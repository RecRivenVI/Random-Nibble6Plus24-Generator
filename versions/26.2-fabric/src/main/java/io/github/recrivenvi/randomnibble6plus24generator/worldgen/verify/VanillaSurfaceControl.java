package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SurfaceGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SurfaceGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SurfaceGenerationRun;

/** Direct-world-seed control path that does not use MosaicSeedResolver. */
public final class VanillaSurfaceControl {

    public SurfaceGenerationRun generateSurface(
            ServerLevel hostLevel,
            long vanillaWorldSeed,
            ChunkPos target) {
        try (SurfaceGenerationContext context = SurfaceGenerationContext.create(
                SurfaceGenerationMode.VANILLA_CONTROL,
                hostLevel,
                vanillaWorldSeed,
                target)) {
            return context.generate();
        }
    }
}
