package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalGenerationPlan;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** ChunkStatus initializes native registries; run this assertion in the real loaded game. */
public final class PhysicalPlanEvidence {
    private PhysicalPlanEvidence() {}

    public static void verify() {
        var owner = new ChunkPos(125, -37);
        for (var status : ChunkStatus.getStatusList()) {
            var plan = MosaicPhysicalGenerationPlan.derive(owner, status);
            if (status == ChunkStatus.EMPTY) {
                require(plan.requirements().isEmpty());
                continue;
            }
            var dependencies =
                    ChunkPyramid.GENERATION_PYRAMID.getStepTo(status).accumulatedDependencies();
            require(
                    (2 * dependencies.getRadius() + 1) * (2 * dependencies.getRadius() + 1)
                            == plan.requirements().size());
            plan.requirements()
                    .forEach(
                            (pos, required) -> {
                                if (required.isOrAfter(ChunkStatus.BIOMES))
                                    require(plan.artifacts().containsKey(pos));
                            });
            require(plan.artifacts().containsKey(owner));
        }
    }

    public static JsonObject describe(ChunkPos owner) {
        var plan = MosaicPhysicalGenerationPlan.derive(owner, ChunkStatus.FULL);
        var requirements = new java.util.TreeMap<String, Integer>();
        var obligations = new java.util.TreeMap<String, Integer>();
        plan.requirements()
                .values()
                .forEach(status -> requirements.merge(status.toString(), 1, Integer::sum));
        plan.artifacts()
                .values()
                .forEach(status -> obligations.merge(status.toString(), 1, Integer::sum));
        var result = new JsonObject();
        var gson = new Gson();
        result.add("nativeRequirements", gson.toJsonTree(requirements));
        result.add("actualDataObligations", gson.toJsonTree(obligations));
        return result;
    }

    private static void require(boolean condition) {
        if (!condition)
            throw new IllegalStateException("Native physical dependency plan invariant failed");
    }
}
