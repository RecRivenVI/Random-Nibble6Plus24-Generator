package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalPreFeaturesPreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A/B control only; ordinary production does not read this property. */
@Mixin(value = PhysicalPreFeaturesPreparation.class, remap = false)
abstract class PerfPhysicalBiomeMixin {
    @WrapOperation(
            method = "lambda$request$1",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/CompactStructureInputs;resolve(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/IsolatedGenerationContext;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/ScopedStructureSources;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/CompactStructureInputs;"))
    private static CompactStructureInputs structureControl(
            IsolatedGenerationContext context,
            ScopedStructureSources sources,
            Operation<CompactStructureInputs> original) {
        var result =
                original.call(
                        context,
                        Boolean.getBoolean("mosaic.profile.referenceBiomeLocks") ? null : sources);
        PerfTrace.event(
                context.hostLevel(),
                context.target(),
                "PHYSICAL_BIOMES_STRUCTURE_READY",
                "narrow=" + result.metrics().narrowTemplateLocks());
        return result;
    }

    @WrapMethod(method = "supported")
    private static boolean reference(ServerLevel level, Operation<Boolean> original) {
        return !Boolean.getBoolean("mosaic.profile.bReferencePhysicalBiomes")
                && original.call(level);
    }

    @WrapMethod(method = "terrainSupported")
    private static boolean terrainReference(ServerLevel level, Operation<Boolean> original) {
        return !Boolean.getBoolean("mosaic.profile.bReferencePhysicalTerrain")
                && original.call(level);
    }

    @WrapMethod(
            method =
                    "request(Lnet/minecraft/server/level/ServerLevel;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/materialization/MosaicPhysicalMaterializer$GenerationKey;Lnet/minecraft/world/level/chunk/status/ChunkStatus;)Ljava/util/concurrent/CompletableFuture;")
    private static CompletableFuture<ProtoChunk> measure(
            ServerLevel level,
            MosaicPhysicalMaterializer.GenerationKey key,
            ChunkStatus status,
            Operation<CompletableFuture<ProtoChunk>> original) {
        String prefix = status == ChunkStatus.BIOMES ? "PHYSICAL_BIOMES_" : "PHYSICAL_TERRAIN_";
        PerfTrace.event(level, key.pos(), prefix + "REQUEST", "");
        var future = original.call(level, key, status);
        future.whenComplete(
                (chunk, failure) ->
                        PerfTrace.event(
                                level,
                                key.pos(),
                                prefix + (failure == null ? "READY" : "FAILURE"),
                                ""));
        return future;
    }
}
