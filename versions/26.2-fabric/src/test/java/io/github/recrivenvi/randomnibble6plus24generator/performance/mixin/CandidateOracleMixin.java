package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;

/** Research A/B only. Baseline is 430ae2c with its production Lazy Stronghold intact. */
@Mixin(value=IsolatedGenerationContext.class,remap=false)
abstract class CandidateOracleMixin {
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/StructureCandidateScope;forV2Target(Lnet/minecraft/world/level/ChunkPos;JLjava/util/List;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/StructureCandidateScope;"))
    private StructureCandidateScope create(ChunkPos target,long seed,List<Holder<StructureSet>> sets,Operation<StructureCandidateScope> original){
        return Boolean.getBoolean("mosaic.candidate.oracle")?null:original.call(target,seed,sets);
    }
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/StructureCandidateStateAccess;randomnibble6plus24generator$setCandidateScope(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/StructureCandidateScope;)V"))
    private void install(StructureCandidateStateAccess state,StructureCandidateScope scope,Operation<Void> original){
        if(!Boolean.getBoolean("mosaic.candidate.oracle"))original.call(state,scope);
    }
}
