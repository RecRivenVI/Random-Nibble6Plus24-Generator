package io.github.recrivenvi.randomnibble6plus24generator.candidatetest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.candidatetest.CandidateContractVerifier;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class CandidateTestServerMixin {
    @Inject(method="setInitialSpawn",at=@At("HEAD"),cancellable=true)
    private static void noTerrain(ServerLevel level,ServerLevelData data,boolean bonus,boolean debug,LevelLoadListener listener,CallbackInfo ci){data.setInitialized(true);ci.cancel();}
    @Inject(method="prepareLevels",at=@At("HEAD"),cancellable=true)
    private void noPreparation(CallbackInfo ci){ci.cancel();}
    @Inject(method="createLevels",at=@At("RETURN"))
    private void verify(CallbackInfo ci){CandidateContractVerifier.run((MinecraftServer)(Object)this);}
}
