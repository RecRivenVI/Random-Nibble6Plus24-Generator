package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/** Native block mutation observation, not player input or a dynamic C production receiver. */
public final class FeatureCEditProtectionVerifier {
    private FeatureCEditProtectionVerifier() {}

    private static final AtomicInteger GENERATED = new AtomicInteger();
    private static volatile boolean observing;

    public static void stage(WorldGenContext context, ChunkStatus status, ChunkAccess chunk) {
        if (observing
                && GenerationContextRegistry.find(context).isEmpty()
                && status == ChunkStatus.FEATURES
                && chunk.getPersistedStatus().isBefore(status)) GENERATED.incrementAndGet();
    }

    public static void run(MinecraftServer server, Path root) throws Exception {
        Files.createDirectories(root);
        var level = server.overworld();
        require(!MosaicWorldIdentity.isMosaicWorld(server), "Disposable native host required");
        long generationsBefore = FeatureCFlowVerifier.generationCount();
        boolean reload = root.getFileName().toString().equals("host_1");
        Path input =
                root.getParent()
                        .getParent()
                        .getParent()
                        .resolve("current/26.3-fabric/host_0/native-tree-contributions.nbt");
        String hash =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(Files.readAllBytes(input)));
        var batches = new ArrayList<TreeContribution>();
        for (var tag :
                NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap())
                        .getList("batches")
                        .orElseThrow()) {
            var batch = TreeContribution.decode((CompoundTag) tag);
            if (batch.cells().stream().anyMatch(c -> c.blockEntity().isPresent()))
                batches.add(batch);
        }
        require(!batches.isEmpty(), "Missing genuine native hive contributions");
        var scope = batches.getFirst().key().scope();
        var saved =
                reload
                        ? NbtIo.readCompressed(
                                root.resolveSibling("host_0/edits.nbt"),
                                NbtAccounter.unlimitedHeap())
                        : new CompoundTag();
        var rows = reload ? saved.getList("receivers").orElseThrow() : new ListTag();
        var guards = new LinkedHashMap<ChunkPos, FeatureCEditTracker>();
        int accepted = 0,
                ownWrites = 0,
                observations = 0,
                protectedPositions = 0,
                duplicateWrites = 0;
        int immediate = 0, delayed = 0, hives = 0, blockTicks = 0;
        boolean workerRejected = false, exceptionPropagated = false;
        observing = true;
        try {
            var receivers =
                    new TreeSet<ChunkPos>(
                            Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x));
            if (reload) {
                require(saved.getString("input_hash").orElseThrow().equals(hash), "Input changed");
                require(
                        saved.getLong("pid").orElseThrow() != ProcessHandle.current().pid(),
                        "Independent JVM required");
                for (var item : rows) receivers.add(owner((CompoundTag) item));
            } else
                batches.forEach(
                        b -> b.cells().forEach(c -> receivers.add(ChunkPos.containing(c.pos()))));
            for (var pos : receivers) FeatureCPhysicalVerifier.loadHalo(level, pos);
            if (reload) {
                require(GENERATED.get() == 0, "Reload regenerated physical FEATURES");
                // Compare all receivers before any delayed acceptance can notify its neighbours.
                for (var item : rows) {
                    var row = (CompoundTag) item;
                    var chunk = loaded(level, owner(row));
                    FeatureCPhysicalVerifier.verifyExpected(
                            level, chunk, row.getList("expected").orElseThrow());
                    require(
                            FeatureCPhysicalVerifier.ticks(chunk, level)
                                    .equals(row.getCompound("ticks").orElseThrow()),
                            "Reload ticks differ");
                    require(
                            FeatureCPhysicalVerifier.metadata(chunk, level)
                                    .equals(row.getCompound("metadata").orElseThrow()),
                            "Reload metadata differs");
                    guards.put(
                            owner(row),
                            FeatureCEditTracker.restore(
                                    level, owner(row), row.getCompound("guard").orElseThrow()));
                }
            } else {
                for (var pos : receivers) {
                    var relevant =
                            batches.stream()
                                    .filter(
                                            b ->
                                                    b.cells().stream()
                                                            .anyMatch(
                                                                    c ->
                                                                            ChunkPos.containing(
                                                                                            c.pos())
                                                                                    .equals(pos)))
                                    .toList();
                    var chunk = loaded(level, pos);
                    var positions =
                            new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
                    relevant.forEach(
                            b ->
                                    b.cells().stream()
                                            .filter(c -> ChunkPos.containing(c.pos()).equals(pos))
                                            .forEach(c -> positions.add(c.pos())));
                    var preview = journal(level, pos, relevant, positions);
                    var plain =
                            preview.project().incoming().values().stream()
                                    .filter(c -> !c.state().hasBlockEntity())
                                    .map(TreeContribution.Cell::pos)
                                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                                    .toList();
                    if (plain.size() < 3) continue;
                    var guard = FeatureCEditTracker.open(level, pos);
                    guards.put(pos, guard);
                    var preEdit = plain.getFirst();
                    // No contribution has been published to the actual journal at this point.
                    require(
                            level.setBlock(
                                    preEdit, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL),
                            "Placement failed");
                    require(
                            level.setBlock(
                                    preEdit, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL),
                            "Dig failed");
                    require(
                            guard.protects(preEdit) && guard.observed() >= 2,
                            "Native mutation was not observed");
                    var journal = journal(level, pos, relevant, positions);
                    guard.protect(journal);
                    require(
                            !journal.project().incoming().containsKey(preEdit),
                            "Late contribution refilled edited air");
                    var row = new CompoundTag();
                    row.putInt("x", pos.x());
                    row.putInt("z", pos.z());
                    row.putLong("pre_edit", preEdit.asLong());
                    row.putLong("post_edit", plain.get(1).asLong());
                    row.putLongArray(
                            "positions", positions.stream().mapToLong(BlockPos::asLong).toArray());
                    row.putBoolean("applied", rows.size() % 2 == 0);
                    row.put("journal", journal.encode());
                    rows.add(row);
                }
                require(rows.size() >= 2, "Missing immediate and restart-delayed receivers");
            }
            for (var item : rows) {
                var row = (CompoundTag) item;
                var pos = owner(row);
                var chunk = loaded(level, pos);
                var guard = guards.get(pos);
                var journal =
                        FeatureContributionJournal.decode(
                                row.getCompound("journal").orElseThrow(),
                                scope,
                                pos,
                                level.getSeed());
                var proposal =
                        FeatureContributionJournal.decode(
                                journal.encode(), scope, pos, level.getSeed());
                guard.protect(proposal);
                var preEdit = BlockPos.of(row.getLong("pre_edit").orElseThrow());
                require(
                        guard.protects(preEdit)
                                && !proposal.project().incoming().containsKey(preEdit),
                        "Edit protection lost");
                protectedPositions++;
                boolean applied = row.getBoolean("applied").orElseThrow();
                if (!workerRejected) {
                    workerRejected =
                            CompletableFuture.supplyAsync(
                                            () -> {
                                                try {
                                                    guard.protect(proposal);
                                                    return false;
                                                } catch (IllegalStateException expected) {
                                                    return expected.getMessage()
                                                            .contains("server thread");
                                                }
                                            })
                                    .get();
                    require(workerRejected, "Worker accessed physical edit state");
                    var error = new IllegalStateException("injected contribution failure");
                    try {
                        FeatureCEditTracker.writeContribution(
                                level,
                                preEdit,
                                Blocks.OAK_LOG.defaultBlockState(),
                                () -> {
                                    throw error;
                                });
                        throw new AssertionError("Failure swallowed");
                    } catch (IllegalStateException actual) {
                        require(actual == error, "Failure replaced");
                    }
                    // A successful call after the failure proves the transient permit was released.
                    require(
                            FeatureCEditTracker.writeContribution(
                                    level, preEdit, Blocks.OAK_LOG.defaultBlockState(), () -> true),
                            "Leaked write permit");
                    exceptionPropagated = true;
                }
                if (reload && applied) {
                    var before = SerializableChunkData.copyOf(level, chunk).write();
                    duplicateWrites +=
                            FeatureCPhysicalBatch.restore(
                                            level, row.getCompound("receipt").orElseThrow())
                                    .apply(level, journal);
                    require(
                            before.equals(SerializableChunkData.copyOf(level, chunk).write()),
                            "Duplicate changed physical state");
                    var post = BlockPos.of(row.getLong("post_edit").orElseThrow());
                    require(
                            guard.protects(post)
                                    && chunk.getBlockState(post).isAir()
                                    && !proposal.project().incoming().containsKey(post),
                            "Post-acceptance edit restored");
                    protectedPositions++;
                    immediate++;
                } else if (applied || reload) {
                    int loaded = level.getChunkSource().getLoadedChunksCount();
                    var metadata = FeatureCPhysicalVerifier.metadata(chunk, level);
                    var batch = new FeatureCPhysicalBatch(level, pos, proposal);
                    int writes = batch.apply(level, proposal);
                    require(
                            writes > 0 && writes == proposal.project().incoming().size(),
                            "Missing actual acceptance");
                    require(guard.owned() == writes, "Own writes were not precisely exempted");
                    require(
                            level.getChunkSource().getLoadedChunksCount() == loaded,
                            "Acceptance loaded chunks");
                    require(
                            metadata.equals(FeatureCPhysicalVerifier.metadata(chunk, level)),
                            "Structure/biome mutation");
                    for (var cell : proposal.project().incoming().values()) {
                        require(
                                chunk.getBlockState(cell.pos()).equals(cell.state()),
                                "Accepted block differs");
                        if (cell.blockEntity().isPresent()) {
                            var be = chunk.getBlockEntity(cell.pos());
                            require(
                                    be != null
                                            && be.saveWithFullMetadata(level.registryAccess())
                                                    .equals(cell.blockEntity().orElseThrow()),
                                    "Hive payload differs");
                            hives++;
                        }
                    }
                    for (var tick : proposal.project().blockTicks())
                        require(
                                chunk.getBlockTicks().hasScheduledTick(tick.pos(), tick.type()),
                                "Missing tick");
                    blockTicks += proposal.project().blockTicks().size();
                    accepted += writes;
                    if (!reload) {
                        var post = BlockPos.of(row.getLong("post_edit").orElseThrow());
                        require(!guard.protects(post), "Own write incorrectly protected");
                        require(
                                level.setBlock(
                                        post, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL),
                                "Post-acceptance dig failed");
                        require(guard.protects(post), "Post-acceptance edit missing");
                        immediate++;
                    } else delayed++;
                    var before = SerializableChunkData.copyOf(level, chunk).write();
                    duplicateWrites += batch.apply(level, proposal);
                    require(
                            before.equals(SerializableChunkData.copyOf(level, chunk).write()),
                            "Duplicate activated effects");
                    row.put("receipt", batch.receipt());
                    row.put("journal", proposal.encode());
                    row.putBoolean("applied", true);
                } else delayed++;
                require(chunk.getBlockState(preEdit).isAir(), "Edited air overwritten");
            }
            for (var item : rows) {
                var row = (CompoundTag) item;
                var pos = owner(row);
                var chunk = loaded(level, pos);
                var light = level.getChunkSource().getLightEngine();
                var wait = light.waitForPendingTasks(pos.x(), pos.z());
                light.tryScheduleUpdate();
                server.managedBlock(wait::isDone);
                wait.join();
                var expected = new ListTag();
                for (long packed : row.getLongArray("positions").orElseThrow())
                    expected.add(
                            TreeContribution.Cell.CODEC
                                    .encodeStart(
                                            NbtOps.INSTANCE,
                                            FeatureCPhysicalBatch.read(
                                                    chunk, BlockPos.of(packed), level))
                                    .getOrThrow());
                row.put("expected", expected);
                row.put("ticks", FeatureCPhysicalVerifier.ticks(chunk, level));
                row.put("metadata", FeatureCPhysicalVerifier.metadata(chunk, level));
                row.put("guard", guards.get(pos).encode());
            }
            require(server.saveEverything(true, true, true), "Save failed");
            saved.putInt("schema", 1);
            saved.putLong("pid", ProcessHandle.current().pid());
            saved.putString("input_hash", hash);
            saved.put("receivers", rows);
            NbtIo.writeCompressed(saved, root.resolve("edits.nbt"));
            ownWrites = guards.values().stream().mapToInt(FeatureCEditTracker::owned).sum();
            observations = guards.values().stream().mapToInt(FeatureCEditTracker::observed).sum();
        } finally {
            observing = false;
            guards.values().forEach(FeatureCEditTracker::close);
        }
        require(
                FeatureCEditTracker.bindingCount() == 0
                        && GenerationContextRegistry.bindingCount() == 0,
                "Context leak");
        require(
                FeatureCFlowVerifier.generationCount() == generationsBefore,
                "Receipt triggered experimental generation");
        require(
                accepted > 0 && immediate > 0 && delayed > 0 && duplicateWrites == 0,
                "Incomplete edit coverage");
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "PASS_PHYSICAL_EDIT_PROTECTION_ONLY");
        result.put("proofVersion", 1);
        result.put("pid", ProcessHandle.current().pid());
        result.put("reload", reload);
        result.put("inputHash", hash);
        result.put("receiverBase", "NATIVE_PHYSICAL_NOT_C");
        result.put("playerInput", "SIMULATED_NATIVE_BLOCK_MUTATION");
        result.put("hostSeed", level.getSeed());
        result.put("receivers", rows.size());
        result.put("accepted", accepted);
        result.put("ownedWrites", ownWrites);
        result.put("observedMutations", observations);
        result.put("protectedPositions", protectedPositions);
        result.put("immediateReceivers", immediate);
        result.put("delayedReceivers", delayed);
        result.put("hives", hives);
        result.put("blockTicks", blockTicks);
        result.put("duplicateWrites", duplicateWrites);
        result.put("workerRejected", workerRejected);
        result.put("exceptionPropagated", exceptionPropagated);
        result.put("nativeGeneratedFeatures", GENERATED.get());
        result.put("experimentalGenerationCalls", 0);
        result.put("bindings", FeatureCEditTracker.bindingCount());
        result.put("dynamicConflictReplacement", "NOT_EVALUATED");
        result.put("crashAtomicity", "NOT_EVALUATED");
        result.put("productionSwitch", false);
        Files.writeString(
                root.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
    }

    private static FeatureContributionJournal journal(
            ServerLevel level,
            ChunkPos pos,
            List<TreeContribution> batches,
            Set<BlockPos> positions) {
        var result =
                new FeatureContributionJournal(
                        batches.getFirst().key().scope(),
                        pos,
                        level.getSeed(),
                        level.getMinY(),
                        level.getHeight());
        batches.forEach(result::publish);
        result.supplyBase(
                positions.stream()
                        .map(p -> FeatureCPhysicalBatch.read(loaded(level, pos), p, level))
                        .toList());
        return result;
    }

    private static ChunkPos owner(CompoundTag row) {
        return new ChunkPos(row.getInt("x").orElseThrow(), row.getInt("z").orElseThrow());
    }

    private static LevelChunk loaded(ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        require(chunk != null, "Receiver unexpectedly unloaded");
        return chunk;
    }

    private static void require(boolean test, String message) {
        if (!test) throw new IllegalStateException(message);
    }
}
