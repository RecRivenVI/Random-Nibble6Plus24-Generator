package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlacementContext.class)
abstract class FeatureCarvingReadMixin {
    @WrapMethod(method = "getCarvingMask")
    private CarvingMask read(
            ChunkPos pos, GenerationStep.Carving stage, Operation<CarvingMask> original) {
        var value = original.call(pos, stage);
        NativeFeatureVerifier.carvingMaskRead(pos, stage, value);
        return value;
    }
}
