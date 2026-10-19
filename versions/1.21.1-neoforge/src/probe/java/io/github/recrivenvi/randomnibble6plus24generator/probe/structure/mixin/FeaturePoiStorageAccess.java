package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Optional;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionStorage.class)
public interface FeaturePoiStorageAccess {
    @Accessor("storage")
    Long2ObjectMap<Optional<PoiSection>> mosaic$storage();

    @Accessor("dirty")
    LongLinkedOpenHashSet mosaic$dirty();
}
