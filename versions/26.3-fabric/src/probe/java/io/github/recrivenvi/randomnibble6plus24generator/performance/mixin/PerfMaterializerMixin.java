package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MosaicPhysicalMaterializer.class, remap = false)
abstract class PerfMaterializerMixin {
    @WrapMethod(method = "registerPhysicalRequest")
    private static void plan(
            ServerLevel level, ChunkStatus status, ChunkPos pos, Operation<Void> original) {
        var span = PerfTrace.begin(level, pos, "PLAN");
        PerfTrace.planLevel(level);
        try {
            original.call(level, status, pos);
        } finally {
            PerfTrace.planLevel(null);
            PerfTrace.end(level, pos, "PLAN", span);
        }
    }

    @WrapMethod(method = "requestPrepared")
    private static CompletableFuture<PreparedMaterialization> request(
            ServerLevel level,
            ChunkPos pos,
            Operation<CompletableFuture<PreparedMaterialization>> original) {
        PerfTrace.event(level, pos, "ARTIFACT_REQUEST", "");
        return original.call(level, pos);
    }

    @WrapMethod(method = "prepare")
    private static PreparedMaterialization prepare(
            ServerLevel level,
            ChunkPos pos,
            GenerationKey key,
            Operation<PreparedMaterialization> original) {
        var span = PerfTrace.begin(level, pos, "PREPARE");
        PerfTrace.active(level, pos, true);
        PerfTrace.purpose("canonical");
        try {
            var result = original.call(level, pos, key);
            PerfTrace.event(
                    level,
                    pos,
                    "PREPARED_METRICS",
                    "isolated="
                            + result.isolatedNanos()
                            + ";capture="
                            + result.artifactNanos()
                            + ";rehydrate="
                            + result.rehydrateNanos()
                            + ";total="
                            + result.totalNanos());
            return result;
        } finally {
            PerfTrace.purpose(null);
            PerfTrace.active(level, pos, false);
            PerfTrace.end(level, pos, "PREPARE", span);
        }
    }

    @WrapMethod(method = "completePhysicalRequest")
    private static void clear(GenerationKey key, Operation<Void> original) {
        ServerLevel level = key.server().getLevel(key.dimension());
        var span = PerfTrace.begin(level, key.pos(), "PLAN_CLEANUP");
        try {
            original.call(key);
        } finally {
            PerfTrace.end(level, key.pos(), "PLAN_CLEANUP", span);
        }
    }
}
