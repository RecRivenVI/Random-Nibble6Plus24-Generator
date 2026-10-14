package io.github.recrivenvi.randomnibble6plus24generator.featurectest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCPreLightVerifier;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(SerializableChunkData.class)
abstract class FeatureCStructureReadMixin {
    @Shadow @Final private ChunkPos chunkPos;

    @WrapOperation(
            method = "read",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;unpackStructureStart(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceSerializationContext;Lnet/minecraft/nbt/CompoundTag;J)Ljava/util/Map;"))
    private Map<Structure, StructureStart> localSeed(
            StructurePieceSerializationContext context,
            CompoundTag data,
            long seed,
            Operation<Map<Structure, StructureStart>> original,
            @Local(argsOnly = true) ServerLevel level) {
        return original.call(
                context, data, FeatureCPreLightVerifier.structureSeed(level, chunkPos, seed));
    }
}
