package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BDevelopmentWorld;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** Ordinary Vanilla generation/save/reload with zero Mosaic production work. No stage driver. */
public final class VanillaLifecycleEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static boolean started;

    private VanillaLifecycleEvidence() {}

    public static void run(MinecraftServer server) {
        if (started) return;
        started = true;
        if (!server.isSameThread() || MosaicWorldIdentity.isMosaicWorld(server))
            throw new IllegalStateException("Vanilla lifecycle requires a real ordinary host");
        var level =
                server.getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION,
                                Identifier.parse(System.getProperty(PREFIX + "dimension"))));
        if (level == null) throw new IllegalStateException("Missing Vanilla dimension");
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator))
            throw new IllegalStateException("Vanilla control requires the native noise generator");
        var position =
                new ChunkPos(
                        Integer.getInteger(PREFIX + "chunkX"),
                        Integer.getInteger(PREFIX + "chunkZ"));
        var future =
                level.getChunkSource()
                        .getChunkFuture(position.x(), position.z(), ChunkStatus.FULL, true);
        server.managedBlock(future::isDone);
        var returned =
                future.join().orElseThrow(() -> new IllegalStateException("Native FULL missing"));
        if (!(returned instanceof LevelChunk chunk)
                || chunk.getPersistedStatus() != ChunkStatus.FULL
                || !chunk.isLightCorrect())
            throw new IllegalStateException("Native FULL lifecycle did not finish");
        var marker =
                new BlockPos(
                        position.getMiddleBlockX(),
                        level.getMinY() + 8,
                        position.getMiddleBlockZ());
        if (Boolean.getBoolean(PREFIX + "expectMarker")
                && !level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK))
            throw new IllegalStateException("Vanilla reload lost its test modification");
        if (Boolean.getBoolean(PREFIX + "mutate"))
            level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        var metrics = MosaicPhysicalMaterializer.metrics();
        var trace = PhysicalMosaicTrace.snapshot();
        if (metrics.artifactCaptureCount() != 0
                || metrics.isolatedGenerationCount() != 0
                || metrics.planCount() != 0
                || metrics.requestCount() != 0
                || metrics.publishCount() != 0
                || metrics.inFlightCount() != 0
                || MosaicPhysicalPoiReconciler.metrics().invocationCount() != 0
                || !trace.physicalStatusCalls().isEmpty()
                || !trace.generatorCalls().isEmpty()
                || BDevelopmentWorld.metrics(server).get("generations") != 0L
                || PhysicalPreFeaturesPreparation.metrics().get("builds") != 0L
                || PhysicalPreFeaturesPreparation.metrics(ChunkStatus.TERRAIN).get("builds") != 0L
                || GenerationContextRegistry.bindingCount() != 0
                || MosaicSpawnContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Ordinary Vanilla entered Mosaic production work");
        var result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("normalVanilla", true);
        result.addProperty("mode", System.getProperty(PREFIX + "mode"));
        result.addProperty("dimension", level.dimension().identifier().toString());
        result.addProperty("target", position.toString());
        result.addProperty("worldSeed", server.getWorldGenSettings().options().seed());
        result.addProperty("fullChunkClass", chunk.getClass().getName());
        result.addProperty("fullStatus", chunk.getPersistedStatus().getName());
        result.addProperty("lightCorrect", true);
        result.addProperty("artifactGenerations", 0);
        result.addProperty("spawnCalls", 0); // Mosaic routing only, not Vanilla's actual SPAWN.
        result.addProperty("markerWritten", Boolean.getBoolean(PREFIX + "mutate"));
        result.addProperty("markerPresent", level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK));
        result.addProperty("pid", ProcessHandle.current().pid());
        var gson = new Gson();
        result.add("mosaicMaterializer", gson.toJsonTree(metrics));
        result.add("mosaicPoi", gson.toJsonTree(MosaicPhysicalPoiReconciler.metrics()));
        result.add("mosaicTrace", gson.toJsonTree(trace));
        result.add(
                "mosaicPartialBiomes", gson.toJsonTree(PhysicalPreFeaturesPreparation.metrics()));
        RuntimeLifecycleEvidence.capture(result);
        try {
            Path output = Path.of(System.getProperty(PREFIX + "output"));
            Files.createDirectories(output.getParent());
            Files.writeString(output, result.toString());
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        if (!RuntimeLifecycleEvidence.enabled()) server.halt(false);
    }
}
