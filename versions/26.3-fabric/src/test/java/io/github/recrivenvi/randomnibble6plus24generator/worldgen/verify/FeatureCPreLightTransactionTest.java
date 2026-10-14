package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureContributionJournal;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FeatureCPreLightTransactionTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    private static CompoundTag record() {
        var pos = new ChunkPos(0, 0);
        long local = FeatureCFrontierVerifier.seed(pos);
        var journal =
                new FeatureContributionJournal(
                        FeatureCFrontierVerifier.SCOPE, pos, local, -64, 384);
        var payload = new CompoundTag();
        payload.putInt("xPos", 0);
        payload.putInt("zPos", 0);
        payload.putString("Status", ChunkStatus.FEATURES.getName());
        var row = new CompoundTag();
        row.putInt("x", 0);
        row.putInt("z", 0);
        row.putLong("seed", local);
        row.put("journal", journal.encode());
        row.put("payload", payload);
        row.putString("payload_hash", FeatureCPreLightTransaction.digest(payload));
        var rows = new ListTag();
        rows.add(row);
        var record = new CompoundTag();
        record.putInt("schema", 1);
        record.putString("world", "save|overworld");
        record.putLong("seed", 23);
        record.putLong("producer_pid", 1);
        record.putBoolean("committed", false);
        record.put("patch", rows);
        return record;
    }

    private static CompoundTag row(CompoundTag record) {
        return (CompoundTag) record.getList("patch").orElseThrow().getFirst();
    }

    @Test
    void preparedAndCommittedRecordsRemainIdentityBound() {
        var record = record();
        FeatureCPreLightTransaction.validate(record, "save|overworld", 23);
        record.putBoolean("committed", true);
        FeatureCPreLightTransaction.validate(record, "save|overworld", 23);
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(record, "save|nether", 23));
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(record, "other|overworld", 23));
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(record, "save|overworld", 24));
    }

    @Test
    void rejectsForeignOwnerSeedAndActivatedPayload() {
        var seed = record();
        row(seed).putLong("seed", 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(seed, "save|overworld", 23));
        var owner = record();
        row(owner).getCompound("payload").orElseThrow().putInt("xPos", 1);
        row(owner)
                .putString(
                        "payload_hash",
                        FeatureCPreLightTransaction.digest(
                                row(owner).getCompound("payload").orElseThrow()));
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(owner, "save|overworld", 23));
        var full = record();
        row(full)
                .getCompound("payload")
                .orElseThrow()
                .putString("Status", ChunkStatus.FULL.getName());
        row(full)
                .putString(
                        "payload_hash",
                        FeatureCPreLightTransaction.digest(
                                row(full).getCompound("payload").orElseThrow()));
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(full, "save|overworld", 23));
    }

    @Test
    void rejectsUnknownEnvelopeFieldsAndDuplicateReceivers() {
        var envelope = record();
        envelope.putBoolean("ignore_failure", true);
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(envelope, "save|overworld", 23));
        var extra = record();
        row(extra).putBoolean("overwrite_full", true);
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(extra, "save|overworld", 23));
        var duplicate = record();
        duplicate.getList("patch").orElseThrow().add(row(duplicate).copy());
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(duplicate, "save|overworld", 23));
    }

    @Test
    void interruptedTemporaryRecordCannotReplacePublishedAuthority() throws Exception {
        var path = directory.resolve("transaction.nbt");
        var prepared = record();
        FeatureCPreLightTransaction.durable(path, prepared);
        var uncommitted = prepared.copy();
        uncommitted.putBoolean("committed", true);
        NbtIo.writeCompressed(uncommitted, path.resolveSibling("transaction.nbt.pending"));
        assertEquals(prepared, NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
        FeatureCPreLightTransaction.durable(path, uncommitted);
        assertEquals(uncommitted, NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
        assertFalse(Files.exists(path.resolveSibling("transaction.nbt.pending")));
    }

    @Test
    void payloadIntegrityIncludesRawEntitiesAndUnknownFields() {
        var value = record();
        row(value).getCompound("payload").orElseThrow().putString("extra", "changed");
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCPreLightTransaction.validate(value, "save|overworld", 23));
        var first = new CompoundTag();
        var entity = new CompoundTag();
        entity.putIntArray("UUID", new int[] {1, 2, 3, 4});
        var entities = new ListTag();
        entities.add(entity);
        first.put("entities", entities);
        var changed = first.copy();
        ((CompoundTag) changed.getList("entities").orElseThrow().getFirst())
                .putIntArray("UUID", new int[] {4, 3, 2, 1});
        assertNotEquals(
                FeatureCPreLightTransaction.digest(first),
                FeatureCPreLightTransaction.digest(changed));
        var listChanged = first.copy();
        listChanged.getList("entities").orElseThrow().add(entity.copy());
        assertNotEquals(
                FeatureCPreLightTransaction.digest(first),
                FeatureCPreLightTransaction.digest(listChanged));
    }

    @Test
    void refusesChangedAndActivatedStoredChunksEvenWhenTheirEnvelopeLooksValid() {
        var original = row(record()).getCompound("payload").orElseThrow();
        for (var status :
                new ChunkStatus[] {
                    ChunkStatus.FEATURES, ChunkStatus.INITIALIZE_LIGHT, ChunkStatus.LIGHT
                }) {
            var stored = original.copy();
            stored.putString("Status", status.getName());
            stored.putLong("LastUpdate", 400);
            assertTrue(FeatureCPreLightTransaction.acceptsStored(original, stored));
        }
        for (var status :
                new ChunkStatus[] {ChunkStatus.TERRAIN, ChunkStatus.SPAWN, ChunkStatus.FULL}) {
            var stored = original.copy();
            stored.putString("Status", status.getName());
            assertFalse(FeatureCPreLightTransaction.acceptsStored(original, stored));
        }
        var changed = original.copy();
        changed.putString("player_data", "preserve me");
        assertFalse(FeatureCPreLightTransaction.acceptsStored(original, changed));
    }
}
