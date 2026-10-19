package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.ColdSpawnEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalSpawner;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MosaicPhysicalSpawner.class, remap = false)
abstract class ColdSpawnMixin {
    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private static void holdNativeLight(
            WorldGenContext world,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess target,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        var held = ColdSpawnEvidence.pause(world.level(), target);
        if (held != null) callback.setReturnValue(held);
    }

    @WrapOperation(
            method = "spawn",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;spawnOriginalMobs(Lnet/minecraft/server/level/WorldGenRegion;)V"))
    private static void observeAfterOriginalPopulation(
            NoiseBasedChunkGenerator generator, WorldGenRegion region, Operation<Void> original) {
        original.call(generator, region);
        ColdSpawnEvidence.afterPopulation(region);
    }
}
