package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.terrain.NativeTerrainVerifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class StructureServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void noDecoration(
            ServerLevel level,
            ServerLevelData data,
            boolean bonus,
            boolean debug,
            CallbackInfo callback) {
        if (!NativeStructureVerifier.enabled()
                && !NativeTerrainVerifier.enabled()
                && !NativeFeatureVerifier.enabled()) return;
        data.setInitialized(true);
        callback.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void noSpawnPreparation(CallbackInfo callback) {
        if (NativeStructureVerifier.enabled()
                || NativeTerrainVerifier.enabled()
                || NativeFeatureVerifier.enabled()) callback.cancel();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void verify(CallbackInfo callback) {
        if (NativeStructureVerifier.enabled())
            NativeStructureVerifier.run((MinecraftServer) (Object) this);
        else if (NativeTerrainVerifier.enabled())
            NativeTerrainVerifier.run((MinecraftServer) (Object) this);
        else if (NativeFeatureVerifier.enabled())
            NativeFeatureVerifier.run((MinecraftServer) (Object) this);
    }
}
