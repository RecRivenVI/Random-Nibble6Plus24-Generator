package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
abstract class PerfChunkMapMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private TicketStorage ticketStorage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bind(CallbackInfo ci) {
        PerfTrace.bind(this, level);
        PerfTrace.bind(ticketStorage, level);
        PerfTrace.bind(((ChunkMap) (Object) this).getDistanceManager(), level);
    }

    @WrapMethod(method = "scheduleGenerationTask")
    private ChunkGenerationTask request(
            ChunkStatus status, ChunkPos pos, Operation<ChunkGenerationTask> original) {
        PerfTrace.bind(this, level);
        PerfTrace.bind(ticketStorage, level);
        PerfTrace.event(level, pos, "PHYSICAL_REQUEST", status.getName());
        var span = PerfTrace.begin(level, pos, "SCHEDULE_CALL");
        try {
            return original.call(status, pos);
        } finally {
            PerfTrace.end(level, pos, "SCHEDULE_CALL", span);
        }
    }

    @WrapMethod(method = "applyStep")
    private CompletableFuture<ChunkAccess> stage(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            Operation<CompletableFuture<ChunkAccess>> original) {
        PerfTrace.bind(holder, level);
        PerfTrace.bind(this, level);
        PerfTrace.bind(ticketStorage, level);
        String phase =
                "STAGE_"
                        + step.targetStatus()
                                .getName()
                                .replace("minecraft:", "")
                                .toUpperCase(java.util.Locale.ROOT);
        var span = PerfTrace.begin(level, holder.getPos(), phase);
        try {
            var result = original.call(holder, step, cache);
            PerfTrace.observe(result, level, holder.getPos(), phase + "_END");
            return result;
        } finally {
            span.end();
            span.commit();
            PerfTrace.event(level, holder.getPos(), phase + "_CALL_END", "");
        }
    }

    @WrapMethod(method = "updateChunkScheduling")
    private ChunkHolder holder(
            long pos,
            int newLevel,
            ChunkHolder old,
            int oldLevel,
            Operation<ChunkHolder> original) {
        PerfTrace.bind(ticketStorage, level);
        PerfTrace.bind(this, level);
        ChunkHolder result = original.call(pos, newLevel, old, oldLevel);
        if (result != null) PerfTrace.bind(result, level);
        PerfTrace.event(level, ChunkPos.unpack(pos), "HOLDER_LEVEL", oldLevel + "->" + newLevel);
        return result;
    }

    @WrapMethod(method = "prepareAccessibleChunk")
    private CompletableFuture<ChunkResult<LevelChunk>> accessible(
            ChunkHolder h, Operation<CompletableFuture<ChunkResult<LevelChunk>>> original) {
        PerfTrace.bind(h, level);
        PerfTrace.event(level, h.getPos(), "PROMOTE_FULL_REQUEST", "");
        var result = original.call(h);
        PerfTrace.observe(result, level, h.getPos(), "PROMOTE_FULL_READY");
        return result;
    }

    @WrapMethod(method = "prepareTickingChunk")
    private CompletableFuture<ChunkResult<LevelChunk>> ticking(
            ChunkHolder h, Operation<CompletableFuture<ChunkResult<LevelChunk>>> original) {
        PerfTrace.bind(h, level);
        PerfTrace.event(level, h.getPos(), "BLOCK_TICKING_REQUEST", "");
        var result = original.call(h);
        PerfTrace.observe(result, level, h.getPos(), "BLOCK_TICKING_READY");
        return result;
    }

    @WrapMethod(method = "prepareEntityTickingChunk")
    private CompletableFuture<ChunkResult<LevelChunk>> entity(
            ChunkHolder h, Operation<CompletableFuture<ChunkResult<LevelChunk>>> original) {
        PerfTrace.bind(h, level);
        PerfTrace.event(level, h.getPos(), "ENTITY_TICKING_REQUEST", "");
        var result = original.call(h);
        PerfTrace.observe(result, level, h.getPos(), "ENTITY_TICKING_READY");
        return result;
    }

    @WrapMethod(method = "onChunkReadyToSend")
    private void ready(ChunkHolder h, LevelChunk chunk, Operation<Void> original) {
        PerfTrace.event(level, chunk.getPos(), "READY_TO_SEND", "");
        original.call(h, chunk);
    }

    @WrapMethod(method = "onFullChunkStatusChange")
    private void status(ChunkPos pos, FullChunkStatus status, Operation<Void> original) {
        PerfTrace.event(level, pos, "FULL_STATUS_" + status.name(), "");
        original.call(pos, status);
    }
}
