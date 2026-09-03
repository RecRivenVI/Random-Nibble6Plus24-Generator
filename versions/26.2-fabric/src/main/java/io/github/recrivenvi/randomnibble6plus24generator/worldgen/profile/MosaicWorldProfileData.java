package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.util.datafix.DataFixTypes;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;

public final class MosaicWorldProfileData extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            RandomNibble6Plus24Generator.MOD_ID,
            "mosaic_world_profile");

    public static final Codec<MosaicWorldProfileData> CODEC = MosaicWorldProfile.CODEC.xmap(
            MosaicWorldProfileData::new,
            MosaicWorldProfileData::profile);

    public static final SavedDataType<MosaicWorldProfileData> TYPE = new SavedDataType<>(
            ID,
            () -> new MosaicWorldProfileData(MosaicWorldProfile.current()),
            CODEC,
            // There is no generic custom SavedData data-fix type in 26.2. The
            // profile is world-generation metadata, so use the vanilla world
            // generation settings category to keep SavedDataStorage reload
            // compatible (and, importantly, non-null for existing files).
            DataFixTypes.SAVED_DATA_WORLD_GEN_SETTINGS);

    private final MosaicWorldProfile profile;

    public MosaicWorldProfileData(MosaicWorldProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        profile.requireSupported();
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    public static Optional<MosaicWorldProfileData> loadIfPresent(MinecraftServer server) {
        return Optional.ofNullable(server.getDataStorage().get(TYPE));
    }

    public static Path path(MinecraftServer server) {
        return ID.withSuffix(".dat").resolveAgainst(server.getWorldPath(LevelResource.DATA));
    }

    public static boolean fileExists(MinecraftServer server) {
        return Files.isRegularFile(path(server));
    }
}
