package io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkSerializerAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureBaseline;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.ticks.ProtoChunkTicks;

/**
 * Immutable pre-light B transport. No server, session, ProtoChunk, RNG or light-engine ownership.
 */
public final class CanonicalChunkArtifact {
    private static final Comparator<BlockPos> POSITIONS =
            Comparator.comparingInt((BlockPos pos) -> pos.getY())
                    .thenComparingInt(BlockPos::getZ)
                    .thenComparingInt(BlockPos::getX);
    private final String dimension;
    private final ChunkPos position;
    private final int minY, height;
    private final long localSeed;
    private final MosaicWorldProfile profile;
    private final byte[] payload;
    private final byte[] pendingBlockEntities;
    private final long[] instantiated;

    private CanonicalChunkArtifact(
            String dimension,
            ProtoChunk source,
            long localSeed,
            MosaicWorldProfile profile,
            CompoundTag payload,
            long[] instantiated) {
        this.dimension = Objects.requireNonNull(dimension);
        this.position = source.getPos();
        this.minY = source.getMinBuildHeight();
        this.height = source.getHeight();
        this.localSeed = localSeed;
        this.profile = profile;
        this.payload = encode(payload);
        CompoundTag pending = new CompoundTag();
        for (var pos : source.getBlockEntityNbts().keySet().stream().sorted(POSITIONS).toList()) {
            var tag = source.getBlockEntityNbts().get(pos);
            if (!tag.contains("x", Tag.TAG_INT)
                    || !tag.contains("y", Tag.TAG_INT)
                    || !tag.contains("z", Tag.TAG_INT)
                    || !new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")).equals(pos))
                throw new IllegalStateException("Pending BlockEntity position mismatch " + pos);
            pending.put(Long.toString(pos.asLong()), tag.copy());
        }
        this.pendingBlockEntities = encode(pending);
        this.instantiated = instantiated.clone();
    }

    public static CanonicalChunkArtifact capture(
            StrictFeatureBaseline.Result result,
            String dimension,
            long localSeed,
            MosaicWorldProfile profile,
            RegistryAccess registry,
            StructurePieceSerializationContext structures) {
        profile.requireSupported();
        ProtoChunk source = result.target();
        if (profile.featureOrderingAlgorithmVersion()
                        != MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY
                || source.getPersistedStatus() != ChunkStatus.FEATURES
                || result.region().getSeed() != localSeed
                || result.terrain().size() != 9
                || result.terrain().get(source.getPos().toLong()) != source)
            throw new IllegalStateException(
                    "Artifact requires this version's complete target-only B writer");
        result.region().assertNoForbiddenEscapes();
        for (var chunk : result.terrain().values())
            if (chunk != source && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES))
                throw new IllegalStateException("B neighbor executed FEATURES");
        for (var chunk : result.region().outerReadInputs().values())
            if (chunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES))
                throw new IllegalStateException("B outer input executed FEATURES");
        if (source.isLightCorrect()
                || source.getBlendingData() != null
                || source.getBelowZeroRetrogen() != null
                || source.isOldNoiseGeneration()
                || !source.getUpgradeData().isEmpty()
                || source.hasAttachments())
            throw new IllegalStateException(
                    "Unaudited light, retrogen, upgrade or attachment state");

        CompoundTag data = NbtUtils.addCurrentDataVersion(new CompoundTag());
        data.putInt("xPos", source.getPos().x);
        data.putInt("zPos", source.getPos().z);
        data.putInt("yPos", source.getMinSection());
        data.putLong("LastUpdate", 0L);
        data.putLong("InhabitedTime", 0L);
        data.putString(
                "Status", BuiltInRegistries.CHUNK_STATUS.getKey(ChunkStatus.FEATURES).toString());
        var biomeCodec = ChunkSerializerAccess.biomes(registry.registryOrThrow(Registries.BIOME));
        ListTag sections = new ListTag();
        for (int index = 0; index < source.getSections().length; index++) {
            var section = source.getSections()[index];
            CompoundTag tag = new CompoundTag();
            tag.putByte("Y", (byte) source.getSectionYFromSectionIndex(index));
            tag.put(
                    "block_states",
                    ChunkSerializerAccess.blockStates()
                            .encodeStart(NbtOps.INSTANCE, section.getStates())
                            .getOrThrow());
            tag.put(
                    "biomes",
                    biomeCodec.encodeStart(NbtOps.INSTANCE, section.getBiomes()).getOrThrow());
            sections.add(tag);
        }
        data.put("sections", sections);
        CompoundTag heights = new CompoundTag();
        for (var entry : source.getHeightmaps())
            heights.putLongArray(
                    entry.getKey().getSerializationKey(), entry.getValue().getRawData().clone());
        data.put("Heightmaps", heights);
        CompoundTag masks = new CompoundTag();
        for (var step : GenerationStep.Carving.values()) {
            var mask = source.getCarvingMask(step);
            if (mask != null) masks.putLongArray(step.toString(), mask.toArray().clone());
        }
        data.put("CarvingMasks", masks);
        data.put(
                "structures",
                ChunkSerializerAccess.packStructures(
                                structures,
                                source.getPos(),
                                source.getAllStarts(),
                                source.getAllReferences())
                        .copy());
        ListTag entities = new ListTag();
        for (var tag : source.getEntities()) entities.add(tag.copy());
        data.put("entities", entities);
        ListTag blockEntities = new ListTag();
        for (var pos : source.getBlockEntitiesPos().stream().sorted(POSITIONS).toList()) {
            if (!new ChunkPos(pos).equals(source.getPos()))
                throw new IllegalStateException("Foreign BlockEntity");
            var tag = source.getBlockEntityNbtForSaving(pos, registry);
            if (tag == null) throw new IllegalStateException("Missing BlockEntity NBT " + pos);
            blockEntities.add(tag.copy());
        }
        data.put("block_entities", blockEntities);
        var ticks = source.getTicksForSerialization();
        data.put(
                "block_ticks",
                ticks.blocks().save(0L, block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
        data.put(
                "fluid_ticks",
                ticks.fluids().save(0L, fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()));
        data.put("PostProcessing", ChunkSerializer.packOffsets(source.getPostProcessing()).copy());
        long[] instantiated =
                source.getBlockEntities().keySet().stream()
                        .sorted(POSITIONS)
                        .mapToLong(BlockPos::asLong)
                        .toArray();
        return new CanonicalChunkArtifact(
                dimension, source, localSeed, profile, data, instantiated);
    }

    public ProtoChunk rehydrate(
            String expectedDimension,
            ChunkPos expectedPosition,
            long expectedSeed,
            MosaicWorldProfile expectedProfile,
            LevelHeightAccessor heights,
            RegistryAccess registry,
            StructurePieceSerializationContext structures) {
        if (!dimension.equals(expectedDimension)
                || !position.equals(expectedPosition)
                || localSeed != expectedSeed
                || !profile.equals(expectedProfile)
                || minY != heights.getMinBuildHeight()
                || height != heights.getHeight())
            throw new IllegalArgumentException("Artifact provenance/height mismatch");
        CompoundTag data = payloadCopy();
        var biomeRegistry = registry.registryOrThrow(Registries.BIOME);
        var biomeCodec = ChunkSerializerAccess.biomes(biomeRegistry);
        LevelChunkSection[] sections = new LevelChunkSection[heights.getSectionsCount()];
        var stored = data.getList("sections", Tag.TAG_COMPOUND);
        if (stored.size() != sections.length)
            throw new IllegalStateException("Incomplete sections");
        for (int i = 0; i < stored.size(); i++) {
            var section = stored.getCompound(i);
            int index = heights.getSectionIndexFromSectionY(section.getByte("Y"));
            if (index < 0 || index >= sections.length || sections[index] != null)
                throw new IllegalStateException("Invalid/duplicate section");
            sections[index] =
                    new LevelChunkSection(
                            ChunkSerializerAccess.blockStates()
                                    .parse(NbtOps.INSTANCE, section.getCompound("block_states"))
                                    .getOrThrow(),
                            biomeCodec
                                    .parse(NbtOps.INSTANCE, section.getCompound("biomes"))
                                    .getOrThrow());
        }
        var blockTicks =
                ProtoChunkTicks.load(
                        data.getList("block_ticks", Tag.TAG_COMPOUND),
                        id ->
                                Optional.of(
                                        BuiltInRegistries.BLOCK
                                                .getOptional(ResourceLocation.parse(id))
                                                .orElseThrow()),
                        position);
        var fluidTicks =
                ProtoChunkTicks.load(
                        data.getList("fluid_ticks", Tag.TAG_COMPOUND),
                        id ->
                                Optional.of(
                                        BuiltInRegistries.FLUID
                                                .getOptional(ResourceLocation.parse(id))
                                                .orElseThrow()),
                        position);
        var chunk =
                new ProtoChunk(
                        position,
                        new UpgradeData(new CompoundTag(), heights),
                        sections,
                        blockTicks,
                        fluidTicks,
                        heights,
                        biomeRegistry,
                        null);
        chunk.setPersistedStatus(ChunkStatus.FEATURES);
        chunk.setLightCorrect(false);
        CompoundTag maps = data.getCompound("Heightmaps");
        for (var type : Heightmap.Types.values())
            if (maps.contains(type.getSerializationKey(), Tag.TAG_LONG_ARRAY))
                chunk.setHeightmap(type, maps.getLongArray(type.getSerializationKey()).clone());
        CompoundTag masks = data.getCompound("CarvingMasks");
        for (var step : GenerationStep.Carving.values())
            if (masks.contains(step.toString(), Tag.TAG_LONG_ARRAY))
                chunk.setCarvingMask(
                        step, new CarvingMask(masks.getLongArray(step.toString()).clone(), minY));
        chunk.setAllStarts(
                ChunkSerializerAccess.loadStarts(
                        structures, data.getCompound("structures").copy(), localSeed));
        chunk.setAllReferences(
                ChunkSerializerAccess.loadReferences(
                        registry, position, data.getCompound("structures").copy()));
        for (var tag : data.getList("entities", Tag.TAG_COMPOUND))
            chunk.addEntity(((CompoundTag) tag).copy());
        Map<BlockPos, CompoundTag> savedBlockEntities = new HashMap<>();
        for (var tag : data.getList("block_entities", Tag.TAG_COMPOUND)) {
            var value = (CompoundTag) tag;
            var pos = new BlockPos(value.getInt("x"), value.getInt("y"), value.getInt("z"));
            if (savedBlockEntities.put(pos, value) != null)
                throw new IllegalStateException("Duplicate BlockEntity " + pos);
        }
        var pending = decode(pendingBlockEntities);
        for (var key : pending.getAllKeys())
            chunk.setBlockEntityNbt(pending.getCompound(key).copy());
        for (long value : instantiated) {
            var pos = BlockPos.of(value);
            var tag = savedBlockEntities.get(pos);
            if (tag == null)
                throw new IllegalStateException("Missing instantiated BlockEntity " + pos);
            var blockEntity =
                    BlockEntity.loadStatic(pos, chunk.getBlockState(pos), tag.copy(), registry);
            if (blockEntity == null)
                throw new IllegalStateException("Cannot restore BlockEntity " + pos);
            chunk.setBlockEntity(blockEntity);
        }
        ListTag post = data.getList("PostProcessing", Tag.TAG_LIST);
        if (post.size() != chunk.getPostProcessing().length)
            throw new IllegalStateException("Invalid postprocessing shape");
        for (int index = 0; index < post.size(); index++)
            for (var tag : post.getList(index))
                chunk.addPackedPostProcess(((ShortTag) tag).getAsShort(), index);
        chunk.setUnsaved(true);
        return chunk;
    }

    public CompoundTag payloadCopy() {
        return decode(payload);
    }

    private static CompoundTag decode(byte[] bytes) {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var tag = NbtIo.read(input, NbtAccounter.unlimitedHeap());
            if (input.available() != 0) throw new IllegalStateException("Trailing artifact bytes");
            return Objects.requireNonNull(tag);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public byte[] encodedPayloadCopy() {
        return payload.clone();
    }

    public CompoundTag pendingBlockEntitiesCopy() {
        return decode(pendingBlockEntities);
    }

    public long[] instantiatedBlockEntityPositionsCopy() {
        return instantiated.clone();
    }

    public String dimension() {
        return dimension;
    }

    public ChunkPos chunkPos() {
        return position;
    }

    public long localWorldSeed() {
        return localSeed;
    }

    public MosaicWorldProfile profile() {
        return profile;
    }

    private static byte[] encode(CompoundTag tag) {
        try (var bytes = new ByteArrayOutputStream();
                var output = new DataOutputStream(bytes)) {
            NbtIo.write(tag, output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
