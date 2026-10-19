package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicProfileChangeSource;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import java.util.Map;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DimensionDataStorage.class)
abstract class DimensionDataIdentityMixin implements MosaicProfileChangeSource {
    @Shadow @Final private Map<String, SavedData> cache;
    @Unique private Runnable mosaic$profileReplacement;

    @Override
    public void randomnibble6plus24generator$onIdentityReplacement(Runnable listener) {
        mosaic$profileReplacement = listener;
    }

    @Inject(method = "set", at = @At("HEAD"))
    private void mosaic$replaced(String id, SavedData data, CallbackInfo callback) {
        if (MosaicWorldProfileData.ID.equals(id) && mosaic$profileReplacement != null)
            mosaic$profileReplacement.run();
    }

    @Override
    public void randomnibble6plus24generator$discardProfileForExplicitReload() {
        if (mosaic$profileReplacement == null)
            throw new IllegalStateException("Profile reload outside its owning world lifecycle");
        mosaic$profileReplacement.run();
        cache.remove(MosaicWorldProfileData.ID);
    }
}
