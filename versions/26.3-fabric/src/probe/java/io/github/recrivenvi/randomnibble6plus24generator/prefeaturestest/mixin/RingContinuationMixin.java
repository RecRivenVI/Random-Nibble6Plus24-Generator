package io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.RingContinuationProbe;
import java.util.concurrent.Executor;
import net.minecraft.TracingExecutor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGeneratorStructureState.class)
abstract class RingContinuationMixin {
    @WrapOperation(
            method = "generateRingPositions",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/TracingExecutor;forName(Ljava/lang/String;)Ljava/util/concurrent/Executor;"))
    private Executor observeExecutor(
            TracingExecutor instance, String name, Operation<Executor> original) {
        return RingContinuationProbe.executor(original.call(instance, name));
    }

    @WrapOperation(
            method = "generateRingPositions",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/util/RandomSource;fork()Lnet/minecraft/util/RandomSource;"))
    private RandomSource observeFork(RandomSource instance, Operation<RandomSource> original) {
        var fork = original.call(instance);
        RingContinuationProbe.forked(instance);
        return fork;
    }
}
