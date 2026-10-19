package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureInputChunk;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Native stored biome palettes with no block sections; terrain access remains rejected. */
public final class BiomePaletteChunk extends StructureInputChunk {
    private final LevelHeightAccessor height;
    private final List<PalettedContainer<Holder<Biome>>> palettes = new ArrayList<>();
    private boolean ready;

    public BiomePaletteChunk(
            ChunkPos position, LevelHeightAccessor height, Registry<Biome> registry) {
        super(position, height);
        this.height = height;
        for (int i = 0; i < height.getSectionsCount(); i++)
            palettes.add(
                    new PalettedContainer<>(
                            registry.asHolderIdMap(),
                            registry.getHolderOrThrow(Biomes.PLAINS),
                            PalettedContainer.Strategy.SECTION_BIOMES));
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return ready ? ChunkStatus.BIOMES : ChunkStatus.STRUCTURE_REFERENCES;
    }

    @Override
    public void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler) {
        if (ready) throw new IllegalStateException("Stored biomes already populated");
        // Native 1.21.1 ChunkAccess/LevelChunkSection section, x, y, z iteration and cached
        // sampler.
        for (int sectionY = height.getMinSection(); sectionY < height.getMaxSection(); sectionY++) {
            var palette = palettes.get(sectionY - height.getMinSection()).recreate();
            for (int x = 0; x < 4; x++)
                for (int y = 0; y < 4; y++)
                    for (int z = 0; z < 4; z++)
                        palette.getAndSetUnchecked(
                                x,
                                y,
                                z,
                                resolver.getNoiseBiome(
                                        QuartPos.fromBlock(getPos().getMinBlockX()) + x,
                                        QuartPos.fromSection(sectionY) + y,
                                        QuartPos.fromBlock(getPos().getMinBlockZ()) + z,
                                        sampler));
            palettes.set(sectionY - height.getMinSection(), palette);
        }
        ready = true;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        if (!ready) throw new IllegalStateException("Stored biomes not ready");
        int min = QuartPos.fromBlock(getMinBuildHeight());
        int clamped = Math.clamp(y, min, min + QuartPos.fromBlock(getHeight()) - 1);
        return palettes.get(height.getSectionIndex(QuartPos.toBlock(clamped)))
                .get(x & 3, clamped & 3, z & 3);
    }

    public void getAllBiomes(java.util.function.Consumer<Holder<Biome>> consumer) {
        if (!ready) throw new IllegalStateException("Stored biomes not ready");
        palettes.forEach(palette -> palette.getAll(consumer));
    }
}
