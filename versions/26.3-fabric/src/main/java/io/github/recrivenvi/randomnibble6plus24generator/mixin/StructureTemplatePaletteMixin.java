package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StructureTemplate.Palette.class)
abstract class StructureTemplatePaletteMixin {
    @WrapMethod(method = "blocks(Lnet/minecraft/world/level/block/Block;)Ljava/util/List;")
    private List<StructureTemplate.StructureBlockInfo> randomnibble6plus24generator$filteredCache(
            Block block, Operation<List<StructureTemplate.StructureBlockInfo>> original) {
        // Native 26.3 uses HashMap.computeIfAbsent here. All consumers of a shared
        // palette must participate, including native physical generation and loading.
        synchronized (this) {
            return original.call(block);
        }
    }

    @WrapMethod(method = "jigsaws")
    private List<StructureTemplate.JigsawBlockInfo> randomnibble6plus24generator$jigsawCache(
            Operation<List<StructureTemplate.JigsawBlockInfo>> original) {
        synchronized (this) {
            return original.call();
        }
    }
}
