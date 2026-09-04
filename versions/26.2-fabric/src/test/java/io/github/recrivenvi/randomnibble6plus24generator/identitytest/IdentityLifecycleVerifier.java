package io.github.recrivenvi.randomnibble6plus24generator.identitytest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import com.google.gson.JsonObject;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicIdentityValidationException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;

public final class IdentityLifecycleVerifier {
    public static final AtomicLong PROFILE_PROBES = new AtomicLong();
    private static JsonObject pendingResult;

    public static void verify(MinecraftServer server) {
        boolean expectMosaic = Boolean.getBoolean("mosaic.identity.test.mosaic");
        var expected = MosaicWorldIdentity.runtimeContext(server);
        check(expected.isPresent() == expectMosaic, "world classification");
        long loadedProbes = PROFILE_PROBES.get();
        check(loadedProbes > 0 && loadedProbes <= 4, "load validation probe count=" + loadedProbes);
        var resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        for (var dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            var level = server.getLevel(dimension);
            check(level != null, "missing dimension " + dimension);
            for (int i = 0; i < 50_000; i++) {
                check(MosaicWorldIdentity.runtimeContext(level) == expected, "context not shared by dimensions");
                check(MosaicWorldIdentity.isMosaicWorld(level) == expectMosaic, "world predicate");
                check(MosaicWorldIdentity.isMosaicDimension(level) == expectMosaic, "dimension predicate");
            }
            if (expectMosaic) {
                var pos = new ChunkPos(125, -37);
                check(expected.orElseThrow().resolveLocalWorldSeed(dimension, pos) == resolver.resolveLocalWorldSeed(
                        server.getWorldGenSettings().options().seed(), dimension, pos), "dimension seed bridge");
            }
        }
        check(PROFILE_PROBES.get() == loadedProbes, "hot queries accessed filesystem");
        server.reloadResources(server.getPackRepository().getSelectedIds()).join();
        check(MosaicWorldIdentity.runtimeContext(server) == expected, "ordinary resource reload replaced world identity");
        check(PROFILE_PROBES.get() == loadedProbes, "ordinary resource reload polled profile identity");

        if (expectMosaic) {
            MosaicWorldProfile profile = expected.orElseThrow().profile();
            server.getDataStorage().set(MosaicWorldProfileData.TYPE, new MosaicWorldProfileData(profile));
            unavailable(() -> MosaicWorldIdentity.runtimeContext(server), "replacement did not invalidate");
            check(PROFILE_PROBES.get() == loadedProbes, "invalidated query accessed filesystem");
            MosaicWorldIdentity.revalidateServerIdentity(server);
            check(MosaicWorldIdentity.runtimeContext(server).orElseThrow() != expected.orElseThrow(), "replacement reused old context");

            var mismatch = new MosaicWorldProfile(2, 1, 1, 1, Level.NETHER);
            server.getDataStorage().set(MosaicWorldProfileData.TYPE, new MosaicWorldProfileData(mismatch));
            unavailable(() -> { MosaicWorldIdentity.revalidateServerIdentity(server); return Optional.empty(); }, "mismatch accepted");
            unavailable(() -> MosaicWorldIdentity.runtimeContext(server), "failed revalidation retained context");
            server.getDataStorage().set(MosaicWorldProfileData.TYPE, new MosaicWorldProfileData(profile));
            MosaicWorldIdentity.revalidateServerIdentity(server);
            server.getDataStorage().scheduleSave().join();
            var beforeReload = MosaicWorldIdentity.runtimeContext(server).orElseThrow();
            MosaicWorldIdentity.reloadProfileFromDisk(server);
            check(MosaicWorldIdentity.runtimeContext(server).orElseThrow() != beforeReload, "disk reload reused context");
        }

        JsonObject result = new JsonObject();
        result.addProperty("mosaic", expectMosaic);
        result.addProperty("loadProbes", loadedProbes);
        result.addProperty("hotQueries", 450_000);
        result.addProperty("hotFilesystemProbes", 0);
        result.addProperty("dimensions", 3);
        pendingResult = result;
    }

    public static void verifyAfterServerStopped(MinecraftServer server) {
        if (pendingResult == null) return;
        long beforeClose = PROFILE_PROBES.get();
        unavailable(() -> MosaicWorldIdentity.runtimeContext(server), "closed server accepted queries");
        for (var level : server.getAllLevels()) {
            unavailable(() -> MosaicWorldIdentity.runtimeContext(level), "closed world accepted dimension query");
            check(((io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicLevelLifecycle) level)
                    .randomnibble6plus24generator$identityLevelClosed(), "dimension close hook missing");
        }
        check(PROFILE_PROBES.get() == beforeClose, "closed query accessed filesystem");
        JsonObject result = pendingResult;
        result.addProperty("status", "PASS");
        result.addProperty("lifecycleProbes", PROFILE_PROBES.get());
        try {
            Path output = Path.of(System.getProperty("mosaic.identity.test.output"));
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, result.toString());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        System.out.println("MOSAIC IDENTITY LIFECYCLE PASS " + result);
    }

    private static void unavailable(java.util.function.Supplier<?> action, String message) {
        try { action.get(); } catch (MosaicIdentityValidationException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
