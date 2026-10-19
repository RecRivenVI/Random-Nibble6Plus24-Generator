package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

/**
 * Boundary for this Target's {@link net.minecraft.server.MinecraftServer}. The returned state owns
 * native {@link net.minecraft.world.level.levelgen.WorldOptions} identity, not a game-independent
 * component contract.
 */
public interface MosaicRuntimeContextOwner {
    MosaicWorldRuntimeState randomnibble6plus24generator$worldIdentity();
}
