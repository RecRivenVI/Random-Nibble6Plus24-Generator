package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatus.class)
abstract class MosaicChunkStatusMixin {
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void mosaic$generate(
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager templates,
            ThreadedLevelLightEngine lighting,
            Function<
                            ChunkAccess,
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    full,
            List<ChunkAccess> neighbors,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    callback) {
        if (!(generator
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                        .MosaicChunkGenerator)) return;
        var status = (ChunkStatus) (Object) this;
        var owner = MosaicPhysicalLevelOwner.forLevel(level);
        if (owner.randomnibble6plus24generator$generationLifecycle().closing()) {
            if (owner.randomnibble6plus24generator$generationLifecycle().active())
                callback.setReturnValue(ChunkHolder.UNLOADED_CHUNK_FUTURE);
            return;
        }
        if (status == ChunkStatus.EMPTY || MosaicWorldIdentity.runtimeContext(level).isEmpty())
            return;
        var target = neighbors.get(neighbors.size() / 2);
        if (ChunkStatus.FEATURES.isOrAfter(status))
            callback.setReturnValue(owner.mosaic$physicalState().stage(status, target));
        else if (status == ChunkStatus.SPAWN)
            callback.setReturnValue(MosaicPhysicalSpawner.spawn(level, target));
    }

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void mosaic$load(
            ServerLevel level,
            StructureTemplateManager templates,
            ThreadedLevelLightEngine lighting,
            Function<
                            ChunkAccess,
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    full,
            ChunkAccess target,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    callback) {
        if (!(level.getChunkSource().getGenerator()
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                        .MosaicChunkGenerator)) return;
        var owner = MosaicPhysicalLevelOwner.forLevel(level);
        if (owner.randomnibble6plus24generator$generationLifecycle().closing()
                && owner.randomnibble6plus24generator$generationLifecycle().active()) {
            callback.setReturnValue(ChunkHolder.UNLOADED_CHUNK_FUTURE);
            return;
        }
        var status = (ChunkStatus) (Object) this;
        if (status != ChunkStatus.EMPTY
                && ChunkStatus.FEATURES.isOrAfter(status)
                && MosaicWorldIdentity.runtimeContext(level).isPresent())
            callback.setReturnValue(owner.mosaic$physicalState().stage(status, target));
    }

    @Inject(method = "generate", at = @At("RETURN"), cancellable = true)
    private void mosaic$generated(
            Executor executor,
            ServerLevel level,
            ChunkGenerator generator,
            StructureTemplateManager templates,
            ThreadedLevelLightEngine lighting,
            Function<
                            ChunkAccess,
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    full,
            List<ChunkAccess> neighbors,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    callback) {
        observe(level, callback);
    }

    @Inject(method = "load", at = @At("RETURN"), cancellable = true)
    private void mosaic$loaded(
            ServerLevel level,
            StructureTemplateManager templates,
            ThreadedLevelLightEngine lighting,
            Function<
                            ChunkAccess,
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    full,
            ChunkAccess target,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    callback) {
        observe(level, callback);
    }

    private void observe(
            ServerLevel level,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    callback) {
        if (!(level.getChunkSource().getGenerator()
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                        .MosaicChunkGenerator)) return;
        var lifecycle =
                (MosaicPhysicalLevelOwner.forLevel(level))
                        .randomnibble6plus24generator$generationLifecycle();
        var status = (ChunkStatus) (Object) this;
        if (status != ChunkStatus.EMPTY
                && !lifecycle.closing()
                && MosaicWorldIdentity.runtimeContext(level).isPresent())
            callback.setReturnValue(
                    (MosaicPhysicalLevelOwner.forLevel(level))
                            .mosaic$physicalState()
                            .observe(status, callback.getReturnValue()));
    }
}
