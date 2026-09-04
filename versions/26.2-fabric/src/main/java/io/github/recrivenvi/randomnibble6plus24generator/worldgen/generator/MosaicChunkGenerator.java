package io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.Structure;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureIndexStore;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlay;

/**
 * Serialized Mosaic world identity and future worldgen integration point.
 *
 * <p>Phase 1B deliberately implements no generation behavior. Every vanilla
 * generation stage fails before touching its arguments so a Mosaic-marked save
 * cannot silently receive ordinary master-seed terrain.
 */
public final class MosaicChunkGenerator extends ChunkGenerator {

    public static final Identifier CODEC_ID = Identifier.fromNamespaceAndPath(
            RandomNibble6Plus24Generator.MOD_ID,
            "mosaic");

    public static final MapCodec<MosaicChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(MosaicChunkGenerator::getBiomeSource),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(MosaicChunkGenerator::generatorSettings),
            MosaicWorldProfile.CODEC.fieldOf("mosaic_profile").forGetter(MosaicChunkGenerator::profile))
            .apply(instance, MosaicChunkGenerator::new));

    private final Holder<NoiseGeneratorSettings> settings;
    private final MosaicWorldProfile profile;

    public MosaicChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings,
            MosaicWorldProfile profile) {
        super(biomeSource);
        this.settings = settings;
        this.profile = profile;
        profile.requireSupported();
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return settings;
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(
            RandomState randomState,
            Blender blender,
            StructureManager structureManager,
            ChunkAccess chunk) {
        throw unavailable("createBiomes");
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager templateManager,
            ResourceKey<Level> dimension) {
        throw unavailable("createStructures");
    }

    @Override
    public void createReferences(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkAccess chunk) {
        throw unavailable("createReferences");
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk) {
        throw unavailable("fillFromNoise");
    }

    @Override
    public void buildSurface(
            WorldGenRegion region,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk) {
        throw unavailable("buildSurface");
    }

    @Override
    public void applyCarvers(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk) {
        throw unavailable("applyCarvers");
    }

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager) {
        throw unavailable("applyBiomeDecoration");
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        throw unavailable("spawnOriginalMobs");
    }

    /**
     * Overlay V1 searches only the persisted physical Mosaic structure index.
     * Unknown chunks are never loaded or generated by this method.
     */
    @Override
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
            ServerLevel level,
            HolderSet<Structure> structures,
            BlockPos origin,
            int radius,
            boolean skipKnownStructures) {
        if (!MosaicStructureOverlay.active(level)) return null;
        return MosaicStructureIndexStore.findNearest(level, structures, origin, radius).orElse(null);
    }

    @Override
    public int getGenDepth() {
        return settings.value().noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return settings.value().seaLevel();
    }

    @Override
    public int getMinY() {
        return settings.value().noiseSettings().minY();
    }

    /**
     * Supplies only the seed-independent anchor used by Minecraft's initial
     * spawn search.  Terrain itself still comes exclusively from the Mosaic
     * materialization pipeline; no vanilla master-seed worldgen is delegated.
     */
    @Override
    public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
        return Math.max(getMinY() + 1, getSeaLevel() + 1);
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types heightmap,
            LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        throw unavailable("getBaseHeight");
    }

    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        throw unavailable("getBaseColumn");
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        throw unavailable("addDebugScreenInfo");
    }

    private static MosaicGenerationUnavailableException unavailable(String operation) {
        PhysicalMosaicTrace.recordGeneratorCall(operation);
        return new MosaicGenerationUnavailableException(operation);
    }
}
