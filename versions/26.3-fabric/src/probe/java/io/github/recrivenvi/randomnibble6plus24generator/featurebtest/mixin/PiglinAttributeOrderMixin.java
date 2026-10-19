package io.github.recrivenvi.randomnibble6plus24generator.featurebtest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.PiglinAttributeOrderTrace;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class PiglinAttributeOrderMixin {
    @WrapOperation(
            method = "addAdditionalSaveData",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/ai/attributes/AttributeMap;pack()Ljava/util/List;"))
    private List<AttributeInstance.Packed> traceAttributeOrder(
            AttributeMap owner, Operation<List<AttributeInstance.Packed>> original) {
        return PiglinAttributeOrderTrace.observe(
                (LivingEntity) (Object) this, owner, original.call(owner));
    }
}
