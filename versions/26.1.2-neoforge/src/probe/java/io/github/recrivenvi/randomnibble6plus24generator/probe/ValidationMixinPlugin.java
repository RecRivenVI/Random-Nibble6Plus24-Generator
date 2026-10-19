package io.github.recrivenvi.randomnibble6plus24generator.probe;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ValidationMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        String name = System.getProperty("validation.name", "");
        enabled =
                mixinPackage.endsWith("prefeaturestest.mixin")
                                && name.equals("conformance-prefeatures")
                        || mixinPackage.endsWith("featurebtest.mixin")
                                && name.equals("conformance-feature_b")
                                && Boolean.getBoolean("featureb.independent")
                        || mixinPackage.endsWith("productiontest.mixin")
                                && name.equals("integration-production_lifecycle")
                                && (Boolean.getBoolean(
                                                io.github.recrivenvi.randomnibble6plus24generator
                                                                .ModConstants.MOD_ID
                                                        + ".phase3c2.production.runtimeProof")
                                        || Boolean.getBoolean(
                                                io.github.recrivenvi.randomnibble6plus24generator
                                                                .ModConstants.MOD_ID
                                                        + ".phase3c2.production.vanillaControl"));
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
