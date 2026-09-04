package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureIndexStore;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2RSpawnTrace;

/** Development-only evidence that Vanilla activates each physical LevelChunk lifecycle once. */
@Mixin(LevelChunk.class)
abstract class LevelChunkLifecycleTraceMixin {

    @Shadow
    @Final
    private Level level;

    @Inject(method = "runPostLoad", at = @At("HEAD"))
    private void randomnibble6plus24generator$tracePostLoad(CallbackInfo callbackInfo) {
        if (level instanceof ServerLevel serverLevel) {
            if (MosaicPhysicalMaterializer.isPhysicalMosaic(serverLevel)) {
                MosaicStructureIndexStore.indexLoadedChunk(serverLevel, (LevelChunk) (Object) this);
            }
            PhysicalMosaicTrace.recordLifecycleCall(
                    serverLevel, "runPostLoad", ((LevelChunk) (Object) this).getPos());
        }
    }

    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceBlockEntityRegistration(CallbackInfo callbackInfo) {
        if (level instanceof ServerLevel serverLevel) {
            PhysicalMosaicTrace.recordLifecycleCall(
                    serverLevel, "registerAllBlockEntitiesAfterLevelLoad",
                    ((LevelChunk) (Object) this).getPos());
        }
    }

    @Inject(method = "registerTickContainerInLevel", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceTickRegistration(
            ServerLevel serverLevel, CallbackInfo callbackInfo) {
        PhysicalMosaicTrace.recordLifecycleCall(
                serverLevel, "registerTickContainerInLevel", ((LevelChunk) (Object) this).getPos());
    }

    @Inject(method = "postProcessGeneration", at = @At("HEAD"))
    private void randomnibble6plus24generator$tracePostProcess(
            ServerLevel serverLevel, CallbackInfo callbackInfo) {
        Phase3C2RSpawnTrace.beginPostProcess(serverLevel, ((LevelChunk) (Object) this).getPos());
        PhysicalMosaicTrace.recordLifecycleCall(
                serverLevel, "postProcessGeneration", ((LevelChunk) (Object) this).getPos());
    }

    @Inject(method = "postProcessGeneration", at = @At("RETURN"))
    private void randomnibble6plus24generator$finishPostProcess(
            ServerLevel serverLevel, CallbackInfo callbackInfo) {
        Phase3C2RSpawnTrace.endPostProcess(serverLevel, ((LevelChunk) (Object) this).getPos());
    }

    @Inject(method = "updateBlockEntityTicker", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceBlockEntityTicker(
            BlockEntity blockEntity, CallbackInfo callbackInfo) {
        if (level instanceof ServerLevel serverLevel) {
            PhysicalMosaicTrace.recordLifecycleCall(
                    serverLevel, "updateBlockEntityTicker", ((LevelChunk) (Object) this).getPos());
        }
    }
}
