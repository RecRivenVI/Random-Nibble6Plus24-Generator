package io.github.recrivenvi.randomnibble6plus24generator.probe;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ValidationMixinPlugin implements IMixinConfigPlugin {
    private boolean enabled;
    private boolean mcpOnly;

    @Override
    public void onLoad(String mixinPackage) {
        String name = System.getProperty("validation.name", "");
        mcpOnly = name.equals("integration-mcp_alpha");
        enabled =
                (mixinPackage.endsWith("featurebtest.mixin")
                                && name.equals("conformance-feature_b"))
                        || (mixinPackage.endsWith("prefeaturestest.mixin")
                                && name.equals("conformance-prefeatures"))
                        || (mixinPackage.endsWith("performance.mixin")
                                && name.equals("performance-worldgen"))
                        || (mixinPackage.endsWith("productiontest.mixin")
                                && (name.equals("integration-production_lifecycle") || mcpOnly));
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("WorldSearchServerMixin"))
            return enabled
                    && Boolean.getBoolean(
                            io.github.recrivenvi.randomnibble6plus24generator.ModConstants.MOD_ID
                                    + ".phase3c2.production.worldSearchProof");
        if (mcpOnly)
            return enabled
                    && (mixinClassName.endsWith("ClientLaunchExportMixin")
                            ? Boolean.getBoolean("mosaic.mcp.launchExport")
                            : Boolean.getBoolean(ModConstants.MOD_ID + ".clientLifecycle.enabled")
                                    && (mixinClassName.contains(".PlayerLifecycle")
                                            || mixinClassName.endsWith("ClientLifecycleTickMixin")
                                            || mixinClassName.endsWith("PlayerPortalEvidenceMixin")
                                            || mixinClassName.endsWith(
                                                    "PlayerEndCreditsEvidenceMixin")));
        if (mixinClassName.endsWith("ClientLaunchExportMixin")
                || mixinClassName.endsWith("ClientLifecycleTickMixin")
                || mixinClassName.contains(".PlayerLifecycle")
                || mixinClassName.endsWith("PlayerPortalEvidenceMixin")
                || mixinClassName.endsWith("PlayerEndCreditsEvidenceMixin")) return false;
        if (mixinClassName.contains(".Natural"))
            return enabled
                    && !System.getProperty(
                                    ModConstants.MOD_ID + ".phase3c2.production.naturalProof", "")
                            .isEmpty();
        if (mixinClassName.contains("featurebtest.mixin")
                && Boolean.getBoolean("featureb.sessions"))
            return enabled && mixinClassName.endsWith("IndependentBServerMixin");
        if (mixinClassName.endsWith("NativeRequestEvidenceMixin"))
            return enabled
                    && Boolean.getBoolean(
                            ModConstants.MOD_ID + ".phase3c2.production.requestProof");
        if (mixinClassName.endsWith("PerfStructureReuseAuditMixin")
                || mixinClassName.endsWith("PerfNativeBiomeStateAccessor"))
            return enabled && Boolean.getBoolean("mosaic.profile.structureReuseAudit");
        return enabled
                && !mixinClassName.contains("performance.mixin.StructureIsolation")
                && !mixinClassName.endsWith("RepairCOracleMixin")
                && !mixinClassName.endsWith("CandidateOracleMixin");
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
