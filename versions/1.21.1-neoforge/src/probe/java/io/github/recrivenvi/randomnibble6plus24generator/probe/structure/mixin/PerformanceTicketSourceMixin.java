package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
abstract class PerformanceTicketSourceMixin {
    @WrapOperation(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerPlayer;adjustSpawnLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos tokenNotPlayerSpawn(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position,
            Operation<BlockPos> original) {
        // This object is only an unregistered identity token. Real player constructors keep
        // Vanilla.
        if (!LegacyPerformanceProbe.constructingTicketSource())
            return original.call(player, level, position);
        LegacyPerformanceProbe.tokenRespawnSkipped();
        return position;
    }
}
