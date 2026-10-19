package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.features.BArtifactHandoffVerifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSerializer.class)
abstract class ArtifactStructureSeedMixin {
    @WrapOperation(
            method = "unpackStructureStart",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/structure/StructureStart;loadStaticStart(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceSerializationContext;Lnet/minecraft/nbt/CompoundTag;J)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
    private static StructureStart seed(
            StructurePieceSerializationContext context,
            CompoundTag data,
            long seed,
            Operation<StructureStart> original) {
        BArtifactHandoffVerifier.recordLoadSeed(seed);
        return original.call(context, data, seed);
    }
}
