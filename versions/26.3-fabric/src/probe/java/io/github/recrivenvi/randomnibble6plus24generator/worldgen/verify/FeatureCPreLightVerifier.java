package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalPoiReconciler;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/** Disposable C patch through native disk loading and LIGHT, never a production C save. */
public final class FeatureCPreLightVerifier {
    private FeatureCPreLightVerifier() {}

    private static volatile ServerLevel activeLevel;
    private static final Set<ChunkPos> PATCH = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> STAGES = new ConcurrentHashMap<>();
    private static final Map<ChunkPos, Long> RELOAD_SEEDS = new ConcurrentHashMap<>();
    private static final Comparator<ChunkPos> ORDER =
            Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x);
    private static final ChunkPos CENTER = new ChunkPos(-5, -15);

    public static long structureSeed(ServerLevel level, ChunkPos pos, long vanillaSeed) {
        if (level != activeLevel || !PATCH.contains(pos)) return vanillaSeed;
        long local = FeatureCFrontierVerifier.seed(pos);
        RELOAD_SEEDS.put(pos, local);
        return local;
    }

    public static void stage(WorldGenContext context, ChunkStatus status, ChunkAccess chunk) {
        if (context.level() != activeLevel || GenerationContextRegistry.find(context).isPresent())
            return;
        require(status.isBefore(ChunkStatus.SPAWN), "Pre-light probe crossed into " + status);
        require(
                !status.isOrAfter(ChunkStatus.FEATURES)
                        || !chunk.getPersistedStatus().isBefore(ChunkStatus.FEATURES),
                "Native FEATURES would decorate the C patch: " + chunk.getPos());
        STAGES.merge(status.getName(), 1, Integer::sum);
    }

    public static void run(MinecraftServer server, Path root) throws Exception {
        Files.createDirectories(root);
        var level = server.overworld();
        require(!MosaicWorldIdentity.isMosaicWorld(server), "Probe must not modify a Mosaic save");
        require(activeLevel == null, "Another pre-light probe is active");
        STAGES.clear();
        RELOAD_SEEDS.clear();
        boolean recovery = Boolean.getBoolean("featurec.recovery");
        int variant = Integer.parseInt(root.getFileName().toString().substring("host_".length()));
        boolean reload = recovery ? variant > 0 : root.getFileName().toString().equals("host_1");
        Path first = reload ? root.resolveSibling("host_0") : root;
        long before = FeatureCFlowVerifier.generationCount();
        var lightStep = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.LIGHT);
        int radius = lightStep.getAccumulatedRadiusOf(ChunkStatus.FEATURES);
        require(radius >= 0 && radius <= 2, "Unaudited LIGHT dependency radius " + radius);
        var requested = square(CENTER, 1);
        var prepared = square(CENTER, 1 + radius);
        var manifest =
                reload
                        ? NbtIo.readCompressed(
                                first.resolve("manifest.nbt"), NbtAccounter.unlimitedHeap())
                        : new CompoundTag();
        var rows = new ListTag();
        int accepted = 0, pending = 0, effects = 0, generated = 0;
        FeatureCPreLightTransaction transaction = null;
        FeatureCPreLightTransaction.Recovery replay = null;
        boolean refusalVerified = false;
        activeLevel = level;
        PATCH.addAll(prepared);
        try {
            if (!reload) {
                if (recovery)
                    require(
                            server.saveEverything(true, true, true),
                            "Initial world identity save failed");
                var results = new TreeMap<ChunkPos, FeatureCFlowVerifier.Result>(ORDER);
                var batches = new ArrayList<TreeContribution>();
                for (var pos : prepared) {
                    require(
                            level.getChunkSource().chunkMap.read(pos).join().isEmpty(),
                            "Refusing to overwrite an existing chunk " + pos);
                    require(
                            level.getChunkSource()
                                            .chunkMap
                                            .getUpdatingChunkIfPresent(
                                                    ChunkPos.pack(pos.x(), pos.z()))
                                    == null,
                            "C target already has a physical holder");
                    System.out.println("C PRELIGHT prepare " + pos);
                    var result =
                            FeatureCFlowVerifier.generate(
                                    server,
                                    new FeatureBVerifier.Fixture(
                                            "patch-" + pos.x() + "-" + pos.z(),
                                            "overworld",
                                            FeatureCFrontierVerifier.seed(pos),
                                            pos.x(),
                                            pos.z()),
                                    "light",
                                    FeatureCFrontierVerifier.SCOPE);
                    require(
                            ((Number) result.trace().get("writers")).intValue() == 1,
                            "Not one complete C writer");
                    result.snapshot().write(root.resolve(name(pos) + "-base.bin.gz"));
                    results.put(pos, result);
                    batches.addAll(
                            result.batches().stream().filter(b -> !b.cells().isEmpty()).toList());
                    generated++;
                }
                require(
                        results.get(CENTER).batches().stream().anyMatch(b -> !b.cells().isEmpty()),
                        "Natural source fixture no longer crosses a boundary");
                var allBatches = new ListTag();
                batches.forEach(b -> allBatches.add(b.encode()));
                manifest.put("contributions", allBatches);
                for (var result : results.values()) {
                    var target = result.target();
                    var pos = target.getPos();
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
                    var journal =
                            new FeatureContributionJournal(
                                    FeatureCFrontierVerifier.SCOPE,
                                    pos,
                                    FeatureCFrontierVerifier.seed(pos),
                                    target.getMinY(),
                                    target.getHeight());
                    relevant.forEach(journal::publish);
                    var positions =
                            new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
                    relevant.forEach(
                            b ->
                                    b.cells().stream()
                                            .filter(c -> ChunkPos.containing(c.pos()).equals(pos))
                                            .forEach(c -> positions.add(c.pos())));
                    journal.supplyBase(
                            positions.stream()
                                    .map(
                                            p ->
                                                    new TreeContribution.Cell(
                                                            p,
                                                            target.getBlockState(p),
                                                            Optional.ofNullable(
                                                                    target
                                                                            .getBlockEntityNbtForSaving(
                                                                                    p,
                                                                                    level
                                                                                            .registryAccess()))))
                                    .toList());
                    var projection = journal.project();
                    require(projection.pendingPositions() == 0, "Missing prepared C base");
                    apply(target, projection);
                    accepted += projection.incoming().size();
                    effects += projection.blockTicks().size() + projection.fluidTicks().size();
                    var payload = SerializableChunkData.copyOf(level, target).write();
                    NbtIo.writeCompressed(payload, root.resolve(name(pos) + "-input.nbt"));
                    // The complete source C is preserved separately; never relabel it as V2
                    // Ordering 1/2.
                    FeatureStageSnapshot.capture(
                                    "minecraft:overworld", target, level.registryAccess())
                            .write(root.resolve(name(pos) + "-received.bin.gz"));
                    if (!recovery)
                        level.getChunkSource().chunkMap.write(pos, payload.copy()).join();
                    var row = new CompoundTag();
                    row.putInt("x", pos.x());
                    row.putInt("z", pos.z());
                    row.putLong("seed", FeatureCFrontierVerifier.seed(pos));
                    row.put("journal", journal.encode());
                    rows.add(row);
                }
                // No recursive generation of recipients beyond the explicit finite LIGHT patch.
                pending =
                        (int)
                                batches.stream()
                                        .flatMap(b -> b.cells().stream())
                                        .filter(c -> !PATCH.contains(ChunkPos.containing(c.pos())))
                                        .count();
                require(accepted > 0 && effects > 0, "No natural C-to-C reception");
                manifest.put("patch", rows);
                manifest.putLong("pid", ProcessHandle.current().pid());
                manifest.putInt("accepted", accepted);
                manifest.putInt("effects", effects);
                manifest.putInt("pending", pending);
                manifest.putInt("radius", radius);
                if (recovery) {
                    require(
                            FeatureCFlowVerifier.generationCount() - before == generated,
                            "Unexpected C preparation regeneration");
                    FeatureCPreLightTransaction.durable(first.resolve("manifest.nbt"), manifest);
                    transaction =
                            FeatureCPreLightTransaction.prepare(
                                    level, first.resolve("transaction.nbt"), rows, first);
                    int written = 0;
                    for (var pos : prepared.subList(0, prepared.size() / 2)) {
                        var payload =
                                NbtIo.readCompressed(
                                        first.resolve(name(pos) + "-input.nbt"),
                                        NbtAccounter.unlimitedHeap());
                        level.getChunkSource().chunkMap.write(pos, payload).join();
                        written++;
                    }
                    level.getChunkSource().chunkMap.synchronize(true).join();
                    interrupt(
                            root,
                            "PARTIAL_REGION_WRITE",
                            generated,
                            prepared.size(),
                            written,
                            0,
                            73,
                            false);
                }
                level.getChunkSource().chunkMap.synchronize(true).join();
            } else {
                require(
                        manifest.getLong("pid").orElseThrow() != ProcessHandle.current().pid(),
                        "Not a restart");
                require(manifest.getInt("radius").orElseThrow() == radius, "Dependencies changed");
                rows = manifest.getList("patch").orElseThrow();
                require(rows.size() == prepared.size(), "Patch manifest differs");
                accepted = manifest.getInt("accepted").orElseThrow();
                effects = manifest.getInt("effects").orElseThrow();
                pending = manifest.getInt("pending").orElseThrow();
                if (recovery) {
                    transaction =
                            FeatureCPreLightTransaction.open(
                                    level, first.resolve("transaction.nbt"));
                    if (variant == 1)
                        refusalVerified = verifyRecoveryRefusal(level, transaction, prepared);
                    replay = transaction.recover(level, prepared);
                    require(
                            variant < 3 || replay.alreadyCommitted(),
                            "Expected a committed reload");
                }
            }
            // Check disk/holder loading before allowing LIGHT; no feature generation is requested.
            for (var pos : prepared) {
                var chunk = request(level, pos, ChunkStatus.FEATURES);
                var expected =
                        NbtIo.readCompressed(
                                first.resolve(name(pos) + "-input.nbt"),
                                NbtAccounter.unlimitedHeap());
                persistedEqual(
                        expected, SerializableChunkData.copyOf(level, chunk).write(), root, pos);
                require(
                        RELOAD_SEEDS.get(pos) != null
                                && RELOAD_SEEDS.get(pos) == FeatureCFrontierVerifier.seed(pos),
                        "Wrong structure reload seed");
                require(
                        MosaicPhysicalPoiReconciler.expectedEntries(chunk)
                                .equals(MosaicPhysicalPoiReconciler.actualEntries(level, chunk)),
                        "POI differs from C sections");
            }
            // Native LIGHT tickets also promote FULL. This stage-bounded probe deliberately uses
            // the physical ChunkMap's native step after ordinary INITIALIZE_LIGHT requests,
            // without claiming that an end-to-end production C scheduler has been installed.
            for (var pos : prepared) request(level, pos, ChunkStatus.INITIALIZE_LIGHT);
            var lighted = new LinkedHashMap<ChunkPos, ChunkAccess>();
            for (var pos : requested) {
                var chunkMap = level.getChunkSource().chunkMap;
                int directRadius = lightStep.directDependencies().getRadius();
                var cache =
                        StaticCache2D.create(
                                pos.x(),
                                pos.z(),
                                directRadius,
                                (x, z) -> {
                                    var holder =
                                            chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(x, z));
                                    require(holder != null, "Missing real light neighbour");
                                    require(
                                            holder.getChunkIfPresentUnchecked(
                                                            ChunkStatus.INITIALIZE_LIGHT)
                                                    != null,
                                            "Light neighbour has not initialized");
                                    return (net.minecraft.server.level.GenerationChunkHolder)
                                            holder;
                                });
                var future =
                        chunkMap.applyStep(
                                chunkMap.getUpdatingChunkIfPresent(pos.pack()), lightStep, cache);
                level.getChunkSource().getLightEngine().tryScheduleUpdate();
                server.managedBlock(future::isDone);
                var chunk = future.join();
                require(
                        chunk instanceof ProtoChunk
                                && chunk.getPersistedStatus() == ChunkStatus.LIGHT
                                && chunk.isLightCorrect(),
                        "Native physical LIGHT incomplete");
                lighted.put(pos, chunk);
            }
            // Later neighbours propagate back into earlier chunks. Snapshot only after the whole
            // finite light workload, not at each first-completed chunk's transient boundary.
            for (var entry : lighted.entrySet()) {
                var pos = entry.getKey();
                var chunk = entry.getValue();
                var saved = SerializableChunkData.copyOf(level, chunk).write();
                var expected =
                        NbtIo.readCompressed(
                                first.resolve(name(pos) + "-input.nbt"),
                                NbtAccounter.unlimitedHeap());
                persistedEqual(expected, saved, root, pos);
                Path lightPath = first.resolve(name(pos) + "-light.nbt");
                if (reload && (!recovery || variant >= 2)) {
                    var old = NbtIo.readCompressed(lightPath, NbtAccounter.unlimitedHeap());
                    if (!lighting(old).equals(lighting(saved))) {
                        var difference = new CompoundTag();
                        difference.put("expected", lighting(old));
                        difference.put("actual", lighting(saved));
                        NbtIo.writeCompressed(difference, root.resolve("light-mismatch.nbt"));
                        throw new IllegalStateException("Light data changed after restart " + pos);
                    }
                } else NbtIo.writeCompressed(saved, lightPath);
            }
            require(server.saveEverything(true, true, true), "Pre-light physical save failed");
            if (recovery) {
                require(
                        FeatureCFlowVerifier.generationCount() - before == generated,
                        "Recovery regenerated C");
                require(transaction != null && replay != null, "Missing recovery transaction");
                if (variant == 1)
                    interrupt(
                            root,
                            "SAVED_LIGHT_BEFORE_COMMIT",
                            generated,
                            prepared.size(),
                            replay.written(),
                            requested.size(),
                            74,
                            refusalVerified);
                transaction.commit(level);
            }
            if (!reload) NbtIo.writeCompressed(manifest, first.resolve("manifest.nbt"));
            require(GenerationContextRegistry.bindingCount() == 0, "Context leaked");
            require(
                    FeatureCFlowVerifier.generationCount() - before == generated,
                    "Unexpected C regeneration");
            var summary = new LinkedHashMap<String, Object>();
            summary.put(
                    "status",
                    recovery ? "PASS_C_PRELIGHT_RECOVERY_ONLY" : "PASS_C_PHYSICAL_PRELIGHT_ONLY");
            summary.put("pid", ProcessHandle.current().pid());
            summary.put("reload", reload);
            summary.put("generated", generated);
            summary.put("prepared", prepared.size());
            summary.put("lightChunks", requested.size());
            summary.put("lightFeatureRadius", radius);
            summary.put("accepted", accepted);
            summary.put("effects", effects);
            summary.put("pendingCells", pending);
            summary.put("localSeedReads", RELOAD_SEEDS.size());
            summary.put("stages", STAGES);
            summary.put("bindings", GenerationContextRegistry.bindingCount());
            summary.put("productionSwitch", false);
            summary.put("fullOrSpawn", false);
            summary.put("dynamicReception", "NOT_EVALUATED");
            if (recovery) {
                summary.put("existingReceivers", replay.existing());
                summary.put("recoveryWrites", replay.written());
                summary.put("alreadyCommitted", replay.alreadyCommitted());
                summary.put("committed", transaction.committed());
                summary.put(
                        "worldPath",
                        server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                .toAbsolutePath()
                                .normalize()
                                .toString());
            }
            Files.writeString(
                    root.resolve("summary.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(summary) + "\n");
        } finally {
            activeLevel = null;
            PATCH.clear();
            STAGES.clear();
            RELOAD_SEEDS.clear();
        }
    }

    private static boolean verifyRecoveryRefusal(
            ServerLevel level, FeatureCPreLightTransaction transaction, List<ChunkPos> prepared) {
        var pos = prepared.get(prepared.size() / 2 - 1);
        var original = level.getChunkSource().chunkMap.read(pos).join().orElseThrow();
        var changed = original.copy();
        changed.putString("probe_validation_change", "must refuse overwrite");
        level.getChunkSource().chunkMap.write(pos, changed).join();
        level.getChunkSource().chunkMap.synchronize(true).join();
        try {
            try {
                transaction.recover(level, prepared);
                throw new AssertionError("Changed receiver accepted");
            } catch (IllegalStateException expected) {
                require(
                        expected.getMessage().contains("Refusing to overwrite"),
                        "Wrong recovery refusal");
            }
            for (var missing : prepared.subList(prepared.size() / 2, prepared.size()))
                require(
                        level.getChunkSource().chunkMap.read(missing).join().isEmpty(),
                        "Failed recovery partially wrote missing receivers");
            require(
                    level.getChunkSource().chunkMap.read(pos).join().orElseThrow().equals(changed),
                    "Refusal overwrote changed data");
            return true;
        } finally {
            // This is deliberately corrupted validation input; restore its exact original bytes.
            level.getChunkSource().chunkMap.write(pos, original).join();
            level.getChunkSource().chunkMap.synchronize(true).join();
        }
    }

    private static void interrupt(
            Path root,
            String point,
            int generated,
            int prepared,
            int written,
            int lightChunks,
            int exit,
            boolean refusalVerified)
            throws Exception {
        require(
                GenerationContextRegistry.bindingCount() == 0,
                "Context leaked before interruption");
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "INTERRUPTED_C_PRELIGHT_TRANSACTION");
        report.put("point", point);
        report.put("pid", ProcessHandle.current().pid());
        report.put("generated", generated);
        report.put("prepared", prepared);
        report.put("written", written);
        report.put("lightChunks", lightChunks);
        report.put("bindings", GenerationContextRegistry.bindingCount());
        report.put("exit", exit);
        report.put("refusalVerified", refusalVerified);
        report.put("stages", new TreeMap<>(STAGES));
        Path evidence = root.resolve("interruption.json");
        Files.writeString(
                evidence, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        try (var channel = java.nio.channels.FileChannel.open(evidence, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        System.out.println("Intentional C recovery interruption: " + point + ", exit=" + exit);
        System.out.flush();
        System.err.flush();
        Runtime.getRuntime().halt(exit);
        throw new AssertionError("JVM halt unexpectedly returned");
    }

    private static List<ChunkPos> square(ChunkPos center, int radius) {
        var result = new ArrayList<ChunkPos>();
        for (int z = -radius; z <= radius; z++)
            for (int x = -radius; x <= radius; x++)
                result.add(new ChunkPos(center.x() + x, center.z() + z));
        return List.copyOf(result);
    }

    private static void apply(ProtoChunk target, FeatureContributionJournal.Projection p) {
        p.incoming().values().stream()
                .sorted(Comparator.comparingLong(c -> c.pos().asLong()))
                .forEach(
                        c -> {
                            require(
                                    target.getBlockState(c.pos()).isAir(),
                                    "Receiver base is not air");
                            target.setBlockState(c.pos(), c.state(), 0);
                            c.blockEntity().ifPresent(nbt -> target.setBlockEntityNbt(nbt.copy()));
                        });
        // ProtoChunk containers own these ticks until the normal physical activation boundary.
        p.blockTicks().forEach(t -> target.getBlockTicks().schedule(t.unpack(0, 0)));
        p.fluidTicks().forEach(t -> target.getFluidTicks().schedule(t.unpack(0, 0)));
        p.postProcessing().forEach(target::markPosForPostProcessing);
    }

    private static ChunkAccess request(ServerLevel level, ChunkPos pos, ChunkStatus status) {
        var future = level.getChunkSource().getChunkFuture(pos.x(), pos.z(), status, true);
        level.getServer().managedBlock(future::isDone);
        var chunk = future.join().orElse(null);
        require(chunk != null, "Missing physical result " + pos);
        return chunk;
    }

    private static void persistedEqual(
            CompoundTag expected, CompoundTag actual, Path root, ChunkPos pos) throws Exception {
        // Explicit native lifecycle envelope is checked separately. No worldgen payload field is
        // ignored.
        var a = content(expected);
        var b = content(actual);
        if (!a.equals(b)) {
            var diff = new CompoundTag();
            diff.put("expected", a);
            diff.put("actual", b);
            NbtIo.writeCompressed(diff, root.resolve("mismatch-" + name(pos) + ".nbt"));
            Files.writeString(
                    root.resolve("mismatch-" + name(pos) + ".txt"),
                    NativeFeatureRepeatVerifier.differences(a, b).toString());
            throw new IllegalStateException("C persisted payload changed " + pos);
        }
    }

    static CompoundTag content(CompoundTag raw) {
        var copy = raw.copy();
        copy.remove("LastUpdate");
        copy.remove("Status");
        copy.remove("isLightOn");
        for (var tag : copy.getListOrEmpty("sections")) {
            var section = (CompoundTag) tag;
            section.remove("BlockLight");
            section.remove("SkyLight");
        }
        // Lighting may serialize empty outer light-only sections; they carry no worldgen data.
        copy.getListOrEmpty("sections")
                .removeIf(t -> ((CompoundTag) t).keySet().equals(Set.of("Y")));
        return copy;
    }

    static CompoundTag lighting(CompoundTag raw) {
        var result = new CompoundTag();
        var sections = new ListTag();
        for (var item : raw.getListOrEmpty("sections")) {
            var section = (CompoundTag) item;
            var values = new CompoundTag();
            values.put("Y", section.get("Y").copy());
            section.getByteArray("BlockLight").ifPresent(v -> values.putByteArray("BlockLight", v));
            section.getByteArray("SkyLight").ifPresent(v -> values.putByteArray("SkyLight", v));
            sections.add(values);
        }
        result.put("sections", sections);
        result.putBoolean("light", raw.getBooleanOr("isLightOn", false));
        return result;
    }

    private static String name(ChunkPos pos) {
        return "chunk_" + pos.x() + "_" + pos.z();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
