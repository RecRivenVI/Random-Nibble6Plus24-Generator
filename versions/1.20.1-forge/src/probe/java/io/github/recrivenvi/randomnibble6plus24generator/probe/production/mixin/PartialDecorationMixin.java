package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.PartialPreLightProbe;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class PartialDecorationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void enter(
            WorldGenLevel region,
            ChunkAccess chunk,
            StructureManager manager,
            CallbackInfo callback) {
        PartialPreLightProbe.decoration(region, chunk, true);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void leave(
            WorldGenLevel region,
            ChunkAccess chunk,
            StructureManager manager,
            CallbackInfo callback) {
        PartialPreLightProbe.decoration(region, chunk, false);
    }
}
