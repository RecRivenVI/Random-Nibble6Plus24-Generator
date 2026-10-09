package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;
import java.util.concurrent.CompletableFuture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
@Mixin(value=IsolatedChunkStatusTasks.class,remap=false)
abstract class LazyStructureStepMixin {
    @WrapMethod(method="generateStructureStarts")
    private static CompletableFuture<ChunkAccess> starts(IsolatedGenerationContext context,ChunkStep step,StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,Operation<CompletableFuture<ChunkAccess>> original){
        var scope=((LazyStateAccess)context.structureState()).mosaicLazyScope();
        if(scope==null)return original.call(context,step,cache,chunk);
        long start=System.nanoTime();try{return original.call(context,step,cache,chunk);}finally{scope.count("structureStageNanos",System.nanoTime()-start);}
    }
}
