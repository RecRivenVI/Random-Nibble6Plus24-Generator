package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import java.util.concurrent.Executor;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkHolder.class)
abstract class PerfChunkHolderMixin {
    @WrapMethod(method = "updateFutures")
    private void futures(ChunkMap map, Executor executor, Operation<Void> original) {
        ChunkHolder self = (ChunkHolder) (Object) this;
        ServerLevel level = PerfTrace.owner(map);
        if (level != null) PerfTrace.bind(self, level);
        if (level != null) {
            if (level.getServer().isSameThread())
                PerfTrace.event(
                        level,
                        self.getPos(),
                        "NATIVE_TICKET_DEMAND",
                        "ticket="
                                + self.getTicketLevel()
                                + ";generation="
                                + ChunkLevel.generationStatus(self.getTicketLevel()));
            else
                PerfTrace.event(
                        level,
                        self.getPos(),
                        "NATIVE_TICKET_DEMAND_OFF_THREAD",
                        Thread.currentThread().getName());
        }
        var span = PerfTrace.begin(level, self.getPos(), "UPDATE_FUTURES");
        try {
            original.call(map, executor);
        } finally {
            PerfTrace.end(level, self.getPos(), "UPDATE_FUTURES", span);
        }
    }
}
