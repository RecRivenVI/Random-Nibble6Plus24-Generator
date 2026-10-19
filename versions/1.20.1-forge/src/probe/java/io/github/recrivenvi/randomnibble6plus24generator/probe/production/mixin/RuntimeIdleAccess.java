package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface RuntimeIdleAccess {
    @Accessor("emptyTime")
    int mosaic$emptyTime();
}
