package io.github.recrivenvi.randomnibble6plus24generator.featureatest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureDesignComparison;
import java.util.function.BiConsumer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LightweightTerrainGenerator.class, remap = false)
abstract class FeatureComparisonTerrainMixin {
    @Inject(method = "generate", at = @At("RETURN"))
    private static void completed(
            IsolatedGenerationContext context,
            CompactStructureInputs structures,
            BiConsumer<ChunkStatus, ChunkAccess> checkpoint,
            CallbackInfoReturnable<LightweightTerrainGenerator.Result> cir) {
        FeatureDesignComparison.terrain(context, cir.getReturnValue().target());
    }
}
