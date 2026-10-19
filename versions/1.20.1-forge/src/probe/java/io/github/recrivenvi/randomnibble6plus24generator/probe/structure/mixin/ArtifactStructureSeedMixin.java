package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.BArtifactHandoffVerifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureStart.class)
abstract class ArtifactStructureSeedMixin {
    @Inject(method = "loadStaticStart", at = @At("HEAD"))
    private static void observe(
            StructurePieceSerializationContext context,
            CompoundTag tag,
            long seed,
            CallbackInfoReturnable<StructureStart> callback) {
        BArtifactHandoffVerifier.recordLoadSeed(seed);
    }
}
