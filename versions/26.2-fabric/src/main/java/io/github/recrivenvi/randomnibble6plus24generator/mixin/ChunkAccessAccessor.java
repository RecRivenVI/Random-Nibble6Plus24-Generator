package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Verification-only access to Vanilla's pending BlockEntity NBT map. */
@Mixin(ChunkAccess.class)
public interface ChunkAccessAccessor {

    @Accessor("pendingBlockEntities")
    Map<BlockPos, CompoundTag> randomnibble6plus24generator$getPendingBlockEntities();
}
