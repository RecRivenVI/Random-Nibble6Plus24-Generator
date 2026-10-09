package io.github.recrivenvi.randomnibble6plus24generator.poitest.mixin;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.poitest.PoiOwnershipVerification;

/** Observes real shared storage calls, without locking their execution or changing results. */
@Mixin(SectionStorage.class)
abstract class PoiStorageObservationMixin {
    @WrapMethod(method="getOrLoad")
    private Optional<?> read(long section, Operation<Optional<?>> original) {
        PoiOwnershipVerification.enter(this,"getOrLoad",section);
        try { return original.call(section); }
        finally { PoiOwnershipVerification.leave(this); }
    }
    @WrapMethod(method="getOrCreate")
    private Object create(long section, Operation<Object> original) {
        PoiOwnershipVerification.enter(this,"getOrCreate",section);
        try { return original.call(section); }
        finally { PoiOwnershipVerification.leave(this); }
    }
    @WrapMethod(method="tick")
    private void tick(BooleanSupplier time, Operation<Void> original) {
        PoiOwnershipVerification.enter(this,"tick/unpackPendingLoads",0);
        try { original.call(time); }
        finally { PoiOwnershipVerification.leave(this); }
    }
}
