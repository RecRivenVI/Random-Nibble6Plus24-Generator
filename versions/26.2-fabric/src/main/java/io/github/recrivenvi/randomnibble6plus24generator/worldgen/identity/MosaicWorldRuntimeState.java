package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;

/**
 * One instance per server/save, never per Chunk or per dimension. Reads only
 * observe a published, validated snapshot. Missing, invalidated and closed are
 * deliberately different from a successfully validated Vanilla world.
 */
public final class MosaicWorldRuntimeState {
    private volatile Snapshot snapshot;
    private volatile boolean closed;

    public record ProfileEvidence(Optional<MosaicWorldProfile> profile, boolean filePresent) {
        public ProfileEvidence {
            Objects.requireNonNull(profile, "profile");
        }
    }

    public synchronized void validate(
            WorldGenSettings settings,
            Supplier<ProfileEvidence> readProfile) {
        if (closed) throw unavailable("server/save is closed");
        // Revalidation must never leave an old usable context behind on failure.
        snapshot = null;
        Objects.requireNonNull(settings, "settings");
        ProfileEvidence evidence = readProfile.get();
        Optional<MosaicWorldProfile> profile = MosaicIdentityValidator.validate(
                settings.dimensions(), evidence.profile(), evidence.filePresent());
        snapshot = new Snapshot(
                settings,
                Map.copyOf(settings.dimensions().dimensions()),
                profile.map(value -> new MosaicRuntimeContext(settings, value)));
    }

    public Optional<MosaicRuntimeContext> requireValidated(WorldGenSettings currentSettings) {
        Snapshot current = snapshot;
        if (current == null) throw unavailable(closed
                ? "server/save is closed"
                : "identity is unvalidated or has been invalidated; explicit validation is required");
        // WorldGenSettings is final in 26.2, but WorldDimensions exposes its map.
        // Detect explicit/in-place replacements without consulting storage.
        if (current.settings() != currentSettings
                || !current.dimensions().equals(currentSettings.dimensions().dimensions())) {
            invalidate();
            throw unavailable("world generation settings changed; explicit validation is required");
        }
        return current.context();
    }

    public synchronized void invalidate() {
        snapshot = null;
    }

    public void requireOpenDimension(ServerLevel level) {
        if (!(level instanceof MosaicLevelLifecycle lifecycle)
                || lifecycle.randomnibble6plus24generator$identityLevelClosed()
                || level.getServer().getLevel(level.dimension()) != level) {
            throw unavailable("closed or replaced dimension instance: " + level.dimension().identifier());
        }
    }

    public synchronized void close() {
        snapshot = null;
        closed = true;
    }

    private static MosaicIdentityValidationException unavailable(String reason) {
        return new MosaicIdentityValidationException(
                "Mosaic runtime identity unavailable; refusing generation/Vanilla fallback: " + reason);
    }

    private record Snapshot(
            WorldGenSettings settings,
            Map<ResourceKey<LevelStem>, LevelStem> dimensions,
            Optional<MosaicRuntimeContext> context) {
    }
}
