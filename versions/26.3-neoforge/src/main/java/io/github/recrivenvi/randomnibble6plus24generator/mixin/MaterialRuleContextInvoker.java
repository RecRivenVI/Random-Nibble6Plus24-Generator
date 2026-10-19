package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes native evaluators to the experimental column query, without intercepting Vanilla. */
@Mixin(MaterialRuleContext.class)
public interface MaterialRuleContextInvoker {
    @Invoker("<init>")
    static MaterialRuleContext randomnibble6plus24generator$create(
            MaterialSystem system,
            RandomState random,
            DensityVolume volume,
            DensitySamplerSet samplers,
            Function<BlockPos, Holder<Biome>> biomes,
            WorldGenerationContext generation,
            Set<Holder<Biome>> possible) {
        throw new AssertionError("Mixin constructor not applied");
    }

    @Invoker("updateXZ")
    void randomnibble6plus24generator$updateXZ(int x, int z, int gradientX, int gradientZ);

    @Invoker("updateY")
    void randomnibble6plus24generator$updateY(int above, int below, int water, int y);
}
