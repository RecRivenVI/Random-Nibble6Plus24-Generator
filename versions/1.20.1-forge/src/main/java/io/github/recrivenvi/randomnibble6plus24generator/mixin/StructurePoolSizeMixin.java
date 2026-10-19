package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(StructureTemplatePool.class)
abstract class StructurePoolSizeMixin {
    @Shadow private int maxSize;

    @Redirect(
            method = "getMaxSize",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;maxSize:I",
                            opcode = Opcodes.GETFIELD),
            require = 2)
    private int read(StructureTemplatePool pool) {
        if (!StructureGenerationLocks.enabled()) return maxSize;
        synchronized (pool) {
            return maxSize;
        }
    }

    @Redirect(
            method = "getMaxSize",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;maxSize:I",
                            opcode = Opcodes.PUTFIELD))
    private void write(StructureTemplatePool pool, int value) {
        if (!StructureGenerationLocks.enabled()) {
            maxSize = value;
            return;
        }
        // Native known elements compute the same immutable size without RNG. Cold
        // duplicate computation is allowed; every publication/read has this fence.
        synchronized (pool) {
            maxSize = value;
        }
    }
}
