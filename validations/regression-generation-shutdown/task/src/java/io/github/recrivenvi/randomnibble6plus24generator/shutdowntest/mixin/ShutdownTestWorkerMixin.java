package io.github.recrivenvi.randomnibble6plus24generator.shutdowntest.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.shutdowntest.ShutdownVerification;

@Mixin(value=MosaicPhysicalMaterializer.class,remap=false)
abstract class ShutdownTestWorkerMixin {
    @Inject(method="prepare",at=@At("HEAD"))
    private static void gate(ServerLevel level,ChunkPos pos,MosaicPhysicalMaterializer.GenerationKey key,
            CallbackInfoReturnable<MosaicPhysicalMaterializer.PreparedMaterialization> ci){
        ShutdownVerification.beforePrepare(level,pos);
    }
}
