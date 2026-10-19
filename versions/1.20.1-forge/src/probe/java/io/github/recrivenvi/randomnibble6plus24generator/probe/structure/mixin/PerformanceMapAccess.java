package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface PerformanceMapAccess {
    @Accessor("updatingChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> mosaic$updatingMap();
}
