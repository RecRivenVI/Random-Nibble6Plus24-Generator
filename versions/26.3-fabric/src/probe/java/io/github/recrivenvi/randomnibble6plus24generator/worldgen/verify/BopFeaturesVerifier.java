package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;

/** Independent native B writer and unrelated-host B consumer, never nine-writer parity. */
final class BopFeaturesVerifier {
    private static final String PREFIX = RandomNibble6Plus24Generator.MOD_ID + ".prefeatures.";

    private BopFeaturesVerifier() {}

    static void run(MinecraftServer server) {
        var id = System.getProperty(PREFIX + "fixture");
        var role = System.getProperty(PREFIX + "role");
        var dimension = System.getProperty(PREFIX + "dimension");
        var level =
                Objects.requireNonNull(
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION, Identifier.parse(dimension))));
        var target =
                new ChunkPos(Integer.getInteger(PREFIX + "x"), Integer.getInteger(PREFIX + "z"));
        long local = Long.parseLong(System.getProperty(PREFIX + "local"));
        long host = server.getWorldGenSettings().options().seed();
        boolean nativeRole = role.startsWith("native");
        if (nativeRole != (host == local))
            throw new IllegalStateException("Wrong BOP independent seed");
        var root =
                Path.of(System.getProperty("validation.result"))
                        .resolve("feature_b")
                        .resolve(System.getProperty("validation.target"))
                        .resolve(id);
        var output = root.resolve(nativeRole ? role : "host_" + host);
        var result = new JsonObject();
        result.addProperty("fixture", id);
        result.addProperty("hostSeed", Long.toString(host));
        result.addProperty("localSeed", Long.toString(local));
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("inputHash", System.getProperty(PREFIX + "inputHash"));
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
        try {
            Files.createDirectories(output);
            ProtoChunk chunk;
            Map<String, Long> rejected;
            Set<String> biomes;
            if (nativeRole) {
                var generated = NativeFeatureBControl.generate(level, target);
                chunk = generated.target();
                rejected = generated.rejected();
                biomes = generated.biomes();
                result.addProperty("terrainHash", generated.terrainHash());
                result.add(
                        "featureOrder", featureOrder(level, level.getChunkSource().getGenerator()));
            } else {
                try (var context =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                level,
                                local,
                                target,
                                MosaicWorldProfile.targetOnly())) {
                    var visible = new TreeSet<String>();
                    var generated =
                            LightweightFeatureBaseline.generateWithStructureReuse(
                                    context,
                                    new LocalFeatureInputs.Observer() {},
                                    (region, inputs) -> {
                                        result.addProperty(
                                                "terrainHash",
                                                FeatureStableSnapshot.capture(
                                                                dimension,
                                                                inputs.chunks().get(target.pack()),
                                                                level.registryAccess())
                                                        .hash());
                                        result.add(
                                                "featureOrder",
                                                featureOrder(level, context.generator()));
                                        var holders =
                                                new HashSet<
                                                        net.minecraft.core.Holder<
                                                                net.minecraft.world.level.biome
                                                                        .Biome>>();
                                        inputs.chunks()
                                                .values()
                                                .forEach(c -> c.collectBiomesInPalette(holders));
                                        var registry =
                                                level.registryAccess()
                                                        .lookupOrThrow(Registries.BIOME);
                                        holders.forEach(
                                                b ->
                                                        visible.add(
                                                                registry.getKey(b.value())
                                                                        .toString()));
                                    });
                    chunk = generated.target();
                    rejected = generated.rejected();
                    biomes = visible;
                    result.add(
                            "inputCounters",
                            new GsonBuilder().create().toJsonTree(generated.inputs().counters()));
                }
            }
            var snapshot = FeatureStableSnapshot.capture(dimension, chunk, level.registryAccess());
            snapshot.write(output.resolve("features.bin.gz"));
            result.addProperty("hash", snapshot.hash());
            result.addProperty("bopQuartEntries", BopContextVerifier.storedBop(chunk));
            long bopBlocks = 0;
            var blockRegistry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
            for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (blockRegistry
                                .getKey(
                                        chunk.getBlockState(
                                                        new net.minecraft.core.BlockPos(
                                                                target.getMinBlockX() + x,
                                                                y,
                                                                target.getMinBlockZ() + z))
                                                .getBlock())
                                .getNamespace()
                                .equals("biomesoplenty")) bopBlocks++;
            result.addProperty("bopBlocks", bopBlocks);
            result.add("biomes", new GsonBuilder().create().toJsonTree(new TreeSet<>(biomes)));
            result.add("rejected", new GsonBuilder().create().toJsonTree(new TreeMap<>(rejected)));
            result.add(
                    "rawEntityNbt", new GsonBuilder().create().toJsonTree(snapshot.rawEntityNbt()));
            result.addProperty("blockEntities", snapshot.blockEntityCount());
            result.addProperty("blockTicks", snapshot.blockTickCount());
            result.addProperty("fluidTicks", snapshot.fluidTickCount());
            result.addProperty("postProcessing", snapshot.postProcessingCount());
            if (!nativeRole) {
                if (loaded != level.getChunkSource().getLoadedChunksCount()
                        || blocks != level.getBlockTicks().count()
                        || fluids != level.getFluidTicks().count()
                        || entities
                                != java.util.stream.StreamSupport.stream(
                                                level.getAllEntities().spliterator(), false)
                                        .count())
                    throw new IllegalStateException("BOP isolated writer polluted physical host");
                result.addProperty("physicalHostUnchanged", true);
                var expected = FeatureStableSnapshot.read(root.resolve("native/features.bin.gz"));
                var nativeSummary =
                        JsonParser.parseString(
                                        Files.readString(root.resolve("native/summary.json")))
                                .getAsJsonObject();
                if (!nativeSummary.get("biomes").equals(result.get("biomes")))
                    throw new IllegalStateException("BOP feature-visible biomes differ");
                if (!expected.hash().equals(snapshot.hash()))
                    throw new IllegalStateException(
                            "BOP native B differs: " + expected.diff(snapshot));
            }
            if (GenerationContextRegistry.bindingCount() != 0)
                throw new IllegalStateException("BOP features leaked context");
            result.addProperty("status", nativeRole ? "PRODUCED" : "PASS");
        } catch (Exception | Error failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("failure", failure.toString());
            throw new IllegalStateException("BOP native B proof failed", failure);
        } finally {
            PreFeaturesVerifier.write(output.resolve("summary.json"), result);
            server.halt(false);
        }
    }

    private static JsonArray featureOrder(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.world.level.chunk.ChunkGenerator generator) {
        try {
            var field =
                    net.minecraft.world.level.chunk.ChunkGenerator.class.getDeclaredField(
                            "featuresPerStep");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var supplier =
                    (java.util.function.Supplier<
                                    List<
                                            net.minecraft.world.level.biome.FeatureSorter
                                                    .StepFeatureData>>)
                            field.get(generator);
            var result = new JsonArray();
            var registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            for (var step : supplier.get()) {
                var entries = new JsonArray();
                for (var feature : step.features()) {
                    var key = registry.getKey(feature);
                    if (key == null)
                        throw new IllegalStateException("Unregistered BOP placed feature");
                    entries.add(key.toString());
                }
                result.add(entries);
            }
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot observe native feature order", failure);
        }
    }
}
