package io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;

/** WorldOptions is the authority, not a dimension seed or transient generation session. */
public final class MosaicRuntimeContext {
    private final WorldOptions options;
    private final Object worldIdentity;
    private final MosaicWorldProfile profile;
    private final MosaicSeedResolver resolver;

    public MosaicRuntimeContext(
            Object worldIdentity, WorldOptions options, MosaicWorldProfile profile) {
        this.worldIdentity = Objects.requireNonNull(worldIdentity);
        this.options = Objects.requireNonNull(options);
        this.profile = Objects.requireNonNull(profile);
        this.resolver = new MosaicSeedResolver(profile);
    }

    public WorldOptions authority() {
        return options;
    }

    public boolean belongsTo(Object identity) {
        return worldIdentity == identity;
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    public long masterSeed() {
        return options.seed();
    }

    public long resolveLocalWorldSeed(ResourceKey<Level> dimension, ChunkPos position) {
        return resolver.resolveLocalWorldSeed(masterSeed(), dimension, position);
    }
}
