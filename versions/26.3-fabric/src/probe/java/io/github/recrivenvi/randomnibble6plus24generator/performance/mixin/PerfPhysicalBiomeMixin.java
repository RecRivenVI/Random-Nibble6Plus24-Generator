package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalBiomePreparation;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;

/** A/B control only; ordinary production does not read this property. */
@Mixin(value = PhysicalBiomePreparation.class, remap = false)
abstract class PerfPhysicalBiomeMixin {
    @WrapMethod(method = "supported")
    private static boolean reference(ServerLevel level, Operation<Boolean> original) {
        return !Boolean.getBoolean("mosaic.profile.bReferencePhysicalBiomes")
                && original.call(level);
    }

    @WrapMethod(method = "request")
    private static CompletableFuture<ProtoChunk> measure(
            ServerLevel level,
            MosaicPhysicalMaterializer.GenerationKey key,
            Operation<CompletableFuture<ProtoChunk>> original) {
        PerfTrace.event(level, key.pos(), "PHYSICAL_BIOMES_REQUEST", "");
        var future = original.call(level, key);
        future.whenComplete(
                (chunk, failure) ->
                        PerfTrace.event(
                                level,
                                key.pos(),
                                failure == null
                                        ? "PHYSICAL_BIOMES_READY"
                                        : "PHYSICAL_BIOMES_FAILURE",
                                ""));
        return future;
    }
}
