package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.ColdSpawnEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.ProductionChunkVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.RuntimeLifecycleEvidence;
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
                && !NativeFeatureVerifier.enabled()
                && !ProductionChunkVerifier.enabled()
                && !LegacyPerformanceProbe.enabled()
                && !ColdSpawnEvidence.enabled()) return;
        data.setInitialized(true);
        callback.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void noSpawnPreparation(CallbackInfo callback) {
        if (NativeStructureVerifier.enabled()
                || NativeTerrainVerifier.enabled()
                || NativeFeatureVerifier.enabled()
                || ProductionChunkVerifier.enabled()
                || LegacyPerformanceProbe.enabled()
                || ColdSpawnEvidence.enabled()) callback.cancel();
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

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void production(java.util.function.BooleanSupplier time, CallbackInfo callback) {
        var server = (MinecraftServer) (Object) this;
        if (ColdSpawnEvidence.enabled()) ColdSpawnEvidence.tick(server);
        else if (LegacyPerformanceProbe.enabled()) LegacyPerformanceProbe.tick(server);
        else if (RuntimeLifecycleEvidence.enabled())
            RuntimeLifecycleEvidence.tick(server, () -> ProductionChunkVerifier.tick(server));
        else ProductionChunkVerifier.tick(server);
    }
}
