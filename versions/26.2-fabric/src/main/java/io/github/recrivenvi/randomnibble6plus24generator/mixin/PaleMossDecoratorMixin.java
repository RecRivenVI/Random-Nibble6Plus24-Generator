package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.treedecorators.PaleMossDecorator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Routes Pale Moss' one physical-generator lookup back to the active virtual universe. */
@Mixin(PaleMossDecorator.class)
abstract class PaleMossDecoratorMixin {

    @Redirect(
            method = "lambda$place$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerChunkCache;getGenerator()Lnet/minecraft/world/level/chunk/ChunkGenerator;"))
    private static ChunkGenerator randomnibble6plus24generator$useIsolatedGenerator(
            ServerChunkCache physicalChunkSource,
            WorldGenLevel level,
            RandomSource random,
            BlockPos origin,
            Holder.Reference<?> feature) {
        if (level instanceof WorldGenRegion region) {
            var context = GenerationContextRegistry.find(region);
            if (context.isPresent()) {
                context.get().recordPaleMossGeneratorRedirect();
                return context.get().generator();
            }
        }
        return physicalChunkSource.getGenerator();
    }
}
