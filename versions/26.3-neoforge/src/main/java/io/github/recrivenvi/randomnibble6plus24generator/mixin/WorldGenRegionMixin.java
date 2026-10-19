package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.PhysicalWorldAccessException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureFrontierEvidence;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.lighting.LevelLightEngine;
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

@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin {

    @Unique
    private static final Identifier RANDOMNIBBLE6PLUS24GENERATOR$WORLDGEN_REGION_RANDOM =
            Identifier.withDefaultNamespace("worldgen_region_random");

    @Shadow @Final private StaticCache2D<ChunkAccess> cache;

    @Shadow @Final private ChunkAccess center;

    @Shadow @Final private ChunkStep generatingStep;

    @Shadow @Final @Mutable private long seed;

    @Shadow @Final @Mutable private RandomSource random;

    @Shadow @Final @Mutable private BiomeManager biomeManager;

    @Shadow private Supplier<String> currentlyGenerating;

    @WrapOperation(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/util/StaticCache2D;map(Lnet/minecraft/util/StaticCache2D$MappingFunction;)Lnet/minecraft/util/StaticCache2D;"))
    private StaticCache2D<ChunkAccess> randomnibble6plus24generator$bindMappedCache(
            StaticCache2D<GenerationChunkHolder> holders,
            StaticCache2D.MappingFunction<GenerationChunkHolder, ChunkAccess> mapper,
            Operation<StaticCache2D<ChunkAccess>> original) {
        StaticCache2D<ChunkAccess> chunks = original.call(holders, mapper);
        GenerationContextRegistry.find(holders)
                .ifPresent(context -> GenerationContextRegistry.bind(chunks, context));
        MosaicSpawnContextRegistry.find(holders)
                .ifPresent(context -> MosaicSpawnContextRegistry.bind(chunks, context));
        return chunks;
    }

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;uncachedBiomeResolver()Lnet/minecraft/world/level/biome/BiomeResolver;"))
    private BiomeResolver randomnibble6plus24generator$localUncachedResolver(ServerLevel level) {
        var isolated = GenerationContextRegistry.find(cache);
        if (isolated.isPresent())
            return isolated.get()
                    .generator()
                    .getBiomeSource()
                    .createUncachedResolver(isolated.get().randomState());
        return MosaicSpawnContextRegistry.find(cache)
                .map(
                        context ->
                                context.generator()
                                        .getBiomeSource()
                                        .createUncachedResolver(context.randomState()))
                .orElseGet(level::uncachedBiomeResolver);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void randomnibble6plus24generator$installSessionSeedState(
            ServerLevel level,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkStep step,
            ChunkAccess center,
            CallbackInfo callback) {
        GenerationContextRegistry.find(cache)
                .ifPresent(
                        context -> {
                            this.seed = context.worldSeed();
                            this.random =
                                    context.randomState()
                                            .getOrCreateRandomFactory(
                                                    RANDOMNIBBLE6PLUS24GENERATOR$WORLDGEN_REGION_RANDOM)
                                            .at(this.center.getPos().getWorldPosition());
                            WorldGenRegion region = (WorldGenRegion) (Object) this;
                            this.biomeManager =
                                    new BiomeManager(
                                            region::getNoiseBiome,
                                            BiomeManager.obfuscateSeed(context.worldSeed()));
                            FeatureFrontierEvidence.captureSurfaceRegion(
                                    region, step, center, context);
                        });
        if (GenerationContextRegistry.find(cache).isEmpty()) {
            MosaicSpawnContextRegistry.find(cache)
                    .ifPresent(
                            context -> {
                                this.seed = context.worldSeed();
                                this.random =
                                        context.randomState()
                                                .getOrCreateRandomFactory(
                                                        RANDOMNIBBLE6PLUS24GENERATOR$WORLDGEN_REGION_RANDOM)
                                                .at(this.center.getPos().getWorldPosition());
                                WorldGenRegion region = (WorldGenRegion) (Object) this;
                                this.biomeManager =
                                        new BiomeManager(
                                                region::getNoiseBiome,
                                                BiomeManager.obfuscateSeed(context.worldSeed()));
                            });
        }
        if (GenerationContextRegistry.find(cache).isEmpty()
                && MosaicSpawnContextRegistry.find(cache).isEmpty()) {
            FeatureFrontierEvidence.captureSurfaceRegion(
                    (WorldGenRegion) (Object) this, step, center, null);
        }
    }

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long randomnibble6plus24generator$useSessionSeedDuringConstruction(ServerLevel level) {
        var isolated = GenerationContextRegistry.find(cache);
        if (isolated.isPresent()) return isolated.get().worldSeed();
        return MosaicSpawnContextRegistry.find(cache)
                .map(context -> context.worldSeed())
                .orElseGet(level::getSeed);
    }

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerChunkCache;randomState()Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState randomnibble6plus24generator$useSessionRandomStateDuringConstruction(
            ServerChunkCache chunkSource) {
        var isolated = GenerationContextRegistry.find(cache);
        if (isolated.isPresent()) return isolated.get().randomState();
        return MosaicSpawnContextRegistry.find(cache)
                .map(context -> context.randomState())
                .orElseGet(chunkSource::randomState);
    }

    @Redirect(
            method = "setBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;updatePOIOnBlockStateChange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void randomnibble6plus24generator$suppressPhysicalPoiMutation(
            ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
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
        GenerationContextRegistry.find(cache)
                .ifPresent(
                        context -> {
                            String feature =
                                    currentlyGenerating == null
                                            ? "<structure-or-unspecified>"
                                            : currentlyGenerating.get();
                            context.recordFeatureWrite(feature, pos, state);
                            FeatureFrontierEvidence.recordFeatureWrite(
                                    FeatureFrontierEvidence.Mode.ISOLATED, feature, pos, state);
                        });
        if (GenerationContextRegistry.find(cache).isEmpty()) {
            String feature =
                    currentlyGenerating == null
                            ? "<structure-or-unspecified>"
                            : currentlyGenerating.get();
            FeatureFrontierEvidence.recordFeatureWrite(
                    FeatureFrontierEvidence.Mode.NATIVE, feature, pos, state);
        }
    }

    @Inject(method = "getChunkSource", at = @At("HEAD"))
    private void randomnibble6plus24generator$forbidPhysicalChunkSource(
            CallbackInfoReturnable<ChunkSource> callback) {
        if (GenerationContextRegistry.find(cache).isPresent()
                || MosaicSpawnContextRegistry.find(cache).isPresent()) {
            throw new PhysicalWorldAccessException("WorldGenRegion.getChunkSource()");
        }
    }

    @Inject(method = "ensureCanWrite", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$clipTargetOnlyWrites(
            BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (generatingStep.targetStatus()
                != net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES) return;
        GenerationContextRegistry.find(cache)
                .ifPresent(
                        context -> {
                            if (!context.featureWriteAllowed(pos)) {
                                context.recordDiscardedFeatureWrite();
                                callback.setReturnValue(false);
                            }
                        });
    }

    @Inject(
            method =
                    "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("RETURN"),
            cancellable = true)
    private void randomnibble6plus24generator$readOnlyFeatureNeighbors(
            int chunkX,
            int chunkZ,
            net.minecraft.world.level.chunk.status.ChunkStatus status,
            boolean allowEmpty,
            CallbackInfoReturnable<ChunkAccess> callback) {
        if (generatingStep.targetStatus()
                        != net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES
                || callback.getReturnValue() == null) return;
        GenerationContextRegistry.find(cache)
                .ifPresent(
                        context ->
                                callback.setReturnValue(
                                        context.featureReadView(callback.getReturnValue())));
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$clipTargetOnlyEntities(
            Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (generatingStep.targetStatus()
                != net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES) return;
        GenerationContextRegistry.find(cache)
                .ifPresent(
                        context -> {
                            if (!context.featureWriteAllowed(entity.blockPosition())) {
                                context.recordDiscardedFeatureWrite();
                                callback.setReturnValue(false);
                            }
                        });
    }

    @Inject(
            method =
                    "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"))
    private void randomnibble6plus24generator$traceSpawnChunkRead(
            int chunkX,
            int chunkZ,
            net.minecraft.world.level.chunk.status.ChunkStatus requestedStatus,
            boolean allowEmpty,
            CallbackInfoReturnable<ChunkAccess> callback) {
        MosaicSpawnContextRegistry.find(cache)
                .ifPresent(
                        context ->
                                context.recordChunkRead(
                                        new ChunkPos(chunkX, chunkZ), requestedStatus));
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
        if (MosaicSpawnContextRegistry.find(cache).isPresent()) callback.setReturnValue(false);
    }

    @Inject(method = "getUncachedNoiseBiome", at = @At("HEAD"))
    private void randomnibble6plus24generator$useSessionNoiseBiome(
            int quartX, int quartY, int quartZ, CallbackInfoReturnable<Holder<Biome>> callback) {
        // The constructor installs the native resolver once using the session's RandomState.
        GenerationContextRegistry.find(cache)
                .ifPresent(context -> context.recordLocalUncachedBiomeRead());
    }

    @Inject(method = "getLightEngine", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$useSessionLightEngine(
            CallbackInfoReturnable<LevelLightEngine> callback) {
        GenerationContextRegistry.find(cache)
                .ifPresent(context -> callback.setReturnValue(context.localLightEngine()));
    }

    @Inject(method = "getLevel", at = @At("HEAD"))
    private void randomnibble6plus24generator$auditPhysicalLevelEscape(
            CallbackInfoReturnable<ServerLevel> callback) {
        GenerationContextRegistry.find(cache)
                .ifPresent(
                        context -> {
                            String caller =
                                    StackWalker.getInstance()
                                            .walk(
                                                    frames ->
                                                            frames.filter(
                                                                            frame ->
                                                                                    !frame.getClassName()
                                                                                            .startsWith(
                                                                                                    "java."))
                                                                    .filter(
                                                                            frame ->
                                                                                    !frame.getMethodName()
                                                                                            .contains(
                                                                                                    "auditPhysicalLevelEscape"))
                                                                    .filter(
                                                                            frame ->
                                                                                    !frame.getMethodName()
                                                                                            .startsWith(
                                                                                                    "lambda$randomnibble6plus24generator"))
                                                                    .filter(
                                                                            frame ->
                                                                                    !frame.getClassName()
                                                                                            .equals(
                                                                                                    WorldGenRegion
                                                                                                            .class
                                                                                                            .getName()))
                                                                    .filter(
                                                                            frame ->
                                                                                    !frame.getClassName()
                                                                                            .equals(
                                                                                                    WorldGenRegionMixin
                                                                                                            .class
                                                                                                            .getName()))
                                                                    .findFirst()
                                                                    .map(
                                                                            frame ->
                                                                                    frame
                                                                                                    .getClassName()
                                                                                            + "."
                                                                                            + frame
                                                                                                    .getMethodName())
                                                                    .orElse("unknown"));
                            context.recordPhysicalLevelEscape(caller);
                        });
    }
}
