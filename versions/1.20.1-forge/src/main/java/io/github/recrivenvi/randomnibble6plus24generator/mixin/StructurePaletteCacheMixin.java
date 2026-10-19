package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(StructureTemplate.Palette.class)
abstract class StructurePaletteCacheMixin {
    @Redirect(
            method = "blocks(Lnet/minecraft/world/level/block/Block;)Ljava/util/List;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object lookup(Map<Object, Object> cache, Object key, Function<Object, Object> factory) {
        if (!StructureGenerationLocks.enabled()) return cache.computeIfAbsent(key, factory);
        // This version uses HashMap, unlike the later Target's ConcurrentMap.
        synchronized (this) {
            return cache.computeIfAbsent(key, factory);
        }
    }
}
