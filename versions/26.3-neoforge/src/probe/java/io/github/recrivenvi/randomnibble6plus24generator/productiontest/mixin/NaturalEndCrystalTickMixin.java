package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.NaturalRuntimeEvidence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndCrystal.class)
abstract class NaturalEndCrystalTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void observed(CallbackInfo callback) {
        NaturalRuntimeEvidence.endCrystalTick((Entity) (Object) this);
    }
}
