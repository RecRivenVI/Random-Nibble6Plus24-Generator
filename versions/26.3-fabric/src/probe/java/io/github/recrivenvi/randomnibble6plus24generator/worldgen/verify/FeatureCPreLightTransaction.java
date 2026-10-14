package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureContributionJournal;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

/** Probe-only write-ahead handoff. Recovery is restricted to unactivated pre-light chunks. */
final class FeatureCPreLightTransaction {
    private static final Set<String> FIELDS =
            Set.of("schema", "world", "seed", "producer_pid", "committed", "patch");
    private final CompoundTag record;
    private final Path path;

    private FeatureCPreLightTransaction(Path path, CompoundTag record) {
        this.path = path;
        this.record = record.copy();
    }

    static FeatureCPreLightTransaction prepare(
            ServerLevel level, Path path, ListTag rows, Path inputs) throws IOException {
        requireThread(level);
        if (Files.exists(path)) throw new IllegalStateException("Transaction already exists");
        var patch = new ListTag();
        for (var item : rows) {
            var row = ((CompoundTag) item).copy();
            var pos = owner(row);
            row.put(
                    "payload",
                    NbtIo.readCompressed(
                            inputs.resolve(name(pos) + "-input.nbt"),
                            NbtAccounter.unlimitedHeap()));
            row.putString("payload_hash", digest(row.getCompound("payload").orElseThrow()));
            patch.add(row);
        }
        var record = new CompoundTag();
        record.putInt("schema", 1);
        record.putString("world", world(level));
        record.putLong("seed", level.getSeed());
        record.putLong("producer_pid", ProcessHandle.current().pid());
        record.putBoolean("committed", false);
        record.put("patch", patch);
        validate(record, world(level), level.getSeed());
        durable(path, record);
        return new FeatureCPreLightTransaction(path, record);
    }

    static FeatureCPreLightTransaction open(ServerLevel level, Path path) throws IOException {
        requireThread(level);
        var record = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        validate(record, world(level), level.getSeed());
        return new FeatureCPreLightTransaction(path, record);
    }

    record Recovery(int existing, int written, boolean alreadyCommitted) {}

    Recovery recover(ServerLevel level, List<ChunkPos> expected) {
        requireThread(level);
        var actual = new HashSet<ChunkPos>();
        var pending = new LinkedHashMap<ChunkPos, CompoundTag>();
        boolean committed = record.getBoolean("committed").orElseThrow();
        int existing = 0;
        // Validate the complete frontier before writing anything, and never acquire tickets.
        for (var item : record.getList("patch").orElseThrow()) {
            var row = (CompoundTag) item;
            var pos = owner(row);
            actual.add(pos);
            if (level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(pos.pack()) != null)
                throw new IllegalStateException(
                        "Recovery requires unloaded, unactivated receivers");
            var payload = row.getCompound("payload").orElseThrow();
            var current = level.getChunkSource().chunkMap.read(pos).join();
            if (current.isEmpty()) {
                if (committed) throw new IllegalStateException("Committed receiver missing " + pos);
                pending.put(pos, payload.copy());
            } else {
                var stored = current.orElseThrow();
                if (!acceptsStored(payload, stored))
                    throw new IllegalStateException(
                            "Refusing to overwrite changed/activated receiver " + pos);
                existing++;
            }
        }
        if (!actual.equals(new HashSet<>(expected)) || actual.size() != expected.size())
            throw new IllegalStateException("Transaction frontier changed");
        for (var entry : pending.entrySet())
            level.getChunkSource().chunkMap.write(entry.getKey(), entry.getValue()).join();
        level.getChunkSource().chunkMap.synchronize(true).join();
        return new Recovery(existing, pending.size(), committed);
    }

    void commit(ServerLevel level) throws IOException {
        requireThread(level);
        if (record.getBoolean("committed").orElseThrow()) return;
        var next = record.copy();
        next.putBoolean("committed", true);
        durable(path, next);
        record.putBoolean("committed", true);
    }

    boolean committed() {
        return record.getBoolean("committed").orElseThrow();
    }

    static boolean acceptsStored(CompoundTag expected, CompoundTag stored) {
        var status = ChunkStatus.byName(stored.getString("Status").orElseThrow());
        return status != null
                && !status.isBefore(ChunkStatus.FEATURES)
                && status.isBefore(ChunkStatus.SPAWN)
                && FeatureCPreLightVerifier.content(stored)
                        .equals(FeatureCPreLightVerifier.content(expected));
    }

    static void validate(CompoundTag record, String world, long seed) {
        if (!record.keySet().equals(FIELDS)
                || record.getInt("schema").orElseThrow() != 1
                || !record.getString("world").orElseThrow().equals(world)
                || record.getLong("seed").orElseThrow() != seed
                || record.getLong("producer_pid").orElseThrow() <= 0
                || record.getBoolean("committed").isEmpty())
            throw new IllegalArgumentException("Foreign/corrupt pre-light transaction");
        var positions = new HashSet<ChunkPos>();
        var rows = record.getList("patch").orElseThrow();
        if (rows.isEmpty() || rows.size() > 49)
            throw new IllegalArgumentException("Unbounded transaction");
        for (var item : rows) {
            var row = (CompoundTag) item;
            if (!row.keySet()
                    .equals(Set.of("x", "z", "seed", "journal", "payload", "payload_hash")))
                throw new IllegalArgumentException("Unknown transaction row field");
            var pos = owner(row);
            if (!positions.add(pos)
                    || row.getLong("seed").orElseThrow() != FeatureCFrontierVerifier.seed(pos))
                throw new IllegalArgumentException("Duplicate/foreign local seed");
            FeatureContributionJournal.decode(
                    row.getCompound("journal").orElseThrow(),
                    FeatureCFrontierVerifier.SCOPE,
                    pos,
                    row.getLong("seed").orElseThrow());
            var payload = row.getCompound("payload").orElseThrow();
            if (!row.getString("payload_hash").orElseThrow().equals(digest(payload)))
                throw new IllegalArgumentException("Corrupt transaction payload");
            if (payload.getInt("xPos").orElseThrow() != pos.x()
                    || payload.getInt("zPos").orElseThrow() != pos.z()
                    || !payload.getString("Status")
                            .orElseThrow()
                            .equals(ChunkStatus.FEATURES.getName()))
                throw new IllegalArgumentException(
                        "Transaction payload must be its own FEATURES receiver");
        }
    }

    static String digest(CompoundTag payload) {
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.write((CompoundTag) ordered(payload), new DataOutputStream(bytes));
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot hash complete C payload", failure);
        }
    }

    private static Tag ordered(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var result = new CompoundTag();
            compound.keySet().stream()
                    .sorted()
                    .forEach(k -> result.put(k, ordered(compound.get(k))));
            return result;
        }
        if (tag instanceof ListTag list) {
            var result = new ListTag();
            list.forEach(t -> result.add(ordered(t)));
            return result;
        }
        return tag.copy();
    }

    static void durable(Path path, CompoundTag record) throws IOException {
        var temporary = path.resolveSibling(path.getFileName() + ".pending");
        NbtIo.writeCompressed(record, temporary);
        try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        // Unsupported atomic publication fails; no partial copy fallback is permitted.
        Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static ChunkPos owner(CompoundTag row) {
        return new ChunkPos(row.getInt("x").orElseThrow(), row.getInt("z").orElseThrow());
    }

    private static String name(ChunkPos pos) {
        return "chunk_" + pos.x() + "_" + pos.z();
    }

    private static String world(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                + "|"
                + level.dimension().identifier();
    }

    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("C recovery requires server thread");
    }
}
