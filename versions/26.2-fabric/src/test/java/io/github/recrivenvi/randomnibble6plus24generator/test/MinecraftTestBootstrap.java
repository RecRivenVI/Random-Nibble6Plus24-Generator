package io.github.recrivenvi.randomnibble6plus24generator.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftTestBootstrap {

    private static boolean bootstrapped;

    private MinecraftTestBootstrap() {
    }

    public static synchronized void ensureBootstrapped() {
        if (bootstrapped) {
            return;
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bootstrapped = true;
    }
}
