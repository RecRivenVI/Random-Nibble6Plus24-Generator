package io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldGenSettings;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;

class MosaicRuntimeContextTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void worldOptionsSeedIsTheOnlyRuntimeMasterSeedAuthority() {
        long masterSeed = Long.MIN_VALUE;
        WorldGenSettings settings = MosaicTestWorlds.mosaicSettings(
                masterSeed,
                MosaicWorldProfile.current());
        MosaicRuntimeContext context = new MosaicRuntimeContext(settings, MosaicWorldProfile.current());

        assertEquals(settings.options().seed(), context.masterSeed());
        assertEquals(masterSeed, context.masterSeed());
    }

    @Test
    void runtimeBridgeMatchesPhaseOneAGoldenResolverSemantics() {
        long masterSeed = 123456789L;
        ChunkPos chunkPos = new ChunkPos(125, -37);
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        WorldGenSettings settings = MosaicTestWorlds.mosaicSettings(masterSeed, profile);
        MosaicRuntimeContext context = new MosaicRuntimeContext(settings, profile);
        MosaicSeedResolver directResolver = new MosaicSeedResolver(profile);

        assertEquals(
                -5161763991829980711L,
                context.resolveLocalWorldSeed(Level.OVERWORLD, chunkPos));
        assertEquals(
                directResolver.resolveLocalWorldSeed(masterSeed, Level.OVERWORLD, chunkPos),
                context.resolveLocalWorldSeed(Level.OVERWORLD, chunkPos));
    }

    @Test
    void runtimeBridgePreservesOriginContract() {
        long masterSeed = -987654321L;
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        MosaicRuntimeContext context = new MosaicRuntimeContext(
                MosaicTestWorlds.mosaicSettings(masterSeed, profile),
                profile);

        assertEquals(masterSeed, context.resolveLocalWorldSeed(Level.OVERWORLD, ChunkPos.ZERO));
    }
}
