package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = IsolatedGenerationContext.class, remap = false)
abstract class PerfContextMixin {
    @org.spongepowered.asm.mixin.Shadow private boolean closed;

    @org.spongepowered.asm.mixin.injection.Inject(
            method = "close",
            at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void ringMetrics(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (closed) return;
        var context = (IsolatedGenerationContext) (Object) this;
        var scope =
                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                        .ConcentricRingStateAccess)
                                context.structureState())
                        .randomnibble6plus24generator$ringScope();
        if (scope != null) {
            var metrics = scope.metrics();
            PerfTrace.count("production.rings.scopes", 1);
            PerfTrace.count("production.rings.slots", metrics.slotsConsidered());
            PerfTrace.count("production.rings.biomeSearches", metrics.biomeSearches());
            PerfTrace.count("production.rings.skipped", metrics.searchesSkipped());
            PerfTrace.count("production.rings.fullFallbacks", metrics.fullRingFallbacks());
            PerfTrace.count("production.rings.wallNanos", metrics.ringWallNanos());
        }
        var candidates =
                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                        .StructureCandidateStateAccess)
                                context.structureState())
                        .randomnibble6plus24generator$candidateScope();
        if (candidates != null) {
            var metrics = candidates.metrics();
            PerfTrace.count("production.candidates.scopes", 1);
            PerfTrace.count("production.candidates.checksBefore", metrics.setChecksBefore());
            PerfTrace.count("production.candidates.checksAfter", metrics.setChecksAfter());
            PerfTrace.count("production.candidates.cellsPossible", metrics.cellsPossible());
            PerfTrace.count("production.candidates.cellsEvaluated", metrics.cellsEvaluated());
            PerfTrace.count("production.candidates.positions", metrics.candidatePositions());
            PerfTrace.count("production.candidates.preparationNanos", metrics.preparationNanos());
            PerfTrace.count("production.candidates.queryFallbacks", metrics.queryFallbacks());
        }
    }

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private long contextSetupNanos;

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private long structureStateNanos;

    @WrapMethod(
            method =
                    "create(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/IsolatedGenerationMode;Lnet/minecraft/server/level/ServerLevel;JLnet/minecraft/world/level/ChunkPos;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/profile/MosaicWorldProfile;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/IsolatedGenerationContext;")
    private static IsolatedGenerationContext create(
            IsolatedGenerationMode mode,
            ServerLevel level,
            long seed,
            ChunkPos pos,
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile
                    profile,
            Operation<IsolatedGenerationContext> original) {
        String phase = PerfTrace.purpose().equals("spawn") ? "SPAWN_CONTEXT" : "CANONICAL_CONTEXT";
        var span = PerfTrace.begin(level, pos, phase);
        try {
            return original.call(mode, level, seed, pos, profile);
        } finally {
            PerfTrace.end(level, pos, phase, span);
        }
    }

    @WrapMethod(method = "generateFeaturesStable")
    private FeatureStableGenerationRun generate(Operation<FeatureStableGenerationRun> original) {
        var self = (IsolatedGenerationContext) (Object) this;
        var span = PerfTrace.begin(self.hostLevel(), self.target(), "CANONICAL");
        try {
            return original.call();
        } finally {
            PerfTrace.end(self.hostLevel(), self.target(), "CANONICAL", span);
        }
    }

    @WrapMethod(method = "generateBiomesForSpawn")
    private Map<ChunkPos, ChunkAccess> biomes(
            Set<ChunkPos> positions, Operation<Map<ChunkPos, ChunkAccess>> original) {
        var self = (IsolatedGenerationContext) (Object) this;
        var span = PerfTrace.begin(self.hostLevel(), self.target(), "SPAWN_BIOMES");
        PerfTrace.count("spawn.prepare.contextSetupNanos", contextSetupNanos);
        PerfTrace.count("spawn.prepare.structureStateSetupNanos", structureStateNanos);
        try {
            return original.call(positions);
        } finally {
            PerfTrace.end(self.hostLevel(), self.target(), "SPAWN_BIOMES", span);
        }
    }
}
