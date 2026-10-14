package io.github.recrivenvi.randomnibble6plus24generator.featurectest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCAppendVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCEditProtectionVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCPhysicalVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCPreLightVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeFeatureRepeatVerifier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStep.class)
abstract class FeatureCNativeStageMixin {
    @Shadow @Final private ChunkStatus targetStatus;

    @Inject(method = "apply", at = @At("HEAD"))
    private void observe(
            WorldGenContext context,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> ci) {
        NativeFeatureRepeatVerifier.stage(context, targetStatus);
        FeatureCPhysicalVerifier.stage(context, targetStatus, chunk);
        FeatureCEditProtectionVerifier.stage(context, targetStatus, chunk);
        FeatureCPreLightVerifier.stage(context, targetStatus, chunk);
        FeatureCAppendVerifier.stage(context, targetStatus, chunk);
    }
}
