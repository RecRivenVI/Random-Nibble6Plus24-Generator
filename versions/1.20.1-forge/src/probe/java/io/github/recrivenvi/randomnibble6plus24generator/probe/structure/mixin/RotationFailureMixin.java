package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.mojang.math.OctahedralGroup;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeRotationVerifier;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OctahedralGroup.class)
abstract class RotationFailureMixin {
    @Inject(
            method = "rotate(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/Direction;",
            at = @At("HEAD"))
    private void fail(Direction input, CallbackInfoReturnable<Direction> ci) {
        NativeRotationVerifier.maybeFail((OctahedralGroup) (Object) this);
    }
}
