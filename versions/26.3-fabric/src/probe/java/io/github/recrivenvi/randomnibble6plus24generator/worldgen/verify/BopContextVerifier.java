package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Additional seed-state evidence; sampled biomes never replace generated stored palettes. */
final class BopContextVerifier {
    private BopContextVerifier() {}

    static boolean enabled() {
        return System.getProperty("validation.name", "").equals("compatibility-bop");
    }

    static JsonObject observe(ServerLevel level, long seed, ChunkPos target, boolean nativeRole) {
        for (var id : List.of("biomesoplenty", "terrablender", "glitchcore", "fabric-api"))
            if (!FabricLoader.getInstance().isModLoaded(id))
                throw new IllegalStateException("Missing BOP input " + id);
        var host = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var before = grid(level, host.getBiomeSource().createUncachedResolver(random));
        JsonObject result = new JsonObject();
        if (nativeRole) result.add("seedGrid", before);
        else {
            try (var context =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC, level, seed, target)) {
                if (context.generator().getBiomeSource() == host.getBiomeSource())
                    throw new IllegalStateException("TerraBlender borrowed host biome state");
                var expected =
                        grid(
                                level,
                                context.generator()
                                        .getBiomeSource()
                                        .createUncachedResolver(context.randomState()));
                result.add("seedGrid", expected);
                try (var child = context.createRelated(new ChunkPos(target.x() + 1, target.z()))) {
                    if (child.generator().getBiomeSource() != context.generator().getBiomeSource()
                            || child.randomState() == context.randomState())
                        throw new IllegalStateException(
                                "Related biome configuration/random-state ownership failed");
                    if (!expected.equals(
                            grid(
                                    level,
                                    child.generator()
                                            .getBiomeSource()
                                            .createUncachedResolver(child.randomState()))))
                        throw new IllegalStateException("Related seed context changed biomes");
                }
                try (var foreign =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                level,
                                seed ^ 0x123456789L,
                                target)) {
                    var other =
                            grid(
                                    level,
                                    foreign.generator()
                                            .getBiomeSource()
                                            .createUncachedResolver(foreign.randomState()));
                    if (expected.equals(other))
                        throw new IllegalStateException(
                                "BOP seed grid cannot distinguish the two test seeds");
                    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                        var a =
                                executor.submit(
                                        () ->
                                                grid(
                                                        level,
                                                        context.generator()
                                                                .getBiomeSource()
                                                                .createUncachedResolver(
                                                                        context.randomState())));
                        var b =
                                executor.submit(
                                        () ->
                                                grid(
                                                        level,
                                                        foreign.generator()
                                                                .getBiomeSource()
                                                                .createUncachedResolver(
                                                                        foreign.randomState())));
                        if (!expected.equals(a.get()) || !other.equals(b.get()))
                            throw new IllegalStateException(
                                    "Concurrent BOP seed-state contamination");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    } catch (java.util.concurrent.ExecutionException failure) {
                        throw new IllegalStateException(failure.getCause());
                    }
                    if (!expected.equals(
                            grid(
                                    level,
                                    context.generator()
                                            .getBiomeSource()
                                            .createUncachedResolver(context.randomState()))))
                        throw new IllegalStateException(
                                "Constructing another seed changed the first context");
                }
            }
        }
        if (!before.equals(grid(level, host.getBiomeSource().createUncachedResolver(random))))
            throw new IllegalStateException("Isolated context mutated host biome semantics");
        if (GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("BOP context leaked");
        result.addProperty("hostUnchanged", true);
        result.addProperty("concurrentSeedIsolation", !nativeRole);
        return result;
    }

    private static JsonArray grid(
            ServerLevel level, net.minecraft.world.level.biome.BiomeResolver resolver) {
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var result = new JsonArray();
        for (int z = -2048; z <= 2048; z += 256)
            for (int x = -2048; x <= 2048; x += 256)
                result.add(registry.getKey(resolver.getNoiseBiome(x, 16, z).value()).toString());
        return result;
    }

    static long storedBop(ChunkAccess target) {
        long count = 0;
        for (int y = QuartPos.fromBlock(target.getMinY());
                y < QuartPos.fromBlock(target.getMinY() + target.getHeight());
                y++)
            for (int z = 0; z < 4; z++)
                for (int x = 0; x < 4; x++)
                    if (target.getNoiseBiome(
                                    QuartPos.fromBlock(target.getPos().getMinBlockX()) + x,
                                    y,
                                    QuartPos.fromBlock(target.getPos().getMinBlockZ()) + z)
                            .unwrapKey()
                            .orElseThrow()
                            .identifier()
                            .getNamespace()
                            .equals("biomesoplenty")) count++;
        return count;
    }

    static JsonArray fixtures(MinecraftServer server, JsonArray existing) {
        var result = new JsonArray();
        for (var key : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            var level = Objects.requireNonNull(server.getLevel(key));
            var resolver =
                    level.getChunkSource()
                            .getGenerator()
                            .getBiomeSource()
                            .createUncachedResolver(level.getChunkSource().randomState());
            int found = 0;
            for (int z = -512; z <= 512 && found < 2; z += 16) {
                for (int x = -512; x <= 512 && found < 2; x += 16) {
                    if (!resolver.getNoiseBiome(x * 4 + 2, 16, z * 4 + 2)
                            .unwrapKey()
                            .orElseThrow()
                            .identifier()
                            .getNamespace()
                            .equals("biomesoplenty")) continue;
                    var f = new JsonObject();
                    f.addProperty("id", "bop-" + key.identifier().getPath() + "-" + found++);
                    f.addProperty("master", "0");
                    f.addProperty("local", "0");
                    f.addProperty("dimension", key.identifier().toString());
                    f.addProperty("x", x);
                    f.addProperty("z", z);
                    result.add(f);
                }
            }
            if (found != 2)
                throw new IllegalStateException("No positive BOP biome fixtures for " + key);
        }
        for (int index : List.of(22, 40, 56)) result.add(existing.get(index));
        return result;
    }
}
