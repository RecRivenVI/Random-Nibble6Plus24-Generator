package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin implements ConcentricRingStateAccess {
    @Shadow private boolean hasGeneratedPositions;
    @Unique private @Nullable ConcentricRingScope mosaic$scope;

    @Override
    public void mosaic$setRingScope(ConcentricRingScope scope) {
        if (hasGeneratedPositions || mosaic$scope != null)
            throw new IllegalStateException("Install ring scope once before native preparation");
        mosaic$scope = scope;
    }

    @Override
    public @Nullable ConcentricRingScope mosaic$ringScope() {
        return mosaic$scope;
    }

    @Inject(method = "generateRingPositions", at = @At("HEAD"))
    private void mosaic$begin(
            Holder<StructureSet> set,
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> callback) {
        if (mosaic$scope != null) mosaic$scope.beginRing(placement.count());
    }

    @ModifyArg(
            method = "generateRingPositions",
            index = 0,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private Supplier<ChunkPos> mosaic$bindSearch(Supplier<ChunkPos> search) {
        return mosaic$scope == null ? search : mosaic$scope.wrapSearch(search);
    }

    @Inject(method = "generateRingPositions", at = @At("RETURN"), cancellable = true)
    private void mosaic$finish(
            Holder<StructureSet> set,
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> callback) {
        if (mosaic$scope != null)
            callback.setReturnValue(callback.getReturnValue().thenApply(mosaic$scope::finish));
    }
}
