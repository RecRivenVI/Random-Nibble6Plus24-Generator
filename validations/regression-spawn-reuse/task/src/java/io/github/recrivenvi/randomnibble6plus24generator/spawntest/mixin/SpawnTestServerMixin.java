package io.github.recrivenvi.randomnibble6plus24generator.spawntest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.spawntest.SpawnReuseVerifier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 500)
abstract class SpawnTestServerMixin {
    @Inject(method="setInitialSpawn", at=@At("HEAD"), cancellable=true)
    private static void noUnmeasuredSpawn(ServerLevel level, ServerLevelData data, boolean bonus,
            boolean debug, LevelLoadListener listener, CallbackInfo ci) { data.setInitialized(true); ci.cancel(); }
    @Inject(method="prepareLevels", at=@At("HEAD"), cancellable=true)
    private void noPreparation(CallbackInfo ci) { ci.cancel(); }
    @Inject(method="tickServer", at=@At("RETURN"))
    private void run(java.util.function.BooleanSupplier time, CallbackInfo ci) {
        SpawnReuseVerifier.run((MinecraftServer)(Object)this);
    }
}
