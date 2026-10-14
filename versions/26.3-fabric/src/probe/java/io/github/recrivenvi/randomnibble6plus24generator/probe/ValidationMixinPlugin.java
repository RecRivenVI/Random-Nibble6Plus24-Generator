package io.github.recrivenvi.randomnibble6plus24generator.probe;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Retains the previously separate test mods' activation boundaries. */
public final class ValidationMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        String name = System.getProperty("validation.name", "");
        String group =
                mixinPackage.substring(
                        mixinPackage.lastIndexOf('.', mixinPackage.length() - 7) + 1);
        enabled =
                switch (group) {
                    case "candidatetest.mixin" -> name.equals("conformance-structure_candidates");
                    case "shutdowntest.mixin" -> name.equals("regression-generation_shutdown");
                    case "identitytest.mixin" -> name.equals("conformance-world_identity");
                    case "poitest.mixin" -> name.equals("regression-poi_ownership");
                    case "spawntest.mixin" -> name.equals("regression-spawn_reuse");
                    case "strongholdtest.mixin" -> name.equals("conformance-stronghold");
                    case "prefeaturestest.mixin" -> name.equals("conformance-prefeatures");
                    case "featurebtest.mixin" -> name.equals("conformance-feature_b");
                    case "featureatest.mixin" -> name.equals("conformance-feature_a");
                    case "featurectest.mixin" -> name.equals("conformance-feature_c");
                    case "performance.mixin" -> name.equals("performance-worldgen");
                    default -> false;
                };
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("performance.mixin.StructureIsolation"))
            return enabled && Boolean.getBoolean("mosaic.profile.structureExperiment");
        if (mixinClassName.endsWith("RepairCOracleMixin"))
            return enabled && Boolean.getBoolean("mosaic.repairC.oracle");
        if (mixinClassName.endsWith("CandidateOracleMixin"))
            return enabled && Boolean.getBoolean("mosaic.candidate.study");
        return enabled;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}
}
