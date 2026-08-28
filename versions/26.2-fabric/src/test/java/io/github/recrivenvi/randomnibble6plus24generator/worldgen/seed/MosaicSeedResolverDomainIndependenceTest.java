package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

class MosaicSeedResolverDomainIndependenceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void frozenDomainIdentifiersAreDistinctAndUseTheApprovedNamespace() {
        assertEquals(
                "randomnibble6plus24generator:seed/authoritative_final/v1",
                MosaicSeedResolver.AUTHORITATIVE_FINAL_DOMAIN_V1.toString());
        assertEquals(
                "randomnibble6plus24generator:seed/presentation_preview/v1",
                MosaicSeedResolver.PRESENTATION_PREVIEW_DOMAIN_V1.toString());
        assertNotEquals(
                MosaicSeedResolver.AUTHORITATIVE_FINAL_DOMAIN_V1,
                MosaicSeedResolver.PRESENTATION_PREVIEW_DOMAIN_V1);
    }

    @Test
    void tenThousandPreviewQueriesDoNotChangeAuthoritativeResult() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        long masterSeed = -0x123456789ABCDEFL;
        ChunkPos chunkPos = new ChunkPos(125, -37);
        long before = resolver.resolveLocalWorldSeed(masterSeed, Level.OVERWORLD, chunkPos);

        for (long previewIndex = 0; previewIndex < 10_000; previewIndex++) {
            resolver.resolvePreviewSeed(masterSeed, Level.OVERWORLD, chunkPos, previewIndex);
        }

        assertEquals(before, resolver.resolveLocalWorldSeed(masterSeed, Level.OVERWORLD, chunkPos));
    }

    @Test
    void previewQueryOrderDoesNotChangePreviewOrAuthoritativeResults() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        long masterSeed = 0x1020304050607080L;
        ChunkPos chunkPos = new ChunkPos(-50_000, 75_000);
        long authoritative = resolver.resolveLocalWorldSeed(masterSeed, Level.END, chunkPos);
        Map<Long, Long> expected = new HashMap<>();
        List<Long> indices = new ArrayList<>();

        for (long i = 0; i < 1_024; i++) {
            indices.add(i);
            expected.put(i, resolver.resolvePreviewSeed(masterSeed, Level.END, chunkPos, i));
        }

        Collections.shuffle(indices, new Random(0x5052455649455751L));
        for (long index : indices) {
            assertEquals(
                    expected.get(index).longValue(),
                    resolver.resolvePreviewSeed(masterSeed, Level.END, chunkPos, index));
        }
        assertEquals(authoritative, resolver.resolveLocalWorldSeed(masterSeed, Level.END, chunkPos));
    }

    @Test
    @Timeout(30)
    void concurrentPreviewQueriesDoNotChangeAuthoritativeResult() throws Exception {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        long masterSeed = Long.MIN_VALUE;
        ChunkPos chunkPos = new ChunkPos(Integer.MAX_VALUE, Integer.MIN_VALUE);
        long authoritative = resolver.resolveLocalWorldSeed(masterSeed, Level.NETHER, chunkPos);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<Long>> previews = new ArrayList<>();
            for (long index = 0; index < 4_096; index++) {
                long previewIndex = index;
                previews.add(executor.submit(
                        () -> resolver.resolvePreviewSeed(masterSeed, Level.NETHER, chunkPos, previewIndex)));
            }
            for (Future<Long> preview : previews) {
                preview.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(authoritative, resolver.resolveLocalWorldSeed(masterSeed, Level.NETHER, chunkPos));
    }
}
