package io.github.recrivenvi.randomnibble6plus24generator.featurebtest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.MonsterRoomProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes the original return value without suppressing or changing the Vanilla warning. */
@Mixin(MonsterRoomFeature.class)
abstract class MonsterRoomProbeMixin {
    @WrapMethod(method = "place")
    private boolean trace(
            WorldGenLevel level,
            ChunkGenerator generator,
            RandomSource random,
            BlockPos origin,
            Operation<Boolean> original) {
        boolean traced = MonsterRoomProbe.begin(level, origin);
        try {
            return original.call(level, generator, random, origin);
        } finally {
            if (traced) MonsterRoomProbe.end();
        }
    }

    @WrapOperation(
            method = "place",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/WorldGenLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState read(WorldGenLevel level, BlockPos pos, Operation<BlockState> original) {
        var state = original.call(level, pos);
        MonsterRoomProbe.read("state", pos, state);
        return state;
    }

    @WrapOperation(
            method = "place",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/WorldGenLevel;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean empty(WorldGenLevel level, BlockPos pos, Operation<Boolean> original) {
        boolean value = original.call(level, pos);
        MonsterRoomProbe.read("empty", pos, value);
        return value;
    }

    @WrapOperation(
            method = "place",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/WorldGenLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity inspect(
            WorldGenLevel level, BlockPos pos, Operation<BlockEntity> original) {
        var entity = original.call(level, pos);
        MonsterRoomProbe.observe(level, pos, entity);
        return entity;
    }
}
