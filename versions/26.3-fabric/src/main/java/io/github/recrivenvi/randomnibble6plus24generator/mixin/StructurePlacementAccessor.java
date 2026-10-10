package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSpreadingStructurePlacement.class)
public interface StructurePlacementAccessor {
    @Accessor("exclusionZone")
    Optional<AbstractSpreadingStructurePlacement.ExclusionZone>
            randomnibble6plus24generator$exclusionZone();
}
