package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseGeneratorSettings.class)
public interface NoiseGeneratorSettingsAccessor {
    // TerraBlender's public accessor may already wrap this field in merged rules.
    @Accessor("materialRule")
    Holder<MaterialRule> randomnibble6plus24generator$rawMaterialRule();

    @Accessor("disableMobGeneration")
    boolean randomnibble6plus24generator$rawDisableMobGeneration();
}
