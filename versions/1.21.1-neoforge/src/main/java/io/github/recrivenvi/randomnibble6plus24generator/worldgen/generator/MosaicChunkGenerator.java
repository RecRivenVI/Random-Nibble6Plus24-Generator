package io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Serialized B identity. The physical scheduler must never execute master-seed terrain methods. */
public final class MosaicChunkGenerator extends ChunkGenerator {
    public static final ResourceLocation CODEC_ID =
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "development_b");
    public static final MapCodec<MosaicChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(
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
            BiomeSource biomes,
            Holder<NoiseGeneratorSettings> settings,
            MosaicWorldProfile profile) {
        super(biomes);
        this.settings = java.util.Objects.requireNonNull(settings);
        this.profile = java.util.Objects.requireNonNull(profile);
        profile.requireSupported();
        if (profile.featureOrderingAlgorithmVersion()
                != MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY)
            throw new IllegalArgumentException(
                    "This target's distinct B codec requires Ordering 2");
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return settings;
    }

    public boolean developmentB() {
        return true;
    }

    @Override
    public net.minecraft.util.random.WeightedRandomList<
                    net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData>
            getMobsAt(
                    Holder<Biome> biome,
                    StructureManager structures,
                    net.minecraft.world.entity.MobCategory category,
                    BlockPos pos) {
        return newLocalGenerator().getMobsAt(biome, structures, category, pos);
    }

    /**
     * Only definitions are reused; the caller must create RandomState and structure state for S(C).
     */
    public NoiseBasedChunkGenerator newLocalGenerator() {
        return new NoiseBasedChunkGenerator(getBiomeSource(), settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
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
    public int getSpawnHeight(LevelHeightAccessor height) {
        return Math.max(getMinY() + 1, getSeaLevel() + 1);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(
            RandomState random, Blender blender, StructureManager structures, ChunkAccess chunk) {
        throw unavailable("createBiomes");
    }

    @Override
    public void createStructures(
            RegistryAccess registry,
            ChunkGeneratorStructureState state,
            StructureManager manager,
            ChunkAccess chunk,
            StructureTemplateManager templates) {
        throw unavailable("createStructures");
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager manager, ChunkAccess chunk) {
        throw unavailable("createReferences");
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
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
            WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        throw unavailable("applyBiomeDecoration");
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        throw unavailable("spawnOriginalMobs");
    }

    @Override
    public int getBaseHeight(
            int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        throw unavailable("getBaseHeight");
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        throw unavailable("getBaseColumn");
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos pos) {
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

    private static IllegalStateException unavailable(String method) {
        return new IllegalStateException(
                "Mosaic physical "
                        + method
                        + " requires its validated production route; refusing master-seed fallback");
    }
}
