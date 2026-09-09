package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
@Mixin(NoiseBasedChunkGenerator.class)
abstract class LazyBeardifierMixin {
    @WrapOperation(method="createNoiseChunk",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/Beardifier;forStructuresInChunk(Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/levelgen/Beardifier;"))
    private Beardifier beard(StructureManager manager,ChunkPos pos,Operation<Beardifier> original,@Local(argsOnly=true) RandomState random){
        Beardifier result=original.call(manager,pos);var scope=LazyScopes.get(random);if(scope!=null)scope.beard(pos,result);return result;
    }
}
