package io.github.recrivenvi.randomnibble6plus24generator.probe.seed;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Actual native Vanilla load/replacement/close; no generator registration or FULL claim. */
public final class NativeIdentityLifecycleChecks {
    private static List<ServerLevel> levels;

    private NativeIdentityLifecycleChecks() {}

    public static JsonObject loaded(MinecraftServer server) {
        if (!server.isSameThread() || levels != null)
            throw new IllegalStateException("Unexpected native identity load boundary");
        levels =
                java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(), false)
                        .toList();
        var checks = new JsonArray();
        exact(checks, "three native dimensions", levels.size() == 3);
        exact(
                checks,
                "actual server validated as Vanilla",
                MosaicWorldIdentity.runtimeContext(server).isEmpty());
        for (var level : levels) {
            exact(
                    checks,
                    "actual dimension authority " + level.dimension().location(),
                    MosaicWorldIdentity.runtimeContext(level).isEmpty());
            exact(
                    checks,
                    "actual dimension is open " + level.dimension().location(),
                    !((MosaicLevelLifecycle) level)
                            .randomnibble6plus24generator$identityLevelClosed());
        }
        var path = MosaicWorldProfileData.path(server);
        exact(checks, "Vanilla load created no Mosaic profile", !Files.exists(path));
        // Only the disposable validation world's cache is changed; discard before any save.
        server.overworld()
                .getDataStorage()
                .set(
                        MosaicWorldProfileData.ID,
                        new MosaicWorldProfileData(MosaicWorldProfile.targetOnly()));
        rejected(
                checks,
                "native SavedData replacement invalidates authority",
                () -> MosaicWorldIdentity.runtimeContext(server));
        MosaicWorldIdentity.reloadProfileFromDisk(server);
        exact(
                checks,
                "explicit reload revalidates actual Vanilla world",
                MosaicWorldIdentity.runtimeContext(server).isEmpty());
        exact(checks, "discarded cache fixture was not written", !Files.exists(path));
        var proof = new JsonObject();
        proof.addProperty("scope", "NATIVE_VANILLA_WORLD_LOAD_REPLACE_CLOSE_NOT_MOSAIC_FULL");
        proof.addProperty("status", "PENDING_SHUTDOWN");
        proof.addProperty("dimensions", levels.size());
        proof.add("checks", checks);
        return proof;
    }

    public static void stopped(MinecraftServer server, JsonObject proof) {
        if (levels == null || !"PENDING_SHUTDOWN".equals(proof.get("status").getAsString()))
            throw new IllegalStateException("No pending actual identity lifecycle");
        var checks = proof.getAsJsonArray("checks");
        try {
            rejected(
                    checks,
                    "actual stopped server rejects authority",
                    () -> MosaicWorldIdentity.runtimeContext(server));
            for (var level : levels)
                exact(
                        checks,
                        "native level was closed " + level.dimension().location(),
                        ((MosaicLevelLifecycle) level)
                                .randomnibble6plus24generator$identityLevelClosed());
            proof.addProperty("status", "PASS");
        } finally {
            levels = null;
            proof.addProperty("retainedLevelReferences", 0);
        }
    }

    private static void rejected(JsonArray checks, String name, Runnable query) {
        try {
            query.run();
        } catch (MosaicIdentityValidationException expected) {
            exact(checks, name, true);
            return;
        }
        throw new AssertionError(name + " was not fail-closed");
    }

    private static void exact(JsonArray checks, String name, boolean matches) {
        if (!matches) throw new AssertionError(name);
        var row = new JsonObject();
        row.addProperty("check", name);
        row.addProperty("exact", true);
        checks.add(row);
    }
}
