package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IsolatedGenerationContext.class, remap = false)
abstract class LazyContextMixin {
    @WrapOperation(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;createState(Lnet/minecraft/core/HolderLookup;Lnet/minecraft/world/level/levelgen/RandomState;J)Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"))
    private ChunkGeneratorStructureState state(
            NoiseBasedChunkGenerator generator,
            HolderLookup<StructureSet> sets,
            RandomState random,
            long seed,
            Operation<ChunkGeneratorStructureState> original,
            @Local(argsOnly = true) ChunkPos target) {
        ChunkGeneratorStructureState state = original.call(generator, sets, random, seed);
        if (Boolean.getBoolean("mosaic.lazy.experiment")) {
            TargetStructureScope scope =
                    new TargetStructureScope(
                            target,
                            TargetStructureScope.v2Radius(),
                            seed,
                            Boolean.getBoolean("mosaic.lazy.enabled"));
            ((LazyStateAccess) state).mosaicLazyScope(scope);
            LazyScopes.bind(random, scope);
        }
        return state;
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void closed(CallbackInfo ci) {
        LazyScopes.remove(((IsolatedGenerationContext) (Object) this).randomState());
    }
}
