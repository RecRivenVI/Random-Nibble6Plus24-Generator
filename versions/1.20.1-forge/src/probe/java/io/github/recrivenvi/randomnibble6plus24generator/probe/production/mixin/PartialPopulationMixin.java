package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.PartialPreLightProbe;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class PartialPopulationMixin {
    @Inject(method = "spawnOriginalMobs", at = @At("HEAD"))
    private void observe(WorldGenRegion region, CallbackInfo callback) {
        PartialPreLightProbe.population(region);
    }
}
