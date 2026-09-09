package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
@Mixin(value=CanonicalChunkArtifact.class,remap=false)
abstract class PerfArtifactMixin {
    @WrapMethod(method="capture")
    private static CanonicalChunkArtifact capture(FeatureStableGenerationRun run,String dim,long seed,MosaicWorldProfile profile,
            RegistryAccess registry,StructurePieceSerializationContext ctx,Operation<CanonicalChunkArtifact> original){
        String key=PerfTrace.currentKey();PerfTrace.keyEvent(key,"ARTIFACT_CAPTURE_BEGIN");
        try{return original.call(run,dim,seed,profile,registry,ctx);}finally{PerfTrace.keyEvent(key,"ARTIFACT_CAPTURE_END");}
    }
    @WrapMethod(method="rehydrate")
    private ProtoChunk rehydrate(RegistryAccess registry,StructurePieceSerializationContext ctx,Operation<ProtoChunk> original){
        String key=PerfTrace.currentKey();PerfTrace.keyEvent(key,"REHYDRATE_BEGIN");
        try{return original.call(registry,ctx);}finally{PerfTrace.keyEvent(key,"REHYDRATE_END");}
    }
}
