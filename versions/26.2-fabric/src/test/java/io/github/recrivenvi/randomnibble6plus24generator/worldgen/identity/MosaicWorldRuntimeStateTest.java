package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;

class MosaicWorldRuntimeStateTest {
    @BeforeAll
    static void bootstrap() { MinecraftTestBootstrap.ensureBootstrapped(); }

    private static WorldGenSettings settings(long seed) {
        return MosaicTestWorlds.mosaicSettings(seed, MosaicWorldProfile.current());
    }

    private static MosaicWorldRuntimeState.ProfileEvidence mosaic() {
        return new MosaicWorldRuntimeState.ProfileEvidence(Optional.of(MosaicWorldProfile.current()), true);
    }

    @Test
    void unvalidatedDoesNotMeanVanilla() {
        MosaicWorldRuntimeState state = new MosaicWorldRuntimeState();
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(settings(1)));
    }

    @Test
    void twoHundredThousandHotReadsNeverRepeatProfileIoOrAllocateContexts() {
        MosaicWorldRuntimeState state = new MosaicWorldRuntimeState();
        WorldGenSettings settings = settings(123456789);
        AtomicInteger profileReads = new AtomicInteger();
        state.validate(settings, () -> { profileReads.incrementAndGet(); return mosaic(); });
        Optional<MosaicRuntimeContext> published = state.requireValidated(settings);
        for (int i = 0; i < 200_000; i++) assertSame(published, state.requireValidated(settings));
        assertEquals(1, profileReads.get());
        assertEquals(-5161763991829980711L, published.orElseThrow()
                .resolveLocalWorldSeed(Level.OVERWORLD, new ChunkPos(125, -37)));
    }

    @Test
    void validatedVanillaAbsenceIsCachedAndDoesNotBecomeMosaic() {
        MosaicWorldRuntimeState state = new MosaicWorldRuntimeState();
        WorldGenSettings settings = new WorldGenSettings(
                new net.minecraft.world.level.levelgen.WorldOptions(17, true, false),
                MosaicTestWorlds.normalDimensions());
        AtomicInteger reads = new AtomicInteger();
        state.validate(settings, () -> {
            reads.incrementAndGet();
            return new MosaicWorldRuntimeState.ProfileEvidence(Optional.empty(), false);
        });
        for (int i = 0; i < 20_000; i++) assertTrue(state.requireValidated(settings).isEmpty());
        assertEquals(1, reads.get());
    }

    @Test
    void serverInstancesDoNotShareSeedOrContextAndDimensionsRemainExplicit() {
        var a = new MosaicWorldRuntimeState();
        var b = new MosaicWorldRuntimeState();
        var sa = settings(1); var sb = settings(-1);
        a.validate(sa, MosaicWorldRuntimeStateTest::mosaic);
        b.validate(sb, MosaicWorldRuntimeStateTest::mosaic);
        var ca = a.requireValidated(sa).orElseThrow(); var cb = b.requireValidated(sb).orElseThrow();
        assertNotSame(ca, cb);
        var resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        for (var dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            var pos = new ChunkPos(125, -37);
            assertEquals(resolver.resolveLocalWorldSeed(1, dimension, pos), ca.resolveLocalWorldSeed(dimension, pos));
            assertEquals(resolver.resolveLocalWorldSeed(-1, dimension, pos), cb.resolveLocalWorldSeed(dimension, pos));
        }
        a.close();
        assertThrows(MosaicIdentityValidationException.class, () -> a.requireValidated(sa));
        assertSame(cb, b.requireValidated(sb).orElseThrow());
    }

    @Test
    void replacementInvalidatesUntilExplicitSuccessfulValidation() {
        var state = new MosaicWorldRuntimeState(); var settings = settings(0);
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        var old = state.requireValidated(settings).orElseThrow();
        state.invalidate();
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(settings));
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        assertNotSame(old, state.requireValidated(settings).orElseThrow());
    }

    @Test
    void corruptOrMissingRevalidationCannotRetainPreviousAuthority() {
        var state = new MosaicWorldRuntimeState(); var settings = settings(0);
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        assertThrows(MosaicIdentityValidationException.class, () -> state.validate(settings,
                () -> new MosaicWorldRuntimeState.ProfileEvidence(Optional.empty(), true)));
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(settings));
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        assertThrows(MosaicIdentityValidationException.class, () -> state.validate(settings,
                () -> new MosaicWorldRuntimeState.ProfileEvidence(Optional.empty(), false)));
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(settings));
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        assertThrows(UncheckedIOException.class, () -> state.validate(settings, () -> {
            throw new UncheckedIOException(new IOException("profile read failed"));
        }));
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(settings));
    }

    @Test
    void profileMismatchFailsClosed() {
        var state = new MosaicWorldRuntimeState(); var settings = settings(0);
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        var other = new MosaicWorldProfile(2, 1, 1, 1, Level.NETHER);
        assertThrows(MosaicIdentityValidationException.class, () -> state.validate(settings,
                () -> new MosaicWorldRuntimeState.ProfileEvidence(Optional.of(other), true)));
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(settings));
    }

    @Test
    void changedWorldSettingsOrDimensionMapCannotUseOldAuthority() {
        var state = new MosaicWorldRuntimeState(); var a = settings(1); var b = settings(2);
        state.validate(a, MosaicWorldRuntimeStateTest::mosaic);
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(b));
        state.validate(a, MosaicWorldRuntimeStateTest::mosaic);
        a.dimensions().dimensions().put(LevelStem.NETHER,
                MosaicTestWorlds.normalDimensions().dimensions().get(LevelStem.NETHER));
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(a));
    }

    @Test
    void closedOwnerCannotBeResurrectedButRestartGetsNewValidation() {
        var old = new MosaicWorldRuntimeState(); var settings = settings(123);
        AtomicInteger reads = new AtomicInteger();
        old.validate(settings, () -> { reads.incrementAndGet(); return mosaic(); });
        old.close();
        assertThrows(MosaicIdentityValidationException.class,
                () -> old.validate(settings, () -> { reads.incrementAndGet(); return mosaic(); }));
        var restarted = new MosaicWorldRuntimeState();
        restarted.validate(settings, () -> { reads.incrementAndGet(); return mosaic(); });
        assertEquals(2, reads.get());
        assertEquals(123, restarted.requireValidated(settings).orElseThrow().masterSeed());
    }

    @Test
    void parallelReadsShareOnlyThePublishedImmutableContext() throws Exception {
        var state = new MosaicWorldRuntimeState(); var settings = settings(123456789);
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        var expected = state.requireValidated(settings).orElseThrow();
        try (var workers = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 8; worker++) futures.add(workers.submit(() -> {
                for (int i = 0; i < 10_000; i++) assertSame(expected, state.requireValidated(settings).orElseThrow());
            }));
            for (var future : futures) future.get();
        }
    }

    @Test
    void validationInProgressDoesNotExposeStaleContext() throws Exception {
        var state = new MosaicWorldRuntimeState(); var settings = settings(123);
        state.validate(settings, MosaicWorldRuntimeStateTest::mosaic);
        var old = state.requireValidated(settings).orElseThrow();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var validation = worker.submit(() -> state.validate(settings, () -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("validation release timeout");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                return mosaic();
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(settings));
            } finally { release.countDown(); }
            validation.get();
        }
        assertNotSame(old, state.requireValidated(settings).orElseThrow());
    }
}
