package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSerializer.class)
abstract class MosaicStructureReloadMixin {
    @WrapOperation(
            method = "read",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;unpackStructureStart(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceSerializationContext;Lnet/minecraft/nbt/CompoundTag;J)Ljava/util/Map;"))
    private static Map<Structure, StructureStart> localSeed(
            StructurePieceSerializationContext context,
            CompoundTag tag,
            long seed,
            Operation<Map<Structure, StructureStart>> original,
            @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) ChunkPos owner) {
        var runtime = MosaicWorldIdentity.runtimeContext(level);
        return original.call(
                context,
                tag,
                runtime.isPresent()
                        ? runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), owner)
                        : seed);
    }
}
