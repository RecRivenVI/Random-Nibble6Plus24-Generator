package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.OrdinaryRequestEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2ProductionVerification;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Phase3C2ProductionVerification.class, remap = false)
abstract class NativeRequestEvidenceMixin {
    @WrapMethod(method = "runIfRequested")
    private static void run(MinecraftServer server, Operation<Void> original) {
        OrdinaryRequestEvidence.begin(server);
        boolean returned = false;
        try {
            original.call(server);
            returned = true;
        } finally {
            OrdinaryRequestEvidence.close(returned);
        }
    }

    @WrapOperation(
            method = "runIfRequested",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private static Object joined(CompletableFuture<?> future, Operation<Object> original) {
        Object value = original.call(future);
        OrdinaryRequestEvidence.joined(value);
        return value;
    }

    @WrapMethod(method = "write")
    private static void write(JsonObject result, Operation<Void> original) {
        original.call(result);
        OrdinaryRequestEvidence.captured(result);
    }

    @WrapOperation(
            method = "runIfRequested",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"),
            require = 1,
            allow = 1)
    private static void boundedObservation(
            MinecraftServer server, BooleanSupplier complete, Operation<Void> original) {
        original.call(
                server,
                (BooleanSupplier)
                        () -> {
                            boolean done = complete.getAsBoolean();
                            if (!done) OrdinaryRequestEvidence.checkDeadline();
                            return done;
                        });
    }
}
