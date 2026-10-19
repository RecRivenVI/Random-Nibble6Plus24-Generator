package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.levelgen.WorldOptions;

/** Owned by one MinecraftServer instance; no filesystem polling or cross-world static cache. */
public final class MosaicWorldRuntimeState {
    private enum Phase {
        UNVALIDATED,
        VALIDATED,
        INVALID,
        CLOSED
    }

    private record Snapshot(
            Phase phase, WorldOptions authority, Optional<MosaicRuntimeContext> context) {}

    private volatile Snapshot state = new Snapshot(Phase.UNVALIDATED, null, Optional.empty());
    private final Object worldIdentity = new Object();

    public MosaicRuntimeContext newContext(WorldOptions authority, MosaicWorldProfile profile) {
        if (state.phase() == Phase.CLOSED)
            throw new MosaicIdentityValidationException("Closed world identity cannot reopen");
        return new MosaicRuntimeContext(worldIdentity, authority, profile);
    }

    public synchronized void publish(
            WorldOptions authority, Optional<MosaicRuntimeContext> context) {
        Objects.requireNonNull(authority);
        Objects.requireNonNull(context);
        if (state.phase() == Phase.CLOSED)
            throw new MosaicIdentityValidationException("Closed world identity cannot reopen");
        if (context.isPresent()
                && (context.orElseThrow().authority() != authority
                        || !context.orElseThrow().belongsTo(worldIdentity)))
            throw new MosaicIdentityValidationException(
                    "Runtime context belongs to another WorldOptions authority");
        state = new Snapshot(Phase.VALIDATED, authority, context);
    }

    public Optional<MosaicRuntimeContext> requireValidated(WorldOptions authority) {
        var observed = state;
        if (observed.phase() != Phase.VALIDATED)
            throw new MosaicIdentityValidationException("World identity is " + observed.phase());
        if (observed.authority() != authority) {
            invalidate();
            throw new MosaicIdentityValidationException(
                    "WorldOptions changed without lifecycle validation");
        }
        return observed.context();
    }

    public synchronized void invalidate() {
        if (state.phase() != Phase.CLOSED)
            state = new Snapshot(Phase.INVALID, null, Optional.empty());
    }

    public synchronized void close() {
        state = new Snapshot(Phase.CLOSED, null, Optional.empty());
    }
}
