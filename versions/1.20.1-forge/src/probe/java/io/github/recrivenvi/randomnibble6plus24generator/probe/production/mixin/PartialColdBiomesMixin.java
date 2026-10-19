package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.PartialPreLightProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalSpawner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MosaicPhysicalSpawner.class, remap = false)
abstract class PartialColdBiomesMixin {
    @Inject(method = "coldBiomes", at = @At("RETURN"), remap = false)
    private static void observe(
            ServerLevel host,
            long seed,
            ChunkPos target,
            MosaicWorldProfile profile,
            CallbackInfoReturnable<SpawnBiomeSnapshot> callback) {
        PartialPreLightProbe.coldBiomes(host, target, callback.getReturnValue());
    }
}
