package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureIndexStore;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayStore;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Read-only physical state at the ordinary FULL request boundary, not an Artifact snapshot. */
public final class PhysicalPersistenceEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static final ThreadLocal<MinecraftServer> CURRENT = new ThreadLocal<>();

    private PhysicalPersistenceEvidence() {}

    public static void observe(MinecraftServer server, Runnable action) {
        if (!Boolean.getBoolean(PREFIX + "stateProof")) {
            action.run();
            return;
        }
        if (CURRENT.get() != null) throw new IllegalStateException("Nested lifecycle observer");
        CURRENT.set(server);
        try {
            action.run();
        } finally {
            CURRENT.remove();
        }
    }

    public static void capture(JsonObject result) {
        if (!Boolean.getBoolean(PREFIX + "stateProof")) return;
        MinecraftServer server = CURRENT.get();
        if (server == null || !server.isSameThread())
            throw new IllegalStateException(
                    "Physical persistence evidence must run on server thread");
        var dimension =
                ResourceKey.create(
                        Registries.DIMENSION,
                        Identifier.parse(result.get("dimension").getAsString()));
        var level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing evidence dimension");
        ChunkPos owner =
                new ChunkPos(
                        Integer.getInteger(PREFIX + "chunkX", 125),
                        Integer.getInteger(PREFIX + "chunkZ", -37));
        var chunk = level.getChunkSource().getChunkNow(owner.x(), owner.z());
        if (chunk == null || !chunk.isLightCorrect())
            throw new IllegalStateException(
                    "Evidence target is not a loaded, lit physical LevelChunk");
        JsonObject state = new JsonObject();
        state.addProperty("schema", 1);
        state.addProperty("dimension", result.get("dimension").getAsString());
        state.addProperty("x", owner.x());
        state.addProperty("z", owner.z());
        state.addProperty("minY", chunk.getMinY());
        state.addProperty("height", chunk.getHeight());
        state.addProperty("localSeed", result.get("localWorldSeed").getAsLong());
        state.addProperty(
                "blocks",
                digest(
                        output -> {
                            var position = new BlockPos.MutableBlockPos();
                            for (int z = owner.getMinBlockZ(); z <= owner.getMaxBlockZ(); z++)
                                for (int x = owner.getMinBlockX(); x <= owner.getMaxBlockX(); x++)
                                    for (int y = chunk.getMinY(); y < chunk.getMaxY(); y++)
                                        output.writeUTF(
                                                chunk.getBlockState(position.set(x, y, z))
                                                        .toString());
                        }));
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        state.addProperty(
                "biomes",
                digest(
                        output -> {
                            for (int z = owner.z() * 4; z < owner.z() * 4 + 4; z++)
                                for (int x = owner.x() * 4; x < owner.x() * 4 + 4; x++)
                                    for (int y = Math.floorDiv(chunk.getMinY(), 4);
                                            y < Math.floorDiv(chunk.getMaxY(), 4);
                                            y++)
                                        output.writeUTF(
                                                biomes.getKey(chunk.getNoiseBiome(x, y, z).value())
                                                        .toString());
                        }));
        state.addProperty(
                "light",
                digest(
                        output -> {
                            var position = new BlockPos.MutableBlockPos();
                            var engine = level.getChunkSource().getLightEngine();
                            var sky = engine.getLayerListener(LightLayer.SKY);
                            var block = engine.getLayerListener(LightLayer.BLOCK);
                            for (int z = owner.getMinBlockZ(); z <= owner.getMaxBlockZ(); z++)
                                for (int x = owner.getMinBlockX(); x <= owner.getMaxBlockX(); x++)
                                    for (int y = chunk.getMinY(); y < chunk.getMaxY(); y++) {
                                        position.set(x, y, z);
                                        output.writeByte(sky.getLightValue(position));
                                        output.writeByte(block.getLightValue(position));
                                    }
                        }));
        JsonObject heights = new JsonObject();
        chunk.getHeightmaps()
                .forEach(
                        entry ->
                                heights.add(
                                        entry.getKey().getSerializationKey(),
                                        longs(entry.getValue().getRawData())));
        state.add("heightmaps", heights);
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        JsonObject starts = new JsonObject();
        chunk.getAllStarts()
                .forEach(
                        (structure, start) ->
                                starts.addProperty(
                                        registry.getKey(structure).toString(),
                                        PreFeaturesVerifier.canonicalNbt(
                                                start.createTag(
                                                        serialization, start.getChunkPos()))));
        state.add("structureStarts", starts);
        JsonObject references = new JsonObject();
        chunk.getAllReferences()
                .forEach(
                        (structure, positions) -> {
                            long[] values = positions.toLongArray();
                            Arrays.sort(values);
                            references.add(registry.getKey(structure).toString(), longs(values));
                        });
        state.add("structureReferences", references);
        JsonObject projection = new JsonObject();
        MosaicStructureOverlayStore.externalStarts(level, owner)
                .forEach(
                        start ->
                                projection.addProperty(
                                        registry.getKey(start.getStructure())
                                                + "@"
                                                + start.getChunkPos(),
                                        PreFeaturesVerifier.canonicalNbt(
                                                start.createTag(
                                                        serialization, start.getChunkPos()))));
        state.add("externalStarts", projection);
        JsonObject locators = new JsonObject();
        MosaicStructureOverlayStore.startsForOwner(level, owner)
                .forEach(
                        start -> {
                            var key = registry.getKey(start.getStructure());
                            var found =
                                    MosaicStructureIndexStore.findNearest(
                                            level,
                                            HolderSet.direct(
                                                    registry.wrapAsHolder(start.getStructure())),
                                            new BlockPos(
                                                    owner.getMiddleBlockX(),
                                                    64,
                                                    owner.getMiddleBlockZ()),
                                            0);
                            if (found.isPresent()
                                    && !ChunkPos.containing(found.get().getFirst()).equals(owner))
                                throw new IllegalStateException(
                                        "Index returned a virtual or foreign owner anchor");
                            locators.addProperty(
                                    key.toString(),
                                    found.map(value -> value.getFirst().toShortString())
                                            .orElse("none"));
                        });
        state.add("ownerLocators", locators);
        JsonObject blockEntities = new JsonObject();
        chunk.getBlockEntitiesPos()
                .forEach(
                        position -> {
                            var tag =
                                    chunk.getBlockEntityNbtForSaving(
                                            position, level.registryAccess());
                            if (tag == null)
                                throw new IllegalStateException(
                                        "Missing saved BlockEntity " + position);
                            blockEntities.addProperty(
                                    position.toShortString(),
                                    PreFeaturesVerifier.canonicalNbt(tag));
                        });
        state.add("blockEntities", blockEntities);
        JsonArray postProcessing = new JsonArray();
        for (var section : chunk.getPostProcessing()) {
            JsonArray positions = new JsonArray();
            if (section != null) for (short position : section) positions.add(position);
            postProcessing.add(positions);
        }
        state.add("postProcessing", postProcessing);
        var poi =
                level.getPoiManager()
                        .getInChunk(type -> true, owner, PoiManager.Occupancy.ANY)
                        .map(
                                record ->
                                        record.getPoiType().unwrapKey().orElseThrow().identifier()
                                                + "@"
                                                + record.getPos().toShortString()
                                                + ":"
                                                + record.getFreeTickets())
                        .sorted()
                        .toList();
        JsonArray poiState = new JsonArray();
        poi.forEach(poiState::add);
        state.add("poi", poiState);
        JsonObject diagnostics = new JsonObject();
        diagnostics.addProperty("gameTime", level.getGameTime());
        var ticks = chunk.getTicksForSerialization(level.getGameTime());
        JsonArray blockTicks = new JsonArray();
        ticks.blocks()
                .forEach(
                        tick ->
                                blockTicks.add(
                                        BuiltInRegistries.BLOCK.getKey(tick.type())
                                                + "@"
                                                + tick.pos().toShortString()
                                                + ":"
                                                + tick.delay()
                                                + ":"
                                                + tick.priority()));
        JsonArray fluidTicks = new JsonArray();
        ticks.fluids()
                .forEach(
                        tick ->
                                fluidTicks.add(
                                        BuiltInRegistries.FLUID.getKey(tick.type())
                                                + "@"
                                                + tick.pos().toShortString()
                                                + ":"
                                                + tick.delay()
                                                + ":"
                                                + tick.priority()));
        diagnostics.add("blockTicks", blockTicks);
        diagnostics.add("fluidTicks", fluidTicks);
        JsonObject entities = new JsonObject();
        level.getAllEntities()
                .forEach(
                        entity -> {
                            if (ChunkPos.containing(entity.blockPosition()).equals(owner))
                                entities.addProperty(
                                        entity.getStringUUID(),
                                        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                                                + "@"
                                                + entity.position()
                                                + ":"
                                                + entity.getYRot()
                                                + ":"
                                                + entity.getXRot());
                        });
        diagnostics.add("physicalEntitiesAtObservation", entities);
        // Runtime ticks/entity-manager completion are recorded separately, not silently normalized
        // or claimed strict merely because the persistent block/structure projection matches.
        result.add("stateProof", PreFeaturesVerifier.canonicalJson(state));
        result.add("lifecycleDiagnostics", diagnostics);
        try {
            Path output = Path.of(System.getProperty(PREFIX + "output")).toAbsolutePath();
            Path raw = output.resolveSibling(output.getFileName() + ".physical.nbt.gz");
            Files.createDirectories(raw.getParent());
            NbtIo.writeCompressed(SerializableChunkData.copyOf(level, chunk).write(), raw);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static JsonArray longs(long[] values) {
        JsonArray result = new JsonArray();
        for (long value : values) result.add(value);
        return result;
    }

    private static String digest(Write writer) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output =
                    new DataOutputStream(
                            new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                writer.write(output);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @FunctionalInterface
    private interface Write {
        void write(DataOutputStream output) throws IOException;
    }
}
