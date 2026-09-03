package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import net.minecraft.server.MinecraftServer;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;

/** Test-only clean shutdown hook for the ordinary Vanilla server regression. */
public final class Phase3C3ANormalVanillaVerification {

    private static final String PROPERTY =
            "randomnibble6plus24generator.phase3c3a.normal.autoStop";
    private static volatile int ticksBeforeStop = 2;
    private static volatile boolean stopped;

    private Phase3C3ANormalVanillaVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        if (!Boolean.getBoolean(PROPERTY) || stopped) return;
        if (MosaicWorldIdentity.isMosaicWorld(server)) {
            throw new IllegalStateException("Normal Vanilla regression unexpectedly has Mosaic identity");
        }
        if (ticksBeforeStop-- > 0) return;
        stopped = true;
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3C3A normal Vanilla regression reached clean shutdown gate");
        server.halt(false);
    }
}
