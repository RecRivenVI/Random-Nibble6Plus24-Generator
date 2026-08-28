package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
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
                    target = "Lnet/minecraft/world/level/levelgen/feature/ConfiguredFeature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean randomnibble6plus24generator$useIsolatedGenerator(
            ConfiguredFeature<?, ?> feature,
            WorldGenLevel level,
            ChunkGenerator physicalGenerator,
            RandomSource random,
            BlockPos origin) {
        ChunkGenerator generator = physicalGenerator;
        if (level instanceof WorldGenRegion region) {
            var context = GenerationContextRegistry.find(region);
            if (context.isPresent()) {
                context.get().recordPaleMossGeneratorRedirect();
                generator = context.get().generator();
            }
        }
        return feature.place(level, generator, random, origin);
    }
}
