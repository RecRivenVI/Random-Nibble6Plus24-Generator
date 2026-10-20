package io.github.recrivenvi.randomnibble6plus24generator.probe.smoke;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

public final class DependencyClientSmoke {
    private static boolean finished;

    public static void register() {
        if (FMLEnvironment.dist == Dist.CLIENT)
            MinecraftForge.EVENT_BUS.addListener(DependencyClientSmoke::tick);
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (!"smoke-dependencies".equals(System.getProperty("validation.name"))
                || finished
                || event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
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
        finished = true;
        client.stop();
    }
}
