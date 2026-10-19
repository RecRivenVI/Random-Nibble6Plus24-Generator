package io.github.recrivenvi.randomnibble6plus24generator.featurebtest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.NativeFeatureBControl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.variant.StructureCheck;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the actual native predicate, with its ordinary native physical structure authority. */
@Mixin(StructureCheck.class)
abstract class IndependentNativeStructureMixin {
    @WrapOperation(
            method = "test(Lnet/minecraft/world/entity/variant/SpawnContext;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/ServerLevelAccessor;getLevel()Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel nativeStructureLevel(
            ServerLevelAccessor accessor, Operation<ServerLevel> original) {
        var nativeLevel = NativeFeatureBControl.nativeStructureLevel(accessor);
        return nativeLevel != null ? nativeLevel : original.call(accessor);
    }
}
