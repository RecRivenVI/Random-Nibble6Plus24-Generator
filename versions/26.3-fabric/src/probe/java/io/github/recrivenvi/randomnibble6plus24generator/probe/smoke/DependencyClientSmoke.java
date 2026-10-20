package io.github.recrivenvi.randomnibble6plus24generator.probe.smoke;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class DependencyClientSmoke {
    @Unique private boolean mosaic$finished;

    @Inject(method = "tick", at = @At("TAIL"))
    private void mosaic$observe(CallbackInfo callback) {
        if (!"smoke-dependencies".equals(System.getProperty("validation.name")) || mosaic$finished)
            return;
        Minecraft client = (Minecraft) (Object) this;
        if (client.level == null
                || client.player == null
                || client.getSingleplayerServer() == null
                || client.player.tickCount < 20) return;
        int x = client.player.getBlockX() >> 4;
        int z = client.player.getBlockZ() >> 4;
        var chunk = client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty()) return;
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("role", "client");
        result.addProperty("target", System.getProperty("validation.target"));
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("logicalProcessors", Runtime.getRuntime().availableProcessors());
        result.addProperty("playerTicks", client.player.tickCount);
        result.addProperty("chunkX", x);
        result.addProperty("chunkZ", z);
        result.addProperty("chunkClass", chunk.getClass().getName());
        result.addProperty("integratedServer", true);
        Path output =
                Path.of(
                        System.getProperty("validation.result"),
                        System.getProperty("validation.target"),
                        "client.json");
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, result + "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        mosaic$finished = true;
        client.stop();
    }
}
