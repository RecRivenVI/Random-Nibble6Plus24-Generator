package io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.ticks.ProtoChunkTicks;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.SerializableChunkDataInvoker;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun;

/**
 * Detached, target-only, pre-light handoff of one stable FEATURES ProtoChunk.
 * The payload is Vanilla SerializableChunkData NBT with deterministic runtime bookkeeping and no light layers.
 */
public final class CanonicalChunkArtifact {

    private static final Comparator<BlockPos> BLOCK_POS_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getZ())
            .thenComparingInt(pos -> pos.getX());

    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int height;
    private final long localWorldSeed;
    private final int mosaicFormatVersion;
    private final int seedDerivationAlgorithmVersion;
    private final int featureOrderingAlgorithmVersion;
    private final byte[] vanillaPayload;
    private final long[] instantiatedBlockEntityPositions;
    private final String rawFingerprint;

    private CanonicalChunkArtifact(
            String dimension,
            ChunkPos chunkPos,
            int minY,
            int height,
            long localWorldSeed,
            MosaicWorldProfile profile,
            byte[] vanillaPayload,
            long[] instantiatedBlockEntityPositions) {
        this.dimension = dimension;
        this.chunkX = chunkPos.x();
        this.chunkZ = chunkPos.z();
        this.minY = minY;
        this.height = height;
        this.localWorldSeed = localWorldSeed;
        this.mosaicFormatVersion = profile.formatVersion();
        this.seedDerivationAlgorithmVersion = profile.seedDerivationAlgorithmVersion();
        this.featureOrderingAlgorithmVersion = profile.featureOrderingAlgorithmVersion();
        this.vanillaPayload = vanillaPayload.clone();
        this.instantiatedBlockEntityPositions = instantiatedBlockEntityPositions.clone();
        this.rawFingerprint = calculateRawFingerprint();
    }

    public static CanonicalChunkArtifact capture(
            FeatureStableGenerationRun run,
            String dimension,
            long localWorldSeed,
            MosaicWorldProfile profile,
            RegistryAccess registryAccess,
            StructurePieceSerializationContext structureContext) {
        profile.requireSupported();
        if (!(run.targetChunk() instanceof ProtoChunk source)) {
            throw new IllegalArgumentException("Canonical artifact requires a ProtoChunk target");
        }
        if (source.getPersistedStatus() != ChunkStatus.FEATURES
                || run.featureTrace().requestedWriters().size() != 9
                || !run.featureTrace().requestedWriters().equals(run.featureTrace().completedWriters())) {
            throw new IllegalStateException("Artifact capture requires a stable FEATURES barrier");
        }
        requireFreshPreLightState(source);

        PalettedContainerFactory containerFactory = PalettedContainerFactory.create(registryAccess);
        List<SerializableChunkData.SectionData> sections = new ArrayList<>(source.getSectionsCount());
        for (int index = 0; index < source.getSections().length; index++) {
            sections.add(new SerializableChunkData.SectionData(
                    source.getSectionYFromSectionIndex(index),
                    source.getSections()[index].copy(),
                    null,
                    null));
        }

        long[] carvingMask = source.getCarvingMask() == null
                ? null
                : source.getCarvingMask().toArray().clone();
        Map<Heightmap.Types, long[]> heightmaps = new EnumMap<>(Heightmap.Types.class);
        for (Map.Entry<Heightmap.Types, Heightmap> entry : source.getHeightmaps()) {
            heightmaps.put(entry.getKey(), entry.getValue().getRawData().clone());
        }
        ShortList[] postProcessing = copyPostProcessing(source.getPostProcessing());
        List<CompoundTag> rawEntities = source.getEntities().stream().map(CompoundTag::copy).toList();

        List<BlockPos> blockEntityPositions = new ArrayList<>(source.getBlockEntitiesPos());
        blockEntityPositions.sort(BLOCK_POS_ORDER);
        List<CompoundTag> rawBlockEntities = new ArrayList<>(blockEntityPositions.size());
        for (BlockPos pos : blockEntityPositions) {
            CompoundTag tag = source.getBlockEntityNbtForSaving(pos, registryAccess);
            if (tag != null) rawBlockEntities.add(tag.copy());
        }
        long[] instantiatedPositions = source.getBlockEntities().keySet().stream()
                .sorted(BLOCK_POS_ORDER)
                .mapToLong(BlockPos::asLong)
                .toArray();

        CompoundTag structureData = SerializableChunkDataInvoker.randomnibble6plus24generator$packStructureData(
                structureContext,
                source.getPos(),
                source.getAllStarts(),
                source.getAllReferences()).copy();
        SerializableChunkData data = new SerializableChunkData(
                containerFactory,
                source.getPos(),
                source.getMinSectionY(),
                0L,
                0L,
                ChunkStatus.FEATURES,
                null,
                null,
                UpgradeData.EMPTY.copy(),
                carvingMask,
                heightmaps,
                source.getTicksForSerialization(0L),
                postProcessing,
                false,
                List.copyOf(sections),
                rawEntities,
                List.copyOf(rawBlockEntities),
                structureData);
        CompoundTag payload = data.write();
        assertNoLightPayload(payload);
        return new CanonicalChunkArtifact(
                dimension,
                source.getPos(),
                source.getMinY(),
                source.getHeight(),
                localWorldSeed,
                profile,
                encode(payload),
                instantiatedPositions);
    }

    /** Reconstructs the immutable handoff value from an already detached transport buffer. */
    public static CanonicalChunkArtifact fromDetachedTransport(
            String dimension,
            int chunkX,
            int chunkZ,
            int minY,
            int height,
            long localWorldSeed,
            int mosaicFormatVersion,
            int seedDerivationAlgorithmVersion,
            int featureOrderingAlgorithmVersion,
            byte[] vanillaPayload,
            long[] instantiatedBlockEntityPositions) {
        MosaicWorldProfile profile = new MosaicWorldProfile(
                mosaicFormatVersion,
                seedDerivationAlgorithmVersion,
                featureOrderingAlgorithmVersion,
                MosaicWorldProfile.PRESENTATION_ALGORITHM_V1,
                net.minecraft.world.level.Level.OVERWORLD);
        profile.requireSupported();
        return new CanonicalChunkArtifact(
                dimension,
                new ChunkPos(chunkX, chunkZ),
                minY,
                height,
                localWorldSeed,
                profile,
                vanillaPayload,
                instantiatedBlockEntityPositions);
    }

    public ProtoChunk rehydrate(
            RegistryAccess registryAccess,
            StructurePieceSerializationContext structureContext) {
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(minY, height);
        PalettedContainerFactory containerFactory = PalettedContainerFactory.create(registryAccess);
        CompoundTag rawPayload = decode(vanillaPayload);
        SerializableChunkData data = SerializableChunkData.parse(
                heightAccessor, containerFactory, rawPayload);
        if (data == null || data.chunkStatus() != ChunkStatus.FEATURES
                || !data.chunkPos().equals(chunkPos())
                || data.lightCorrect()
                || data.blendingData() != null
                || data.belowZeroRetrogen() != null
                || !data.upgradeData().isEmpty()) {
            throw new IllegalStateException("Invalid detached FEATURES artifact payload");
        }

        LevelChunkSection[] sections = new LevelChunkSection[heightAccessor.getSectionsCount()];
        for (SerializableChunkData.SectionData sectionData : data.sectionData()) {
            if (sectionData.blockLight() != null || sectionData.skyLight() != null) {
                throw new IllegalStateException("Pre-light artifact unexpectedly contains light data");
            }
            if (sectionData.chunkSection() != null) {
                int index = heightAccessor.getSectionIndexFromSectionY(sectionData.y());
                if (index >= 0 && index < sections.length) {
                    sections[index] = sectionData.chunkSection().copy();
                }
            }
        }
        ProtoChunk staging = new ProtoChunk(
                chunkPos(),
                data.upgradeData().copy(),
                sections,
                ProtoChunkTicks.load(List.copyOf(data.packedTicks().blocks())),
                ProtoChunkTicks.load(List.copyOf(data.packedTicks().fluids())),
                heightAccessor,
                containerFactory,
                null);
        staging.setInhabitedTime(0L);
        staging.setPersistedStatus(ChunkStatus.FEATURES);
        staging.setLightCorrect(false);
        CompoundTag rawHeightmaps = rawPayload.getCompoundOrEmpty("Heightmaps");
        for (Heightmap.Types type : Heightmap.Types.values()) {
            rawHeightmaps.getLongArray(type.getSerializationKey())
                    .ifPresent(values -> staging.setHeightmap(type, values.clone()));
        }
        staging.setAllStarts(SerializableChunkDataInvoker.randomnibble6plus24generator$unpackStructureStarts(
                structureContext, data.structureData().copy(), localWorldSeed));
        staging.setAllReferences(SerializableChunkDataInvoker.randomnibble6plus24generator$unpackStructureReferences(
                registryAccess, chunkPos(), data.structureData().copy()));
        for (int index = 0; index < data.postProcessingSections().length; index++) {
            ShortList values = data.postProcessingSections()[index];
            if (values != null) staging.addPackedPostProcess(new ShortArrayList(values), index);
        }
        for (CompoundTag entity : data.entities()) staging.addEntity(entity.copy());
        for (CompoundTag blockEntity : data.blockEntities()) staging.setBlockEntityNbt(blockEntity.copy());
        for (long packedPos : instantiatedBlockEntityPositions) {
            BlockPos pos = BlockPos.of(packedPos);
            CompoundTag tag = staging.getBlockEntityNbt(pos);
            if (tag == null) throw new IllegalStateException("Missing instantiated BlockEntity NBT at " + pos);
            BlockEntity blockEntity = BlockEntity.loadStatic(
                    pos, staging.getBlockState(pos), tag.copy(), registryAccess);
            if (blockEntity == null) throw new IllegalStateException("Unable to restore BlockEntity at " + pos);
            staging.setBlockEntity(blockEntity);
        }
        if (data.carvingMask() != null) {
            staging.setCarvingMask(new CarvingMask(data.carvingMask().clone(), minY));
        }
        if (staging.getPersistedStatus() != ChunkStatus.FEATURES || staging.isLightCorrect()) {
            throw new IllegalStateException("Staging chunk crossed the pre-light boundary");
        }
        return staging;
    }

    public String dimension() { return dimension; }
    public ChunkPos chunkPos() { return new ChunkPos(chunkX, chunkZ); }
    public int minY() { return minY; }
    public int height() { return height; }
    public long localWorldSeed() { return localWorldSeed; }
    public int mosaicFormatVersion() { return mosaicFormatVersion; }
    public int seedDerivationAlgorithmVersion() { return seedDerivationAlgorithmVersion; }
    public int featureOrderingAlgorithmVersion() { return featureOrderingAlgorithmVersion; }
    public int encodedSize() { return vanillaPayload.length; }
    public String rawFingerprint() { return rawFingerprint; }
    public byte[] encodedPayloadCopy() { return vanillaPayload.clone(); }
    public long[] instantiatedBlockEntityPositionsCopy() { return instantiatedBlockEntityPositions.clone(); }

    private static void requireFreshPreLightState(ProtoChunk source) {
        if (!source.getUpgradeData().isEmpty()) throw new IllegalStateException("UpgradeData is not empty");
        if (source.getBlendingData() != null) throw new IllegalStateException("BlendingData is present");
        if (source.getBelowZeroRetrogen() != null) throw new IllegalStateException("BelowZeroRetrogen is present");
        if (source.isOldNoiseGeneration()) throw new IllegalStateException("Old-noise generation is present");
        if (source.isLightCorrect()) throw new IllegalStateException("Stable FEATURES source claims completed light");
    }

    private static ShortList[] copyPostProcessing(ShortList[] source) {
        ShortList[] copy = new ShortList[source.length];
        for (int index = 0; index < source.length; index++) {
            if (source[index] != null) copy[index] = new ShortArrayList(source[index]);
        }
        return copy;
    }

    private static void assertNoLightPayload(CompoundTag payload) {
        if (payload.getBooleanOr("isLightOn", false)) {
            throw new IllegalStateException("Pre-light artifact claims light correctness");
        }
        var sections = payload.getListOrEmpty("sections");
        for (int index = 0; index < sections.size(); index++) {
            CompoundTag section = sections.getCompound(index).orElse(null);
            if (section != null && (section.contains("BlockLight") || section.contains("SkyLight"))) {
                throw new IllegalStateException("Pre-light artifact contains serialized light layers");
            }
        }
    }

    private String calculateRawFingerprint() {
        MessageDigest digest = digest();
        digest.update(dimension.getBytes(StandardCharsets.UTF_8));
        update(digest, chunkX); update(digest, chunkZ); update(digest, localWorldSeed);
        digest.update(vanillaPayload);
        for (long value : instantiatedBlockEntityPositions) update(digest, value);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] encode(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(tag, output);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode canonical chunk artifact", exception);
        }
    }

    private static CompoundTag decode(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return NbtIo.read(input, NbtAccounter.unlimitedHeap());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to decode canonical chunk artifact", exception);
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(new byte[] {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(new byte[] {
                (byte) (value >>> 56), (byte) (value >>> 48), (byte) (value >>> 40), (byte) (value >>> 32),
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value});
    }
}
