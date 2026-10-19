package io.github.recrivenvi.randomnibble6plus24generator.probe.seed;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicIdentityValidationException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldRuntimeState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;

/** Checks run after Forge's real registry bootstrap; they do not claim a physical FULL path. */
public final class RuntimeIdentityChecks {
    private RuntimeIdentityChecks() {}

    public static JsonObject run(MinecraftServer server) {
        if (!server.isSameThread())
            throw new IllegalStateException("Expected native server thread");
        var checks = new JsonArray();
        var dimensions =
                server.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);
        equal(
                checks,
                "native server exposes dimension generator registry",
                true,
                dimensions.size() >= 3);
        var options = server.getWorldData().worldGenOptions();
        var state = new MosaicWorldRuntimeState();
        rejected(checks, "unvalidated is fail-closed", () -> state.requireValidated(options));
        state.publish(options, Optional.empty());
        equal(
                checks,
                "validated Vanilla has no Mosaic authority",
                true,
                state.requireValidated(options).isEmpty());
        var profile = MosaicWorldProfile.targetOnly();
        var context = state.newContext(options, profile);
        state.publish(options, Optional.of(context));
        for (int index = 0; index < 10000; index++) {
            if (state.requireValidated(options).orElseThrow() != context)
                throw new AssertionError("Runtime context was recreated");
        }
        equal(
                checks,
                "ten thousand memory queries retain authority",
                true,
                state.requireValidated(options).orElseThrow() == context);
        equal(
                checks,
                "master seed is WorldOptions authority",
                options.seed(),
                context.masterSeed());
        equal(
                checks,
                "origin preservation",
                options.seed(),
                context.resolveLocalWorldSeed(Level.OVERWORLD, new ChunkPos(0, 0)));
        var location = new ChunkPos(125, -37);
        var seeds =
                java.util.stream.Stream.of(Level.OVERWORLD, Level.NETHER, Level.END)
                        .map(dimension -> context.resolveLocalWorldSeed(dimension, location))
                        .toList();
        equal(checks, "dimension domains stay separate", 3L, seeds.stream().distinct().count());
        var replacement = new WorldOptions(options.seed(), true, false);
        rejected(
                checks,
                "equal-seed replacement is not the same authority",
                () -> state.requireValidated(replacement));
        rejected(checks, "stale authority remains invalid", () -> state.requireValidated(options));
        var next = state.newContext(replacement, profile);
        state.publish(replacement, Optional.of(next));
        equal(
                checks,
                "explicit replacement publishes new authority",
                true,
                state.requireValidated(replacement).orElseThrow() == next);
        var other = new MosaicWorldRuntimeState();
        rejected(
                checks,
                "cross-world context rejected even at same seed",
                () -> other.publish(replacement, Optional.of(next)));
        state.close();
        state.invalidate();
        rejected(
                checks,
                "closed world cannot publish",
                () -> state.publish(replacement, Optional.empty()));
        rejected(
                checks,
                "closed world cannot create context",
                () -> state.newContext(replacement, profile));
        rejected(checks, "closed world cannot query", () -> state.requireValidated(replacement));
        var restarted = new MosaicWorldRuntimeState();
        rejected(checks, "restart is not prevalidated", () -> restarted.requireValidated(options));
        restarted.publish(options, Optional.empty());
        equal(
                checks,
                "restart may validate independent Vanilla world",
                true,
                restarted.requireValidated(options).isEmpty());
        var data =
                new io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile
                        .MosaicWorldProfileData(profile);
        equal(
                checks,
                "native SavedData NBT round-trip",
                profile,
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile
                        .MosaicWorldProfileData.load(data.save(new net.minecraft.nbt.CompoundTag()))
                        .profile());
        var proof = new JsonObject();
        proof.addProperty("scope", "OWNERSHIP_STATE_NATIVE_GAME_NOT_PRODUCTION_WORLD_LOAD");
        proof.addProperty("status", "PASS");
        proof.add("checks", checks);
        return proof;
    }

    private static void rejected(JsonArray checks, String name, Runnable action) {
        try {
            action.run();
        } catch (MosaicIdentityValidationException expected) {
            equal(checks, name, true, true);
            return;
        }
        throw new AssertionError(name + " did not reject");
    }

    private static void equal(JsonArray checks, String name, Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError(name + ": " + actual);
        var row = new JsonObject();
        row.addProperty("check", name);
        row.addProperty("exact", true);
        checks.add(row);
    }
}
