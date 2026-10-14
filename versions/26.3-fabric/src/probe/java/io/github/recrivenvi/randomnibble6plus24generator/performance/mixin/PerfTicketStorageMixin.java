package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TicketStorage.class)
abstract class PerfTicketStorageMixin {
    @WrapMethod(method = "addTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean add(long key, Ticket ticket, Operation<Boolean> original) {
        boolean result = original.call(key, ticket);
        if (result)
            PerfTrace.event(
                    this,
                    ChunkPos.unpack(key),
                    "TICKET_ADD",
                    BuiltInRegistries.TICKET_TYPE.getKey(ticket.getType())
                            + "@"
                            + ticket.getTicketLevel());
        return result;
    }

    @WrapMethod(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)Z")
    private boolean remove(long key, Ticket ticket, Operation<Boolean> original) {
        boolean result = original.call(key, ticket);
        if (result)
            PerfTrace.event(
                    this,
                    ChunkPos.unpack(key),
                    "TICKET_REMOVE",
                    BuiltInRegistries.TICKET_TYPE.getKey(ticket.getType())
                            + "@"
                            + ticket.getTicketLevel());
        return result;
    }
}
