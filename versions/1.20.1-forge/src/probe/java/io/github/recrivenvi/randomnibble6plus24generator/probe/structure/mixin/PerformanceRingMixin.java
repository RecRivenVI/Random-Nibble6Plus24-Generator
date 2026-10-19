package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGeneratorStructureState.class)
abstract class PerformanceRingMixin {
    @Inject(method = "generateRingPositions", at = @At("HEAD"))
    private void begin(
            Holder<StructureSet> set,
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> ci) {
        LegacyPerformanceProbe.ring(false);
    }

    @ModifyArg(
            method = "generateRingPositions",
            index = 0,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private Supplier<ChunkPos> ringTask(Supplier<ChunkPos> original) {
        return LegacyPerformanceProbe.ringTask(original);
    }
}
