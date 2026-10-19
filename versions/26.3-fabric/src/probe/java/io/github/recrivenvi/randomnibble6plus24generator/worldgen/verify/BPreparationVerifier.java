package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Checks the actual detached B handoff, not only a standalone decoration snapshot. */
final class BPreparationVerifier {
    private static final Map<IsolatedGenerationContext, java.util.concurrent.atomic.AtomicInteger>
            WRITERS = new java.util.concurrent.ConcurrentHashMap<>();

    private BPreparationVerifier() {}

    static List<FeatureBVerifier.Fixture> fixtures() {
        var fixtures = new ArrayList<>(FeatureBVerifier.FIXTURES);
        var profile = MosaicWorldProfile.targetOnly();
        long catSeed =
                new MosaicSeedResolver(profile)
                        .resolveLocalWorldSeed(123456789, Level.OVERWORLD, new ChunkPos(0, 2));
        fixtures.add(new FeatureBVerifier.Fixture("spawn-village-cat", "overworld", catSeed, 0, 2));
        fixtures.add(
                new FeatureBVerifier.Fixture(
                        "template-rooted-sulfur",
                        "overworld",
                        new MosaicSeedResolver(profile)
                                .resolveLocalWorldSeed(
                                        123456789, Level.OVERWORLD, new ChunkPos(129, -42)),
                        129,
                        -42));
        fixtures.add(
                new FeatureBVerifier.Fixture(
                        "extension-fallback", "overworld", -4896871951352006295L, -1, 1));
        fixtures.add(
                new FeatureBVerifier.Fixture(
                        "carved-surface-fallback",
                        "overworld",
                        -195955039870900853L,
                        -20000,
                        30000));
        return List.copyOf(fixtures);
    }

    static void run(MinecraftServer server, Path root) throws Exception {
        verifyNativeCats(root);
        var fixtures = fixtures();
        var profile = MosaicWorldProfile.targetOnly();
        var rows = new ArrayList<Object>();
        for (var f : fixtures) {
            var dimension =
                    ResourceKey.create(
                            Registries.DIMENSION, Identifier.withDefaultNamespace(f.dimension()));
            var level = Objects.requireNonNull(server.getLevel(dimension));
            var pos = new ChunkPos(f.x(), f.z());
            var serialization = StructurePieceSerializationContext.fromLevel(level);
            var spawnReference = new AtomicReference<SpawnBiomeSnapshot>();
            FeatureStableSnapshot old;
            ListTag oldRaw;
            List<CompoundTag> starts;
            try (var context =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                    level,
                                    f.seed(),
                                    pos,
                                    profile);
                    var observation = new WriterObservation(context)) {
                var original =
                        StrictFeatureBaseline.generate(
                                context,
                                (region, chunks) ->
                                        spawnReference.set(
                                                SpawnBiomeSnapshot.capture(
                                                        dimension,
                                                        pos,
                                                        f.seed(),
                                                        profile,
                                                        p -> chunks.get(p.pack()))));
                old =
                        FeatureStableSnapshot.capture(
                                dimension.identifier().toString(),
                                original.target(),
                                server.registryAccess());
                oldRaw = raw(original.target().getEntities());
                starts = original.structures().captureStartTags(serialization);
            }
            var output = root.resolve(f.id());
            Files.createDirectories(output);
            old.write(output.resolve("original.bin.gz"));
            NbtIo.writeCompressed(entities(oldRaw), output.resolve("original-entities.nbt"));
            try (var context =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                    level,
                                    f.seed(),
                                    pos,
                                    profile);
                    var observation = new WriterObservation(context)) {
                var prepared = BFeaturePreparation.generate(context, profile);
                require(
                        prepared.reads().columns().proofColumns() == 0,
                        "Physical B handoff captured full column proofs");
                var reuse = Objects.requireNonNull(prepared.structureReuse());
                write(output.resolve("structure-reuse.json"), reuse);
                require(
                        reuse.closed()
                                && reuse.resident() == 0
                                && reuse.peakResident() <= 361
                                && reuse.queries() == reuse.hits() + reuse.evaluations(),
                        "Detached B retained/escaped its source scope");
                if (f.id().equals("template-rooted-sulfur"))
                    require(
                            prepared.levelEscapes()
                                            .getOrDefault(
                                                    "net.minecraft.world.level.levelgen.feature.TemplateFeature.place",
                                                    0L)
                                    > 0,
                            "Template feature fixture did not hit audited resource access");
                var snapshot =
                        FeatureStableSnapshot.capture(
                                dimension.identifier().toString(),
                                prepared.run().targetChunk(),
                                server.registryAccess());
                var currentRaw =
                        raw(
                                ((net.minecraft.world.level.chunk.ProtoChunk)
                                                prepared.run().targetChunk())
                                        .getEntities());
                if (f.id().equals("spawn-village-cat")) {
                    var nativeCat =
                            root.getParent()
                                    .getParent()
                                    .getParent()
                                    .resolve("native_cat")
                                    .resolve(root.getParent().getFileName())
                                    .resolve("host_0/entities-raw.nbt");
                    require(
                            NativeEntityAcceptance.compare(
                                            readRaw(nativeCat), currentRaw, false, true)
                                    .accepted(),
                            "B cat variant/position/deterministic data differs from independent physical Vanilla");
                }
                snapshot.write(output.resolve("prepared.bin.gz"));
                NbtIo.writeCompressed(
                        entities(currentRaw), output.resolve("prepared-entities.nbt"));
                boolean endCity = f.id().equals("end-city");
                var comparison =
                        NativeEntityAcceptance.compare(old, oldRaw, snapshot, currentRaw, endCity);
                write(output.resolve("comparison.json"), comparison);
                require(comparison.accepted(), "B handoff changed original B: " + f.id());
                require(
                        starts.equals(prepared.externalStarts()),
                        "Referenced structure inputs changed");
                var trace = prepared.run().featureTrace();
                require(
                        trace.requestedWriters().equals(List.of(pos))
                                && trace.completedWriters().equals(List.of(pos))
                                && trace.chunksAtOrBeyondFeatures().equals(Set.of(pos))
                                && trace.maxConcurrentFeatureWriters() == 1
                                && trace.featureSeedInvocationCount() > 0
                                && trace.observedWorldSeed() == f.seed(),
                        "Incomplete actual B writer trace");
                var biomeHash = MessageDigest.getInstance("SHA-256");
                int cells = 0;
                var expected = spawnReference.get();
                prepared.spawnBiomes().validate(dimension, pos, f.seed(), profile);
                for (var p : SpawnBiomeSnapshot.neighbors(pos)) {
                    require(
                            expected.minY(p) == prepared.spawnBiomes().minY(p)
                                    && expected.height(p) == prepared.spawnBiomes().height(p),
                            "SPAWN height changed");
                    for (int y = QuartPos.fromBlock(expected.minY(p));
                            y < QuartPos.fromBlock(expected.minY(p) + expected.height(p));
                            y++)
                        for (int z = 0; z < 4; z++)
                            for (int x = 0; x < 4; x++) {
                                var a = expected.biome(p, p.x() * 4 + x, y, p.z() * 4 + z);
                                var b =
                                        prepared.spawnBiomes()
                                                .biome(p, p.x() * 4 + x, y, p.z() * 4 + z);
                                require(
                                        a.equals(b),
                                        "Stored SPAWN palette differs from dense original B");
                                biomeHash.update(
                                        a.unwrapKey()
                                                .orElseThrow()
                                                .identifier()
                                                .toString()
                                                .getBytes(StandardCharsets.UTF_8));
                                biomeHash.update((byte) 0);
                                cells++;
                            }
                }
                var artifact =
                        CanonicalChunkArtifact.capture(
                                prepared.run(),
                                dimension.identifier().toString(),
                                f.seed(),
                                profile,
                                server.registryAccess(),
                                serialization);
                var restored = artifact.rehydrate(server.registryAccess(), serialization);
                // Native 26.3 Jigsaw load recomputes BB from the template even though BB was saved.
                // Compare both stage boundaries, never remove structure metadata from the hash.
                var source = prepared.run().targetChunk();
                var originalStarts = new HashMap<>(source.getAllStarts());
                var nativeStarts =
                        new HashMap<
                                net.minecraft.world.level.levelgen.structure.Structure,
                                net.minecraft.world.level.levelgen.structure.StructureStart>();
                originalStarts.forEach(
                        (structure, start) ->
                                nativeStarts.put(
                                        structure,
                                        Objects.requireNonNull(
                                                net.minecraft.world.level.levelgen.structure
                                                        .StructureStart.loadStaticStart(
                                                        serialization,
                                                        start.createTag(
                                                                serialization, start.getChunkPos()),
                                                        f.seed()))));
                FeatureStableSnapshot nativeLoaded;
                source.setAllStarts(nativeStarts);
                try {
                    nativeLoaded =
                            FeatureStableSnapshot.capture(
                                    dimension.identifier().toString(),
                                    source,
                                    server.registryAccess());
                } finally {
                    source.setAllStarts(originalStarts);
                }
                nativeLoaded.write(output.resolve("native-loaded-control.bin.gz"));
                var detached =
                        FeatureStableSnapshot.capture(
                                dimension.identifier().toString(),
                                restored,
                                server.registryAccess());
                require(
                        nativeStarts.keySet().equals(restored.getAllStarts().keySet()),
                        "Native-loaded structure set changed");
                nativeStarts.forEach(
                        (structure, start) ->
                                require(
                                        start.createTag(serialization, start.getChunkPos())
                                                .equals(
                                                        restored.getStartForStructure(structure)
                                                                .createTag(
                                                                        serialization,
                                                                        start.getChunkPos())),
                                        "Native-loaded complete structure pieces/NBT changed"));
                detached.write(output.resolve("rehydrated.bin.gz"));
                write(
                        output.resolve("structure-roundtrip.json"),
                        Map.of(
                                "source",
                                FeatureStageSnapshot.capture(
                                                dimension.identifier().toString(),
                                                prepared.run().targetChunk(),
                                                server.registryAccess())
                                        .structureStartData(),
                                "rehydrated",
                                FeatureStageSnapshot.capture(
                                                dimension.identifier().toString(),
                                                restored,
                                                server.registryAccess())
                                        .structureStartData()));
                write(
                        output.resolve("artifact-diff.json"),
                        Map.of(
                                "diff",
                                snapshot.diff(detached),
                                "rawEntitiesEqual",
                                currentRaw.equals(raw(restored.getEntities())),
                                "beforeHash",
                                snapshot.hash(),
                                "afterHash",
                                detached.hash()));
                require(
                        nativeLoaded.hash().equals(detached.hash())
                                && currentRaw.equals(raw(restored.getEntities())),
                        "Artifact differs from native-loaded B: "
                                + f.id()
                                + " "
                                + nativeLoaded.diff(detached));
                detached.write(output.resolve("rehydrated.bin.gz"));
                rows.add(
                        Map.of(
                                "fixture",
                                f,
                                "hash",
                                snapshot.hash(),
                                "statusIndependentHash",
                                snapshot.worldgenDataHash(),
                                "strictParity",
                                comparison.strictParity(),
                                "trace",
                                trace,
                                "spawnCells",
                                cells,
                                "spawnHash",
                                HexFormat.of().formatHex(biomeHash.digest()),
                                "structures",
                                starts.size(),
                                "fullNeighbors",
                                prepared.reads().fullNeighbors(),
                                "artifactRawEntitiesEqual",
                                true));
            }
            require(GenerationContextRegistry.bindingCount() == 0, "B preparation leaked context");
        }
        if (root.getFileName().toString().equals("host_1")) {
            var comparisons = new ArrayList<Object>();
            for (var f : fixtures) {
                var first = root.resolveSibling("host_0").resolve(f.id());
                var second = root.resolve(f.id());
                var result =
                        NativeEntityAcceptance.compare(
                                FeatureStableSnapshot.read(first.resolve("prepared.bin.gz")),
                                readRaw(first.resolve("prepared-entities.nbt")),
                                FeatureStableSnapshot.read(second.resolve("prepared.bin.gz")),
                                readRaw(second.resolve("prepared-entities.nbt")),
                                f.id().equals("end-city"),
                                f.id().equals("spawn-village-cat"));
                comparisons.add(Map.of("fixture", f.id(), "comparison", result));
                write(second.resolve("cross-host.json"), result);
                require(result.accepted(), "Cross-host B changed: " + f.id() + " " + result);
            }
            write(root.resolve("cross-host.json"), comparisons);
        }
        write(
                root.resolve("summary.json"),
                Map.of(
                        "status",
                        "PASS_B_DETACHED_HANDOFF",
                        "pid",
                        ProcessHandle.current().pid(),
                        "hostSeed",
                        server.overworld().getSeed(),
                        "fixtures",
                        rows,
                        "bindings",
                        GenerationContextRegistry.bindingCount(),
                        "productionSwitch",
                        false,
                        "finiteExceptions",
                        "approved native random fields; independently evidenced cat attribute list order only"));
    }

    private static ListTag readRaw(Path path) throws Exception {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getListOrEmpty("entities");
    }

    private static void verifyNativeCats(Path root) throws Exception {
        var nativeRoot =
                root.getParent()
                        .getParent()
                        .getParent()
                        .resolve("native_cat")
                        .resolve(root.getParent().getFileName());
        var pids = new HashSet<Long>();
        var worlds = new HashSet<String>();
        var first = FeatureStableSnapshot.read(nativeRoot.resolve("host_0/target.bin.gz"));
        var raw = readRaw(nativeRoot.resolve("host_0/entities-raw.nbt"));
        String trace = Files.readString(nativeRoot.resolve("host_0/trace.json"));
        for (int i = 0; i < 3; i++) {
            var path = nativeRoot.resolve("host_" + i);
            var summary =
                    com.google.gson.JsonParser.parseString(
                                    Files.readString(path.resolve("summary.json")))
                            .getAsJsonObject();
            var fixture = summary.getAsJsonObject("fixture");
            require(
                    summary.get("status").getAsString().equals("CAPTURED_NATIVE_ONLY")
                            && summary.get("experimentalGenerations").getAsLong() == 0
                            && summary.get("bindings").getAsInt() == 0
                            && summary.get("generator")
                                    .getAsString()
                                    .equals(
                                            "net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator")
                            && summary.get("chunkMap")
                                    .getAsString()
                                    .equals("net.minecraft.server.level.ChunkMap")
                            && fixture.get("seed").getAsLong() == 6493561816561378690L
                            && fixture.get("dimension").getAsString().equals("minecraft:overworld")
                            && fixture.get("x").getAsInt() == 0
                            && fixture.get("z").getAsInt() == 2
                            && pids.add(summary.get("pid").getAsLong())
                            && worlds.add(summary.get("worldPath").getAsString())
                            && trace.equals(Files.readString(path.resolve("trace.json"))),
                    "Missing independent cat evidence");
            var snapshot = FeatureStableSnapshot.read(path.resolve("target.bin.gz"));
            var current = readRaw(path.resolve("entities-raw.nbt"));
            require(
                    !current.isEmpty()
                            && snapshot.hash().equals(summary.get("featureHash").getAsString()),
                    "Native cat data missing/changed");
            for (var entry : current)
                require(
                        entry instanceof CompoundTag entity
                                && entity.getStringOr("id", "").equals("minecraft:cat"),
                        "Wrong native entity");
            require(
                    NativeEntityAcceptance.compare(first, raw, snapshot, current, false, true)
                            .accepted(),
                    "Native cats differed beyond UUID/list order; do not broaden rule");
        }
    }

    static void observeWriter(
            WorldGenLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if (!(level instanceof StrictFeatureRegion region)) return;
        var context = GenerationContextRegistry.find(region).orElseThrow();
        var count = WRITERS.get(context);
        require(
                count != null
                        && context.target().equals(chunk.getPos())
                        && count.incrementAndGet() == 1,
                "Unregistered/repeated actual B preparation writer");
    }

    private static final class WriterObservation implements AutoCloseable {
        private final IsolatedGenerationContext context;

        WriterObservation(IsolatedGenerationContext context) {
            this.context = context;
            require(
                    WRITERS.putIfAbsent(context, new java.util.concurrent.atomic.AtomicInteger())
                            == null,
                    "Repeated observer");
        }

        @Override
        public void close() {
            require(
                    WRITERS.remove(context).get() == 1,
                    "B did not execute exactly one actual writer");
        }
    }

    private static ListTag raw(List<CompoundTag> entries) {
        var list = new ListTag();
        entries.forEach(e -> list.add(e.copy()));
        return list;
    }

    private static CompoundTag entities(ListTag entries) {
        var tag = new CompoundTag();
        tag.put("entities", entries);
        return tag;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void write(Path path, Object value) throws Exception {
        Files.writeString(
                path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }
}
