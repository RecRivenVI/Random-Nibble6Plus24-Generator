package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Minimal access to Vanilla's structure NBT codec for detached chunk transport. */
@Mixin(SerializableChunkData.class)
public interface SerializableChunkDataInvoker {

    @Invoker("packStructureData")
    static CompoundTag randomnibble6plus24generator$packStructureData(
            StructurePieceSerializationContext context,
            ChunkPos chunkPos,
            Map<Structure, StructureStart> starts,
            Map<Structure, LongSet> references) {
        throw new AssertionError();
    }

    @Invoker("unpackStructureStart")
    static Map<Structure, StructureStart> randomnibble6plus24generator$unpackStructureStarts(
            StructurePieceSerializationContext context,
            CompoundTag structureData,
            long localWorldSeed) {
        throw new AssertionError();
    }

    @Invoker("unpackStructureReferences")
    static Map<Structure, LongSet> randomnibble6plus24generator$unpackStructureReferences(
            RegistryAccess registryAccess,
            ChunkPos chunkPos,
            CompoundTag structureData) {
        throw new AssertionError();
    }
}
