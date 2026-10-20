package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import java.util.List;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.ticks.ProtoChunkTicks;

/** A private working copy, never an ImposterProtoChunk sharing physical block storage. */
public final class DetachedTerrainCopy {
    private DetachedTerrainCopy() {}

    public static ProtoChunk copy(
            IsolatedGenerationContext context,
            CompactStructureInputs structures,
            ProtoChunk source) {
        if (!source.getPos().equals(context.target())
                || source.getPersistedStatus() != ChunkStatus.TERRAIN
                || source.getMinY() != context.hostLevel().getMinY()
                || source.getHeight() != context.hostLevel().getHeight())
            throw new IllegalArgumentException("Foreign or incomplete TERRAIN input");
        // Old upgrading/blending inputs have not passed this detached-copy proof. The caller
        // retains the exact existing full generation path for that actual load shape.
        if (source.getBlendingData() != null
                || source.getBelowZeroRetrogen() != null
                || !source.getUpgradeData().isEmpty()) return null;
        var sections = source.getSections().clone();
        for (int i = 0; i < sections.length; i++) sections[i] = sections[i].copy();
        var ticks = source.getTicksForSerialization(0L);
        var target =
                new ProtoChunk(
                        source.getPos(),
                        source.getUpgradeData().copy(),
                        sections,
                        ProtoChunkTicks.load(List.copyOf(ticks.blocks())),
                        ProtoChunkTicks.load(List.copyOf(ticks.fluids())),
                        context.hostLevel(),
                        context.hostLevel().palettedContainerFactory(),
                        null);
        source.getHeightmaps()
                .forEach(
                        entry ->
                                target.setHeightmap(
                                        entry.getKey(), entry.getValue().getRawData().clone()));
        for (int i = 0; i < source.getPostProcessing().length; i++) {
            var values = source.getPostProcessing()[i];
            if (values != null) target.addPackedPostProcess(new ShortArrayList(values), i);
        }
        for (var entity : source.getEntities()) target.addEntity(entity.copy());
        for (var pos : source.getBlockEntitiesPos()) {
            var nbt = source.getBlockEntityNbtForSaving(pos, context.hostLevel().registryAccess());
            if (nbt == null)
                throw new IllegalStateException("Missing TERRAIN BlockEntity input " + pos);
            if (source.getBlockEntities().containsKey(pos)) {
                var blockEntity =
                        BlockEntity.loadStatic(
                                pos,
                                target.getBlockState(pos),
                                nbt.copy(),
                                context.hostLevel().registryAccess());
                if (blockEntity == null)
                    throw new IllegalStateException("Invalid TERRAIN BlockEntity input " + pos);
                target.setBlockEntity(blockEntity);
            } else target.setBlockEntityNbt(nbt.copy());
        }
        // Piece placement can mutate starts. Obtain fresh isolated starts and exact B reference
        // containers, rather than sharing the physical input's mutable StructureStart objects.
        structures.copyStartsTo(target);
        structures.copyReferencesTo(target);
        target.setPersistedStatus(ChunkStatus.TERRAIN);
        return target;
    }
}
