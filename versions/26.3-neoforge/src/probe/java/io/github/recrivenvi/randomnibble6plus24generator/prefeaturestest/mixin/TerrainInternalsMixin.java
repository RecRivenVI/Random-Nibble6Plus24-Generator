package io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.TerrainProbeEvidence;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class TerrainInternalsMixin {
    @WrapOperation(
            method = "doFill",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler$Bound;sampleVolume(Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;)Lnet/minecraft/world/level/levelgen/densityfunction/ScopedDensityBuffer;"))
    private ScopedDensityBuffer density(
            DensitySampler.Bound sampler,
            DensityVolume volume,
            Operation<ScopedDensityBuffer> original,
            NoiseChunk noise,
            ChunkAccess chunk) {
        var buffer = original.call(sampler, volume);
        TerrainProbeEvidence.density(chunk, volume, buffer);
        return buffer;
    }

    @Inject(method = "doFill", at = @At("RETURN"))
    private void noise(NoiseChunk noise, ChunkAccess chunk, CallbackInfo callback) {
        TerrainProbeEvidence.snapshot("noise", chunk);
    }

    @Inject(method = "buildSurface", at = @At("RETURN"))
    private void surface(
            ChunkAccess chunk,
            NoiseChunk noise,
            RandomState random,
            BiomeManager biomes,
            Set<Holder<Biome>> possible,
            MaterialRule rule,
            CallbackInfo callback) {
        TerrainProbeEvidence.snapshot("surface", chunk);
    }

    @WrapMethod(method = "generateCarvers")
    private void carvers(
            ChunkAccess chunk,
            Blender blender,
            NoiseChunk noise,
            RandomState random,
            BiomeManager biomes,
            WorldGenRegion region,
            MaterialRule rule,
            Operation<Void> original) {
        TerrainProbeEvidence.enterCarvers(chunk, random);
        try {
            original.call(chunk, blender, noise, random, biomes, region, rule);
            TerrainProbeEvidence.snapshot("carved", chunk);
        } finally {
            TerrainProbeEvidence.leaveCarvers();
        }
    }

    @WrapOperation(
            method = "generateCarvers",
            at = @At(value = "NEW", target = "(II)Lnet/minecraft/world/level/chunk/CarvingMask;"))
    private CarvingMask mask(int min, int max, Operation<CarvingMask> original) {
        var mask = original.call(min, max);
        TerrainProbeEvidence.mask(mask);
        return mask;
    }

    @WrapOperation(
            method = "getBiomeGenerationSettingsForCarver",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/biome/BiomeResolver;getNoiseBiome(III)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> source(
            BiomeResolver resolver,
            int x,
            int y,
            int z,
            Operation<Holder<Biome>> original,
            BiomeResolver parameter,
            ChunkPos pos) {
        var biome = original.call(resolver, x, y, z);
        TerrainProbeEvidence.source(pos, biome);
        return biome;
    }
}
