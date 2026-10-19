package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.ColdSpawnEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MosaicPhysicalLevelState.class, remap = false)
abstract class ColdSpawnStateMixin implements ColdSpawnEvidence.Inputs {
    @Override
    @Accessor("spawnBiomes")
    public abstract Map<ChunkAccess, SpawnBiomeSnapshot> coldProof$biomes();

    @Override
    @Accessor("spawnCollisions")
    public abstract Map<ChunkAccess, SpawnCollisionSnapshot> coldProof$collisions();

    @Inject(method = "prepare", at = @At("HEAD"))
    private void preparation(ChunkPos pos, CallbackInfoReturnable<?> callback) {
        ColdSpawnEvidence.preparing(pos);
    }

    @WrapMethod(method = "loaded")
    private CompletableFuture<ChunkAccess> observeDisk(
            GenerationChunkHolder holder,
            CompletableFuture<ChunkAccess> future,
            Operation<CompletableFuture<ChunkAccess>> original) {
        if (!ColdSpawnEvidence.enabled()) return original.call(holder, future);
        return original.call(
                holder,
                future.thenApply(
                        chunk -> {
                            ColdSpawnEvidence.loaded(chunk);
                            return chunk;
                        }));
    }

    @Inject(method = "beginClosing", at = @At("TAIL"))
    private void releaseStagedPopulation(CallbackInfo callback) {
        ColdSpawnEvidence.closing((MosaicPhysicalLevelState) (Object) this);
    }

    @Inject(method = "clearClosed", at = @At("TAIL"))
    private void cleared(CallbackInfo callback) {
        ColdSpawnEvidence.cleared((MosaicPhysicalLevelState) (Object) this);
    }
}
