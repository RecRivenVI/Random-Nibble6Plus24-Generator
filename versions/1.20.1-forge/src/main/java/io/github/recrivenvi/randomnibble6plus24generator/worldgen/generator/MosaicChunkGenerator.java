package io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Explicit B Codec with fail-closed physical generator entry points. */
public final class MosaicChunkGenerator extends ChunkGenerator {
    public static final Codec<MosaicChunkGenerator> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            BiomeSource.CODEC
                                                    .fieldOf("biome_source")
                                                    .forGetter(
                                                            MosaicChunkGenerator::getBiomeSource),
                                            NoiseGeneratorSettings.CODEC
                                                    .fieldOf("settings")
                                                    .forGetter(
                                                            MosaicChunkGenerator
                                                                    ::generatorSettings),
                                            MosaicWorldProfile.CODEC
                                                    .fieldOf("mosaic_profile")
                                                    .forGetter(MosaicChunkGenerator::profile))
                                    .apply(instance, MosaicChunkGenerator::new));
    private final Holder<NoiseGeneratorSettings> settings;
    private final MosaicWorldProfile profile;

    public MosaicChunkGenerator(
            BiomeSource source,
            Holder<NoiseGeneratorSettings> settings,
            MosaicWorldProfile profile) {
        super(source);
        this.settings = Objects.requireNonNull(settings);
        this.profile = Objects.requireNonNull(profile);
        profile.requireSupported();
        if (profile.featureOrderingAlgorithmVersion()
                != MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY)
            throw new IllegalArgumentException(
                    "This Target's distinct B codec requires Ordering 2");
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return settings;
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    public NoiseBasedChunkGenerator newLocalGenerator() {
        return new NoiseBasedChunkGenerator(getBiomeSource(), settings);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getGenDepth() {
        return settings.value().noiseSettings().height();
    }

    @Override
    public int getMinY() {
        return settings.value().noiseSettings().minY();
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        return Math.max(getMinY() + 1, getSeaLevel() + 1);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(
            Executor executor,
            RandomState random,
            Blender blender,
            StructureManager structures,
            ChunkAccess chunk) {
        throw unavailable("createBiomes");
    }

    @Override
    public void createStructures(
            RegistryAccess registries,
            ChunkGeneratorStructureState state,
            StructureManager structures,
            ChunkAccess chunk,
            StructureTemplateManager templates) {
        throw unavailable("createStructures");
    }

    @Override
    public void createReferences(
            WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {
        throw unavailable("createReferences");
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Executor executor,
            Blender blender,
            RandomState random,
            StructureManager structures,
            ChunkAccess chunk) {
        throw unavailable("fillFromNoise");
    }

    @Override
    public void buildSurface(
            WorldGenRegion region,
            StructureManager structures,
            RandomState random,
            ChunkAccess chunk) {
        throw unavailable("buildSurface");
    }

    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState random,
            BiomeManager biomes,
            StructureManager structures,
            ChunkAccess chunk,
            GenerationStep.Carving stage) {
        throw unavailable("applyCarvers");
    }

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel region, ChunkAccess chunk, StructureManager structures) {
        throw unavailable("applyBiomeDecoration");
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        throw unavailable("spawnOriginalMobs");
    }

    @Override
    public int getBaseHeight(
            int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        throw unavailable("getBaseHeight");
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        throw unavailable("getBaseColumn");
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos position) {
        throw unavailable("addDebugScreenInfo");
    }

    @Override
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
            ServerLevel level,
            HolderSet<Structure> structures,
            BlockPos origin,
            int radius,
            boolean skipKnown) {
        throw unavailable("locate before Mosaic index routing");
    }

    @Override
    public net.minecraft.util.random.WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(
            Holder<Biome> biome,
            StructureManager structures,
            MobCategory category,
            BlockPos position) {
        throw unavailable("runtime structure spawning before Overlay routing");
    }

    private static IllegalStateException unavailable(String entry) {
        return new IllegalStateException(
                "Mosaic physical "
                        + entry
                        + " requires its validated native lifecycle; refusing master-seed generation");
    }
}
