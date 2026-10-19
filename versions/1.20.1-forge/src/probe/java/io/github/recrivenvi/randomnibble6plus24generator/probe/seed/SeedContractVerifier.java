package io.github.recrivenvi.randomnibble6plus24generator.probe.seed;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class SeedContractVerifier {
    private SeedContractVerifier() {}

    public static void run(MinecraftServer server) {
        if (!server.isSameThread())
            throw new IllegalStateException("Expected native server thread");
        JsonObject result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("target", System.getProperty("validation.target"));
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty(
                "save", server.getWorldPath(LevelResource.ROOT).toAbsolutePath().toString());
        result.addProperty("masterSeed", server.getWorldData().worldGenOptions().seed());
        result.addProperty("nativeLifecycle", "ServerStartedEvent");
        result.addProperty(
                "minecraft", net.minecraft.SharedConstants.getCurrentVersion().getName());
        result.addProperty("java", Runtime.version().feature());
        var mods = new JsonObject();
        net.minecraftforge.fml.ModList.get()
                .getMods()
                .forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
        result.add("mods", mods);
        try {
            if (Boolean.getBoolean("randomnibble6plus24generator.runtimeIdentity.enabled")) {
                result.addProperty(
                        "inputHash",
                        System.getProperty(
                                "randomnibble6plus24generator.runtimeIdentity.inputHash"));
                result.add("runtimeIdentity", RuntimeIdentityChecks.run(server));
            }
            new MosaicSeedResolverGoldenChecks().authoritativeV1GoldenVectorsRemainStable();
            var specification = new MosaicSeedResolverSpecificationChecks();
            specification.authoritativeResolverMatchesTheFrozenNativePrimitiveCallChain();
            specification.chunkPositionSamplingUsesWorldBlockOriginAndFixedZeroY();
            var codec = new MosaicWorldProfileCodecChecks();
            codec.bothPersistedOrderingsRoundTripWithoutChangingTheDefault();
            codec.unsupportedVersionsAndMissingOrderingRemainFailClosed();
            result.addProperty("status", "PASS");
            if (Boolean.getBoolean("randomnibble6plus24generator.runtimeIdentity.worldLoad")) {
                result.add("nativeIdentityLifecycle", NativeIdentityLifecycleChecks.loaded(server));
                result.addProperty("status", "PENDING_SHUTDOWN");
            }
        } catch (RuntimeException | Error failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            result.add("checks", NativeAssertions.observations());
            Path output =
                    Path.of(System.getProperty("validation.result"))
                            .resolve(
                                    Boolean.getBoolean(
                                                    "randomnibble6plus24generator.runtimeIdentity.enabled")
                                            ? System.getProperty(
                                                    "randomnibble6plus24generator.runtimeIdentity.variant")
                                            : "current")
                            .resolve(System.getProperty("validation.target"))
                            .resolve("native-seed.json");
            try {
                Files.createDirectories(output.getParent());
                Files.writeString(
                        output,
                        new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            server.halt(false);
        }
    }

    public static void stopped(MinecraftServer server) {
        if (!Boolean.getBoolean("randomnibble6plus24generator.runtimeIdentity.worldLoad")) return;
        Path output =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                System.getProperty(
                                        "randomnibble6plus24generator.runtimeIdentity.variant"))
                        .resolve(System.getProperty("validation.target"))
                        .resolve("native-seed.json");
        try {
            var result =
                    com.google.gson.JsonParser.parseString(Files.readString(output))
                            .getAsJsonObject();
            if (!"PENDING_SHUTDOWN".equals(result.get("status").getAsString())) return;
            try {
                NativeIdentityLifecycleChecks.stopped(
                        server, result.getAsJsonObject("nativeIdentityLifecycle"));
                result.addProperty("status", "PASS");
            } catch (RuntimeException | Error failure) {
                result.addProperty("status", "FAIL");
                result.addProperty("failure", failure.toString());
                throw failure;
            } finally {
                Files.writeString(
                        output,
                        new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
