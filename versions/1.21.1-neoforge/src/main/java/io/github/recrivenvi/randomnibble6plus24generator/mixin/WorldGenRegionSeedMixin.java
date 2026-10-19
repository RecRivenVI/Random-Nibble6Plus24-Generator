package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionSeedMixin {
    @WrapOperation(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long localSeed(ServerLevel level, Operation<Long> original) {
        var context = SeedLocalRegionScope.current(level);
        if (context != null) return context.seed();
        var spawn =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                        .SpawnConstructorScope.current();
        if (spawn != null && spawn.host() != level)
            throw new IllegalStateException("SPAWN region belongs to another world");
        return spawn == null ? original.call(level) : spawn.seed();
    }

    @WrapOperation(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerChunkCache;randomState()Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState localRandomState(ServerChunkCache chunks, Operation<RandomState> original) {
        var context = SeedLocalRegionScope.current(chunks);
        if (context != null) return context.randomState();
        var spawn =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                        .SpawnConstructorScope.current();
        if (spawn != null && spawn.host().getChunkSource() != chunks)
            throw new IllegalStateException("SPAWN random state belongs to another world");
        return spawn == null ? original.call(chunks) : spawn.randomState();
    }
}
