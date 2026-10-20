package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import com.google.gson.Gson;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bootstrap metadata only; this launch is not MCP installation or gameplay evidence. */
@Mixin(Minecraft.class)
abstract class ClientLaunchExportMixin {
    @Unique private boolean mosaic$exported;

    @Inject(method = "tick", at = @At("TAIL"))
    private void export(CallbackInfo callback) throws java.io.IOException {
        if (mosaic$exported) return;
        var command = System.getProperty("sun.java.command").split(" ");
        var record = new LinkedHashMap<String, Object>();
        record.put("target", System.getProperty("validation.target"));
        record.put(
                "loader",
                ModList.get()
                        .getModContainerById("neoforge")
                        .orElseThrow()
                        .getModInfo()
                        .getVersion()
                        .toString());
        record.put("java", ProcessHandle.current().info().command().orElseThrow());
        record.put("classpath", System.getProperty("java.class.path"));
        record.put("main", command[0]);
        record.put("gameArguments", Arrays.copyOfRange(command, 1, command.length));
        record.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        record.put("scope", "NATIVE_CLIENT_LAUNCH_EXPORT_NOT_MCP_OR_WORLD_PROOF");
        var output =
                Path.of(
                        System.getProperty("validation.result"),
                        "native-launch",
                        System.getProperty("validation.target"),
                        "client.json");
        Files.createDirectories(output.getParent());
        if (Files.exists(output))
            throw new IllegalStateException("Preserve native bootstrap evidence");
        Files.writeString(output, new Gson().toJson(record) + "\n");
        mosaic$exported = true;
        ((Minecraft) (Object) this).stop();
    }
}
