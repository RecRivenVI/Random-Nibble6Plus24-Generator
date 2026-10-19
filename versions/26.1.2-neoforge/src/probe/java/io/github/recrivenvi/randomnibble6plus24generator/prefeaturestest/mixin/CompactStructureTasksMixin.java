package io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PreFeaturesVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedChunkStatusTasks;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** New input consumer only in the independent validation, never a production scheduler hook. */
@Mixin(IsolatedChunkStatusTasks.class)
abstract class CompactStructureTasksMixin {
    @Inject(method = "generateStructureStarts", at = @At("HEAD"), cancellable = true)
    private static void starts(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        var inputs = PreFeaturesVerifier.compactInputs(chunk);
        if (inputs == null) return;
        inputs.copyStartsTo(chunk);
        context.recordStage(ChunkStatus.STRUCTURE_STARTS);
        context.structureCheck().onStructureLoad(chunk.getPos(), chunk.getAllStarts());
        callback.setReturnValue(CompletableFuture.completedFuture(chunk));
    }

    @Inject(method = "generateStructureReferences", at = @At("HEAD"), cancellable = true)
    private static void references(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        var inputs = PreFeaturesVerifier.compactInputs(chunk);
        if (inputs == null) return;
        inputs.copyReferencesTo(chunk);
        context.recordStage(ChunkStatus.STRUCTURE_REFERENCES);
        callback.setReturnValue(CompletableFuture.completedFuture(chunk));
    }

    @WrapOperation(
            method = {"generateBiomes", "generateNoise", "generateSurface", "generateCarvers"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/StructureManager;forWorldGenRegion(Lnet/minecraft/server/level/WorldGenRegion;)Lnet/minecraft/world/level/StructureManager;"))
    private static StructureManager manager(
            StructureManager manager,
            WorldGenRegion region,
            Operation<StructureManager> original,
            @Local(argsOnly = true) ChunkAccess chunk) {
        var inputs = PreFeaturesVerifier.compactInputs(chunk);
        return inputs == null ? original.call(manager, region) : inputs.manager();
    }
}
