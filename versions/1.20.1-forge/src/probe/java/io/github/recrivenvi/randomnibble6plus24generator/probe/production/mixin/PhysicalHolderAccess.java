package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface PhysicalHolderAccess {
    @Invoker("getUpdatingChunkIfPresent")
    ChunkHolder mosaic$holder(long position);
}
