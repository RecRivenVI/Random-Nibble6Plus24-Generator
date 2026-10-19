package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.probe.features.ShulkerYawCausality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCityPieces.EndCityPiece.class)
abstract class ShulkerMarkerEntropyMixin {
    @WrapOperation(
            method = "handleDataMarker",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private Entity created(
            EntityType<?> type,
            Level level,
            Operation<Entity> original,
            @Local(argsOnly = true) String name,
            @Local(argsOnly = true) BlockPos pos)
            throws Exception {
        // Keep the native getLevel caller unchanged; do not weaken its production escape guard.
        try (var scope = ShulkerYawCausality.marker(name, pos)) {
            var entity = original.call(type, level);
            ShulkerYawCausality.created(entity);
            return entity;
        }
    }
}
