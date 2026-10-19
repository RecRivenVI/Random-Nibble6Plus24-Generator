package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native placement casts to ProtoChunk. Adapt only our explicit capability, not Vanilla. */
@Mixin(PlacementContext.class)
abstract class PlacementCarvingMaskMixin {
    @Shadow @Final private WorldGenLevel level;

    @Inject(method = "getCarvingMask", at = @At("HEAD"), cancellable = true)
    private void localMask(
            ChunkPos pos,
            GenerationStep.Carving stage,
            CallbackInfoReturnable<CarvingMask> callback) {
        if (level instanceof StrictFeatureRegion region)
            callback.setReturnValue(region.carvingMask(pos, stage));
    }
}
