package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PoiManager.class)
abstract class FeaturePoiAccessMixin {
    @Inject(method = "add", at = @At("HEAD"))
    private void add(BlockPos pos, Holder<PoiType> type, CallbackInfo callback) {
        NativeFeatureVerifier.checkPhysicalRequest("poi:add", pos.getX() >> 4, pos.getZ() >> 4);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void remove(BlockPos pos, CallbackInfo callback) {
        NativeFeatureVerifier.checkPhysicalRequest("poi:remove", pos.getX() >> 4, pos.getZ() >> 4);
    }
}
