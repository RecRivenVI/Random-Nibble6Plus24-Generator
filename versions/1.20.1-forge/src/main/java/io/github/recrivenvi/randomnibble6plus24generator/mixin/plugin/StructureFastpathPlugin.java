package io.github.recrivenvi.randomnibble6plus24generator.mixin.plugin;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureLockEnvironment;
import java.util.*;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Do not install behavior-changing fastpath injections when disabled or unaudited. */
public final class StructureFastpathPlugin implements IMixinConfigPlugin {
    private static final Set<String> OPTIONAL =
            Set.of(
                    "StructureGenerationMixin",
                    "StructurePoolSizeMixin",
                    "StructurePaletteCacheMixin",
                    "StructureRotationCacheMixin");

    @Override
    public void onLoad(String pkg) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!OPTIONAL.contains(mixin.substring(mixin.lastIndexOf('.') + 1))) return true;
        if (!Boolean.getBoolean(ModConstants.MOD_ID + ".structure.narrowLocks")) return false;
        var mods = FMLLoader.getLoadingModList();
        if (mods == null) return false;
        for (var mod : mods.getMods())
            if (!StructureLockEnvironment.knownMod(mod.getModId())) return false;
        return true;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
