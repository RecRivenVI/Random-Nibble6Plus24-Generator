package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;

public final class IsolatedGenerationSession {

    private final MosaicWorldProfile profile;
    private final MosaicSeedResolver seedResolver;

    public IsolatedGenerationSession(MosaicWorldProfile profile) {
        this.profile = profile;
        this.seedResolver = new MosaicSeedResolver(profile);
    }

    public SurfaceGenerationRun generateSurface(
            ServerLevel hostLevel,
            long masterSeed,
            ChunkPos target) {
        long localWorldSeed = seedResolver.resolveLocalWorldSeed(
                masterSeed,
                hostLevel.dimension(),
                target);
        try (SurfaceGenerationContext context = SurfaceGenerationContext.create(
                SurfaceGenerationMode.ISOLATED_MOSAIC,
                hostLevel,
                localWorldSeed,
                target)) {
            return context.generate();
        }
    }

    public CarverGenerationRun generateCarvers(
            ServerLevel hostLevel,
            long masterSeed,
            ChunkPos target) {
        long localWorldSeed = seedResolver.resolveLocalWorldSeed(
                masterSeed,
                hostLevel.dimension(),
                target);
        try (SurfaceGenerationContext context = SurfaceGenerationContext.create(
                SurfaceGenerationMode.ISOLATED_MOSAIC,
                hostLevel,
                localWorldSeed,
                target)) {
            return context.generateCarvers();
        }
    }

    public MosaicWorldProfile profile() {
        return profile;
    }
}
