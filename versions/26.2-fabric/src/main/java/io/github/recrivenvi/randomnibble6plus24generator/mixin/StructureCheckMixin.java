package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Keeps entity-variant structure predicates inside the active virtual region. */
@Mixin(StructureCheck.class)
abstract class StructureCheckMixin {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$routeVirtualStructureCheck(
            SpawnContext context,
            CallbackInfoReturnable<Boolean> callback) {
        if (!(context.level() instanceof WorldGenRegion region)) return;
        GenerationContextRegistry.find(region).ifPresent(isolated -> {
            StructureStart start = isolated.structureManager()
                    .forWorldGenRegion(region)
                    .getStructureWithPieceAt(context.pos(), ((StructureCheck) (Object) this).requiredStructures());
            callback.setReturnValue(start.isValid());
        });
    }
}
