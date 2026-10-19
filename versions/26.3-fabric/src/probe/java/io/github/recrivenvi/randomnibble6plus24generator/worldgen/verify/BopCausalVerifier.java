package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ProtoChunk;

/** Separate causal evidence, never overwrites or waives the original strict B failures. */
final class BopCausalVerifier {
    private BopCausalVerifier() {}

    static void run(MinecraftServer server) {
        var name = System.getProperty(BopCausalTrace.PROPERTY + ".run");
        if (name == null || !name.matches("[a-z0-9_]+"))
            throw new IllegalArgumentException("Invalid causal run");
        var output =
                Path.of(System.getProperty("validation.result"))
                        .resolve("causal_end")
                        .resolve(name);
        var level = Objects.requireNonNull(server.getLevel(Level.END));
        var target = new ChunkPos(112, -512);
        boolean nativeRole = name.startsWith("native");
        long host = server.getWorldGenSettings().options().seed();
        if (nativeRole != (host == 0)) throw new IllegalStateException("Wrong causal host");
        var result = new JsonObject();
        result.addProperty("run", name);
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("javaRuntime", System.getProperty("java.runtime.version"));
        result.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        result.addProperty("hostSeed", host);
        result.addProperty("localSeed", 0);
        result.addProperty("inputHash", System.getProperty(BopCausalTrace.PROPERTY + ".inputHash"));
        result.addProperty(
                "save",
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .toString());
        result.add("environment", PreFeaturesVerifier.environment(level));
        int loaded = level.getChunkSource().getLoadedChunksCount();
        int blocks = level.getBlockTicks().count(), fluids = level.getFluidTicks().count();
        long entities =
                java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                        .count();
        try (var trace = BopCausalTrace.open(level, output)) {
            ProtoChunk chunk;
            Map<String, Long> rejected;
            if (nativeRole) {
                var generated =
                        NativeFeatureBControl.generate(
                                level,
                                target,
                                () -> trace.prepare(level.getChunkSource().getGenerator()));
                chunk = generated.target();
                rejected = generated.rejected();
                result.addProperty("terrainHash", generated.terrainHash());
            } else {
                try (var context =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                level,
                                0,
                                target,
                                MosaicWorldProfile.targetOnly())) {
                    var generated =
                            LightweightFeatureBaseline.generateWithStructureReuse(
                                    context,
                                    new LocalFeatureInputs.Observer() {},
                                    (region, inputs) -> {
                                        trace.prepare(context.generator());
                                    });
                    chunk = generated.target();
                    rejected = generated.rejected();
                }
                if (loaded != level.getChunkSource().getLoadedChunksCount()
                        || blocks != level.getBlockTicks().count()
                        || fluids != level.getFluidTicks().count()
                        || entities
                                != java.util.stream.StreamSupport.stream(
                                                level.getAllEntities().spliterator(), false)
                                        .count())
                    throw new IllegalStateException("Causal isolated host mutation");
                result.addProperty("physicalHostUnchanged", true);
            }
            result.addProperty("terrainHash", trace.terrainHash());
            trace.verifyFinal(chunk);
            var snapshot =
                    FeatureStableSnapshot.capture(
                            "minecraft:the_end", chunk, level.registryAccess());
            snapshot.write(output.resolve("features.bin.gz"));
            result.addProperty("hash", snapshot.hash());
            result.addProperty("entities", snapshot.entityCount());
            result.addProperty("blockEntities", snapshot.blockEntityCount());
            result.addProperty("blockTicks", snapshot.blockTickCount());
            result.addProperty("fluidTicks", snapshot.fluidTickCount());
            result.addProperty("postProcessing", snapshot.postProcessingCount());
            result.add("rawEntityNbt", new Gson().toJsonTree(snapshot.rawEntityNbt()));
            result.add("rejected", new Gson().toJsonTree(new TreeMap<>(rejected)));
            if (GenerationContextRegistry.bindingCount() != 0)
                throw new IllegalStateException("Causal session leak");
            result.addProperty("status", "CAPTURED");
        } catch (Exception | Error failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("failure", failure.toString());
            throw new IllegalStateException("BOP causal capture failed", failure);
        } finally {
            PreFeaturesVerifier.write(output.resolve("summary.json"), result);
            server.halt(false);
        }
    }
}
