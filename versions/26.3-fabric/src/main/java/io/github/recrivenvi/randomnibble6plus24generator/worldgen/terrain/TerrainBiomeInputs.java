package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Stored BIOMES and read-only terrain preparation, owned by one live same-seed B target. */
public final class TerrainBiomeInputs implements AutoCloseable {
    private final IsolatedGenerationContext owner;
    private final Map<Long, BiomePaletteChunk> palettes = new HashMap<>();
    private final Map<Long, Prepared> prepared = new HashMap<>();
    private final WorldGenerationContext generation;
    private final int radius;
    private long requests, hits, nativeBuilds, targetCopies, preparationHits;
    private long nativeBuildNanos, copyNanos;
    private int paletteCount;
    private boolean closed;

    public TerrainBiomeInputs(IsolatedGenerationContext owner) {
        this.owner = owner;
        int writers =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.FEATURES)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.TERRAIN);
        if (writers != 1 || biomeRadius() != 1)
            throw new IllegalStateException("Unaudited B biome input frontier");
        radius = writers + biomeRadius();
        generation = new WorldGenerationContext(owner.generator(), owner.hostLevel());
    }

    public record Prepared(Set<Holder<Biome>> possible, BiomeManager manager, int inputs) {}

    public record Counters(
            long requests,
            long hits,
            long nativeBuilds,
            long targetCopies,
            long preparationHits,
            long nativeBuildNanos,
            long copyNanos,
            int palettes,
            int resident,
            boolean closed) {}

    public static int biomeRadius() {
        int radius =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.TERRAIN)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.BIOMES);
        if (radius != 1)
            throw new IllegalStateException("Unaudited terrain biome radius " + radius);
        return radius;
    }

    private void open() {
        if (closed || GenerationContextRegistry.find(owner.worldGenContext()).orElse(null) != owner)
            throw new IllegalStateException("Closed stored biome input owner");
    }

    public void requireCompatible(IsolatedGenerationContext context) {
        open();
        if (owner.hostLevel() != context.hostLevel()
                || owner.worldSeed() != context.worldSeed()
                || owner.generator().getBiomeSource() != context.generator().getBiomeSource()
                || !owner.generator()
                        .generatorSettings()
                        .equals(context.generator().generatorSettings()))
            throw new IllegalArgumentException(
                    "Stored biomes belong to another world/seed/generator");
        checkPosition(context.target());
    }

    private void checkPosition(ChunkPos pos) {
        if (Math.abs((long) pos.x() - owner.target().x()) > radius
                || Math.abs((long) pos.z() - owner.target().z()) > radius)
            throw new IllegalArgumentException("Outside B stored biome frontier " + pos);
    }

    public BiomePaletteChunk palette(ChunkPos pos) {
        open();
        checkPosition(pos);
        requests++;
        var known = palettes.get(pos.pack());
        if (known != null) {
            hits++;
            return known;
        }
        var chunk =
                new BiomePaletteChunk(
                        pos, owner.hostLevel(), owner.hostLevel().palettedContainerFactory());
        long start = System.nanoTime();
        owner.generator()
                .createBiomes(owner.randomState(), Blender.empty(), owner.structureManager(), chunk)
                .join();
        nativeBuildNanos += System.nanoTime() - start;
        palettes.put(pos.pack(), chunk);
        nativeBuilds++;
        paletteCount++;
        return chunk;
    }

    public boolean copyKnownTo(IsolatedGenerationContext context, ProtoChunk target) {
        requireCompatible(context);
        if (!context.target().equals(target.getPos()))
            throw new IllegalArgumentException("Foreign biome target");
        requests++;
        var source = palettes.get(target.getPos().pack());
        if (source == null) return false;
        long start = System.nanoTime();
        source.copyTo(target);
        hits++;
        targetCopies++;
        copyNanos += System.nanoTime() - start;
        return true;
    }

    public void rememberGenerated(
            IsolatedGenerationContext context, ProtoChunk target, long biomeNanos) {
        requireCompatible(context);
        if (!context.target().equals(target.getPos())
                || palettes.containsKey(target.getPos().pack()))
            throw new IllegalArgumentException("Duplicate/foreign generated biome input");
        var snapshot =
                BiomePaletteChunk.snapshot(target, owner.hostLevel().palettedContainerFactory());
        nativeBuildNanos += biomeNanos;
        palettes.put(target.getPos().pack(), snapshot);
        nativeBuilds++;
        paletteCount++;
    }

    public Prepared prepare(IsolatedGenerationContext context, ChunkPos position) {
        requireCompatible(context);
        if (Math.abs((long) position.x() - owner.target().x()) > radius - biomeRadius()
                || Math.abs((long) position.z() - owner.target().z()) > radius - biomeRadius())
            throw new IllegalArgumentException("Outside B terrain input frontier " + position);
        var known = prepared.get(position.pack());
        if (known != null) {
            preparationHits++;
            return known;
        }
        Map<Long, BiomePaletteChunk> halo = new HashMap<>();
        Set<Holder<Biome>> possible = new ObjectOpenHashSet<>();
        int r = biomeRadius();
        for (int z = position.z() - r; z <= position.z() + r; z++)
            for (int x = position.x() - r; x <= position.x() + r; x++) {
                var p = new ChunkPos(x, z);
                var input = palette(p);
                halo.put(p.pack(), input);
                input.collectBiomesInPalette(possible);
            }
        var manager =
                new BiomeManager(
                        (x, y, z) -> {
                            open();
                            var input =
                                    halo.get(
                                            ChunkPos.pack(
                                                    QuartPos.toSection(x), QuartPos.toSection(z)));
                            if (input == null)
                                throw new IllegalStateException(
                                        "Stored biome lookup outside finite terrain frontier");
                            return input.getNoiseBiome(x, y, z);
                        },
                        BiomeManager.obfuscateSeed(owner.worldSeed()));
        var result = new Prepared(Collections.unmodifiableSet(possible), manager, halo.size());
        prepared.put(position.pack(), result);
        return result;
    }

    public WorldGenerationContext generation(IsolatedGenerationContext context) {
        requireCompatible(context);
        return generation;
    }

    public Counters counters() {
        return new Counters(
                requests,
                hits,
                nativeBuilds,
                targetCopies,
                preparationHits,
                nativeBuildNanos,
                copyNanos,
                paletteCount,
                palettes.size(),
                closed);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        prepared.clear();
        palettes.clear();
    }
}
