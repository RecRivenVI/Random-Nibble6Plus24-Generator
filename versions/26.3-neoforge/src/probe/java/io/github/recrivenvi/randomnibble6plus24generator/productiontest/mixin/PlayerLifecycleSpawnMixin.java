package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.ClientLifecycleEvidence;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerSpawnFinder.class)
abstract class PlayerLifecycleSpawnMixin {
    @Inject(method = "findSpawn", at = @At("HEAD"))
    private static void find(
            ServerLevel level,
            BlockPos suggestion,
            CallbackInfoReturnable<CompletableFuture<Vec3>> callback) {
        ClientLifecycleEvidence.findSpawn(level);
    }
}
