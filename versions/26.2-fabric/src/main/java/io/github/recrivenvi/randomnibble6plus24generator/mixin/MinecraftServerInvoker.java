package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerInvoker {

    @Invoker("setInitialSpawn")
    static void randomnibble6plus24generator$invokeSetInitialSpawn(
            ServerLevel level,
            ServerLevelData levelData,
            boolean generateBonusChest,
            boolean debug,
            LevelLoadListener listener) {
        throw new AssertionError("Mixin invoker was not transformed");
    }
}
