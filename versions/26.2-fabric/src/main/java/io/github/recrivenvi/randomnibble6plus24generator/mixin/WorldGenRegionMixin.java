package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.RandomState;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.PhysicalWorldAccessException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureFrontierEvidence;

@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin {

    @Unique
    private static final Identifier RANDOMNIBBLE6PLUS24GENERATOR$WORLDGEN_REGION_RANDOM =
            Identifier.withDefaultNamespace("worldgen_region_random");

    @Shadow
    @Final
    private StaticCache2D<GenerationChunkHolder> cache;

    @Shadow
    @Final
    private ChunkAccess center;

    @Shadow
    @Final
    @Mutable
    private long seed;

    @Shadow
    @Final
    @Mutable
    private RandomSource random;

    @Shadow
    @Final
    @Mutable
    private BiomeManager biomeManager;

    @Shadow
    private Supplier<String> currentlyGenerating;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void randomnibble6plus24generator$installSessionSeedState(
            ServerLevel level,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkStep step,
            ChunkAccess center,
            CallbackInfo callback) {
        GenerationContextRegistry.find(cache).ifPresent(context -> {
            this.seed = context.worldSeed();
            this.random = context.randomState()
                    .getOrCreateRandomFactory(RANDOMNIBBLE6PLUS24GENERATOR$WORLDGEN_REGION_RANDOM)
                    .at(this.center.getPos().getWorldPosition());
            WorldGenRegion region = (WorldGenRegion) (Object) this;
            this.biomeManager = new BiomeManager(
                    region::getNoiseBiome,
                    BiomeManager.obfuscateSeed(context.worldSeed()));
            FeatureFrontierEvidence.captureSurfaceRegion(region, step, center, context);
        });
        if (GenerationContextRegistry.find(cache).isEmpty()) {
            FeatureFrontierEvidence.captureSurfaceRegion(
                    (WorldGenRegion) (Object) this, step, center, null);
        }
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long randomnibble6plus24generator$useSessionSeedDuringConstruction(ServerLevel level) {
        return GenerationContextRegistry.find(cache)
                .map(context -> context.worldSeed())
                .orElseGet(level::getSeed);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerChunkCache;randomState()Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState randomnibble6plus24generator$useSessionRandomStateDuringConstruction(
            ServerChunkCache chunkSource) {
        return GenerationContextRegistry.find(cache)
                .map(context -> context.randomState())
                .orElseGet(chunkSource::randomState);
    }

    @Redirect(
            method = "setBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;updatePOIOnBlockStateChange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void randomnibble6plus24generator$suppressPhysicalPoiMutation(
            ServerLevel level,
            BlockPos pos,
            BlockState oldState,
            BlockState newState) {
        var context = GenerationContextRegistry.find(cache);
        if (context.isPresent()) {
            context.get().recordSuppressedPhysicalPoiUpdate();
            return;
        }
        level.updatePOIOnBlockStateChange(pos, oldState, newState);
    }

    @Inject(method = "setBlock", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceFeatureWrite(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback) {
        GenerationContextRegistry.find(cache).ifPresent(context -> {
            String feature = currentlyGenerating == null ? "<structure-or-unspecified>" : currentlyGenerating.get();
            context.recordFeatureWrite(feature, pos, state);
        });
    }

    @Inject(method = "getChunkSource", at = @At("HEAD"))
    private void randomnibble6plus24generator$forbidPhysicalChunkSource(
            CallbackInfoReturnable<ChunkSource> callback) {
        if (GenerationContextRegistry.find(cache).isPresent()) {
            throw new PhysicalWorldAccessException("WorldGenRegion.getChunkSource()");
        }
    }

    @Inject(method = "isOldChunkAround", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$useFreshVirtualStorageForBlending(
            net.minecraft.world.level.ChunkPos pos,
            int radius,
            CallbackInfoReturnable<Boolean> callback) {
        if (GenerationContextRegistry.find(cache).isPresent()) {
            // A new isolated Vanilla universe has no legacy chunks to blend with.
            // The vanilla implementation consults the physical ChunkMap directly.
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$useSessionNoiseBiome(
            int quartX,
            int quartY,
            int quartZ,
            CallbackInfoReturnable<Holder<Biome>> callback) {
        GenerationContextRegistry.find(cache).ifPresent(context -> {
            context.recordLocalUncachedBiomeRead();
            callback.setReturnValue(context.generator().getBiomeSource().getNoiseBiome(
                    quartX,
                    quartY,
                    quartZ,
                    context.randomState().sampler()));
        });
    }

    @Inject(method = "getLevel", at = @At("HEAD"))
    private void randomnibble6plus24generator$auditPhysicalLevelEscape(
            CallbackInfoReturnable<ServerLevel> callback) {
        GenerationContextRegistry.find(cache).ifPresent(context -> {
            String caller = StackWalker.getInstance().walk(frames -> frames
                    .filter(frame -> !frame.getClassName().startsWith("java."))
                    .filter(frame -> !frame.getMethodName().contains("auditPhysicalLevelEscape"))
                    .filter(frame -> !frame.getMethodName().startsWith("lambda$randomnibble6plus24generator"))
                    .filter(frame -> !frame.getClassName().equals(WorldGenRegion.class.getName()))
                    .filter(frame -> !frame.getClassName().equals(WorldGenRegionMixin.class.getName()))
                    .findFirst()
                    .map(frame -> frame.getClassName() + "." + frame.getMethodName())
                    .orElse("unknown"));
            context.recordPhysicalLevelEscape(caller);
        });
    }
}
