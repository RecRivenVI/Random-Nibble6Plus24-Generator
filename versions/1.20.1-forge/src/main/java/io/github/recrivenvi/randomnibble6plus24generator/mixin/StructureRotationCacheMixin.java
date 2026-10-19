package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.math.OctahedralGroup;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import java.util.Map;
import net.minecraft.core.Direction;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(OctahedralGroup.class)
abstract class StructureRotationCacheMixin {
    @Shadow private Map<Direction, Direction> rotatedDirections;

    @Redirect(
            method = "rotate(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/Direction;",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lcom/mojang/math/OctahedralGroup;rotatedDirections:Ljava/util/Map;",
                            opcode = Opcodes.GETFIELD),
            require = 3,
            allow = 3)
    private Map<Direction, Direction> complete(OctahedralGroup owner, Direction requested) {
        if (!StructureGenerationLocks.enabled()
                || StructureGenerationLocks.rotationOwner() == owner) return rotatedDirections;
        synchronized (owner) {
            if (rotatedDirections == null) {
                var previous = StructureGenerationLocks.beginRotation(owner);
                try {
                    // The reentrant native method builds all six entries using its
                    // unchanged algorithm; only this owning thread may see its partial map.
                    owner.rotate(requested);
                } finally {
                    StructureGenerationLocks.endRotation(previous);
                }
            }
            if (rotatedDirections == null || rotatedDirections.size() != Direction.values().length)
                throw new IllegalStateException(
                        "Native rotation cache is incomplete outside its initializer");
            return rotatedDirections;
        }
    }
}
