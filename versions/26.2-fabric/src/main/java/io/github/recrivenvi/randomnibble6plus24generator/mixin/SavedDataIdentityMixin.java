package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.Map;
import java.util.Optional;

import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicProfileChangeSource;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;

/** Only the server-root storage has an identity listener; ordinary SavedData is untouched. */
@Mixin(SavedDataStorage.class)
abstract class SavedDataIdentityMixin implements MosaicProfileChangeSource {
    @Shadow @Final
    private Map<SavedDataType<?>, Optional<SavedData>> cache;

    @Unique
    private volatile Runnable randomnibble6plus24generator$identityReplacement;

    @Override
    public void randomnibble6plus24generator$onIdentityReplacement(Runnable listener) {
        randomnibble6plus24generator$identityReplacement = listener;
    }

    @Override
    public void randomnibble6plus24generator$discardProfileForExplicitReload() {
        Runnable listener = randomnibble6plus24generator$identityReplacement;
        if (listener != null) listener.run();
        cache.remove(MosaicWorldProfileData.TYPE);
    }

    @Inject(method = "set", at = @At("HEAD"))
    private void randomnibble6plus24generator$invalidateReplacedIdentity(
            SavedDataType<?> type, SavedData data, CallbackInfo callback) {
        Runnable listener = randomnibble6plus24generator$identityReplacement;
        if (listener != null && (type.id().equals(MosaicWorldProfileData.ID)
                || type.id().equals(WorldGenSettings.TYPE.id()))) {
            listener.run();
        }
    }
}
