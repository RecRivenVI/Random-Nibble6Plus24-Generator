package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;

/** Counts Vanilla LevelChunk block-entity ticker invocations during the runtime smoke. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper")
abstract class BlockEntityTickerTraceMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceTicker(CallbackInfo callbackInfo) {
        PhysicalMosaicTrace.recordRuntimeTick("block_entity_ticker");
    }
}
