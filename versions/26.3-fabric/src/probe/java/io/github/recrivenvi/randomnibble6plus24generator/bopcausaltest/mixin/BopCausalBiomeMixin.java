package io.github.recrivenvi.randomnibble6plus24generator.bopcausaltest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.BopCausalTrace;
import net.minecraft.core.*;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BiomeManager.class)
abstract class BopCausalBiomeMixin {
    @Inject(method = "getBiome", at = @At("RETURN"))
    private void read(BlockPos pos, CallbackInfoReturnable<Holder<Biome>> ci) {
        BopCausalTrace.biome(this, pos, ci.getReturnValue());
    }
}
