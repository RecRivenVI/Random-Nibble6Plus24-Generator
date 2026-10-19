package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** This version's native custom SavedData envelope; profile fields retain format V2. */
public final class MosaicWorldProfileData extends SavedData {
    public static final String ID = ModConstants.MOD_ID + "/mosaic_world_profile";
    public static final Factory<MosaicWorldProfileData> FACTORY =
            new Factory<>(
                    () -> {
                        throw new IllegalStateException("Mosaic profile must not be guessed");
                    },
                    (tag, registry) ->
                            new MosaicWorldProfileData(
                                    MosaicWorldProfile.CODEC
                                            .parse(NbtOps.INSTANCE, tag)
                                            .getOrThrow()));
    private final MosaicWorldProfile profile;

    public MosaicWorldProfileData(MosaicWorldProfile profile) {
        this.profile = Objects.requireNonNull(profile);
        profile.requireSupported();
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
        return (CompoundTag)
                MosaicWorldProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow();
    }

    public static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ID + ".dat");
    }
}
