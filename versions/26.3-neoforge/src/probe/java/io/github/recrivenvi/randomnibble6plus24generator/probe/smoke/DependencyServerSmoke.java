package io.github.recrivenvi.randomnibble6plus24generator.probe.smoke;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class DependencyServerSmoke {
    @Unique private boolean mosaic$finished;

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void mosaic$observe(BooleanSupplier budget, CallbackInfo callback) {
        if (!"smoke-dependencies".equals(System.getProperty("validation.name"))
                || !"server".equals(System.getProperty("validation.role"))
                || mosaic$finished) return;
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (server.getTickCount() < 40) return;
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("role", "server");
        result.addProperty("target", System.getProperty("validation.target"));
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("logicalProcessors", Runtime.getRuntime().availableProcessors());
        result.addProperty("nativeTicks", server.getTickCount());
        result.addProperty(
                "save", server.getWorldPath(LevelResource.ROOT).toAbsolutePath().toString());
        Path output =
                Path.of(
                        System.getProperty("validation.result"),
                        System.getProperty("validation.target"),
                        "server.json");
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, result + "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        mosaic$finished = true;
        server.halt(false);
    }
}
