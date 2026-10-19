package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {
    @Accessor("exclusionZone")
    Optional<StructurePlacement.ExclusionZone> randomnibble6plus24generator$exclusionZone();
}
