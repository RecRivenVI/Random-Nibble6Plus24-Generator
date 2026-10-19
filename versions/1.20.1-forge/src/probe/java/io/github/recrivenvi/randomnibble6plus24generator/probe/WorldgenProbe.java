package io.github.recrivenvi.randomnibble6plus24generator.probe;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.seed.SeedContractVerifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(ModConstants.MOD_ID + "_probe")
public final class WorldgenProbe {
    public WorldgenProbe() {
        if (io.github.recrivenvi.randomnibble6plus24generator.probe.performance
                .LegacyPerformanceProbe.enabled())
            MinecraftForge.EVENT_BUS.addListener(WorldgenProbe::performanceTick);
        if (io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativePhysicalProbe
                .enabled()) {
            MinecraftForge.EVENT_BUS.addListener(WorldgenProbe::physicalStarted);
            MinecraftForge.EVENT_BUS.addListener(WorldgenProbe::physicalTick);
            MinecraftForge.EVENT_BUS.addListener(WorldgenProbe::physicalStopped);
        }
        if ("conformance-seed".equals(System.getProperty("validation.name"))) {
            MinecraftForge.EVENT_BUS.addListener(WorldgenProbe::started);
            MinecraftForge.EVENT_BUS.addListener(WorldgenProbe::stopped);
        }
    }

    private static void performanceTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
            io.github.recrivenvi.randomnibble6plus24generator.probe.performance
                    .LegacyPerformanceProbe.tick(event.getServer());
    }

    private static void physicalStarted(ServerStartedEvent event) {
        if (io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe
                .enabled()) {
            io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe
                    .started(event.getServer());
            return;
        }
        io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativePhysicalProbe
                .started(event.getServer());
    }

    private static void physicalTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe
                .enabled()) {
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
                io.github.recrivenvi.randomnibble6plus24generator.probe.production
                        .NativeLifecycleProbe.tick(event.getServer());
            return;
        }
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END)
            io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativePhysicalProbe
                    .tick(event.getServer());
    }

    private static void physicalStopped(ServerStoppedEvent event) {
        if (io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe
                .enabled()) {
            io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe
                    .stopped(event.getServer());
            return;
        }
        io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativePhysicalProbe
                .stopped(event.getServer());
    }

    private static void started(ServerStartedEvent event) {
        if (Boolean.getBoolean(
                io.github.recrivenvi.randomnibble6plus24generator.probe.seed
                                .NativePhysicalPlanVerifier.PREFIX
                        + "enabled"))
            io.github.recrivenvi.randomnibble6plus24generator.probe.seed.NativePhysicalPlanVerifier
                    .run(event.getServer());
        else SeedContractVerifier.run(event.getServer());
    }

    private static void stopped(ServerStoppedEvent event) {
        SeedContractVerifier.stopped(event.getServer());
    }
}
