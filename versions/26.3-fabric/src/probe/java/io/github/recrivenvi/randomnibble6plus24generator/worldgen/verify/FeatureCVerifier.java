package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;

/** C tree kernel and detached receiving proof. Does not activate a C production world. */
public final class FeatureCVerifier {
    private FeatureCVerifier() {}

    private static final TreeContribution.Scope SCOPE =
            new TreeContribution.Scope(
                    "feature-c-kernel-world", "minecraft:overworld", "26.3-c-tree-candidate-2");

    private static void write(Path p, Object value) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }

    public static void run(MinecraftServer server) {
        Path base =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                Boolean.getBoolean("featurec.append")
                                        ? "append"
                                        : Boolean.getBoolean("featurec.recovery")
                                                ? "recovery"
                                                : Boolean.getBoolean("featurec.editProtection")
                                                        ? "edit_protection"
                                                        : Boolean.getBoolean("featurec.preLight")
                                                                ? "prelight"
                                                                : Boolean.getBoolean(
                                                                                "featurec.physicalHives")
                                                                        ? "physical_hives"
                                                                        : Boolean.getBoolean(
                                                                                        "featurec.acceptedShulker")
                                                                                ? "accepted_shulker"
                                                                                : Boolean
                                                                                                .getBoolean(
                                                                                                        "featurec.nativeShulker")
                                                                                        ? "native_shulker"
                                                                                        : Boolean
                                                                                                        .getBoolean(
                                                                                                                "featurec.acceptedFrontier")
                                                                                                ? "accepted_frontier"
                                                                                                : Boolean
                                                                                                                .getBoolean(
                                                                                                                        "featurec.physical")
                                                                                                        ? "physical"
                                                                                                        : Boolean
                                                                                                                        .getBoolean(
                                                                                                                                "featurec.nativeRepeat")
                                                                                                                ? "native_repeat"
                                                                                                                : Boolean
                                                                                                                                .getBoolean(
                                                                                                                                        "featurec.replay")
                                                                                                                        ? "replay"
                                                                                                                        : Boolean
                                                                                                                                        .getBoolean(
                                                                                                                                                "featurec.frontier")
                                                                                                                                ? "frontier"
                                                                                                                                : Boolean
                                                                                                                                                .getBoolean(
                                                                                                                                                        "featurec.flow")
                                                                                                                                        ? "flow"
                                                                                                                                        : "current")
                        .resolve(System.getProperty("validation.target"));
        try {
            if (Boolean.getBoolean("featurec.export")) {
                var launch = new LinkedHashMap<String, Object>();
                launch.put("java", ProcessHandle.current().info().command().orElseThrow());
                launch.put("directory", System.getProperty("user.dir"));
                launch.put("classpath", System.getProperty("java.class.path"));
                launch.put(
                        "jvmArguments",
                        java.lang.management.ManagementFactory.getRuntimeMXBean()
                                .getInputArguments()
                                .stream()
                                .filter(s -> !s.startsWith("-Dfeaturec."))
                                .toList());
                var command = System.getProperty("sun.java.command").split(" ");
                launch.put("main", command[0]);
                launch.put("gameArguments", Arrays.copyOfRange(command, 1, command.length));
                if (Boolean.getBoolean("featurec.nativeRepeat")
                        || Boolean.getBoolean("featurec.nativeShulker"))
                    launch.put("nativeFixture", NativeFeatureRepeatVerifier.fixture());
                write(base.resolve("launcher.json"), launch);
            } else if (Boolean.getBoolean("featurec.append")) {
                FeatureCAppendVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.editProtection")) {
                FeatureCEditProtectionVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.preLight")
                    || Boolean.getBoolean("featurec.recovery")) {
                FeatureCPreLightVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.acceptedShulker")) {
                FeatureCShulkerVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.acceptedFrontier")) {
                FeatureCFrontierVerifier.run(
                        server,
                        base.resolve(System.getProperty("featurec.variant", "host_0")),
                        true);
            } else if (Boolean.getBoolean("featurec.physical")) {
                FeatureCPhysicalVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.nativeRepeat")
                    || Boolean.getBoolean("featurec.nativeShulker")) {
                NativeFeatureRepeatVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.replay")) {
                FeatureCReplayVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.frontier")) {
                FeatureCFrontierVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else if (Boolean.getBoolean("featurec.flow")) {
                FeatureCFlowVerifier.run(
                        server, base.resolve(System.getProperty("featurec.variant", "host_0")));
            } else {
                Path root = base.resolve(System.getProperty("featurec.variant", "host_0"));
                Files.createDirectories(root);
                var host = server.overworld();
                int loaded = host.getChunkSource().getLoadedChunksCount();
                int blockTicks = host.getBlockTicks().count(),
                        fluidTicks = host.getFluidTicks().count();
                long entities =
                        java.util.stream.StreamSupport.stream(
                                        host.getAllEntities().spliterator(), false)
                                .count();
                var batches = new ArrayList<TreeContribution>();
                var kernelEvidence = new ArrayList<Object>();
                for (int fixture = 0; fixture < 2; fixture++)
                    kernel(
                            host,
                            new ChunkPos(-12 + fixture, -12),
                            fixture,
                            batches,
                            kernelEvidence);
                if (batches.isEmpty()
                        || batches.stream()
                                .flatMap(b -> b.cells().stream())
                                .noneMatch(c -> c.blockEntity().isPresent()))
                    throw new IllegalStateException("No real crossing tree / hive positive");
                var raw = new ListTag();
                batches.forEach(b -> raw.add(b.encode()));
                var saved = new CompoundTag();
                saved.put("batches", raw);
                NbtIo.writeCompressed(saved, root.resolve("native-tree-contributions.nbt"));
                if (root.getFileName().toString().equals("host_1")) {
                    var previous =
                            NbtIo.readCompressed(
                                    base.resolve("host_0/native-tree-contributions.nbt"),
                                    NbtAccounter.unlimitedHeap());
                    if (!previous.equals(saved))
                        throw new IllegalStateException(
                                "Raw tree contributions differ across host JVMs");
                }
                var receiving = receive(host, root, batches);
                if (GenerationContextRegistry.bindingCount() != 0
                        || loaded != host.getChunkSource().getLoadedChunksCount()
                        || blockTicks != host.getBlockTicks().count()
                        || fluidTicks != host.getFluidTicks().count()
                        || entities
                                != java.util.stream.StreamSupport.stream(
                                                host.getAllEntities().spliterator(), false)
                                        .count())
                    throw new IllegalStateException("C kernel physical pollution / context leak");
                write(
                        root.resolve("summary.json"),
                        Map.of(
                                "status",
                                "PASS",
                                "proofVersion",
                                2,
                                "pid",
                                ProcessHandle.current().pid(),
                                "hostSeed",
                                host.getSeed(),
                                "kernels",
                                kernelEvidence,
                                "batches",
                                batches.size(),
                                "cells",
                                batches.stream().mapToInt(b -> b.cells().size()).sum(),
                                "hives",
                                batches.stream()
                                        .flatMap(b -> b.cells().stream())
                                        .filter(c -> c.blockEntity().isPresent())
                                        .count(),
                                "receiving",
                                receiving,
                                "bindings",
                                GenerationContextRegistry.bindingCount()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("C contribution verification failed", e);
        }
        server.halt(false);
    }

    private static void kernel(
            ServerLevel host,
            ChunkPos source,
            long seed,
            List<TreeContribution> batches,
            List<Object> evidence) {
        try (var left =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, source);
                var right =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, source)) {
            var light =
                    LightweightFeatureBaseline.generate(
                            left, new LocalFeatureInputs.Observer() {}, (r, i) -> {});
            var a = light.contributionInputs(left);
            var nativeBase = StrictFeatureBaseline.generate(right, (r, t) -> {});
            if (!snapshot(host, a.target()).equals(snapshot(host, nativeBase.target())))
                throw new IllegalStateException("C preparation changed B");
            int attempt = 0;
            for (String name :
                    List.of(
                            "super_birch_bees",
                            "birch",
                            "fallen_birch_tree",
                            "oak",
                            "spruce",
                            "pine",
                            "fallen_spruce_tree")) {
                var feature =
                        host.registryAccess()
                                .lookupOrThrow(Registries.FEATURE)
                                .getOrThrow(
                                        ResourceKey.create(
                                                Registries.FEATURE,
                                                Identifier.withDefaultNamespace(name)))
                                .value();
                var unsupported =
                        SelectiveTreeSupport.unsupported(
                                new net.minecraft.world.level.levelgen.placement.PlacedFeature(
                                        Holder.direct(feature), List.of()),
                                host.registryAccess());
                if (unsupported != null)
                    throw new IllegalStateException("Unaudited C kernel: " + unsupported);
                // These are native registered kernel configurations at real surface positions,
                // not a claim that the biome naturally selects every configuration here.
                for (int[] offset :
                        List.of(
                                new int[] {0, 0},
                                new int[] {15, 0},
                                new int[] {0, 15},
                                new int[] {15, 15})) {
                    var copies = new HashMap<Long, RecordingChunk>();
                    try (var sparse = new TreeProjectionRegion(left, a);
                            var dense =
                                    new TreeProjectionRegion(
                                            right,
                                            nativeBase,
                                            base -> {
                                                var copy = new RecordingChunk(base, host);
                                                copies.put(base.getPos().pack(), copy);
                                                return copy;
                                            })) {
                        int x = source.getMinBlockX() + offset[0],
                                z = source.getMinBlockZ() + offset[1];
                        int y = sparse.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                        if (y != dense.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z))
                            throw new IllegalStateException("C tree placement height differs");
                        var p = new BlockPos(x, y, z);
                        var ar = new WorldgenRandom(new XoroshiroRandomSource(1000 + attempt));
                        var nr = new WorldgenRandom(new XoroshiroRandomSource(1000 + attempt));
                        boolean result = feature.place(sparse, left.generator(), ar, p);
                        boolean control = feature.place(dense, right.generator(), nr, p);
                        if (result != control
                                || ar.getCount() != nr.getCount()
                                || ar.nextLong() != nr.nextLong())
                            throw new IllegalStateException("C native tree RNG/result differs");
                        var key =
                                new TreeContribution.Key(
                                        SCOPE,
                                        source.x(),
                                        source.z(),
                                        seed,
                                        "minecraft:" + name,
                                        9,
                                        0,
                                        attempt++);
                        var contribution = sparse.contribution(key);
                        var nativeContribution =
                                capture(
                                        key,
                                        a.target().getMinY(),
                                        a.target().getHeight(),
                                        host,
                                        copies.values());
                        if (!contribution.equals(nativeContribution)
                                || !snapshot(host, a.target())
                                        .equals(snapshot(host, nativeBase.target())))
                            throw new IllegalStateException(
                                    "C target/outside native state differs: " + key);
                        if (!contribution.cells().isEmpty()) batches.add(contribution);
                        evidence.add(
                                Map.of(
                                        "source",
                                        source.toString(),
                                        "seed",
                                        seed,
                                        "feature",
                                        name,
                                        "origin",
                                        p.toString(),
                                        "success",
                                        result,
                                        "cells",
                                        contribution.cells().size(),
                                        "rngCount",
                                        ar.getCount(),
                                        "targetHash",
                                        snapshot(host, a.target()),
                                        "unattachedEffects",
                                        contribution.unattachedEffects(),
                                        "effects",
                                        Map.of(
                                                "blockTicks",
                                                contribution.blockTicks().size(),
                                                "fluidTicks",
                                                contribution.fluidTicks().size(),
                                                "postProcessing",
                                                contribution.postProcessing().size())));
                        expectFailure(
                                () ->
                                        sparse.contribution(
                                                new TreeContribution.Key(
                                                        SCOPE,
                                                        source.x(),
                                                        source.z(),
                                                        seed + 1,
                                                        key.feature(),
                                                        key.step(),
                                                        key.index(),
                                                        key.attempt())));
                        sparse.beginWriter(new ChunkPos(source.x() + 1, source.z()));
                        sparse.beginWriter(source);
                        expectFailure(() -> sparse.contribution(key));
                        sparse.close();
                        expectFailure(() -> sparse.contribution(key));
                        if (!TreeContribution.decode(contribution.encode()).equals(contribution))
                            throw new IllegalStateException("Detached contribution changed");
                    }
                }
            }
        }
    }

    private static String snapshot(ServerLevel host, ProtoChunk chunk) {
        return FeatureStableSnapshot.capture("minecraft:overworld", chunk, host.registryAccess())
                .hash();
    }

    static final class RecordingChunk extends ProtoChunk {
        final Set<BlockPos> written = new HashSet<>();

        RecordingChunk(ChunkAccess base, ServerLevel host) {
            super(
                    base.getPos(),
                    UpgradeData.EMPTY,
                    Arrays.stream(base.getSections())
                            .map(LevelChunkSection::copy)
                            .toArray(LevelChunkSection[]::new),
                    new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                    new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                    host,
                    host.palettedContainerFactory(),
                    null);
            setPersistedStatus(ChunkStatus.TERRAIN);
            for (var type : Heightmap.Types.values())
                if (base.hasPrimedHeightmap(type)) {
                    var values =
                            new net.minecraft.util.SimpleBitStorage(
                                    net.minecraft.util.Mth.ceillog2(base.getHeight() + 1), 256);
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++)
                            values.set(x + z * 16, base.getHeight(type, x, z) + 1 - base.getMinY());
                    setHeightmap(type, values.getRaw().clone());
                }
        }

        @Override
        public BlockState setBlockState(BlockPos p, BlockState s, int flags) {
            if (!isOutsideBuildHeight(p)) written.add(p.immutable());
            return super.setBlockState(p, s, flags);
        }
    }

    static TreeContribution capture(
            TreeContribution.Key key,
            int minY,
            int height,
            ServerLevel host,
            Collection<RecordingChunk> copies) {
        var cells = new ArrayList<TreeContribution.Cell>();
        var blocks =
                new ArrayList<
                        net.minecraft.world.ticks.SavedTick<
                                net.minecraft.world.level.block.Block>>();
        var fluids =
                new ArrayList<
                        net.minecraft.world.ticks.SavedTick<
                                net.minecraft.world.level.material.Fluid>>();
        var post = new ArrayList<BlockPos>();
        copies.stream()
                .sorted(
                        Comparator.comparingInt((RecordingChunk c) -> c.getPos().z())
                                .thenComparingInt(c -> c.getPos().x()))
                .forEach(
                        c -> {
                            c.written.stream()
                                    .sorted(Comparator.comparingLong(BlockPos::asLong))
                                    .forEach(
                                            p ->
                                                    cells.add(
                                                            new TreeContribution.Cell(
                                                                    p,
                                                                    c.getBlockState(p),
                                                                    Optional.ofNullable(
                                                                            c
                                                                                    .getBlockEntityNbtForSaving(
                                                                                            p,
                                                                                            host
                                                                                                    .registryAccess())))));
                            blocks.addAll(c.getTicksForSerialization(0).blocks());
                            fluids.addAll(c.getTicksForSerialization(0).fluids());
                            var offsets = c.getPostProcessing();
                            for (int i = 0; i < offsets.length; i++)
                                if (offsets[i] != null)
                                    for (short offset : offsets[i])
                                        post.add(
                                                ProtoChunk.unpackOffsetCoordinates(
                                                        offset,
                                                        c.getSectionYFromSectionIndex(i),
                                                        c.getPos()));
                        });
        return new TreeContribution(key, minY, height, cells, blocks, fluids, post);
    }

    private static Map<String, Object> receive(
            ServerLevel host, Path root, List<TreeContribution> batches) throws Exception {
        var receivers =
                new TreeSet<ChunkPos>(
                        Comparator.comparingInt(ChunkPos::z).thenComparingInt(ChunkPos::x));
        batches.forEach(b -> b.cells().forEach(c -> receivers.add(ChunkPos.containing(c.pos()))));
        int accepted = 0, rejected = 0, protectedPositions = 0, persisted = 0, loadedPrevious = 0;
        int acceptedConiferCells = 0;
        int acceptedHives = 0,
                acceptedBlockTicks = 0,
                acceptedFluidTicks = 0,
                acceptedPost = 0,
                unattached = 0;
        for (var pos : receivers) {
            long seed = 789123L + pos.pack();
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
            if (relevant.stream().anyMatch(b -> b.key().seed() == seed))
                throw new IllegalStateException("C receiver must exercise a distinct local seed");
            int minY = relevant.getFirst().minY(), height = relevant.getFirst().height();
            var journal = new FeatureContributionJournal(SCOPE, pos, seed, minY, height);
            relevant.forEach(journal::publish);
            if (journal.project().pendingPositions() == 0
                    || !journal.project().incoming().isEmpty())
                throw new IllegalStateException("Missing receiver was not pending");
            var footprint = new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
            relevant.forEach(
                    b ->
                            b.cells()
                                    .forEach(
                                            c -> {
                                                if (ChunkPos.containing(c.pos()).equals(pos))
                                                    footprint.add(c.pos());
                                            }));
            var base = new ArrayList<TreeContribution.Cell>();
            try (var c =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, pos)) {
                var target =
                        LightweightFeatureBaseline.generate(
                                        c, new LocalFeatureInputs.Observer() {}, (r, i) -> {})
                                .target();
                for (var p : footprint)
                    base.add(
                            new TreeContribution.Cell(
                                    p,
                                    target.getBlockState(p),
                                    Optional.ofNullable(
                                            target.getBlockEntityNbtForSaving(
                                                    p, host.registryAccess()))));
            }
            journal.supplyBase(base);
            var expected = journal.project();
            accepted += expected.incoming().size();
            acceptedConiferCells +=
                    (int)
                            expected.incoming().values().stream()
                                    .filter(
                                            cell ->
                                                    cell.state().is(Blocks.SPRUCE_LOG)
                                                            || cell.state()
                                                                    .is(Blocks.SPRUCE_LEAVES))
                                    .count();
            rejected += expected.rejectedPositions();
            acceptedHives +=
                    (int)
                            expected.incoming().values().stream()
                                    .filter(c -> c.blockEntity().isPresent())
                                    .count();
            acceptedBlockTicks += expected.blockTicks().size();
            acceptedFluidTicks += expected.fluidTicks().size();
            acceptedPost += expected.postProcessing().size();
            unattached += expected.unattachedEffects();
            var reverse = new FeatureContributionJournal(SCOPE, pos, seed, minY, height);
            reverse.supplyBase(base);
            var backwards = new ArrayList<>(relevant);
            Collections.reverse(backwards);
            backwards.forEach(reverse::publish);
            if (!reverse.project().equals(expected))
                throw new IllegalStateException("C receiver order dependence");
            try (var pool = Executors.newFixedThreadPool(4)) {
                var parallel = new FeatureContributionJournal(SCOPE, pos, seed, minY, height);
                parallel.supplyBase(base);
                var futures = new ArrayList<Future<?>>();
                for (int i = 0; i < 3; i++)
                    for (var b : backwards) futures.add(pool.submit(() -> parallel.publish(b)));
                for (var future : futures) future.get();
                if (!parallel.encode().equals(journal.encode()))
                    throw new IllegalStateException("C receiving race/duplicate");
            }
            if (!expected.incoming().isEmpty()) {
                var p =
                        expected.incoming().keySet().stream()
                                .min(Comparator.comparingLong(BlockPos::asLong))
                                .orElseThrow();
                journal.protect(
                        new TreeContribution.Cell(
                                p, Blocks.AIR.defaultBlockState(), Optional.empty()));
                if (journal.project().incoming().containsKey(p)
                        || journal.project().postProcessing().contains(p)
                        || journal.project().blockTicks().stream().anyMatch(t -> t.pos().equals(p)))
                    throw new IllegalStateException("Player-dug air was refilled");
                protectedPositions++;
            }
            Path file = root.resolve("receiver-" + pos.x() + "-" + pos.z() + ".nbt");
            NbtIo.writeCompressed(journal.encode(), file);
            var disk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            var restored = FeatureContributionJournal.decode(disk, SCOPE, pos, seed);
            if (!restored.project().equals(journal.project()))
                throw new IllegalStateException("C reload changed data");
            for (var b : relevant)
                if (restored.publish(b)) throw new IllegalStateException("C reload duplicate");
            for (var b : relevant) restored.remove(b.key());
            if (!restored.project().incoming().isEmpty()
                    || !restored.project().blockTicks().isEmpty())
                throw new IllegalStateException("Stale removed C contribution");
            NbtIo.writeCompressed(
                    restored.encode(), root.resolve("removed-" + pos.x() + "-" + pos.z() + ".nbt"));
            if (!FeatureContributionJournal.decode(restored.encode(), SCOPE, pos, seed)
                    .project()
                    .incoming()
                    .isEmpty()) throw new IllegalStateException("Stale C after removed reload");
            if (root.getFileName().toString().equals("host_1")) {
                var old =
                        NbtIo.readCompressed(
                                root.resolveSibling("host_0").resolve(file.getFileName()),
                                NbtAccounter.unlimitedHeap());
                if (!FeatureContributionJournal.decode(old, SCOPE, pos, seed)
                        .project()
                        .equals(journal.project()))
                    throw new IllegalStateException("Independent JVM C replay differs");
                loadedPrevious++;
            }
            persisted++;
        }
        if (accepted == 0 || protectedPositions == 0 || acceptedConiferCells == 0)
            throw new IllegalStateException(
                    "No real receiver acceptance/player protection positive");
        return Map.of(
                "receivers",
                persisted,
                "accepted",
                accepted,
                "rejected",
                rejected,
                "protected",
                protectedPositions,
                "priorJvmReloads",
                loadedPrevious,
                "physicalApplications",
                0,
                "scope",
                "detached replay only",
                "effects",
                Map.of(
                        "hives",
                        acceptedHives,
                        "coniferCells",
                        acceptedConiferCells,
                        "blockTicks",
                        acceptedBlockTicks,
                        "fluidTicks",
                        acceptedFluidTicks,
                        "postProcessing",
                        acceptedPost,
                        "unattachedRejected",
                        unattached));
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new IllegalStateException("Missing C lifecycle rejection");
    }
}
