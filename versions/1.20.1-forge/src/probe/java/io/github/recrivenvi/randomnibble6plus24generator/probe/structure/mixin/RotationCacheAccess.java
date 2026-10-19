package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.mojang.math.OctahedralGroup;
import java.util.Map;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OctahedralGroup.class)
public interface RotationCacheAccess {
    @Accessor("rotatedDirections")
    Map<Direction, Direction> mosaic$directions();

    @Accessor("rotatedDirections")
    void mosaic$directions(Map<Direction, Direction> value);
}
