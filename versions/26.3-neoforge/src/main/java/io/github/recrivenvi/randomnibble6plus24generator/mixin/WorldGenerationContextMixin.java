package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The height-provider context must not obtain its generator through the physical level. */
@Mixin(WorldGenerationContext.class)
abstract class WorldGenerationContextMixin {

    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$useSessionGenerator(
            LevelAccessor level, CallbackInfoReturnable<WorldGenerationContext> callback) {
        if (level instanceof WorldGenRegion region) {
            GenerationContextRegistry.find(region)
                    .ifPresent(
                            context ->
                                    callback.setReturnValue(
                                            new WorldGenerationContext(
                                                    context.generator(), region)));
        }
    }
}
