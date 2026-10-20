package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PlayerLifecycleEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.RuntimeLifecycleEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.VanillaLifecycleEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2ProductionVerification;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Phase3C2ProductionVerification.class, remap = false)
abstract class RuntimeProductionEvidenceMixin {
    @WrapMethod(method = "runIfRequested")
    private static void observe(MinecraftServer server, Operation<Void> original) {
        if (PlayerLifecycleEvidence.enabled()) {
            PlayerLifecycleEvidence.tick(server);
        } else if (RuntimeLifecycleEvidence.enabled()) {
            RuntimeLifecycleEvidence.tick(server, () -> request(server, original));
        } else {
            request(server, original);
        }
    }

    private static void request(MinecraftServer server, Operation<Void> original) {
        if (Boolean.getBoolean(ModConstants.MOD_ID + ".phase3c2.production.vanillaControl")) {
            VanillaLifecycleEvidence.run(server);
        } else {
            original.call(server);
        }
    }

    @Inject(method = "write", at = @At("HEAD"))
    private static void capture(JsonObject result, CallbackInfo callback) {
        RuntimeLifecycleEvidence.capture(result);
    }
}
