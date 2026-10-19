package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Exact fresh Pre-FEATURES input path; deliberately not wired to production/save dispatch. */
public final class LightweightTerrainGenerator {
    private LightweightTerrainGenerator() {}

    public static Result generate(
            IsolatedGenerationContext context,
            CompactStructureInputs structures,
            BiConsumer<ChunkStatus, ChunkAccess> checkpoint) {
        return generate(context, structures, checkpoint, null);
    }

    public static Result generate(
            IsolatedGenerationContext context,
            CompactStructureInputs structures,
            BiConsumer<ChunkStatus, ChunkAccess> checkpoint,
            TerrainBiomeInputs retained) {
        long began = System.nanoTime();
        structures.requireCompatible(context);
        var host = context.hostLevel();
        var target =
                new ProtoChunk(
                        context.target(),
                        UpgradeData.EMPTY,
                        host,
                        host.palettedContainerFactory(),
                        null);
        structures.copyStartsTo(target);
        target.setPersistedStatus(ChunkStatus.STRUCTURE_STARTS);
        checkpoint.accept(ChunkStatus.STRUCTURE_STARTS, target);
        structures.copyReferencesTo(target);
        target.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
        checkpoint.accept(ChunkStatus.STRUCTURE_REFERENCES, target);
        long biomeBegan = System.nanoTime();
        boolean copied = retained != null && retained.copyKnownTo(context, target);
        if (!copied)
            context.generator()
                    .createBiomes(
                            context.randomState(), Blender.empty(), structures.manager(), target)
                    .join();
        target.setPersistedStatus(ChunkStatus.BIOMES);
        if (retained != null && !copied)
            retained.rememberGenerated(context, target, System.nanoTime() - biomeBegan);
        checkpoint.accept(ChunkStatus.BIOMES, target);
        int radius =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.TERRAIN)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.BIOMES);
        // collectPossibleBiomes and surface's stored-palette lookup were audited together.
        if (radius != 1)
            throw new IllegalStateException("Unaudited terrain biome radius " + radius);
        AtomicLong storedReads = new AtomicLong();
        Set<Holder<Biome>> possible;
        BiomeManager manager;
        int carriers;
        if (retained != null) {
            var prepared = retained.prepare(context, context.target());
            possible = prepared.possible();
            manager =
                    prepared.manager()
                            .withDifferentSource(
                                    (x, y, z) -> {
                                        storedReads.incrementAndGet();
                                        return prepared.manager().getNoiseBiomeAtQuart(x, y, z);
                                    });
            carriers = prepared.inputs() - 1;
        } else {
            Map<Long, ChunkAccess> biomes = new HashMap<>();
            biomes.put(ChunkPos.pack(target.getPos().x(), target.getPos().z()), target);
            possible = new ObjectOpenHashSet<>();
            for (int z = target.getPos().z() - radius; z <= target.getPos().z() + radius; z++) {
                for (int x = target.getPos().x() - radius; x <= target.getPos().x() + radius; x++) {
                    long packed = ChunkPos.pack(x, z);
                    var chunk = biomes.get(packed);
                    if (chunk == null) {
                        chunk =
                                new BiomePaletteChunk(
                                        new ChunkPos(x, z), host, host.palettedContainerFactory());
                        context.generator()
                                .createBiomes(
                                        context.randomState(),
                                        Blender.empty(),
                                        structures.manager(),
                                        chunk)
                                .join();
                        biomes.put(packed, chunk);
                    }
                    chunk.collectBiomesInPalette(possible);
                }
            }
            manager =
                    new BiomeManager(
                            (x, y, z) -> {
                                ChunkAccess source =
                                        biomes.get(
                                                ChunkPos.pack(
                                                        QuartPos.toSection(x),
                                                        QuartPos.toSection(z)));
                                if (source == null)
                                    throw new IllegalStateException(
                                            "Stored biome lookup outside finite frontier: "
                                                    + x
                                                    + ","
                                                    + z);
                                storedReads.incrementAndGet();
                                return source.getNoiseBiome(x, y, z);
                            },
                            BiomeManager.obfuscateSeed(context.worldSeed()));
            carriers = biomes.size() - 1;
        }
        long biomeNanos = System.nanoTime() - biomeBegan;
        long terrainBegan = System.nanoTime();
        // Native 26.3 null-region branch evaluates source biome/carver RNG directly. It does not
        // replace stored surface biomes with uncached sampling or construct source ProtoChunks.
        context.generator()
                .buildTerrain(
                        target,
                        Blender.empty(),
                        context.randomState(),
                        structures.manager(),
                        manager,
                        null,
                        possible)
                .join();
        Heightmap.primeHeightmaps(
                target,
                EnumSet.of(
                        Heightmap.Types.MOTION_BLOCKING,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Heightmap.Types.OCEAN_FLOOR,
                        Heightmap.Types.WORLD_SURFACE));
        target.setPersistedStatus(ChunkStatus.TERRAIN);
        checkpoint.accept(ChunkStatus.TERRAIN, target);
        return new Result(
                target,
                new Metrics(
                        1,
                        carriers,
                        radius,
                        storedReads.get(),
                        biomeNanos,
                        System.nanoTime() - terrainBegan,
                        System.nanoTime() - began));
    }

    public record Result(ProtoChunk target, Metrics metrics) {}

    public record Metrics(
            int protoChunks,
            int biomeCarriers,
            int biomeRadius,
            long storedBiomeReads,
            long biomeNanos,
            long terrainNanos,
            long elapsedNanos) {}
}
