package io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime;

import java.util.Objects;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldGenSettings;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;

public final class MosaicRuntimeContext {

    private final WorldGenSettings worldGenSettings;
    private final MosaicWorldProfile profile;
    private final MosaicSeedResolver seedResolver;

    public MosaicRuntimeContext(WorldGenSettings worldGenSettings, MosaicWorldProfile profile) {
        this.worldGenSettings = Objects.requireNonNull(worldGenSettings, "worldGenSettings");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.seedResolver = new MosaicSeedResolver(profile);
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    public long masterSeed() {
        return worldGenSettings.options().seed();
    }

    public long resolveLocalWorldSeed(ResourceKey<Level> dimension, ChunkPos chunkPos) {
        return seedResolver.resolveLocalWorldSeed(masterSeed(), dimension, chunkPos);
    }

    public MosaicSeedResolver seedResolver() {
        return seedResolver;
    }
}
