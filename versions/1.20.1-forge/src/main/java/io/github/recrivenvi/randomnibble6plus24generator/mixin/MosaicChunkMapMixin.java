package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
abstract class MosaicChunkMapMixin {
    @Shadow @Final private ServerLevel level;

    @Unique
    private static final ThreadLocal<Integer> mosaic$dependencies =
            ThreadLocal.withInitial(() -> 0);

    @Shadow
    private CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>>
            getChunkRangeFuture(
                    ChunkHolder holder, int radius, IntFunction<ChunkStatus> dependency) {
        throw new AssertionError();
    }

    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void mosaic$plan(
            ChunkHolder holder,
            ChunkStatus status,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    callback) {
        if (!(level.getChunkSource().getGenerator()
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                        .MosaicChunkGenerator)) return;
        var owner = MosaicPhysicalLevelOwner.forLevel(level);
        if (owner.randomnibble6plus24generator$generationLifecycle().closing()) {
            if (owner.randomnibble6plus24generator$generationLifecycle().active())
                callback.setReturnValue(ChunkHolder.UNLOADED_CHUNK_FUTURE);
            return;
        }
        if (status != ChunkStatus.EMPTY
                && mosaic$dependencies.get() == 0
                && MosaicWorldIdentity.runtimeContext(level).isPresent())
            owner.mosaic$physicalState().plan(status, holder.getPos());
    }

    // This native call synchronously schedules children. They are not new external targets.
    @Redirect(
            method = "scheduleChunkGeneration",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ChunkMap;getChunkRangeFuture(Lnet/minecraft/server/level/ChunkHolder;ILjava/util/function/IntFunction;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>>
            mosaic$dependencies(
                    ChunkMap map,
                    ChunkHolder holder,
                    int radius,
                    IntFunction<ChunkStatus> dependency) {
        if (!(level.getChunkSource().getGenerator()
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                        .MosaicChunkGenerator))
            return getChunkRangeFuture(holder, radius, dependency);
        int previous = mosaic$dependencies.get();
        mosaic$dependencies.set(previous + 1);
        try {
            return getChunkRangeFuture(holder, radius, dependency);
        } finally {
            if (previous == 0) mosaic$dependencies.remove();
            else mosaic$dependencies.set(previous);
        }
    }
}
