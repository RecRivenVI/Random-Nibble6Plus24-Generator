package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.RingEntropyCapture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ChunkGeneratorStructureState.class)
abstract class RingEntropyMixin {
    @Redirect(
            method = "generateRingPositions",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextDouble()D"),
            require = 3)
    private double mosaic$double(RandomSource random) {
        long before = RingEntropyCapture.before(random);
        double result = random.nextDouble();
        RingEntropyCapture.drew(random, before, result);
        return result;
    }

    @Redirect(
            method = "generateRingPositions",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/util/RandomSource;fork()Lnet/minecraft/util/RandomSource;"))
    private RandomSource mosaic$fork(RandomSource random) {
        long before = RingEntropyCapture.before(random);
        var result = random.fork();
        RingEntropyCapture.forked(random, before, result);
        return result;
    }
}
