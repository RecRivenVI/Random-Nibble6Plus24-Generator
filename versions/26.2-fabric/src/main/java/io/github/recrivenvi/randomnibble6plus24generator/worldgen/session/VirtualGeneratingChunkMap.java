package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

final class VirtualGeneratingChunkMap implements GeneratingChunkMap {

    private final IsolatedGenerationContext context;
    private final Map<Long, VirtualGenerationChunkHolder> holders = new ConcurrentHashMap<>();

    VirtualGeneratingChunkMap(IsolatedGenerationContext context) {
        this.context = context;
    }

    @Override
    public GenerationChunkHolder acquireGeneration(long packedPos) {
        VirtualGenerationChunkHolder holder = holders.computeIfAbsent(
                packedPos,
                key -> new VirtualGenerationChunkHolder(ChunkPos.unpack(key)));
        holder.increaseGenerationRefCount();
        return holder;
    }

    @Override
    public void releaseGeneration(GenerationChunkHolder holder) {
        holder.decreaseGenerationRefCount();
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache) {
        GenerationContextRegistry.bind(cache, context);
        if (step.targetStatus() == ChunkStatus.EMPTY) {
            context.recordStage(ChunkStatus.EMPTY);
            return CompletableFuture.completedFuture(createEmptyChunk(holder.getPos()));
        }

        ChunkAccess parent = holder.getChunkIfPresentUnchecked(step.targetStatus().getParent());
        if (parent == null) {
            throw new IllegalStateException("Virtual parent chunk is missing for "
                    + holder.getPos()
                    + " at "
                    + step.targetStatus());
        }
        return step.apply(context.worldGenContext(), cache, parent);
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(ChunkStatus status, ChunkPos pos) {
        return ChunkGenerationTask.create(this, status, pos);
    }

    @Override
    public void runGenerationTasks() {
        // The explicit session driver advances tasks through runUntilWait().
    }

    int virtualChunkCount() {
        return holders.size();
    }

    private ProtoChunk createEmptyChunk(ChunkPos pos) {
        return new ProtoChunk(
                pos,
                UpgradeData.EMPTY,
                context.hostLevel(),
                context.hostLevel().palettedContainerFactory(),
                null);
    }
}
