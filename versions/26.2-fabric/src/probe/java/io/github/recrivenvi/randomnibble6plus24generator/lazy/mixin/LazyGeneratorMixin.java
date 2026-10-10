package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGenerator.class)
abstract class LazyGeneratorMixin {
    @WrapOperation(
            method = "createStructures",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;possibleStructureSets()Ljava/util/List;"))
    private List<Holder<StructureSet>> sets(
            ChunkGeneratorStructureState state,
            Operation<List<Holder<StructureSet>>> original,
            @Local(argsOnly = true) ChunkAccess chunk) {
        var sets = original.call(state);
        var scope = ((LazyStateAccess) state).mosaicLazyScope();
        return scope == null ? sets : scope.forSource(state, chunk.getPos(), sets);
    }
}
