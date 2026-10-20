package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import java.util.concurrent.atomic.AtomicReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MosaicPhysicalLevelState.class, remap = false)
public interface PhysicalStateAccess {
    @Accessor(value = "requested", remap = false)
    java.util.Map<net.minecraft.world.level.ChunkPos, net.minecraft.world.level.chunk.ChunkStatus>
            mosaic$requested();

    @Accessor(value = "plans", remap = false)
    java.util.Map<
                    net.minecraft.world.level.ChunkPos,
                    java.util.Map<
                            net.minecraft.world.level.ChunkPos,
                            net.minecraft.world.level.chunk.ChunkStatus>>
            mosaic$plans();

    @Accessor(value = "obligations", remap = false)
    java.util.Set<net.minecraft.world.level.ChunkPos> mosaic$obligations();

    @Accessor(value = "biomes", remap = false)
    java.util.Map<
                    net.minecraft.world.level.chunk.ChunkAccess,
                    io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                            .SpawnBiomeSnapshot>
            mosaic$spawnBiomes();

    @Accessor(value = "failure", remap = false)
    AtomicReference<Throwable> mosaic$retainedFailure();
}
