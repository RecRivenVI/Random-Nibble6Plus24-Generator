package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.TagValueInput;

/** Incremental FULL receiving mechanics on genuine native bases, not a playable C world. */
public final class FeatureCAppendVerifier {
    private FeatureCAppendVerifier() {}

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
        int variant = Integer.parseInt(root.getFileName().toString().substring(5));
        boolean reload = variant % 2 == 1, reverse = variant >= 2;
        Path first = root.resolveSibling(reverse ? "host_2" : "host_0");
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
        for (var item :
                NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap())
                        .getList("batches")
                        .orElseThrow()) batches.add(TreeContribution.decode((CompoundTag) item));
        require(!batches.isEmpty(), "Missing genuine tree input");
        var scope = batches.getFirst().key().scope();
        var saved =
                reload
                        ? NbtIo.readCompressed(
                                first.resolve("append.nbt"), NbtAccounter.unlimitedHeap())
                        : new CompoundTag();
        var rows = reload ? saved.getList("receivers").orElseThrow() : new ListTag();
        var receivers =
                new TreeSet<ChunkPos>(
                        Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x));
        if (reload) for (var item : rows) receivers.add(owner((CompoundTag) item));
        else
            batches.forEach(
                    b -> b.cells().forEach(c -> receivers.add(ChunkPos.containing(c.pos()))));
        long callsBefore = FeatureCFlowVerifier.generationCount();
        int protectedBlockEntities = 0;
        int accepted = 0,
                lateBatches = 0,
                conflicts = 0,
                duplicateWrites = 0,
                protectedEdits = 0,
                hives = 0,
                rawTicks = 0;
        boolean workerRejected = false, foreignRejected = false;
        var records = new LinkedHashMap<ChunkPos, FeatureCAppendReceiver>();
        var diagnostics = new ArrayList<Object>();
        observing = true;
        try {
            for (var pos : receivers) FeatureCPhysicalVerifier.loadHalo(level, pos);
            if (reload) {
                require(saved.getString("input_hash").orElseThrow().equals(hash), "Input changed");
                require(
                        saved.getLong("pid").orElseThrow() != ProcessHandle.current().pid(),
                        "Not an independent JVM");
                require(GENERATED.get() == 0, "Reload regenerated FEATURES");
                for (var item : rows) {
                    var row = (CompoundTag) item;
                    var pos = owner(row);
                    var chunk = loaded(level, pos);
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
                    var receiver =
                            FeatureCAppendReceiver.restore(
                                    level, pos, scope, row.getCompound("receipt").orElseThrow());
                    records.put(pos, receiver);
                    var before = SerializableChunkData.copyOf(level, chunk).write();
                    for (var batch : relevant(batches, pos))
                        require(!receiver.offer(batch), "Duplicate lost on reload");
                    var replay = receiver.flush();
                    duplicateWrites += replay.writes();
                    require(
                            before.equals(SerializableChunkData.copyOf(level, chunk).write()),
                            "Reload reactivated physical data");
                    require(
                            replay.status().name().equals(row.getString("status").orElseThrow()),
                            "Unresolved conflict was relabelled ready");
                    if (replay.status() == FeatureCAppendReceiver.Status.PENDING_CONFLICT)
                        conflicts++;
                    var edited = BlockPos.of(row.getLong("edited").orElseThrow());
                    require(
                            receiver.protects(edited) && chunk.getBlockState(edited).isAir(),
                            "Edit protection lost");
                    for (long packed : row.getLongArray("edited_be").orElseThrow()) {
                        require(
                                receiver.protects(BlockPos.of(packed)),
                                "BE protection lost on reload");
                        protectedBlockEntities++;
                    }
                    protectedEdits++;
                }
            } else {
                for (var pos : receivers) {
                    var relevant = new ArrayList<>(relevant(batches, pos));
                    relevant.sort(Comparator.comparing(TreeContribution::key));
                    if (reverse) Collections.reverse(relevant);
                    var positions = positions(relevant, pos);
                    var chunk = loaded(level, pos);
                    var preview =
                            new FeatureContributionJournal(
                                    scope,
                                    pos,
                                    level.getSeed(),
                                    level.getMinY(),
                                    level.getHeight());
                    relevant.forEach(preview::publish);
                    preview.supplyBase(
                            positions.stream()
                                    .map(p -> FeatureCPhysicalBatch.read(chunk, p, level))
                                    .toList());
                    var editable =
                            preview.project().incoming().values().stream()
                                    .filter(c -> !c.state().hasBlockEntity())
                                    .map(TreeContribution.Cell::pos)
                                    .min(Comparator.comparingLong(BlockPos::asLong));
                    if (editable.isEmpty()) continue;
                    var receiver = FeatureCAppendReceiver.open(level, pos, scope);
                    records.put(pos, receiver);
                    var edited = editable.orElseThrow();
                    require(
                            level.setBlock(
                                    edited, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL),
                            "Placement failed");
                    require(
                            level.setBlock(
                                    edited, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL),
                            "Dig failed");
                    if (!workerRejected) {
                        workerRejected =
                                CompletableFuture.supplyAsync(
                                                () -> {
                                                    try {
                                                        receiver.flush();
                                                        return false;
                                                    } catch (IllegalStateException expected) {
                                                        return expected.getMessage()
                                                                .contains("server thread");
                                                    }
                                                })
                                        .get();
                        require(workerRejected, "Worker mutated physical receiver");
                    }
                    for (var batch : relevant) {
                        var before = SerializableChunkData.copyOf(level, chunk).write();
                        int loads = level.getChunkSource().getLoadedChunksCount();
                        var metadata = FeatureCPhysicalVerifier.metadata(chunk, level);
                        require(
                                CompletableFuture.supplyAsync(() -> receiver.offer(batch)).get(),
                                "New native packet treated as duplicate");
                        int oldBatches = receiver.batches();
                        var result = receiver.flush();
                        accepted += result.writes();
                        if (result.writes() > 0 && oldBatches > 0) lateBatches++;
                        require(
                                level.getChunkSource().getLoadedChunksCount() == loads,
                                "Append loaded chunks");
                        require(
                                metadata.equals(FeatureCPhysicalVerifier.metadata(chunk, level)),
                                "Append changed structure/biomes");
                        if (result.status() == FeatureCAppendReceiver.Status.PENDING_CONFLICT) {
                            require(
                                    before.equals(
                                            SerializableChunkData.copyOf(level, chunk).write()),
                                    "Conflict partially wrote physical data");
                            conflicts++;
                            var p = result.conflicts().getFirst();
                            var receipt = receiver.receipt();
                            var desired =
                                    FeatureContributionJournal.decode(
                                                    receipt.getCompound("pending").orElseThrow(),
                                                    scope,
                                                    pos,
                                                    level.getSeed())
                                            .project();
                            diagnostics.add(
                                    Map.of(
                                            "receiver",
                                            pos.toString(),
                                            "position",
                                            p.toString(),
                                            "physical",
                                            chunk.getBlockState(p).toString(),
                                            "preferred",
                                            String.valueOf(desired.incoming().get(p)),
                                            "conflictPositions",
                                            result.conflicts().size(),
                                            "source",
                                            batch.key()));
                        } else
                            require(
                                    result.status() == FeatureCAppendReceiver.Status.READY,
                                    "Unsupported/unfinished append: " + result.status());
                        before = SerializableChunkData.copyOf(level, chunk).write();
                        require(!receiver.offer(batch), "Duplicate not idempotent");
                        duplicateWrites += receiver.flush().writes();
                        require(
                                before.equals(SerializableChunkData.copyOf(level, chunk).write()),
                                "Duplicate mutated block/BE/ticks");
                    }
                    require(
                            receiver.protects(edited) && chunk.getBlockState(edited).isAir(),
                            "Late append refilled edited air");
                    protectedEdits++;
                    require(
                            receiver.ownedWrites() == receiver.writes(),
                            "Contribution writes not precisely exempted");
                    var receipt = receiver.receipt();
                    var applied =
                            FeatureContributionJournal.decode(
                                            receipt.getCompound("applied").orElseThrow(),
                                            scope,
                                            pos,
                                            level.getSeed())
                                    .project();
                    var editedBlockEntities = new ArrayList<BlockPos>();
                    for (var cell : applied.incoming().values())
                        if (cell.blockEntity().isPresent()) {
                            var be = chunk.getBlockEntity(cell.pos());
                            require(
                                    be != null
                                            && be.saveWithFullMetadata(level.registryAccess())
                                                    .equals(cell.blockEntity().orElseThrow()),
                                    "Hive payload differs");
                            hives++;
                            var modified = be.saveWithFullMetadata(level.registryAccess());
                            var bees = modified.getList("bees").orElseThrow();
                            require(!bees.isEmpty(), "No real bee data to protect");
                            var bee = (CompoundTag) bees.getFirst();
                            bee.putInt(
                                    "ticks_in_hive", bee.getInt("ticks_in_hive").orElseThrow() + 1);
                            var problems = new ProblemReporter.Collector();
                            be.loadWithComponents(
                                    TagValueInput.create(
                                            problems, level.registryAccess(), modified));
                            require(problems.isEmpty(), "Invalid modified bee data");
                            be.setChanged();
                            require(
                                    be.saveWithFullMetadata(level.registryAccess())
                                            .equals(modified),
                                    "BE edit did not round-trip");
                            var beforeReplay = SerializableChunkData.copyOf(level, chunk).write();
                            duplicateWrites += receiver.flush().writes();
                            require(
                                    receiver.protects(cell.pos())
                                            && beforeReplay.equals(
                                                    SerializableChunkData.copyOf(level, chunk)
                                                            .write()),
                                    "Replay overwrote modified BE contents");
                            editedBlockEntities.add(cell.pos());
                            protectedBlockEntities++;
                        }
                    rawTicks += applied.blockTicks().size();
                    var row = new CompoundTag();
                    row.putInt("x", pos.x());
                    row.putInt("z", pos.z());
                    row.putLong("edited", edited.asLong());
                    row.putLongArray(
                            "edited_be",
                            editedBlockEntities.stream().mapToLong(BlockPos::asLong).toArray());
                    row.putLongArray(
                            "positions", positions.stream().mapToLong(BlockPos::asLong).toArray());
                    row.putString("status", receiver.receipt().getString("status").orElseThrow());
                    row.put("receipt", receiver.receipt());
                    rows.add(row);
                    var foreign = receipt.copy();
                    foreign.putLong("seed", level.getSeed() + 1);
                    try {
                        FeatureCAppendReceiver.restore(level, pos, scope, foreign);
                        throw new AssertionError("Foreign receipt accepted");
                    } catch (IllegalArgumentException expected) {
                        foreignRejected = true;
                    }
                }
                require(
                        accepted > 0 && lateBatches > 0 && protectedEdits > 0,
                        "Missing genuine late append positives");
                require(!reverse || conflicts > 0, "No genuine earlier-winner conflict reproduced");
                require(reverse || conflicts == 0, "Sorted monotone input unexpectedly conflicted");
            }
            for (var item : rows) {
                var row = (CompoundTag) item;
                var pos = owner(row);
                var chunk = loaded(level, pos);
                var light = level.getChunkSource().getLightEngine();
                var future = light.waitForPendingTasks(pos.x(), pos.z());
                light.tryScheduleUpdate();
                server.managedBlock(future::isDone);
                future.join();
                require(chunk.isLightCorrect(), "Append light not ready");
                FeatureCPhysicalVerifier.verifyHeights(chunk);
                var lightValues = new ListTag();
                for (long packed : row.getLongArray("positions").orElseThrow()) {
                    var p = BlockPos.of(packed);
                    var value = new CompoundTag();
                    value.putLong("pos", packed);
                    value.putInt("sky", light.getLayerListener(LightLayer.SKY).getLightValue(p));
                    value.putInt(
                            "block", light.getLayerListener(LightLayer.BLOCK).getLightValue(p));
                    lightValues.add(value);
                }
                if (reload)
                    require(
                            lightValues.equals(row.getList("light").orElseThrow()),
                            "Append light differs on reload");
                else row.put("light", lightValues);
                if (!reload) {
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
                    row.put("receipt", records.get(pos).receipt());
                }
            }
            require(server.saveEverything(true, true, true), "Append save failed");
            if (!reload) {
                saved.putLong("pid", ProcessHandle.current().pid());
                saved.putString("input_hash", hash);
                saved.put("receivers", rows);
                NbtIo.writeCompressed(saved, root.resolve("append.nbt"));
            }
        } finally {
            observing = false;
            records.values().forEach(FeatureCAppendReceiver::close);
        }
        require(
                FeatureCEditTracker.bindingCount() == 0
                        && GenerationContextRegistry.bindingCount() == 0,
                "Context leaked");
        require(
                FeatureCFlowVerifier.generationCount() == callsBefore && duplicateWrites == 0,
                "Append regenerated C or duplicated writes");
        var summary = new LinkedHashMap<String, Object>();
        summary.put("status", "PASS_MONOTONE_APPEND_ONLY");
        summary.put("proofVersion", 1);
        summary.put("pid", ProcessHandle.current().pid());
        summary.put("reload", reload);
        summary.put("reverse", reverse);
        summary.put("inputHash", hash);
        summary.put("receiverBase", "NATIVE_PHYSICAL_NOT_C");
        summary.put("hostSeed", level.getSeed());
        summary.put("inputBatches", batches.size());
        summary.put("receivers", rows.size());
        summary.put("accepted", accepted);
        summary.put("lateBatches", lateBatches);
        summary.put("conflictChecks", conflicts);
        summary.put("duplicateWrites", duplicateWrites);
        summary.put("protectedEdits", protectedEdits);
        summary.put("hives", hives);
        summary.put("protectedBlockEntities", protectedBlockEntities);
        summary.put("heightColumnsVerified", rows.size() * 256 * 4);
        summary.put("lightReloadEqual", reload);
        summary.put("rawBlockTicks", rawTicks);
        summary.put("workerRejected", workerRejected);
        summary.put("foreignRejected", foreignRejected);
        summary.put("nativeGeneratedFeatures", GENERATED.get());
        summary.put("experimentalGenerationCalls", 0);
        summary.put("bindings", FeatureCEditTracker.bindingCount());
        summary.put("conflictReplacement", "PENDING_NOT_IMPLEMENTED");
        summary.put("runtimeAtomicity", "NOT_EVALUATED");
        summary.put("productionSwitch", false);
        Files.writeString(
                root.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(summary) + "\n");
        Files.writeString(
                root.resolve("conflicts.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(diagnostics) + "\n");
    }

    private static List<TreeContribution> relevant(List<TreeContribution> batches, ChunkPos pos) {
        return batches.stream()
                .filter(
                        b ->
                                b.cells().stream()
                                        .anyMatch(c -> ChunkPos.containing(c.pos()).equals(pos)))
                .toList();
    }

    private static Set<BlockPos> positions(List<TreeContribution> batches, ChunkPos pos) {
        var p = new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
        batches.forEach(
                b ->
                        b.cells().stream()
                                .filter(c -> ChunkPos.containing(c.pos()).equals(pos))
                                .forEach(c -> p.add(c.pos())));
        return p;
    }

    private static ChunkPos owner(CompoundTag row) {
        return new ChunkPos(row.getInt("x").orElseThrow(), row.getInt("z").orElseThrow());
    }

    private static LevelChunk loaded(ServerLevel level, ChunkPos pos) {
        var c = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        require(c != null, "Receiver unloaded");
        return c;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
