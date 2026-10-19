package io.github.recrivenvi.randomnibble6plus24generator.probe.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureChunk;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureRegion;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Real physical native prerequisites, followed by exactly one native writer under B's guards. */
public final class NativeFeatureBControl {
    private static final ThreadLocal<NativeScope> active = new ThreadLocal<>();

    private static final class NativeScope {
        final StrictFeatureRegion region;
        final ServerLevel level;
        long structureReads;

        NativeScope(StrictFeatureRegion region, ServerLevel level) {
            this.region = region;
            this.level = level;
        }
    }

    private NativeFeatureBControl() {}

    /** Only the audited native entity-variant predicate may use this physical S(C) world. */
    public static ServerLevel nativeStructureLevel(
            net.minecraft.world.level.ServerLevelAccessor accessor) {
        var scope = active.get();
        if (scope == null || scope.region != accessor) return null;
        if (GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Native predicate entered an isolated context");
        scope.structureReads++;
        return scope.level;
    }

    public record Result(
            ProtoChunk target,
            Map<String, Long> rejected,
            Set<String> biomes,
            String terrainHash,
            long nativeStructureReads,
            Map<Long, ProtoChunk> outerReadInputs) {}

    public static Result generate(ServerLevel level, ChunkPos pos) {
        return generate(level, pos, () -> {});
    }

    public static Result generate(ServerLevel level, ChunkPos pos, Runnable beforeDecoration) {
        if (GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Native B entered isolation");
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        int radius = step.directDependencies().getRadiusOf(ChunkStatus.CARVERS);
        if (radius != 1 || step.blockStateWriteRadius() != 1)
            throw new IllegalStateException("Unaudited B radius");
        var terrain = new HashMap<Long, ProtoChunk>();
        for (int z = pos.z() - radius; z <= pos.z() + radius; z++)
            for (int x = pos.x() - radius; x <= pos.x() + radius; x++) {
                var chunk = (ProtoChunk) level.getChunk(x, z, ChunkStatus.CARVERS, true);
                if (chunk.getPersistedStatus() != ChunkStatus.CARVERS)
                    throw new IllegalStateException("Native prerequisite escaped TERRAIN");
                terrain.put(ChunkPos.pack(x, z), chunk);
            }
        var target = terrain.get(pos.pack());
        // A structure processor may read beyond the CARVERS ring. Record the real native
        // prerequisite state before narrowing write authority; never supply guessed air.
        var nativeFrontier = new java.util.TreeMap<String, Object>();
        for (int distance = radius + 1; distance < step.directDependencies().size(); distance++) {
            var sample =
                    level.getChunk(pos.x(), pos.z() - distance, ChunkStatus.STRUCTURE_STARTS, true);
            nativeFrontier.put(
                    Integer.toString(distance),
                    java.util.Map.of(
                            "declared", step.directDependencies().get(distance).toString(),
                            "accumulated", step.accumulatedDependencies().get(distance).toString(),
                            "actual", sample.getPersistedStatus().toString(),
                            "position", sample.getPos().toString()));
        }
        System.out.println(
                "NATIVE B READ FRONTIER " + new com.google.gson.Gson().toJson(nativeFrontier));
        String terrainHash =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify
                        .FeatureStableSnapshot.capture(
                                level.dimension().identifier().toString(),
                                target,
                                level.registryAccess())
                        .hash();
        Map<String, Long> rejected = new TreeMap<>();
        java.util.function.Consumer<String> drop = api -> rejected.merge(api, 1L, Long::sum);
        Map<Long, StrictFeatureChunk> inputs = new HashMap<>();
        Set<String> biomes = new TreeSet<>();
        var registry =
                level.registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        terrain.forEach(
                (key, chunk) -> {
                    inputs.put(
                            key,
                            new StrictFeatureChunk(
                                    chunk,
                                    pos,
                                    level.palettedContainerFactory(),
                                    drop,
                                    level.registryAccess()));
                    var values =
                            new HashSet<
                                    net.minecraft.core.Holder<
                                            net.minecraft.world.level.biome.Biome>>();
                    for (var section : chunk.getSections()) section.getBiomes().getAll(values::add);
                    values.forEach(b -> biomes.add(registry.getKey(b.value()).toString()));
                });
        var cache =
                StaticCache2D.create(
                        pos.x(),
                        pos.z(),
                        0,
                        (x, z) -> StrictFeatureRegion.constructorCarrier(target));
        var region = new StrictFeatureRegion(level, cache, step, target, inputs, drop);
        region.bindOuterReadSource(
                (x, z) -> (ProtoChunk) level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, true));
        if (region.getSeed() != level.getServer().getWorldGenSettings().options().seed())
            throw new IllegalStateException("Native B seed is not physical S(C)");
        // Real native starts/references have already been provided by the physical pyramid.
        net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(
                target,
                EnumSet.of(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        net.minecraft.world.level.levelgen.Heightmap.Types
                                .MOTION_BLOCKING_NO_LEAVES,
                        net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE));
        beforeDecoration.run();
        if (active.get() != null) throw new IllegalStateException("Nested native B control");
        var scope = new NativeScope(region, level);
        active.set(scope);
        try {
            generator.applyBiomeDecoration(region, target, level.structureManager());
        } finally {
            active.remove();
        }
        Blender.generateBorderTicks(region, target);
        target.setPersistedStatus(ChunkStatus.FEATURES);
        if (GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Native B unexpectedly bound a session");
        return new Result(
                target,
                Map.copyOf(rejected),
                Set.copyOf(biomes),
                terrainHash,
                scope.structureReads,
                region.outerReadInputs());
    }
}
