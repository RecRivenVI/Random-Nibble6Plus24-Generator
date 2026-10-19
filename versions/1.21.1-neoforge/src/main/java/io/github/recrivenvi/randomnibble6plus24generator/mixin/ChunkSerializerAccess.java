package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Native codecs/loaders without ChunkSerializer.write's physical light-engine reads. */
@Mixin(ChunkSerializer.class)
public interface ChunkSerializerAccess {
    @Accessor("BLOCK_STATE_CODEC")
    static Codec<PalettedContainer<BlockState>> blockStates() {
        throw new AssertionError();
    }

    @Invoker("makeBiomeCodec")
    static Codec<PalettedContainerRO<Holder<Biome>>> biomes(Registry<Biome> registry) {
        throw new AssertionError();
    }

    @Invoker("packStructureData")
    static CompoundTag packStructures(
            StructurePieceSerializationContext context,
            ChunkPos pos,
            Map<Structure, StructureStart> starts,
            Map<Structure, LongSet> references) {
        throw new AssertionError();
    }

    @Invoker("unpackStructureStart")
    static Map<Structure, StructureStart> loadStarts(
            StructurePieceSerializationContext context, CompoundTag data, long seed) {
        throw new AssertionError();
    }

    @Invoker("unpackStructureReferences")
    static Map<Structure, LongSet> loadReferences(
            RegistryAccess registry, ChunkPos pos, CompoundTag data) {
        throw new AssertionError();
    }
}
