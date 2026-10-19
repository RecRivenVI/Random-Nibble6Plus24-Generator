package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.ShulkerYawCausality;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
abstract class ShulkerConstructorEntropyMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/Math;random()D"))
    private double nativeDraw() {
        // Still consumes the original global stream once, even for diagnostic replay.
        return ShulkerYawCausality.draw((LivingEntity) (Object) this, Math.random());
    }
}
