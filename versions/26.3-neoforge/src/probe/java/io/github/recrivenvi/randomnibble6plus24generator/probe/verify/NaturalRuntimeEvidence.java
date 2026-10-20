package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Real published worldgen input; never creates or registers a physical fixture. */
public final class NaturalRuntimeEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static final Map<UUID, Entity> live = new LinkedHashMap<>();
    private static final Map<BlockPos, BlockEntity> liveBlockEntities = new LinkedHashMap<>();
    private static final Map<UUID, Long> lastTick = new HashMap<>();
    private static final Map<UUID, Integer> ticks = new HashMap<>();
    private static final Map<BlockPos, CompoundTag> reloadSaved = new HashMap<>();
    private static final AtomicBoolean captured = new AtomicBoolean();
    private static volatile CompoundTag source;
    private static volatile RuntimeException sourceFailure;
    private static MinecraftServer server;
    private static ChunkPos owner;
    private static ServerLevel level;
    private static Path folder;
    private static boolean tracking;
    private static boolean saveArmed;

    private NaturalRuntimeEvidence() {}

    public static boolean enabled() {
        return !System.getProperty(PREFIX + "naturalProof", "").isEmpty();
    }

    public static void install(MinecraftServer host) {
        if (!enabled()) return;
        server = host;
        owner =
                new ChunkPos(
                        Integer.getInteger(PREFIX + "chunkX"),
                        Integer.getInteger(PREFIX + "chunkZ"));
        folder = Path.of(System.getProperty(PREFIX + "output")).toAbsolutePath().getParent();
        if (System.getProperty(PREFIX + "mode").equals("reload")) {
            try {
                source =
                        NbtIo.readCompressed(
                                folder.resolve("natural-source.nbt"), NbtAccounter.unlimitedHeap());
                var saved =
                        NbtIo.readCompressed(
                                folder.resolve("natural-saved-full.nbt"),
                                NbtAccounter.unlimitedHeap());
                if (!saved.getStringOr("inputHash", "")
                        .equals(System.getProperty(PREFIX + "naturalInputHash")))
                    throw new IllegalStateException("Foreign native saved BE evidence");
                for (var tag : saved.getListOrEmpty("block_entities")) {
                    var nbt = (CompoundTag) tag;
                    reloadSaved.put(position(nbt), nbt.copy());
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        } else {
            if (Files.exists(folder.resolve("natural-source.nbt")))
                throw new IllegalStateException("Preserve existing natural source evidence");
            MosaicPhysicalMaterializer.setVerificationObserver(NaturalRuntimeEvidence::capture);
        }
    }

    private static void capture(MosaicPhysicalMaterializer.Publication publication) {
        if (publication.key().server() != server
                || !publication.key().pos().equals(owner)
                || !publication
                        .prepared()
                        .artifact()
                        .dimension()
                        .equals(System.getProperty(PREFIX + "dimension"))) return;
        if (!captured.compareAndSet(false, true))
            throw new IllegalStateException("Duplicate natural source publication");
        try {
            var prepared = publication.prepared();
            var chunk = prepared.chunk();
            var root = new CompoundTag();
            root.putInt("schema", 1);
            root.putString("inputHash", System.getProperty(PREFIX + "naturalInputHash"));
            root.putString("dimension", prepared.artifact().dimension());
            root.putString(
                    "save",
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .toAbsolutePath()
                            .normalize()
                            .toString());
            root.putInt("x", owner.x());
            root.putInt("z", owner.z());
            root.putString("localSeed", Long.toString(prepared.artifact().localWorldSeed()));
            root.putString("artifact", prepared.artifact().rawFingerprint());
            var entities = new ListTag();
            chunk.getEntities().forEach(entity -> entities.add(entity.copy()));
            root.put("entities", entities);
            var raw = new ListTag();
            for (var pos : chunk.getBlockEntitiesPos()) {
                var nbt = chunk.getBlockEntityNbtForSaving(pos, server.registryAccess());
                if (nbt == null) throw new IllegalStateException("Missing natural BE NBT");
                raw.add(nbt.copy());
            }
            root.put("block_entities", raw);
            root.putInt("rawBlockEntityNbtEntries", chunk.getBlockEntityNbts().size());
            root.putInt(
                    "pendingBlockEntities",
                    (int)
                            chunk.getBlockEntityNbts().keySet().stream()
                                    .filter(pos -> !chunk.getBlockEntities().containsKey(pos))
                                    .count());
            write("natural-source-raw.nbt", root);
            var controls = new ListTag();
            for (var tag : raw) {
                var nbt = (CompoundTag) tag;
                var pos = position(nbt);
                var control = nativeLoadControl(pos, chunk.getBlockState(pos), nbt);
                if (control == null)
                    throw new IllegalStateException("Unproved natural BE loader control: " + nbt);
                controls.add(control.saveWithFullMetadata(server.registryAccess()));
            }
            root.put("nativeControls", controls);
            write("natural-source.nbt", root);
            source = root;
        } catch (RuntimeException failure) {
            sourceFailure = failure;
            throw failure;
        } finally {
            MosaicPhysicalMaterializer.setVerificationObserver(null);
        }
    }

    public static void checkFailure() {
        if (enabled() && sourceFailure != null) throw sourceFailure;
    }

    private static BlockEntity nativeLoadControl(
            BlockPos pos, net.minecraft.world.level.block.state.BlockState state, CompoundTag nbt) {
        // 26.3 LevelChunk.promotePendingBlockEntity has a separate DUMMY branch. It creates
        // from the actual block state and does not load DUMMY as a registry id or load its tag.
        var control =
                nbt.getStringOr("id", "").equals("DUMMY")
                        ? (state.hasBlockEntity()
                                ? ((net.minecraft.world.level.block.EntityBlock) state.getBlock())
                                        .newBlockEntity(pos, state)
                                : null)
                        : BlockEntity.loadStatic(pos, state, nbt.copy(), server.registryAccess());
        if (control != null && control.hasLevel())
            throw new IllegalStateException("Native BE control unexpectedly belongs to a Level");
        return control;
    }

    public static boolean ready(ServerLevel host, ChunkPos position) {
        if (!enabled()) return true;
        if (source == null)
            throw new IllegalStateException(
                    "Natural owner reached ticking without its published source evidence");
        if (host.getServer() != server
                || !owner.equals(position)
                || !source.getStringOr("inputHash", "")
                        .equals(System.getProperty(PREFIX + "naturalInputHash"))
                || !source.getStringOr("dimension", "")
                        .equals(host.dimension().identifier().toString())
                || source.getIntOr("x", Integer.MIN_VALUE) != owner.x()
                || source.getIntOr("z", Integer.MIN_VALUE) != owner.z()
                || !source.getStringOr("save", "")
                        .equals(
                                server.getWorldPath(
                                                net.minecraft.world.level.storage.LevelResource
                                                        .ROOT)
                                        .toAbsolutePath()
                                        .normalize()
                                        .toString())
                || !source.getStringOr("localSeed", "")
                        .equals(
                                Long.toString(
                                        MosaicWorldIdentity.runtimeContext(host)
                                                .orElseThrow()
                                                .resolveLocalWorldSeed(host.dimension(), owner))))
            throw new IllegalStateException("Foreign natural generation source");
        var entities = source.getListOrEmpty("entities");
        var controls = source.getListOrEmpty("nativeControls");
        String expected = System.getProperty(PREFIX + "naturalProof");
        if ((expected.equals("entities") && entities.isEmpty())
                || (expected.equals("block_entities") && controls.isEmpty()))
            throw new IllegalStateException("Natural positive fixture was not hit: " + expected);
        for (var tag : entities) {
            var nbt = (CompoundTag) tag;
            Entity entity = host.getEntity(uuid(nbt));
            if (entity == null) return false;
            if (!BuiltInRegistries.ENTITY_TYPE
                    .getKey(entity.getType())
                    .toString()
                    .equals(nbt.getStringOr("id", "")))
                throw new IllegalStateException("Natural entity type changed");
            live.put(entity.getUUID(), entity);
        }
        var chunk = host.getChunkSource().getChunkNow(owner.x(), owner.z());
        if (chunk == null) throw new IllegalStateException("Natural owner is not physical");
        for (var tag : controls) {
            var nbt = (CompoundTag) tag;
            var pos = position(nbt);
            // No getBlockEntity(): that could create an unactivated pending BE for the test.
            var be = chunk.getBlockEntities().get(pos);
            if (be == null) return false;
            if (liveBlockEntities.get(pos) != be)
                throw new IllegalStateException(
                        "Natural physical BE bypassed the observed native installation");
        }
        level = host;
        tracking = true;
        return true;
    }

    public static void endCrystalTick(Entity entity) {
        if (!tracking || entity.level() != level || !live.containsKey(entity.getUUID())) return;
        Long previous = lastTick.put(entity.getUUID(), level.getGameTime());
        if (previous != null && previous.longValue() == level.getGameTime())
            throw new IllegalStateException("Natural End Crystal ticked twice in one game tick");
        ticks.merge(entity.getUUID(), 1, Integer::sum);
    }

    public static JsonObject finish() {
        if (!enabled()) return null;
        var observation = new CompoundTag();
        var observedEntities = new ListTag();
        for (var entry : live.entrySet()) {
            long registrations =
                    java.util.stream.StreamSupport.stream(
                                    level.getAllEntities().spliterator(), false)
                            .filter(e -> e.getUUID().equals(entry.getKey()))
                            .count();
            if (registrations != 1 || level.getEntity(entry.getKey()) != entry.getValue())
                throw new IllegalStateException("Natural entity lost or registered twice");
            if (System.getProperty(PREFIX + "naturalProof").equals("entities")
                    && ticks.getOrDefault(entry.getKey(), 0) <= 0)
                throw new IllegalStateException(
                        "Natural End Crystal did not execute its actual tick");
            var entity = new CompoundTag();
            entity.putString("uuid", entry.getKey().toString());
            entity.putString(
                    "type",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entry.getValue().getType()).toString());
            entity.putInt("registrations", 1);
            entity.putInt("actualTicks", ticks.getOrDefault(entry.getKey(), 0));
            observedEntities.add(entity);
        }
        observation.put("entities", observedEntities);
        var actualBEs = new ListTag();
        var chunk = level.getChunkSource().getChunkNow(owner.x(), owner.z());
        for (var entry : liveBlockEntities.entrySet()) {
            if (chunk.getBlockEntities().get(entry.getKey()) != entry.getValue())
                throw new IllegalStateException(
                        "Natural BE instance was replaced during simulation");
            actualBEs.add(entry.getValue().saveWithFullMetadata(server.registryAccess()));
        }
        observation.put("block_entities", actualBEs);
        write("natural-live-" + System.getProperty(PREFIX + "mode") + ".nbt", observation);
        var result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("inputHash", source.getStringOr("inputHash", ""));
        result.addProperty("sourceArtifact", source.getStringOr("artifact", ""));
        result.addProperty("sourceEntities", source.getListOrEmpty("entities").size());
        result.addProperty("sourceBlockEntities", source.getListOrEmpty("block_entities").size());
        result.addProperty(
                "sourcePendingBlockEntities", source.getIntOr("pendingBlockEntities", -1));
        result.addProperty("liveEntities", live.size());
        result.addProperty("liveBlockEntities", liveBlockEntities.size());
        result.addProperty("capturedPublications", captured.get() ? 1 : 0);
        result.addProperty(
                "actualEndCrystalTicks", ticks.values().stream().mapToInt(Integer::intValue).sum());
        result.addProperty("nativeBlockEntityLoadControlExact", true);
        result.addProperty("probeCreatedPhysicalEntitiesOrBlockEntities", false);
        result.addProperty(
                "scope", "NATURAL_INPUT_ACTIVATION_AND_IDENTITY_NOT_RUNTIME_NBT_IMMUTABILITY");
        tracking = false;
        saveArmed = true;
        return result;
    }

    public static synchronized void blockEntityInstalled(
            net.minecraft.world.level.chunk.LevelChunk chunk, BlockEntity be) {
        if (!enabled()
                || server == null
                || source == null
                || !chunk.getPos().equals(owner)
                || chunk.getLevel().getServer() != server
                || !chunk.getLevel()
                        .dimension()
                        .identifier()
                        .toString()
                        .equals(source.getStringOr("dimension", ""))) return;
        CompoundTag expected = null;
        for (var tag : source.getListOrEmpty("nativeControls")) {
            var nbt = (CompoundTag) tag;
            if (position(nbt).equals(be.getBlockPos())) {
                expected = nbt;
                break;
            }
        }
        if (expected == null) return;
        if (System.getProperty(PREFIX + "mode").equals("reload")) {
            var raw = reloadSaved.get(be.getBlockPos());
            if (raw == null)
                throw new IllegalStateException("Natural BE missing from actual saved native data");
            var control = nativeLoadControl(be.getBlockPos(), be.getBlockState(), raw);
            if (control == null)
                throw new IllegalStateException("Cannot build native saved BE load control");
            expected = control.saveWithFullMetadata(server.registryAccess());
        }
        var actual = be.saveWithFullMetadata(server.registryAccess());
        if (!expected.equals(actual)) {
            var mismatch = new CompoundTag();
            mismatch.put("expected", expected.copy());
            mismatch.put("actual", actual);
            if (!Files.exists(folder.resolve("natural-be-first-difference.nbt")))
                write("natural-be-first-difference.nbt", mismatch);
            throw new IllegalStateException(
                    "Natural BE differs at native installation: "
                            + be.getBlockPos()
                            + " "
                            + expected
                            + " / "
                            + actual);
        }
        if (liveBlockEntities.putIfAbsent(be.getBlockPos(), be) != null)
            throw new IllegalStateException(
                    "Natural BE installed more than once: " + be.getBlockPos());
    }

    public static void saved(
            ServerLevel host,
            net.minecraft.world.level.chunk.ChunkAccess chunk,
            net.minecraft.world.level.chunk.storage.SerializableChunkData data) {
        if (!enabled() || !saveArmed || host != level || !chunk.getPos().equals(owner)) return;
        var root = new CompoundTag();
        root.putString("inputHash", System.getProperty(PREFIX + "naturalInputHash"));
        var blockEntities = new ListTag();
        data.blockEntities().forEach(nbt -> blockEntities.add(nbt.copy()));
        root.put("block_entities", blockEntities);
        write("natural-saved-" + System.getProperty(PREFIX + "mode") + ".nbt", root);
    }

    public static void closed() {
        if (!enabled() || server == null) return;
        if (saveArmed
                && !Files.exists(
                        folder.resolve(
                                "natural-saved-" + System.getProperty(PREFIX + "mode") + ".nbt")))
            throw new IllegalStateException("Natural owner was not serialized by native shutdown");
        MosaicPhysicalMaterializer.setVerificationObserver(null);
        live.clear();
        liveBlockEntities.clear();
        ticks.clear();
        lastTick.clear();
        reloadSaved.clear();
        source = null;
        sourceFailure = null;
        level = null;
        server = null;
        tracking = false;
        var proof = new JsonObject();
        proof.addProperty("schema", 1);
        proof.addProperty("completed", saveArmed);
        proof.addProperty("inputHash", System.getProperty(PREFIX + "naturalInputHash"));
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("retainedEntities", live.size());
        proof.addProperty("retainedBlockEntities", liveBlockEntities.size());
        proof.addProperty("sourceRetained", source != null);
        proof.addProperty("levelRetained", level != null);
        proof.addProperty("serverRetained", server != null);
        try {
            Files.writeString(
                    folder.resolve(
                            "natural-close-" + System.getProperty(PREFIX + "mode") + ".json"),
                    proof.toString());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        folder = null;
    }

    private static BlockPos position(CompoundTag nbt) {
        if (!(nbt.get("x") instanceof IntTag x)
                || !(nbt.get("y") instanceof IntTag y)
                || !(nbt.get("z") instanceof IntTag z))
            throw new IllegalArgumentException("Natural BE lacks its complete position");
        return new BlockPos(x.intValue(), y.intValue(), z.intValue());
    }

    private static UUID uuid(CompoundTag nbt) {
        if (!(nbt.get("UUID") instanceof IntArrayTag encoded)
                || encoded.getAsIntArray().length != 4)
            throw new IllegalArgumentException("Natural entity lacks its raw UUID");
        int[] bits = encoded.getAsIntArray();
        return new UUID(
                ((long) bits[0] << 32) | (bits[1] & 0xffffffffL),
                ((long) bits[2] << 32) | (bits[3] & 0xffffffffL));
    }

    private static void write(String name, CompoundTag data) {
        try {
            NbtIo.writeCompressed(data, folder.resolve(name));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
