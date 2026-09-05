package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingStateAccess;

@Mixin(ChunkGeneratorStructureState.class)
abstract class ChunkGeneratorStructureStateMixin implements ConcentricRingStateAccess {
    @Shadow private boolean hasGeneratedPositions;
    @Unique private @Nullable ConcentricRingScope randomnibble6plus24generator$ringScope;

    @Override
    public void randomnibble6plus24generator$setRingScope(ConcentricRingScope scope) {
        if (hasGeneratedPositions || randomnibble6plus24generator$ringScope != null) {
            throw new IllegalStateException("Concentric scope must be installed once, before native preparation");
        }
        randomnibble6plus24generator$ringScope = scope;
    }

    @Override
    public @Nullable ConcentricRingScope randomnibble6plus24generator$ringScope() {
        return randomnibble6plus24generator$ringScope;
    }

    @WrapOperation(method = "generateRingPositions", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkPos> randomnibble6plus24generator$searchRelevantSlots(
            Supplier<ChunkPos> search, Executor executor, Operation<CompletableFuture<ChunkPos>> original,
            @Local(name = "initialX") int initialX, @Local(name = "initialZ") int initialZ) {
        ConcentricRingScope scope = randomnibble6plus24generator$ringScope;
        return scope == null ? original.call(search, executor)
                : scope.search(initialX, initialZ, search, executor);
    }

    @WrapMethod(method = "generateRingPositions")
    private CompletableFuture<List<ChunkPos>> randomnibble6plus24generator$finiteRingView(
            Holder<StructureSet> set, ConcentricRingsStructurePlacement placement,
            Operation<CompletableFuture<List<ChunkPos>>> original) {
        ConcentricRingScope scope = randomnibble6plus24generator$ringScope;
        if (scope == null) return original.call(set, placement);
        scope.beginRing();
        long start = System.nanoTime();
        return original.call(set, placement).thenApply(list -> scope.finish(list, System.nanoTime() - start));
    }
}
