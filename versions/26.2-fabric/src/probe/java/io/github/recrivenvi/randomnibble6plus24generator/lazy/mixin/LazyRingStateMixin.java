package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGeneratorStructureState.class)
abstract class LazyRingStateMixin implements LazyStateAccess {
    @Unique private TargetStructureScope scope;

    @Override
    public TargetStructureScope mosaicLazyScope() {
        return scope;
    }

    @Override
    public void mosaicLazyScope(TargetStructureScope scope) {
        this.scope = scope;
    }

    @WrapOperation(
            method = "generateRingPositions",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkPos> job(
            Supplier<ChunkPos> work,
            Executor executor,
            Operation<CompletableFuture<ChunkPos>> original,
            @Local(name = "initialX") int x,
            @Local(name = "initialZ") int z) {
        return scope == null || Boolean.getBoolean("mosaic.repairC")
                ? original.call(work, executor)
                : scope.ringJob(x, z, work, executor);
    }

    @WrapMethod(method = "generateRingPositions")
    private CompletableFuture<List<ChunkPos>> positions(
            Holder<StructureSet> set,
            ConcentricRingsStructurePlacement placement,
            Operation<CompletableFuture<List<ChunkPos>>> original) {
        if (scope == null || Boolean.getBoolean("mosaic.repairC"))
            return original.call(set, placement);
        scope.count("ringLayouts", 1);
        long start = System.nanoTime();
        return original.call(set, placement)
                .thenApply(
                        list -> {
                            scope.count("ringWallNanos", System.nanoTime() - start);
                            return scope.ringResult(list);
                        });
    }
}
