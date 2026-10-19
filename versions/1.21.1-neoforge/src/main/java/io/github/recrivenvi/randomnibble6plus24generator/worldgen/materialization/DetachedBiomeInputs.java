package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.ticks.ProtoChunkTicks;

/**
 * Native BIOMES only; the detached result retains neither NoiseChunk nor the live input resolver.
 */
public final class DetachedBiomeInputs {
    private DetachedBiomeInputs() {}

    public static ProtoChunk generate(
            StructureGenerationContext context, CompactStructureInputs structures) {
        structures.requireCompatible(context);
        var host = context.host();
        var registry = host.registryAccess().registryOrThrow(Registries.BIOME);
        var working = new ProtoChunk(context.target(), UpgradeData.EMPTY, host, registry, null);
        structures.copyStartsTo(working);
        structures.copyReferencesTo(working);
        working.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
        // In this version createBiomes builds the native Beardifier/NoiseChunk before using
        // its cachedClimateSampler. Direct BiomeSource sampling is not the palette authority.
        context.generator()
                .createBiomes(context.randomState(), Blender.empty(), structures.manager(), working)
                .join();
        context.seed(); // Includes the owning physical lifecycle cancellation checkpoint.
        LevelChunkSection[] sections = new LevelChunkSection[working.getSections().length];
        for (int i = 0; i < sections.length; i++) {
            var source = working.getSection(i);
            if (source.getBiomes().getClass() != PalettedContainer.class)
                throw new UnsupportedOperationException(
                        "Unaudited detached biome container " + source.getBiomes().getClass());
            @SuppressWarnings("unchecked")
            var biomes = (PalettedContainer<Holder<Biome>>) source.getBiomes();
            sections[i] = new LevelChunkSection(source.getStates().copy(), biomes.copy());
        }
        if (working.getBlockTicks().count() != 0 || working.getFluidTicks().count() != 0)
            throw new UnsupportedOperationException("BIOMES unexpectedly scheduled ticks");
        var detached =
                new ProtoChunk(
                        context.target(),
                        UpgradeData.EMPTY,
                        sections,
                        new ProtoChunkTicks<>(),
                        new ProtoChunkTicks<>(),
                        host,
                        registry,
                        null);
        // These starts were computed solely for this owner and are not used again after transfer.
        // The copied reference sets retain the native iteration layout, with no shared mutations.
        structures.copyStartsTo(detached);
        structures.copyReferencesTo(detached);
        detached.setPersistedStatus(ChunkStatus.BIOMES);
        detached.setUnsaved(true);
        return detached;
    }
}
