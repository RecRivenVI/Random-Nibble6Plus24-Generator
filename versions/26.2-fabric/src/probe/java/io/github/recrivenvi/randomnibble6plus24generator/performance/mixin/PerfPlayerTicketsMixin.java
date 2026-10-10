package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$PlayerTicketTracker")
abstract class PerfPlayerTicketsMixin {
    @Shadow @Final private DistanceManager this$0;

    @WrapMethod(method = "onLevelChange(JIZZ)V")
    private void demand(
            long key, int distance, boolean was, boolean now, Operation<Void> original) {
        if (was != now)
            PerfTrace.event(
                    this$0,
                    ChunkPos.unpack(key),
                    now ? "VIEW_DEMAND" : "VIEW_WITHDRAWN",
                    "distance=" + distance);
        original.call(key, distance, was, now);
    }
}
