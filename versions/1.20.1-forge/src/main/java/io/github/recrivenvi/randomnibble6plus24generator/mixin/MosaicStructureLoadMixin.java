package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ChunkSerializer.class)
abstract class MosaicStructureLoadMixin {
    @Redirect(
            method = "read",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;unpackStructureStart(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceSerializationContext;Lnet/minecraft/nbt/CompoundTag;J)Ljava/util/Map;"))
    private static Map<Structure, StructureStart> mosaic$localStartSeed(
            StructurePieceSerializationContext context,
            CompoundTag data,
            long seed,
            ServerLevel level,
            PoiManager poi,
            ChunkPos owner,
            CompoundTag chunk) {
        if (!(level.getChunkSource().getGenerator()
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                        .MosaicChunkGenerator))
            return ChunkSerializerAccess.loadStarts(context, data, seed);
        // Native storage can finish a read while its level is closing. Server authority outlives
        // that drain; do not reopen a closed level or substitute the master seed.
        var runtime = MosaicWorldIdentity.runtimeContext(level.getServer());
        if (runtime.isPresent()
                && (!(level.getChunkSource().getGenerator()
                                instanceof
                                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                                                .MosaicChunkGenerator
                                        mosaic)
                        || !mosaic.profile().equals(runtime.orElseThrow().profile())))
            throw new IllegalStateException("Foreign structure-load identity");
        return ChunkSerializerAccess.loadStarts(
                context,
                data,
                runtime.isPresent()
                        ? runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), owner)
                        : seed);
    }
}
