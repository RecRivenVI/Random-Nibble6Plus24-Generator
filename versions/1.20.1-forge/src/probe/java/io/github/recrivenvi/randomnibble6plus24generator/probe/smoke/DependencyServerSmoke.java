package io.github.recrivenvi.randomnibble6plus24generator.probe.smoke;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;

public final class DependencyServerSmoke {
    private static boolean finished;

    public static void tick(TickEvent.ServerTickEvent event) {
        if (!"smoke-dependencies".equals(System.getProperty("validation.name"))
                || !"server".equals(System.getProperty("validation.role"))
                || finished
                || event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
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
        finished = true;
        server.halt(false);
    }
}
