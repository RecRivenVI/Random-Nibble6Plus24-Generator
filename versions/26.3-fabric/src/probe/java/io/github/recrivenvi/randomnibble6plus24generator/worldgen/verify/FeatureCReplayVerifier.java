package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** Frozen-input receiving proof, independent of the still-failing regeneration parity gate. */
public final class FeatureCReplayVerifier {
    private FeatureCReplayVerifier() {}

    public static void run(MinecraftServer server, Path root) throws Exception {
        Files.createDirectories(root);
        long started = System.nanoTime();
        var host = server.overworld();
        var physical = FeatureCFrontierVerifier.physical(host);
        long beforeGeneration = FeatureCFlowVerifier.generationCount();
        boolean producer = root.getFileName().toString().equals("host_0");
        Path source = producer ? root : root.resolveSibling("host_0");
        FeatureCFrontierVerifier.Prepared prepared = null;
        if (producer) {
            prepared = FeatureCFrontierVerifier.prepare(server, root);
            capture(server, root, prepared);
        }
        long beforeReplay = FeatureCFlowVerifier.generationCount();
        require(producer || beforeReplay == beforeGeneration, "Consumer invoked generation");
        Path input = source.resolve("inputs.nbt");
        var inputHash = hash(input);
        var bundle = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
        require(bundle.getInt("schema").orElseThrow() == 1, "Unknown replay proof schema");
        require(
                bundle.getLong("master").orElseThrow() == FeatureCFrontierVerifier.MASTER,
                "Foreign replay master");
        var sourceRows = bundle.getList("targets").orElseThrow();
        var snapshotFiles = new LinkedHashMap<String, String>();
        var targetEvidence = new ArrayList<Object>();
        int rawEntities = 0;
        for (var entry : sourceRows) {
            var row = (CompoundTag) entry;
            var pos = position(row);
            require(row.getLong("seed").orElseThrow() == seed(pos), "Foreign C base seed");
            Path file = source.resolve(snapshotName(pos));
            var snapshot = FeatureStableSnapshot.read(file);
            require(
                    snapshot.chunkPos().equals(pos)
                            && snapshot.hash().equals(row.getString("hash").orElseThrow()),
                    "C base snapshot changed");
            var entities = row.getList("raw_entities").orElseThrow();
            require(entities.size() == snapshot.entityCount(), "Incomplete raw entity evidence");
            rawEntities += entities.size();
            snapshotFiles.put(file.getFileName().toString(), hash(file));
            targetEvidence.add(
                    Map.of(
                            "x",
                            pos.x(),
                            "z",
                            pos.z(),
                            "seed",
                            seed(pos),
                            "hash",
                            snapshot.hash(),
                            "rawEntities",
                            entities.size()));
        }
        int accepted = 0,
                rejected = 0,
                pending = 0,
                protectedCount = 0,
                pendingReceivers = 0,
                blockTicks = 0,
                fluidTicks = 0,
                post = 0,
                priorReloads = 0;
        var receiverEvidence = new ArrayList<Object>();
        for (var entry : bundle.getList("receivers").orElseThrow()) {
            var row = (CompoundTag) entry;
            var pos = position(row);
            var pendingTag = row.getCompound("pending").orElseThrow();
            var relevant = new ArrayList<TreeContribution>();
            for (var tag : pendingTag.getList("contributions").orElseThrow()) {
                var contribution = TreeContribution.decode((CompoundTag) tag);
                require(
                        contribution.key().seed() == seed(contribution.key().source())
                                && contribution.key().seed() != seed(pos),
                        "Source/receiver seed provenance differs");
                relevant.add(contribution);
            }
            var j = decode(pendingTag, pos);
            require(
                    j.project().incoming().isEmpty() && j.project().pendingPositions() > 0,
                    "Missing receiver must remain pending");
            Path pendingFile = root.resolve("pending-" + pos.x() + "-" + pos.z() + ".nbt");
            NbtIo.writeCompressed(j.encode(), pendingFile);
            j = decode(NbtIo.readCompressed(pendingFile, NbtAccounter.unlimitedHeap()), pos);
            var baseOrder =
                    TreeContribution.Cell.CODEC
                            .listOf()
                            .parse(NbtOps.INSTANCE, row.getList("base").orElseThrow())
                            .getOrThrow();
            Map<BlockPos, TreeContribution.Cell> base = new HashMap<>();
            baseOrder.forEach(
                    cell -> {
                        require(
                                base.putIfAbsent(cell.pos(), cell) == null,
                                "Duplicate receiver base");
                    });
            if (!row.getBoolean("generated").orElseThrow()) {
                require(baseOrder.isEmpty(), "Absent receiver acquired a placeholder");
                pendingReceivers++;
            }
            for (var cell : baseOrder) {
                j.supplyBase(List.of(cell));
                // Every intermediate state must survive a process-style serialization boundary.
                j = decode(j.encode(), pos);
            }
            var projected = j.project();
            FeatureCFrontierVerifier.verifyIndependentProjection(
                    projected, relevant, base, Map.of(), pos);
            accepted += projected.incoming().size();
            rejected += projected.rejectedPositions();
            pending += projected.pendingPositions();
            blockTicks += projected.blockTicks().size();
            fluidTicks += projected.fluidTicks().size();
            post += projected.postProcessing().size();

            var reverse = newJournal(pos, pendingTag);
            reverse.supplyBase(baseOrder);
            var backwards = new ArrayList<>(relevant);
            Collections.reverse(backwards);
            backwards.forEach(reverse::publish);
            require(reverse.encode().equals(j.encode()), "Arrival/readiness order changed result");
            try (var pool = Executors.newFixedThreadPool(3)) {
                var parallel = newJournal(pos, pendingTag);
                var jobs = new ArrayList<Future<?>>();
                for (var cell : baseOrder)
                    jobs.add(pool.submit(() -> parallel.supplyBase(List.of(cell))));
                for (int repeat = 0; repeat < 4; repeat++)
                    for (var contribution : backwards)
                        jobs.add(pool.submit(() -> parallel.publish(contribution)));
                for (var job : jobs) job.get();
                require(
                        parallel.encode().equals(j.encode()),
                        "Concurrent replay is not idempotent");
            }
            if (!projected.incoming().isEmpty()) {
                var p =
                        projected.incoming().keySet().stream()
                                .min(Comparator.comparingLong(BlockPos::asLong))
                                .orElseThrow();
                var edit =
                        new TreeContribution.Cell(
                                p, Blocks.AIR.defaultBlockState(), Optional.empty());
                j.protect(edit);
                protectedCount++;
                FeatureCFrontierVerifier.verifyIndependentProjection(
                        j.project(), relevant, base, Map.of(p, edit), pos);
                require(!j.project().incoming().containsKey(p), "Protected dug air refilled");
            }
            Path file = root.resolve("receiver-" + pos.x() + "-" + pos.z() + ".nbt");
            NbtIo.writeCompressed(j.encode(), file);
            var restored = decode(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), pos);
            require(restored.project().equals(j.project()), "Disk reload changed projection");
            for (var contribution : relevant)
                require(!restored.publish(contribution), "Disk reload lost dedup state");
            for (var contribution : relevant) restored.remove(contribution.key());
            var removed = restored.project();
            require(
                    removed.incoming().isEmpty()
                            && removed.blockTicks().isEmpty()
                            && removed.fluidTicks().isEmpty()
                            && removed.postProcessing().isEmpty()
                            && removed.protectedEdits().equals(j.project().protectedEdits()),
                    "Removal left effects or erased protection");
            restored = decode(restored.encode(), pos);
            for (var contribution : relevant) restored.publish(contribution);
            require(restored.encode().equals(j.encode()), "Republish changed receiver state");
            if (!producer) {
                var previous =
                        NbtIo.readCompressed(
                                source.resolve(file.getFileName()), NbtAccounter.unlimitedHeap());
                require(
                        previous.equals(j.encode())
                                && decode(previous, pos).project().equals(j.project()),
                        "Frozen-input cross-JVM replay differs");
                priorReloads++;
            }
            receiverEvidence.add(
                    Map.of(
                            "x",
                            pos.x(),
                            "z",
                            pos.z(),
                            "seed",
                            seed(pos),
                            "generated",
                            !baseOrder.isEmpty(),
                            "incoming",
                            projected.incoming().size(),
                            "rejected",
                            projected.rejectedPositions(),
                            "pending",
                            projected.pendingPositions(),
                            "protected",
                            j.project().protectedEdits().size()));
        }
        if (prepared != null) {
            for (var result : prepared.generated().values()) {
                var after =
                        FeatureStableSnapshot.capture(
                                FeatureCFrontierVerifier.SCOPE.dimension(),
                                result.target(),
                                host.registryAccess());
                require(
                        after.hash().equals(result.snapshot().hash())
                                && after.rawEntityNbt().equals(result.snapshot().rawEntityNbt()),
                        "Replay mutated original C base or raw entity state");
            }
        }
        require(hash(input).equals(inputHash), "Replay mutated frozen input");
        for (var file : snapshotFiles.entrySet())
            require(
                    hash(source.resolve(file.getKey())).equals(file.getValue()),
                    "Replay changed snapshot bytes");
        require(
                accepted > 0 && rejected > 0 && protectedCount > 0 && rawEntities > 0,
                "Missing natural acceptance/rejection or full entity preservation coverage");
        require(
                GenerationContextRegistry.bindingCount() == 0
                        && physical.equals(FeatureCFrontierVerifier.physical(host)),
                "Replay leaked session or physical state");
        require(FeatureCFlowVerifier.generationCount() == beforeReplay, "Replay invoked worldgen");
        var summary = new LinkedHashMap<String, Object>();
        summary.put("status", "PASS_FROZEN_INPUT_REPLAY_ONLY");
        summary.put("proofVersion", 1);
        summary.put("generationParity", "NOT_EVALUATED");
        summary.put("pid", ProcessHandle.current().pid());
        summary.put("hostSeed", host.getSeed());
        summary.put("producer", producer);
        summary.put("generatedTargets", producer ? prepared.generated().size() : 0);
        summary.put("preparationGenerationCalls", beforeReplay - beforeGeneration);
        summary.put("replayGenerationCalls", FeatureCFlowVerifier.generationCount() - beforeReplay);
        summary.put("scanCount", bundle.getInt("scan_count").orElseThrow());
        summary.put("sources", bundle.getInt("sources").orElseThrow());
        summary.put("packages", bundle.getInt("packages").orElseThrow());
        summary.put("inputHash", inputHash);
        summary.put("targets", targetEvidence);
        summary.put("receivers", receiverEvidence);
        summary.put("rawEntities", rawEntities);
        summary.put("accepted", accepted);
        summary.put("rejected", rejected);
        summary.put("pending", pending);
        summary.put("pendingReceivers", pendingReceivers);
        summary.put("protected", protectedCount);
        summary.put("priorJvmReloads", priorReloads);
        summary.put(
                "effects",
                Map.of("blockTicks", blockTicks, "fluidTicks", fluidTicks, "postProcessing", post));
        summary.put("physicalApplications", 0);
        summary.put("physicalUnchanged", true);
        summary.put("bindings", 0);
        summary.put("elapsedNanos", System.nanoTime() - started);
        Files.writeString(
                root.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(summary) + "\n");
    }

    private static void capture(
            MinecraftServer server, Path root, FeatureCFrontierVerifier.Prepared prepared)
            throws Exception {
        var bundle = new CompoundTag();
        bundle.putInt("schema", 1);
        bundle.putLong("master", FeatureCFrontierVerifier.MASTER);
        bundle.putInt("scan_count", prepared.scan().size());
        bundle.putInt("sources", prepared.sources().size());
        bundle.putInt("packages", prepared.packages().size());
        var targets = new ListTag();
        for (var entry : prepared.generated().entrySet()) {
            var pos = entry.getKey();
            var result = entry.getValue();
            result.snapshot().write(root.resolve(snapshotName(pos)));
            var row = coordinates(pos);
            row.putString("hash", result.snapshot().hash());
            var entities = new ListTag();
            result.target().getEntities().forEach(entity -> entities.add(entity.copy()));
            row.put("raw_entities", entities);
            targets.add(row);
        }
        bundle.put("targets", targets);
        var receivers =
                new TreeSet<ChunkPos>(
                        Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x));
        prepared.packages()
                .forEach(b -> b.cells().forEach(c -> receivers.add(ChunkPos.containing(c.pos()))));
        var entries = new ListTag();
        for (var pos : receivers) {
            var relevant =
                    prepared.packages().stream()
                            .filter(
                                    b ->
                                            b.cells().stream()
                                                    .anyMatch(
                                                            c ->
                                                                    ChunkPos.containing(c.pos())
                                                                            .equals(pos)))
                            .toList();
            var first = relevant.getFirst();
            var journal =
                    new FeatureContributionJournal(
                            FeatureCFrontierVerifier.SCOPE,
                            pos,
                            seed(pos),
                            first.minY(),
                            first.height());
            relevant.forEach(journal::publish);
            var own = prepared.generated().get(pos);
            Map<BlockPos, TreeContribution.Cell> base = new HashMap<>();
            if (own != null) {
                relevant.forEach(
                        b ->
                                b.cells().stream()
                                        .filter(c -> ChunkPos.containing(c.pos()).equals(pos))
                                        .forEach(
                                                c ->
                                                        base.put(
                                                                c.pos(),
                                                                new TreeContribution.Cell(
                                                                        c.pos(),
                                                                        own.target()
                                                                                .getBlockState(
                                                                                        c.pos()),
                                                                        Optional.ofNullable(
                                                                                own.target()
                                                                                        .getBlockEntityNbtForSaving(
                                                                                                c
                                                                                                        .pos(),
                                                                                                server
                                                                                                        .registryAccess()))))));
            }
            var row = coordinates(pos);
            row.putBoolean("generated", own != null);
            row.put("pending", journal.encode());
            row.put(
                    "base",
                    TreeContribution.Cell.CODEC
                            .listOf()
                            .encodeStart(
                                    NbtOps.INSTANCE,
                                    base.values().stream()
                                            .sorted(Comparator.comparingLong(c -> c.pos().asLong()))
                                            .toList())
                            .getOrThrow());
            entries.add(row);
        }
        bundle.put("receivers", entries);
        NbtIo.writeCompressed(bundle, root.resolve("inputs.nbt"));
    }

    private static FeatureContributionJournal newJournal(ChunkPos pos, CompoundTag tag) {
        return new FeatureContributionJournal(
                FeatureCFrontierVerifier.SCOPE,
                pos,
                seed(pos),
                tag.getInt("min_y").orElseThrow(),
                tag.getInt("height").orElseThrow());
    }

    private static FeatureContributionJournal decode(CompoundTag tag, ChunkPos pos) {
        return FeatureContributionJournal.decode(
                tag, FeatureCFrontierVerifier.SCOPE, pos, seed(pos));
    }

    private static CompoundTag coordinates(ChunkPos pos) {
        var row = new CompoundTag();
        row.putInt("x", pos.x());
        row.putInt("z", pos.z());
        row.putLong("seed", seed(pos));
        return row;
    }

    private static ChunkPos position(CompoundTag row) {
        return new ChunkPos(row.getInt("x").orElseThrow(), row.getInt("z").orElseThrow());
    }

    private static String snapshotName(ChunkPos pos) {
        return "base-" + pos.x() + "-" + pos.z() + ".bin.gz";
    }

    private static long seed(ChunkPos pos) {
        return FeatureCFrontierVerifier.seed(pos);
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
}
