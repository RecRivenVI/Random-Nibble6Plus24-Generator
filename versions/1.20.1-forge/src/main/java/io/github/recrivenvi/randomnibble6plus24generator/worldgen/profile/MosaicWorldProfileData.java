package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** This version uses Function<CompoundTag,T>/save(CompoundTag), without a SavedData Factory. */
public final class MosaicWorldProfileData extends SavedData {
    public static final String ID = ModConstants.MOD_ID + "/mosaic_world_profile";
    private final MosaicWorldProfile profile;

    public MosaicWorldProfileData(MosaicWorldProfile profile) {
        this.profile = Objects.requireNonNull(profile);
        profile.requireSupported();
    }

    public static MosaicWorldProfileData load(CompoundTag tag) {
        return new MosaicWorldProfileData(
                MosaicWorldProfile.CODEC
                        .parse(NbtOps.INSTANCE, tag)
                        .getOrThrow(
                                false,
                                message -> {
                                    throw new IllegalStateException(message);
                                }));
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return (CompoundTag)
                MosaicWorldProfile.CODEC
                        .encodeStart(NbtOps.INSTANCE, profile)
                        .getOrThrow(
                                false,
                                message -> {
                                    throw new IllegalStateException(message);
                                });
    }

    public static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ID + ".dat");
    }
}
