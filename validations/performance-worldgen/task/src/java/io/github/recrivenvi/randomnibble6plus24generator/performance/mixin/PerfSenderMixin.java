package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.*;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
@Mixin(PlayerChunkSender.class)
abstract class PerfSenderMixin {
    @WrapMethod(method="markChunkPendingToSend")
    private void pending(LevelChunk chunk,Operation<Void> original){
        PerfTrace.event((ServerLevel)chunk.getLevel(),chunk.getPos(),"SEND_PENDING","");original.call(chunk);
    }
    @WrapMethod(method="sendChunk")
    private static void packet(ServerGamePacketListenerImpl connection,ServerLevel level,LevelChunk chunk,Operation<Void> original){
        var span=PerfTrace.begin(level,chunk.getPos(),"PACKET_ENQUEUE");
        try{original.call(connection,level,chunk);}finally{PerfTrace.end(level,chunk.getPos(),"PACKET_ENQUEUE",span);}
    }
}
