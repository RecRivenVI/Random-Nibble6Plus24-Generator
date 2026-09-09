package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureCandidateStateAccess;

@Mixin(ChunkGenerator.class)
abstract class StructureCandidateGeneratorMixin {
    @WrapOperation(method = "createStructures", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;possibleStructureSets()Ljava/util/List;"))
    private List<Holder<StructureSet>> randomnibble6plus24generator$relevantCandidateSets(
            ChunkGeneratorStructureState state, Operation<List<Holder<StructureSet>>> original,
            @Local(argsOnly = true) ChunkAccess chunk) {
        var sets = original.call(state);
        var scope = ((StructureCandidateStateAccess) state).randomnibble6plus24generator$candidateScope();
        return scope == null ? sets : scope.forSource(chunk.getPos(), sets);
    }
}
