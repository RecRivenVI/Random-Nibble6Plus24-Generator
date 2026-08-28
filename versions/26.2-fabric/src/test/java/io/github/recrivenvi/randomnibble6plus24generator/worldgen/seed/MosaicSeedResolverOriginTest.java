package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

class MosaicSeedResolverOriginTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void protectedOriginReturnsMasterSeedBitForBit() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.version1());
        long[] masterSeeds = {
            0L,
            1L,
            -1L,
            123456789L,
            -987654321L,
            Long.MIN_VALUE,
            Long.MAX_VALUE
        };

        for (long masterSeed : masterSeeds) {
            assertEquals(
                    masterSeed,
                    resolver.resolveLocalWorldSeed(masterSeed, Level.OVERWORLD, ChunkPos.ZERO));
        }
    }

    @Test
    void protectedOriginHoldsForManyDeterministicRandomMasterSeeds() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.version1());
        SplittableRandom inputs = new SplittableRandom(0x4F524947494E5F31L);

        for (int i = 0; i < 10_000; i++) {
            long masterSeed = inputs.nextLong();
            assertEquals(
                    masterSeed,
                    resolver.resolveLocalWorldSeed(masterSeed, Level.OVERWORLD, ChunkPos.ZERO));
        }
    }

    @Test
    void originPreservationAppliesOnlyToThePrimaryDimension() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.version1());
        long masterSeed = 123456789L;

        assertEquals(masterSeed, resolver.resolveLocalWorldSeed(masterSeed, Level.OVERWORLD, ChunkPos.ZERO));
        assertEquals(
                183755513761836264L,
                resolver.resolveLocalWorldSeed(masterSeed, Level.NETHER, ChunkPos.ZERO));
    }
}
