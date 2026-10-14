package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;

/** Real physical receiving mechanics only: native host bases, not a complete C Mosaic world. */
public final class FeatureCPhysicalVerifier {
    private FeatureCPhysicalVerifier() {}

    private static final AtomicInteger GENERATED = new AtomicInteger();
    private static final AtomicInteger LOADING_STEPS = new AtomicInteger();
    private static volatile boolean observing;

    public static void stage(WorldGenContext context, ChunkStatus status, ChunkAccess chunk) {
        if (observing
                && GenerationContextRegistry.find(context).isEmpty()
                && status == ChunkStatus.FEATURES) {
            if (chunk.getPersistedStatus().isBefore(status)) GENERATED.incrementAndGet();
            else LOADING_STEPS.incrementAndGet();
        }
    }

    public static void run(MinecraftServer server, Path root) throws Exception {
        Files.createDirectories(root);
        var level = server.overworld();
        require(!MosaicWorldIdentity.isMosaicWorld(server), "Must use a disposable native host");
        long experimentalBefore = FeatureCFlowVerifier.generationCount();
        boolean reload = root.getFileName().toString().equals("host_1");
        Path first = reload ? root.resolveSibling("host_0") : root;
        Path input =
                root.getParent()
                        .getParent()
                        .getParent()
                        .resolve("current/26.3-fabric/host_0/native-tree-contributions.nbt");
        var inputHash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(input)));
        var raw = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        var batches = new ArrayList<TreeContribution>();
        for (var tag : raw.getList("batches").orElseThrow())
            batches.add(TreeContribution.decode((CompoundTag) tag));
        boolean hiveSuite = Boolean.getBoolean("featurec.physicalHives");
        if (hiveSuite)
            batches.removeIf(b -> b.cells().stream().noneMatch(c -> c.blockEntity().isPresent()));
        require(!batches.isEmpty(), "No genuine native tree input");
        var saved =
                reload
                        ? NbtIo.readCompressed(
                                first.resolve("receipts.nbt"), NbtAccounter.unlimitedHeap())
                        : new CompoundTag();
        var rows = new ListTag();
        int accepted = 0, hives = 0, ticks = 0, protectedCount = 0, duplicateWrites = 0;
        boolean workerRejected = false,
                unloadedDeferred = false,
                changedBatchRejected = false,
                staleBaseRejected = false,
                foreignReceiptRejected = false;
        Set<ChunkPos> receivers =
                new TreeSet<>(Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x));
        batches.forEach(b -> b.cells().forEach(c -> receivers.add(ChunkPos.containing(c.pos()))));
        observing = true;
        if (reload) {
            require(saved.getString("input_hash").orElseThrow().equals(inputHash), "Input changed");
            require(
                    saved.getLong("pid").orElseThrow() != ProcessHandle.current().pid(),
                    "Not a restart");
            for (var item : saved.getList("receivers").orElseThrow()) {
                var row = (CompoundTag) item;
                var pos =
                        new ChunkPos(row.getInt("x").orElseThrow(), row.getInt("z").orElseThrow());
                loadHalo(level, pos);
                var receipt = row.getCompound("receipt").orElseThrow();
                var journal =
                        FeatureContributionJournal.decode(
                                receipt.getCompound("journal").orElseThrow(),
                                batches.getFirst().key().scope(),
                                pos,
                                level.getSeed());
                var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
                verifyExpected(level, chunk, row.getList("expected").orElseThrow());
                for (var tag : row.getListOrEmpty("block_entity_payloads")) {
                    var payload = (CompoundTag) tag;
                    verifyBlockEntity(
                            level,
                            chunk,
                            BlockPos.of(payload.getLong("pos").orElseThrow()),
                            payload.getCompound("source").orElseThrow(),
                            root);
                    hives++;
                }
                if (!ticks(chunk, level).equals(row.getCompound("ticks").orElseThrow())) {
                    var difference = new CompoundTag();
                    difference.put("expected", row.getCompound("ticks").orElseThrow());
                    difference.put("actual", ticks(chunk, level));
                    NbtIo.writeCompressed(difference, root.resolve("tick-mismatch.nbt"));
                    Files.writeString(root.resolve("tick-mismatch.txt"), difference.toString());
                    throw new IllegalStateException("Saved ticks differ; raw evidence retained");
                }
                require(
                        metadata(chunk, level).equals(row.getCompound("metadata").orElseThrow()),
                        "Saved metadata differs");
                var before = SerializableChunkData.copyOf(level, chunk).write();
                duplicateWrites +=
                        FeatureCPhysicalBatch.restore(level, receipt).apply(level, journal);
                require(
                        before.equals(SerializableChunkData.copyOf(level, chunk).write()),
                        "Reload duplicate mutated chunk");
                rows.add(row.copy());
            }
            require(GENERATED.get() == 0, "Reload generated new physical FEATURES");
        } else {
            for (var pos : receivers) {
                var relevant =
                        batches.stream()
                                .filter(
                                        b ->
                                                b.cells().stream()
                                                        .anyMatch(
                                                                c ->
                                                                        ChunkPos.containing(c.pos())
                                                                                .equals(pos)))
                                .toList();
                var journal =
                        new FeatureContributionJournal(
                                batches.getFirst().key().scope(),
                                pos,
                                level.getSeed(),
                                level.getMinY(),
                                level.getHeight());
                relevant.forEach(journal::publish);
                loadHalo(level, pos);
                var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
                var positions = new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
                relevant.forEach(
                        b ->
                                b.cells().stream()
                                        .filter(c -> ChunkPos.containing(c.pos()).equals(pos))
                                        .forEach(c -> positions.add(c.pos())));
                journal.supplyBase(
                        positions.stream()
                                .map(p -> FeatureCPhysicalBatch.read(chunk, p, level))
                                .toList());
                if (journal.project().incoming().size() < 2) continue;
                // A real native-level edit back to air is deliberately indistinguishable from
                // untouched air.
                var protectedPos =
                        journal.project().incoming().keySet().stream()
                                .filter(
                                        p ->
                                                !journal.project()
                                                        .incoming()
                                                        .get(p)
                                                        .state()
                                                        .hasBlockEntity())
                                .min(Comparator.comparingLong(BlockPos::asLong))
                                .orElseThrow();
                require(
                        level.setBlock(
                                protectedPos, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL),
                        "Edit failed");
                require(
                        level.setBlock(
                                protectedPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL),
                        "Dig failed");
                journal.protect(FeatureCPhysicalBatch.read(chunk, protectedPos, level));
                protectedCount++;
                var projection = journal.project();
                var batch = new FeatureCPhysicalBatch(level, pos, journal);
                if (!workerRejected) {
                    var error =
                            CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            batch.apply(level, journal);
                                            return false;
                                        } catch (IllegalStateException expected) {
                                            return expected.getMessage().contains("server thread");
                                        }
                                    });
                    workerRejected = error.get();
                    require(workerRejected, "Worker acquired physical write permission");
                }
                var structureBefore = metadata(chunk, level);
                var stalePos =
                        projection.incoming().keySet().stream()
                                .min(Comparator.comparingLong(BlockPos::asLong))
                                .orElseThrow();
                require(
                        level.setBlock(
                                stalePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL),
                        "Stale base edit failed");
                var staleBefore = SerializableChunkData.copyOf(level, chunk).write();
                try {
                    batch.apply(level, journal);
                    throw new AssertionError("Stale physical base accepted");
                } catch (IllegalStateException expectedFailure) {
                    require(
                            expectedFailure.getMessage().contains("Physical base changed"),
                            "Wrong stale-base failure");
                    require(
                            staleBefore.equals(SerializableChunkData.copyOf(level, chunk).write()),
                            "Stale preflight partially mutated chunk");
                    staleBaseRejected = true;
                }
                require(
                        level.setBlock(stalePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL),
                        "Stale test restore failed");
                int loaded = level.getChunkSource().getLoadedChunksCount();
                int blockEntitiesBefore = chunk.getBlockEntities().size();
                long entitiesBefore = entityCount(level);
                int count = batch.apply(level, journal);
                require(
                        count > 0 && count == projection.incoming().size(),
                        "No physical acceptance");
                accepted += count;
                hives +=
                        (int)
                                projection.incoming().values().stream()
                                        .filter(c -> c.blockEntity().isPresent())
                                        .count();
                ticks += projection.blockTicks().size();
                int incomingBlockEntities =
                        (int)
                                projection.incoming().values().stream()
                                        .filter(c -> c.blockEntity().isPresent())
                                        .count();
                require(
                        chunk.getBlockEntities().size()
                                        == blockEntitiesBefore + incomingBlockEntities
                                && entityCount(level) == entitiesBefore,
                        "BE registration/entity isolation mismatch");
                require(
                        level.getChunkSource().getLoadedChunksCount() == loaded,
                        "Receiving loaded a chunk");
                require(
                        structureBefore.equals(metadata(chunk, level)),
                        "Receiving changed biomes/structures");
                var blockEntityPayloads = new ListTag();
                for (var cell : projection.incoming().values()) {
                    require(
                            chunk.getBlockState(cell.pos()).equals(cell.state()),
                            "Physical block differs");
                    if (cell.blockEntity().isPresent())
                        blockEntityPayloads.add(
                                verifyBlockEntity(
                                        level,
                                        chunk,
                                        cell.pos(),
                                        cell.blockEntity().orElseThrow(),
                                        root));
                }
                for (var tick : projection.blockTicks())
                    require(
                            chunk.getBlockTicks().hasScheduledTick(tick.pos(), tick.type()),
                            "Missing physical block tick");
                var before = SerializableChunkData.copyOf(level, chunk).write();
                for (int repeat = 0; repeat < 4; repeat++)
                    duplicateWrites += batch.apply(level, journal);
                require(
                        chunk.getBlockEntities().size()
                                        == blockEntitiesBefore + incomingBlockEntities
                                && entityCount(level) == entitiesBefore,
                        "Duplicate BE/entity registration");
                require(
                        before.equals(SerializableChunkData.copyOf(level, chunk).write()),
                        "Duplicate activated effects");
                var row = new CompoundTag();
                row.putInt("x", pos.x());
                row.putInt("z", pos.z());
                row.put("receipt", batch.receipt());
                row.put("block_entity_payloads", blockEntityPayloads);
                var foreign = batch.receipt();
                foreign.putLong("seed", level.getSeed() + 1);
                try {
                    FeatureCPhysicalBatch.restore(level, foreign);
                    throw new AssertionError("Foreign receipt accepted");
                } catch (IllegalArgumentException expectedFailure) {
                    foreignReceiptRejected = true;
                }
                // Exercise modification AFTER physical acceptance, then duplicate delivery and
                // reload.
                var edited =
                        projection.incoming().keySet().stream()
                                .filter(p -> !projection.incoming().get(p).state().hasBlockEntity())
                                .min(Comparator.comparingLong(BlockPos::asLong))
                                .orElseThrow();
                require(
                        level.setBlock(edited, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL),
                        "Post-acceptance dig failed");
                duplicateWrites += batch.apply(level, journal);
                require(chunk.getBlockState(edited).isAir(), "Duplicate restored player-dug air");
                var expected = new ListTag();
                for (var p : positions)
                    expected.add(
                            TreeContribution.Cell.CODEC
                                    .encodeStart(
                                            NbtOps.INSTANCE,
                                            FeatureCPhysicalBatch.read(chunk, p, level))
                                    .getOrThrow());
                row.put("expected", expected);
                row.put("ticks", ticks(chunk, level));
                row.put("metadata", metadata(chunk, level));
                rows.add(row);
                journal.remove(relevant.getFirst().key());
                try {
                    batch.apply(level, journal);
                    throw new AssertionError("Changed batch accepted");
                } catch (IllegalStateException expectedFailure) {
                    require(
                            expectedFailure.getMessage().contains("Changed physical batch"),
                            "Wrong rejection");
                    changedBatchRejected = true;
                }
            }
            require(
                    accepted > 0 && ticks > 0 && protectedCount > 0,
                    "Missing natural physical positives: accepted="
                            + accepted
                            + " ticks="
                            + ticks
                            + " protected="
                            + protectedCount);
            var far = new ChunkPos(100000, 100000);
            var missing =
                    new FeatureContributionJournal(
                            batches.getFirst().key().scope(),
                            far,
                            level.getSeed(),
                            level.getMinY(),
                            level.getHeight());
            int loaded = level.getChunkSource().getLoadedChunksCount();
            unloadedDeferred =
                    new FeatureCPhysicalBatch(level, far, missing).apply(level, missing) == -1;
            require(
                    unloadedDeferred && loaded == level.getChunkSource().getLoadedChunksCount(),
                    "Missing receiver loaded");
            saved.putInt("schema", 1);
            saved.putLong("pid", ProcessHandle.current().pid());
            saved.putString("input_hash", inputHash);
            saved.put("receivers", rows);
        }
        for (var item : rows) {
            var row = (CompoundTag) item;
            var light = level.getChunkSource().getLightEngine();
            var wait =
                    light.waitForPendingTasks(
                            row.getInt("x").orElseThrow(), row.getInt("z").orElseThrow());
            light.tryScheduleUpdate();
            server.managedBlock(wait::isDone);
            wait.join();
            var chunk =
                    level.getChunkSource()
                            .getChunkNow(
                                    row.getInt("x").orElseThrow(), row.getInt("z").orElseThrow());
            require(chunk.isLightCorrect(), "Physical receiver light not ready");
            verifyHeights(chunk);
            var lightValues = new ListTag();
            for (var tag : row.getList("expected").orElseThrow()) {
                var cell = TreeContribution.Cell.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
                var values = new CompoundTag();
                values.putLong("pos", cell.pos().asLong());
                values.putInt(
                        "sky", light.getLayerListener(LightLayer.SKY).getLightValue(cell.pos()));
                values.putInt(
                        "block",
                        light.getLayerListener(LightLayer.BLOCK).getLightValue(cell.pos()));
                lightValues.add(values);
            }
            if (reload)
                require(
                        lightValues.equals(row.getList("light").orElseThrow()),
                        "Physical light changed across restart");
            else row.put("light", lightValues);
        }
        // Neighbour notifications from a later receiver may add ticks to an earlier receiver.
        // Capture the save boundary after the complete physical workload, never per-batch early.
        if (!reload)
            for (var item : rows) {
                var row = (CompoundTag) item;
                var chunk =
                        level.getChunkSource()
                                .getChunkNow(
                                        row.getInt("x").orElseThrow(),
                                        row.getInt("z").orElseThrow());
                verifyExpected(level, chunk, row.getList("expected").orElseThrow());
                row.put("ticks", ticks(chunk, level));
                row.put("metadata", metadata(chunk, level));
            }
        require(server.saveEverything(true, true, true), "Physical save failed");
        if (hiveSuite) require(hives > 0, "No actual physical bee nest accepted/reloaded");
        if (!reload) NbtIo.writeCompressed(saved, root.resolve("receipts.nbt"));
        require(
                duplicateWrites == 0
                        && GenerationContextRegistry.bindingCount() == 0
                        && FeatureCFlowVerifier.generationCount() == experimentalBefore,
                "Receiving invoked experimental generation/leaked");
        observing = false;
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "PASS_SEALED_PHYSICAL_BATCH_ONLY");
        result.put("proofVersion", 1);
        result.put("pid", ProcessHandle.current().pid());
        result.put("reload", reload);
        result.put("inputHash", inputHash);
        result.put("receiverBase", "NATIVE_PHYSICAL_NOT_C");
        result.put("hiveSuite", hiveSuite);
        result.put("hostSeed", level.getSeed());
        result.put("inputBatches", batches.size());
        result.put("beePayloadsCompared", hives);
        result.put("receivers", rows.size());
        result.put("accepted", accepted);
        result.put("hives", hives);
        result.put("blockTicks", ticks);
        result.put("protected", protectedCount);
        result.put("duplicateWrites", duplicateWrites);
        result.put("nativeGeneratedFeatures", GENERATED.get());
        result.put("experimentalGenerationCalls", 0);
        result.put("nativeFeaturesLoadingSteps", LOADING_STEPS.get());
        result.put("workerRejected", workerRejected);
        result.put("unloadedDeferred", unloadedDeferred);
        result.put("changedBatchRejected", changedBatchRejected);
        result.put("bindings", GenerationContextRegistry.bindingCount());
        result.put("staleBaseRejected", staleBaseRejected);
        result.put("foreignReceiptRejected", foreignReceiptRejected);
        result.put("heightColumnsVerified", rows.size() * 256 * 4);
        result.put("lightReloadEqual", reload);
        result.put("postProcessing", "REJECTED_NOT_IMPLEMENTED");
        result.put("crashAtomicity", "NOT_EVALUATED");
        Files.writeString(
                root.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
    }

    static void loadHalo(ServerLevel level, ChunkPos center) {
        for (int z = -1; z <= 1; z++)
            for (int x = -1; x <= 1; x++) {
                var future =
                        level.getChunkSource()
                                .getChunkFuture(
                                        center.x() + x, center.z() + z, ChunkStatus.FULL, true);
                level.getServer().managedBlock(future::isDone);
                require(
                        future.join().orElse(null) instanceof LevelChunk,
                        "Native FULL unavailable");
            }
    }

    static CompoundTag metadata(LevelChunk chunk, ServerLevel level) {
        var all = SerializableChunkData.copyOf(level, chunk).write();
        var result = new CompoundTag();
        result.put("structures", all.getCompound("structures").orElseThrow().copy());
        var biomes = new ListTag();
        for (var section : all.getList("sections").orElseThrow()) {
            var data = (CompoundTag) section;
            data.getCompound("biomes").ifPresent(b -> biomes.add(b.copy()));
        }
        result.put("biomes", biomes);
        return result;
    }

    static CompoundTag ticks(LevelChunk chunk, ServerLevel level) {
        var all = SerializableChunkData.copyOf(level, chunk).write();
        var result = new CompoundTag();
        result.put("block_ticks", all.getList("block_ticks").orElseThrow().copy());
        result.put("fluid_ticks", all.getList("fluid_ticks").orElseThrow().copy());
        return result;
    }

    static void verifyExpected(ServerLevel level, LevelChunk chunk, ListTag list) {
        for (var tag : list) {
            var cell = TreeContribution.Cell.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            require(
                    cell.equals(FeatureCPhysicalBatch.read(chunk, cell.pos(), level)),
                    "Saved cell/BE/edit differs: " + cell.pos());
        }
    }

    private static CompoundTag verifyBlockEntity(
            ServerLevel level, LevelChunk chunk, BlockPos pos, CompoundTag source, Path root)
            throws Exception {
        var blockEntity = chunk.getBlockEntity(pos);
        require(blockEntity != null, "Missing physical block entity");
        var payload = blockEntity.saveWithFullMetadata(level.registryAccess());
        if (!source.equals(payload)) {
            var difference = new CompoundTag();
            difference.put("expected", source.copy());
            difference.put("actual", payload);
            NbtIo.writeCompressed(difference, root.resolve("be-mismatch.nbt"));
            Files.writeString(root.resolve("be-mismatch.txt"), difference.toString());
            throw new IllegalStateException(
                    "Physical BE payload differs: "
                            + NativeFeatureRepeatVerifier.differences(source, payload));
        }
        // Vanilla LevelChunk's save envelope adds keepPacked=false; ProtoChunk's live BE
        // payload does not. Compare the complete payload AND the exact storage envelope.
        var expectedStorage = source.copy();
        expectedStorage.putBoolean("keepPacked", false);
        var storage = chunk.getBlockEntityNbtForSaving(pos, level.registryAccess());
        require(expectedStorage.equals(storage), "Physical BE storage envelope differs");
        var evidence = new CompoundTag();
        evidence.putLong("pos", pos.asLong());
        evidence.put("source", source.copy());
        evidence.put("physical_payload", payload);
        evidence.put("physical_storage", storage);
        return evidence;
    }

    static void verifyHeights(LevelChunk chunk) {
        for (var type :
                List.of(
                        Heightmap.Types.WORLD_SURFACE,
                        Heightmap.Types.OCEAN_FLOOR,
                        Heightmap.Types.MOTION_BLOCKING,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)) {
            require(chunk.hasPrimedHeightmap(type), "Missing FULL heightmap");
            var p = new BlockPos.MutableBlockPos();
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int expected = chunk.getMinY() - 1;
                    for (int y = chunk.getMinY() + chunk.getHeight() - 1;
                            y >= chunk.getMinY();
                            y--) {
                        p.set(
                                chunk.getPos().getMinBlockX() + x,
                                y,
                                chunk.getPos().getMinBlockZ() + z);
                        if (type.isOpaque().test(chunk.getBlockState(p))) {
                            expected = y;
                            break;
                        }
                    }
                    require(
                            chunk.getHeight(type, x, z) == expected,
                            "Stale physical heightmap " + type + " " + p);
                }
        }
    }

    private static void require(boolean test, String message) {
        if (!test) throw new IllegalStateException(message);
    }

    private static long entityCount(ServerLevel level) {
        return java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .count();
    }
}
