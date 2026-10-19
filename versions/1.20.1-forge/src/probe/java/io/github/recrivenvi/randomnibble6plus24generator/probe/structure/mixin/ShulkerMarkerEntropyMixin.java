package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.ShulkerYawCausality;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EndCityPieces.EndCityPiece.class)
abstract class ShulkerMarkerEntropyMixin {
    @Redirect(
            method = "handleDataMarker",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"))
    private Entity created(
            EntityType<?> type,
            Level level,
            String name,
            BlockPos pos,
            ServerLevelAccessor region,
            RandomSource random,
            BoundingBox box)
            throws Exception {
        // Keep the native getLevel caller unchanged; do not weaken its production escape guard.
        try (var scope = ShulkerYawCausality.marker(name, pos)) {
            var entity = type.create(level);
            ShulkerYawCausality.created(entity);
            return entity;
        }
    }
}
