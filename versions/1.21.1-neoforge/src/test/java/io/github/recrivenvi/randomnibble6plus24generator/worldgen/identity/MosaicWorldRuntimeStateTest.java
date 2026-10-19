package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.junit.jupiter.api.Test;

class MosaicWorldRuntimeStateTest {
    private static MosaicRuntimeContext context(
            MosaicWorldRuntimeState state, WorldOptions options) {
        return state.newContext(
                options,
                new MosaicWorldProfile(
                        2,
                        1,
                        2,
                        1,
                        ResourceKey.create(
                                Registries.DIMENSION,
                                ResourceLocation.parse("minecraft:overworld"))));
    }

    @Test
    void noImplicitFallbackBeforeValidation() {
        var options = new WorldOptions(123, true, false);
        var state = new MosaicWorldRuntimeState();
        assertThrows(
                MosaicIdentityValidationException.class, () -> state.requireValidated(options));
        state.publish(options, Optional.empty());
        assertTrue(state.requireValidated(options).isEmpty());
    }

    @Test
    void manyQueriesKeepTheValidatedContext() {
        var options = new WorldOptions(123, true, false);
        var state = new MosaicWorldRuntimeState();
        var context = context(state, options);
        state.publish(options, Optional.of(context));
        for (int i = 0; i < 10000; i++)
            assertSame(context, state.requireValidated(options).orElseThrow());
        assertEquals(123, context.masterSeed());
    }

    @Test
    void replacementInvalidatesEvenWhenSeedMatches() {
        var options = new WorldOptions(123, true, false);
        var replacement = new WorldOptions(123, true, false);
        var state = new MosaicWorldRuntimeState();
        state.publish(options, Optional.of(context(state, options)));
        assertThrows(
                MosaicIdentityValidationException.class, () -> state.requireValidated(replacement));
        assertThrows(
                MosaicIdentityValidationException.class, () -> state.requireValidated(options));
        var next = context(state, replacement);
        state.publish(replacement, Optional.of(next));
        assertSame(next, state.requireValidated(replacement).orElseThrow());
    }

    @Test
    void closeAndRestartDoNotShareState() {
        var options = new WorldOptions(123, true, false);
        var old = new MosaicWorldRuntimeState();
        old.publish(options, Optional.of(context(old, options)));
        old.close();
        old.invalidate();
        assertThrows(
                MosaicIdentityValidationException.class,
                () -> old.publish(options, Optional.empty()));
        var restarted = new MosaicWorldRuntimeState();
        assertThrows(
                MosaicIdentityValidationException.class, () -> restarted.requireValidated(options));
        restarted.publish(options, Optional.empty());
        assertTrue(restarted.requireValidated(options).isEmpty());
    }

    @Test
    void crossWorldContextIsRejected() {
        var left = new WorldOptions(123, true, false);
        var right = new WorldOptions(124, true, false);
        var state = new MosaicWorldRuntimeState();
        var separate = new MosaicWorldRuntimeState();
        assertThrows(
                MosaicIdentityValidationException.class,
                () -> state.publish(left, Optional.of(context(separate, right))));
        assertThrows(
                MosaicIdentityValidationException.class,
                () -> state.publish(left, Optional.of(context(separate, left))));
        separate.publish(right, Optional.of(context(separate, right)));
        assertThrows(MosaicIdentityValidationException.class, () -> state.requireValidated(left));
        assertEquals(124, separate.requireValidated(right).orElseThrow().masterSeed());
    }
}
