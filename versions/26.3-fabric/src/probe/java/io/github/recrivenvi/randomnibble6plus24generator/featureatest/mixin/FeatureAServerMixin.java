package io.github.recrivenvi.randomnibble6plus24generator.featureatest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureAVerifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class FeatureAServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void noSpawn(
            ServerLevel level,
            ServerLevelData data,
            boolean bonus,
            boolean debug,
            LevelLoadListener listener,
            CallbackInfo ci) {
        data.setInitialized(true);
        ci.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void noPrepare(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void verify(CallbackInfo ci) {
        if (Boolean.getBoolean("featurea.compare"))
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify
                    .FeatureDesignComparison.run((MinecraftServer) (Object) this);
        else if (Boolean.getBoolean("featurea.trees"))
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureATreeVerifier
                    .run((MinecraftServer) (Object) this);
        else FeatureAVerifier.run((MinecraftServer) (Object) this);
    }
}
