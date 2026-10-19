package io.github.recrivenvi.randomnibble6plus24generator.probe.features;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicIdentityValidationException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

/** Explicit probe of the native SavedData replacement and server-owned context boundaries. */
public final class WorldIdentityEvidence {
    private static MinecraftServer pending;
    private static Path output;

    private WorldIdentityEvidence() {}

    public static void verify(MinecraftServer server, Path directory) {
        if (!Boolean.getBoolean(ModConstants.MOD_ID + ".featureBInputs.identityLifecycle")) return;
        if (pending != null) throw new IllegalStateException("Duplicate identity lifecycle probe");
        var authority = server.getWorldData().worldGenOptions();
        for (int i = 0; i < 10000; i++)
            if (MosaicWorldIdentity.runtimeContext(server).isPresent())
                throw new IllegalStateException("Ordinary Oracle acquired Mosaic authority");
        int dimensions = 0;
        for (var level : server.getAllLevels()) {
            if (MosaicWorldIdentity.runtimeContext(level).isPresent())
                throw new IllegalStateException("Native dimension acquired Mosaic authority");
            dimensions++;
        }
        if (dimensions != 3 || Files.exists(MosaicWorldProfileData.path(server)))
            throw new IllegalStateException("Not a fresh ordinary three-dimension control");
        // The fixture changes only custom identity metadata in a disposable probe world,
        // never blocks, seed, generator, or the inputs to the subsequent B/Oracle comparison.
        server.overworld()
                .getDataStorage()
                .set(
                        MosaicWorldProfileData.ID,
                        new MosaicWorldProfileData(MosaicWorldProfile.targetOnly()));
        rejects(() -> MosaicWorldIdentity.runtimeContext(server));
        for (var level : server.getAllLevels())
            rejects(() -> MosaicWorldIdentity.runtimeContext(level));
        rejects(() -> MosaicWorldIdentity.initializeServerIdentity(server));
        MosaicWorldIdentity.reloadProfileFromDisk(server);
        if (server.getWorldData().worldGenOptions() != authority
                || MosaicWorldIdentity.runtimeContext(server).isPresent()
                || Files.exists(MosaicWorldProfileData.path(server)))
            throw new IllegalStateException("Explicit reload changed ordinary world authority");
        var proof = new JsonObject();
        proof.addProperty("status", "PASS");
        proof.addProperty("scope", "NATIVE_IDENTITY_BOUNDARY_NOT_MOSAIC_FULL");
        proof.addProperty("dimensionCount", dimensions);
        proof.addProperty("memoryQueries", 10000);
        proof.addProperty("replacementInvalidatedAllDimensions", true);
        proof.addProperty("mixedProfileRejected", true);
        proof.addProperty("explicitReloadRestoredVanilla", true);
        proof.addProperty("authorityUnchanged", true);
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("worldSeed", Long.toString(authority.seed()));
        try {
            Files.createDirectories(directory);
            NativeStructureVerifier.write(directory.resolve("identity.json"), proof);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        pending = server;
        output = directory;
    }

    public static void closed(MinecraftServer server) {
        if (server != pending) return;
        rejects(() -> MosaicWorldIdentity.runtimeContext(server));
        var proof = new JsonObject();
        proof.addProperty("status", "PASS");
        proof.addProperty("closedRejectsQueries", true);
        proof.addProperty("pid", ProcessHandle.current().pid());
        NativeStructureVerifier.write(output.resolve("identity-close.json"), proof);
        pending = null;
        output = null;
    }

    private static void rejects(Runnable query) {
        try {
            query.run();
        } catch (MosaicIdentityValidationException expected) {
            return;
        }
        throw new IllegalStateException("Invalid world authority was silently accepted");
    }
}
