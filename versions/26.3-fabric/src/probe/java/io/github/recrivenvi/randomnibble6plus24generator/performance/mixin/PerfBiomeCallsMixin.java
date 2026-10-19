package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
abstract class PerfBiomeCallsMixin {
    @Inject(method = "createBiomes", at = @At("HEAD"))
    private void count(
            RandomState state,
            Blender blender,
            StructureManager structures,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (PerfTrace.currentKey() != null) PerfTrace.count("b.nativeBiomeCalls", 1);
    }
}
