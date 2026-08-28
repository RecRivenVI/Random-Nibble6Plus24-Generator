package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Temporary, ignored Phase 2C1R writer-frontier evidence recorder. */
public final class FeatureFrontierEvidence {

    private static final int FORMAT_VERSION = 1;
    private static final int MAGIC = 0x32433152;
    private static final Comparator<ChunkPos> Z_THEN_X = Comparator
            .comparingInt(ChunkPos::z)
            .thenComparingInt(ChunkPos::x);
    private static final AtomicReference<Session> ACTIVE = new AtomicReference<>();

    public enum Mode {
        NATIVE,
        ISOLATED
    }

    public enum Phase {
        PRE,
        POST
    }

    public record Divergence(
            int writerIndex,
            Phase phase,
            ChunkPos chunkPos,
            String field,
            String nativeValue,
            String isolatedValue) {
    }

    private FeatureFrontierEvidence() {
    }

    public static void begin(
            Mode mode,
            Path root,
            ResourceKey<Level> dimension,
            ChunkPos target,
            List<ChunkPos> writers) {
        Session session = new Session(mode, root.toAbsolutePath().normalize(), dimension, target, writers);
        if (!ACTIVE.compareAndSet(null, session)) {
            throw new IllegalStateException("FEATURES frontier evidence session is already active");
        }
        try {
            Files.createDirectories(session.root);
        } catch (IOException exception) {
            ACTIVE.compareAndSet(session, null);
            throw new IllegalStateException("Unable to create FEATURES evidence root " + session.root, exception);
        }
    }

    public static void beginStageProbe(
            Mode mode,
            Path root,
            ResourceKey<Level> dimension,
            ChunkPos probe) {
        Session session = new Session(
                mode, root.toAbsolutePath().normalize(), dimension, probe, List.of(), probe);
        if (!ACTIVE.compareAndSet(null, session)) {
            throw new IllegalStateException("FEATURES stage evidence session is already active");
        }
        try {
            Files.createDirectories(session.root);
        } catch (IOException exception) {
            ACTIVE.compareAndSet(session, null);
            throw new IllegalStateException("Unable to create stage evidence root " + session.root, exception);
        }
    }

    public static void finishStageProbe(Mode mode) {
        Session session = ACTIVE.get();
        if (session == null || session.mode != mode || session.stageProbe == null) {
            throw new IllegalStateException("No active " + mode + " stage evidence session");
        }
        ACTIVE.compareAndSet(session, null);
    }

    public static boolean active(Mode mode) {
        Session session = ACTIVE.get();
        return session != null && session.mode == mode;
    }

    public static void capture(
            Mode mode,
            WorldGenContext worldGenContext,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess writer,
            Phase phase) {
        Session session = ACTIVE.get();
        if (session == null || session.mode != mode
                || !worldGenContext.level().dimension().equals(session.dimension)) {
            return;
        }
        Integer writerIndex = session.writerIndices.get(ChunkPos.pack(writer.getPos().x(), writer.getPos().z()));
        if (writerIndex == null) {
            return;
        }
        Checkpoint checkpoint = snapshot(session, writerIndex, writer.getPos(), phase, worldGenContext, cache);
        checkpoint.write(checkpointPath(session.root, writerIndex, phase));
        session.record(writerIndex, phase);
    }

    public static void finish(Mode mode, FeatureStableSnapshot finalSnapshot) {
        Session session = ACTIVE.get();
        if (session == null || session.mode != mode) {
            throw new IllegalStateException("No active " + mode + " FEATURES evidence session");
        }
        session.requireComplete();
        finalSnapshot.write(session.root.resolve("final-feature-stable.bin.gz"));
        writeFeatureWrites(session.root.resolve("feature-writes.bin.gz"), session.featureWrites);
        ACTIVE.compareAndSet(session, null);
    }

    public static void recordFeatureWrite(
            Mode mode,
            String feature,
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        Session session = ACTIVE.get();
        if (session != null && session.mode == mode) {
            session.featureWrites.add(new FeatureWrite(
                    feature, pos.getX(), pos.getY(), pos.getZ(), state.toString()));
        }
    }

    public static String firstFeatureWriteDivergence(Path nativeRoot, Path isolatedRoot) {
        List<FeatureWrite> nativeWrites = readFeatureWrites(nativeRoot.resolve("feature-writes.bin.gz"));
        List<FeatureWrite> isolatedWrites = readFeatureWrites(isolatedRoot.resolve("feature-writes.bin.gz"));
        int common = Math.min(nativeWrites.size(), isolatedWrites.size());
        for (int index = 0; index < common; index++) {
            FeatureWrite nativeWrite = nativeWrites.get(index);
            FeatureWrite isolatedWrite = isolatedWrites.get(index);
            if (!nativeWrite.equals(isolatedWrite)) {
                return "index=" + index + ",native=" + nativeWrite + ",isolated=" + isolatedWrite;
            }
        }
        return nativeWrites.size() == isolatedWrites.size()
                ? "none"
                : "common=" + common + ",nativeSize=" + nativeWrites.size()
                        + ",isolatedSize=" + isolatedWrites.size()
                        + ",nextNative=" + (common < nativeWrites.size() ? nativeWrites.get(common) : "none")
                        + ",nextIsolated=" + (common < isolatedWrites.size() ? isolatedWrites.get(common) : "none");
    }

    public static void abort(Mode mode) {
        Session session = ACTIVE.get();
        if (session != null && session.mode == mode) {
            ACTIVE.compareAndSet(session, null);
        }
    }

    public static Divergence firstDivergence(Path nativeRoot, Path isolatedRoot, int writerCount) {
        Path nativePath = nativeRoot.toAbsolutePath().normalize();
        Path isolatedPath = isolatedRoot.toAbsolutePath().normalize();
        for (int writer = 0; writer < writerCount; writer++) {
            for (Phase phase : Phase.values()) {
                Checkpoint nativeCheckpoint = Checkpoint.read(checkpointPath(nativePath, writer, phase));
                Checkpoint isolatedCheckpoint = Checkpoint.read(checkpointPath(isolatedPath, writer, phase));
                Divergence divergence = nativeCheckpoint.firstDifference(isolatedCheckpoint);
                if (divergence != null) {
                    return divergence;
                }
            }
        }
        return null;
    }

    public static List<String> checkpointDifferences(
            Path nativeRoot,
            Path isolatedRoot,
            int writerIndex,
            Phase phase) {
        Checkpoint nativeCheckpoint = Checkpoint.read(checkpointPath(
                nativeRoot.toAbsolutePath().normalize(), writerIndex, phase));
        Checkpoint isolatedCheckpoint = Checkpoint.read(checkpointPath(
                isolatedRoot.toAbsolutePath().normalize(), writerIndex, phase));
        return nativeCheckpoint.allDifferences(isolatedCheckpoint);
    }

    public static void captureStage(
            WorldGenContext worldGenContext,
            ChunkStatus status,
            ChunkAccess chunk) {
        Session session = ACTIVE.get();
        if (!shouldCaptureStage(worldGenContext, status, chunk)) return;
        RegistryAccess registryAccess = worldGenContext.level().registryAccess();
        RandomState randomState = GenerationContextRegistry.find(worldGenContext)
                .map(context -> context.randomState())
                .orElseGet(() -> worldGenContext.level().getChunkSource().randomState());
        var generator = GenerationContextRegistry.find(worldGenContext)
                .map(context -> (net.minecraft.world.level.chunk.ChunkGenerator) context.generator())
                .orElse(worldGenContext.generator());
        ChunkEvidence evidence = chunkEvidence(chunk, status, registryAccess, generator, randomState);
        Checkpoint checkpoint = new Checkpoint(
                session.mode,
                session.dimension.identifier().toString(),
                session.target,
                -1,
                session.stageProbe,
                Phase.POST,
                generatorConfig(generator),
                List.of(),
                List.of(evidence));
        checkpoint.write(session.root.resolve(stageFileName(status)));
        if (status.isOrAfter(ChunkStatus.BIOMES)) {
            FeatureStageSnapshot.capture(
                    session.dimension.identifier().toString(),
                    chunk,
                    worldGenContext.level().registryAccess())
                    .write(session.root.resolve(stageDetailFileName(status)));
        }
    }

    public static void captureSurfacePre(
            WorldGenContext worldGenContext,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        Session session = ACTIVE.get();
        if (session == null || session.stageProbe == null
                || !session.dimension.equals(worldGenContext.level().dimension())
                || !session.stageProbe.equals(chunk.getPos())) return;
        Checkpoint checkpoint = snapshot(
                session, -2, chunk.getPos(), Phase.PRE, worldGenContext, cache);
        checkpoint.write(session.root.resolve("surface-pre.bin.gz"));
    }

    public static void captureSurfaceRegion(
            WorldGenRegion region,
            ChunkStep step,
            ChunkAccess center,
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext context) {
        Session session = ACTIVE.get();
        if (session == null || session.stageProbe == null || session.blockProbe == null
                || step.targetStatus() != ChunkStatus.SURFACE
                || !session.stageProbe.equals(center.getPos())) return;
        Registry<Biome> biomes = region.registryAccess().lookupOrThrow(Registries.BIOME);
        BlockPos pos = session.blockProbe;
        Holder<Biome> actual = region.getBiomeManager().getBiome(pos);
        int qx = QuartPos.fromBlock(pos.getX());
        int qy = QuartPos.fromBlock(pos.getY());
        int qz = QuartPos.fromBlock(pos.getZ());
        Holder<Biome> uncached = region.getUncachedNoiseBiome(qx, qy, qz);
        Holder<Biome> expected = context == null
                ? region.getLevel().getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                        qx, qy, qz, region.getLevel().getChunkSource().randomState().sampler())
                : context.generator().getBiomeSource().getNoiseBiome(
                        qx, qy, qz, context.randomState().sampler());
        String value = "actual=" + biomeKey(actual, biomes) + System.lineSeparator()
                + "uncached=" + biomeKey(uncached, biomes) + System.lineSeparator()
                + "directExpected=" + biomeKey(expected, biomes) + System.lineSeparator()
                + "blockPos=" + pos + System.lineSeparator()
                + "quart=" + qx + "," + qy + "," + qz + System.lineSeparator();
        StringBuilder valueBuilder = new StringBuilder(value);
        int parentX = (pos.getX() - 2) >> 2;
        int parentY = (pos.getY() - 2) >> 2;
        int parentZ = (pos.getZ() - 2) >> 2;
        for (int i = 0; i < 8; i++) {
            int cornerX = (i & 4) == 0 ? parentX : parentX + 1;
            int cornerY = (i & 2) == 0 ? parentY : parentY + 1;
            int cornerZ = (i & 1) == 0 ? parentZ : parentZ + 1;
            valueBuilder.append("corner").append(i).append('=')
                    .append(cornerX).append(',').append(cornerY).append(',').append(cornerZ).append(':')
                    .append(biomeKey(region.getUncachedNoiseBiome(cornerX, cornerY, cornerZ), biomes))
                    .append(System.lineSeparator());
        }
        try {
            Files.writeString(session.root.resolve("surface-region-context.txt"), valueBuilder, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Surface region context", exception);
        }
    }

    public static Divergence surfacePreDivergence(Path nativeRoot, Path isolatedRoot) {
        Path nativePath = nativeRoot.resolve("surface-pre.bin.gz");
        Path isolatedPath = isolatedRoot.resolve("surface-pre.bin.gz");
        if (!Files.exists(nativePath) || !Files.exists(isolatedPath)) return null;
        return Checkpoint.read(nativePath).firstDifference(Checkpoint.read(isolatedPath));
    }

    public static List<String> surfacePreDifferences(Path nativeRoot, Path isolatedRoot) {
        Path nativePath = nativeRoot.resolve("surface-pre.bin.gz");
        Path isolatedPath = isolatedRoot.resolve("surface-pre.bin.gz");
        if (!Files.exists(nativePath) || !Files.exists(isolatedPath)) return List.of("missing-surface-pre");
        return Checkpoint.read(nativePath).allDifferences(Checkpoint.read(isolatedPath));
    }

    public static boolean shouldCaptureStage(
            WorldGenContext worldGenContext,
            ChunkStatus status,
            ChunkAccess chunk) {
        Session session = ACTIVE.get();
        return session != null
                && session.stageProbe != null
                && session.dimension.equals(worldGenContext.level().dimension())
                && session.stageProbe.equals(chunk.getPos())
                && !status.isOrAfter(ChunkStatus.FEATURES);
    }

    public static void captureDirectStage(
            Mode mode,
            net.minecraft.server.level.ServerLevel level,
            long worldSeed,
            ChunkStatus status,
            ChunkAccess chunk) {
        Session session = ACTIVE.get();
        if (session == null || session.mode != mode || session.stageProbe == null
                || !session.stageProbe.equals(chunk.getPos())) return;
        var generator = level.getChunkSource().getGenerator();
        RandomState randomState;
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            randomState = RandomState.create(
                    noiseGenerator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE),
                    worldSeed);
        } else {
            throw new IllegalStateException("Stage probe requires NoiseBasedChunkGenerator");
        }
        ChunkEvidence evidence = chunkEvidence(
                chunk, status, level.registryAccess(), generator, randomState);
        new Checkpoint(
                mode,
                session.dimension.identifier().toString(),
                session.target,
                -1,
                session.stageProbe,
                Phase.POST,
                generatorConfig(generator),
                List.of(),
                List.of(evidence))
                .write(session.root.resolve(stageFileName(status)));
        if (status.isOrAfter(ChunkStatus.BIOMES)) {
            FeatureStageSnapshot.capture(
                    session.dimension.identifier().toString(),
                    chunk,
                    level.registryAccess())
                    .write(session.root.resolve(stageDetailFileName(status)));
        }
    }

    public static Divergence firstStageDivergence(Path nativeRoot, Path isolatedRoot) {
        for (ChunkStatus status : List.of(
                ChunkStatus.EMPTY,
                ChunkStatus.STRUCTURE_STARTS,
                ChunkStatus.STRUCTURE_REFERENCES,
                ChunkStatus.BIOMES,
                ChunkStatus.NOISE,
                ChunkStatus.SURFACE,
                ChunkStatus.CARVERS)) {
            Path nativePath = nativeRoot.resolve(stageFileName(status));
            Path isolatedPath = isolatedRoot.resolve(stageFileName(status));
            if (!Files.exists(nativePath) || !Files.exists(isolatedPath)) continue;
            Divergence divergence = Checkpoint.read(nativePath).firstDifference(Checkpoint.read(isolatedPath));
            if (divergence != null) return new Divergence(
                    -1, Phase.POST, divergence.chunkPos(), status + ":" + divergence.field(),
                    divergence.nativeValue(), divergence.isolatedValue());
        }
        return null;
    }

    private static String stageFileName(ChunkStatus status) {
        return "stage-" + status.toString().replace(':', '_').replace('/', '_') + ".bin.gz";
    }

    private static String stageDetailFileName(ChunkStatus status) {
        return "stage-detail-" + status.toString().replace(':', '_').replace('/', '_') + ".bin.gz";
    }

    public static String stageDetailedDifference(
            Path nativeRoot,
            Path isolatedRoot,
            ChunkStatus status) {
        Path nativePath = nativeRoot.resolve(stageDetailFileName(status));
        Path isolatedPath = isolatedRoot.resolve(stageDetailFileName(status));
        if (!Files.exists(nativePath) || !Files.exists(isolatedPath)) return "missing";
        FeatureStageSnapshot left = FeatureStageSnapshot.read(nativePath);
        FeatureStageSnapshot right = FeatureStageSnapshot.read(isolatedPath);
        FeatureStageSnapshot.Diff diff = left.diff(right);
        return "first=" + diff.firstDifference()
                + ",differingBlocks=" + diff.differingBlocks()
                + ",categories=" + diff.blockCategories()
                + ",metadata=" + left.deterministicMetadataDifference(right);
    }

    public static String stageDetailedDifference(
            Path nativeRoot,
            Path isolatedRoot,
            Divergence divergence) {
        for (ChunkStatus status : List.of(
                ChunkStatus.EMPTY, ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_REFERENCES,
                ChunkStatus.BIOMES, ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS)) {
            if (divergence.field().startsWith(status.toString() + ":")) {
                return stageDetailedDifference(nativeRoot, isolatedRoot, status);
            }
        }
        return "unknown-stage:" + divergence.field();
    }

    private static Path checkpointPath(Path root, int writerIndex, Phase phase) {
        return root.resolve(String.format(java.util.Locale.ROOT, "writer-%02d-%s.bin.gz",
                writerIndex,
                phase.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static void writeFeatureWrites(Path path, List<FeatureWrite> writes) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(path))))) {
                output.writeInt(writes.size());
                for (FeatureWrite write : writes) {
                    output.writeUTF(write.feature());
                    output.writeInt(write.x()); output.writeInt(write.y()); output.writeInt(write.z());
                    output.writeUTF(write.state());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write FEATURES write trace " + path, exception);
        }
    }

    private static List<FeatureWrite> readFeatureWrites(Path path) {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(path))))) {
            int count = input.readInt();
            List<FeatureWrite> writes = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                writes.add(new FeatureWrite(
                        input.readUTF(), input.readInt(), input.readInt(), input.readInt(), input.readUTF()));
            }
            return List.copyOf(writes);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read FEATURES write trace " + path, exception);
        }
    }

    private static Checkpoint snapshot(
            Session session,
            int writerIndex,
            ChunkPos writer,
            Phase phase,
            WorldGenContext worldGenContext,
            StaticCache2D<GenerationChunkHolder> cache) {
        RegistryAccess registryAccess = worldGenContext.level().registryAccess();
        RandomState randomState = GenerationContextRegistry.find(worldGenContext)
                .map(context -> context.randomState())
                .orElseGet(() -> worldGenContext.level().getChunkSource().randomState());
        var generator = GenerationContextRegistry.find(worldGenContext)
                .map(context -> (net.minecraft.world.level.chunk.ChunkGenerator) context.generator())
                .orElse(worldGenContext.generator());
        String generatorConfig = generatorConfig(generator);

        List<GenerationChunkHolder> holders = new ArrayList<>();
        cache.forEach(holders::add);
        holders.sort(Comparator.comparing(GenerationChunkHolder::getPos, Z_THEN_X));
        List<ChunkEvidence> chunks = new ArrayList<>(holders.size());
        for (GenerationChunkHolder holder : holders) {
            chunks.add(chunkEvidence(holder, registryAccess, generator, randomState));
        }
        List<String> visibleBiomes = featureVisibleBiomes(cache, writer, registryAccess);
        return new Checkpoint(
                session.mode,
                session.dimension.identifier().toString(),
                session.target,
                writerIndex,
                writer,
                phase,
                generatorConfig,
                visibleBiomes,
                List.copyOf(chunks));
    }

    private static ChunkEvidence chunkEvidence(
            GenerationChunkHolder holder,
            RegistryAccess registryAccess,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            RandomState randomState) {
        ChunkStatus latest = holder.getLatestStatus();
        ChunkStatus persisted = holder.getPersistedStatus();
        ChunkAccess chunk = latest == null ? null : holder.getChunkIfPresentUnchecked(latest);
        if (chunk == null) {
            return new ChunkEvidence(
                    holder.getPos(), name(persisted), name(latest), "missing", "missing", "missing",
                    "missing", "missing", "missing", 0, "none");
        }

        String blockHash = latest.isOrAfter(ChunkStatus.NOISE) ? blockHash(chunk) : "not-generated";
        BiomeEvidence biomeEvidence = latest.isOrAfter(ChunkStatus.BIOMES)
                ? biomeHash(chunk, registryAccess, generator, randomState)
                : new BiomeEvidence("not-generated", 0, "none");
        return new ChunkEvidence(
                holder.getPos(),
                name(persisted),
                name(latest),
                blockHash,
                biomeEvidence.hash,
                heightmapHash(chunk),
                structureStartsHash(chunk, registryAccess),
                structureReferencesHash(chunk, registryAccess),
                carvingMaskHash(chunk),
                biomeEvidence.mismatchCount,
                biomeEvidence.firstMismatch);
    }

    private static ChunkEvidence chunkEvidence(
            ChunkAccess chunk,
            ChunkStatus status,
            RegistryAccess registryAccess,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            RandomState randomState) {
        String blockHash = status.isOrAfter(ChunkStatus.NOISE) ? blockHash(chunk) : "not-generated";
        BiomeEvidence biomeEvidence = status.isOrAfter(ChunkStatus.BIOMES)
                ? biomeHash(chunk, registryAccess, generator, randomState)
                : new BiomeEvidence("not-generated", 0, "none");
        return new ChunkEvidence(
                chunk.getPos(), status.toString(), status.toString(), blockHash, biomeEvidence.hash,
                heightmapHash(chunk), structureStartsHash(chunk, registryAccess),
                structureReferencesHash(chunk, registryAccess), carvingMaskHash(chunk),
                biomeEvidence.mismatchCount, biomeEvidence.firstMismatch);
    }

    private static String generatorConfig(net.minecraft.world.level.chunk.ChunkGenerator generator) {
        String settings = generator instanceof NoiseBasedChunkGenerator noiseGenerator
                ? noiseGenerator.generatorSettings().unwrapKey()
                        .map(key -> key.identifier().toString())
                        .orElse("direct")
                : "not-noise-based";
        return generator.getClass().getName()
                + "|" + generator.getBiomeSource().getClass().getName()
                + "|" + settings
                + "|minY=" + generator.getMinY()
                + "|sea=" + generator.getSeaLevel();
    }

    private static String blockHash(ChunkAccess chunk) {
        MessageDigest digest = digest();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    update(digest, chunk.getBlockState(cursor.set(
                            chunk.getPos().getMinBlockX() + x,
                            y,
                            chunk.getPos().getMinBlockZ() + z)).toString());
                }
            }
        }
        return hex(digest);
    }

    private static BiomeEvidence biomeHash(
            ChunkAccess chunk,
            RegistryAccess registryAccess,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            RandomState randomState) {
        MessageDigest digest = digest();
        Registry<Biome> biomes = registryAccess.lookupOrThrow(Registries.BIOME);
        int mismatchCount = 0;
        String firstMismatch = "none";
        int minQuartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int minQuartY = QuartPos.fromBlock(chunk.getMinY());
        int minQuartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        int quartHeight = chunk.getHeight() / QuartPos.SIZE;
        for (int y = 0; y < quartHeight; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    int qx = minQuartX + x;
                    int qy = minQuartY + y;
                    int qz = minQuartZ + z;
                    Holder<Biome> stored = chunk.getNoiseBiome(qx, qy, qz);
                    Holder<Biome> expected = generator.getBiomeSource().getNoiseBiome(
                            qx, qy, qz, randomState.sampler());
                    String storedKey = biomeKey(stored, biomes);
                    String expectedKey = biomeKey(expected, biomes);
                    update(digest, storedKey);
                    if (!stored.equals(expected)) {
                        mismatchCount++;
                        if (firstMismatch.equals("none")) {
                            firstMismatch = qx + "," + qy + "," + qz
                                    + ":stored=" + storedKey + ",expected=" + expectedKey;
                        }
                    }
                }
            }
        }
        return new BiomeEvidence(hex(digest), mismatchCount, firstMismatch);
    }

    private static List<String> featureVisibleBiomes(
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkPos writer,
            RegistryAccess registryAccess) {
        Registry<Biome> biomes = registryAccess.lookupOrThrow(Registries.BIOME);
        Set<String> result = new TreeSet<>();
        for (int z = writer.z() - 1; z <= writer.z() + 1; z++) {
            for (int x = writer.x() - 1; x <= writer.x() + 1; x++) {
                if (!cache.contains(x, z)) {
                    result.add("<missing:" + x + "," + z + ">");
                    continue;
                }
                GenerationChunkHolder holder = cache.get(x, z);
                ChunkStatus latest = holder.getLatestStatus();
                ChunkAccess chunk = latest == null ? null : holder.getChunkIfPresentUnchecked(latest);
                if (chunk == null) {
                    result.add("<unavailable:" + x + "," + z + ">");
                    continue;
                }
                for (LevelChunkSection section : chunk.getSections()) {
                    section.getBiomes().getAll(biome -> result.add(biomeKey(biome, biomes)));
                }
            }
        }
        return List.copyOf(result);
    }

    public static String featureVisibleBiomeSignature(
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkPos writer,
            RegistryAccess registryAccess) {
        return writer + "=" + featureVisibleBiomes(cache, writer, registryAccess);
    }

    private static String heightmapHash(ChunkAccess chunk) {
        MessageDigest digest = digest();
        Map<String, long[]> values = new TreeMap<>();
        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            values.put(entry.getKey().getSerializationKey(), entry.getValue().getRawData().clone());
        }
        values.forEach((key, data) -> {
            update(digest, key);
            for (long value : data) update(digest, value);
        });
        return hex(digest);
    }

    private static String structureStartsHash(ChunkAccess chunk, RegistryAccess registryAccess) {
        MessageDigest digest = digest();
        Registry<Structure> structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        Map<String, String> values = new TreeMap<>();
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            values.put(Objects.toString(structures.getKey(entry.getKey())),
                    start.isValid() + "|" + start.getChunkPos() + "|" + start.getReferences()
                            + "|" + start.getPieces().size()
                            + "|" + (start.isValid() ? start.getBoundingBox() : "null"));
        }
        values.forEach((key, value) -> { update(digest, key); update(digest, value); });
        return hex(digest);
    }

    private static String structureReferencesHash(ChunkAccess chunk, RegistryAccess registryAccess) {
        MessageDigest digest = digest();
        Registry<Structure> structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        Map<String, long[]> values = new TreeMap<>();
        chunk.getAllReferences().forEach((structure, references) -> {
            long[] data = references.toLongArray();
            Arrays.sort(data);
            values.put(Objects.toString(structures.getKey(structure)), data);
        });
        values.forEach((key, data) -> {
            update(digest, key);
            for (long value : data) update(digest, value);
        });
        return hex(digest);
    }

    private static String carvingMaskHash(ChunkAccess chunk) {
        if (!(chunk instanceof ProtoChunk protoChunk)) return "not-proto";
        CarvingMask mask = protoChunk.getCarvingMask();
        if (mask == null) return "absent";
        MessageDigest digest = digest();
        for (long value : mask.toArray()) update(digest, value);
        return hex(digest);
    }

    private static String biomeKey(Holder<Biome> biome, Registry<Biome> registry) {
        return biome.unwrapKey().map(key -> key.identifier().toString())
                .orElseGet(() -> Objects.toString(registry.getKey(biome.value())));
    }

    private static String name(ChunkStatus status) {
        return status == null ? "null" : status.toString();
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(new byte[] {
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        });
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(new byte[] {
                (byte) (value >>> 56), (byte) (value >>> 48), (byte) (value >>> 40), (byte) (value >>> 32),
                (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        });
    }

    private static String hex(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    private record BiomeEvidence(String hash, int mismatchCount, String firstMismatch) {
    }

    private record FeatureWrite(String feature, int x, int y, int z, String state) {
    }

    private record ChunkEvidence(
            ChunkPos pos,
            String persistedStatus,
            String latestStatus,
            String blockHash,
            String biomeHash,
            String heightmapHash,
            String structureStartsHash,
            String structureReferencesHash,
            String carvingMaskHash,
            int biomeMismatchCount,
            String firstBiomeMismatch) {

        private void write(DataOutputStream output) throws IOException {
            output.writeInt(pos.x()); output.writeInt(pos.z());
            output.writeUTF(persistedStatus); output.writeUTF(latestStatus);
            output.writeUTF(blockHash); output.writeUTF(biomeHash); output.writeUTF(heightmapHash);
            output.writeUTF(structureStartsHash); output.writeUTF(structureReferencesHash);
            output.writeUTF(carvingMaskHash); output.writeInt(biomeMismatchCount);
            output.writeUTF(firstBiomeMismatch);
        }

        private static ChunkEvidence read(DataInputStream input) throws IOException {
            return new ChunkEvidence(
                    new ChunkPos(input.readInt(), input.readInt()),
                    input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(),
                    input.readUTF(), input.readUTF(), input.readUTF(), input.readInt(), input.readUTF());
        }
    }

    private record Checkpoint(
            Mode mode,
            String dimension,
            ChunkPos target,
            int writerIndex,
            ChunkPos writer,
            Phase phase,
            String generatorConfig,
            List<String> visibleBiomes,
            List<ChunkEvidence> chunks) {

        private void write(Path path) {
            try {
                Files.createDirectories(path.getParent());
                try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                        new GZIPOutputStream(Files.newOutputStream(path))))) {
                    output.writeInt(MAGIC); output.writeInt(FORMAT_VERSION);
                    output.writeUTF(mode.name()); output.writeUTF(dimension);
                    output.writeInt(target.x()); output.writeInt(target.z());
                    output.writeInt(writerIndex); output.writeInt(writer.x()); output.writeInt(writer.z());
                    output.writeUTF(phase.name()); output.writeUTF(generatorConfig);
                    output.writeInt(visibleBiomes.size());
                    for (String biome : visibleBiomes) output.writeUTF(biome);
                    output.writeInt(chunks.size());
                    for (ChunkEvidence chunk : chunks) chunk.write(output);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to write FEATURES checkpoint " + path, exception);
            }
        }

        private static Checkpoint read(Path path) {
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                    new GZIPInputStream(Files.newInputStream(path))))) {
                if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                    throw new IllegalArgumentException("Unsupported FEATURES checkpoint " + path);
                }
                Mode mode = Mode.valueOf(input.readUTF());
                String dimension = input.readUTF();
                ChunkPos target = new ChunkPos(input.readInt(), input.readInt());
                int writerIndex = input.readInt();
                ChunkPos writer = new ChunkPos(input.readInt(), input.readInt());
                Phase phase = Phase.valueOf(input.readUTF());
                String generatorConfig = input.readUTF();
                int biomeCount = input.readInt();
                List<String> visibleBiomes = new ArrayList<>(biomeCount);
                for (int i = 0; i < biomeCount; i++) visibleBiomes.add(input.readUTF());
                int chunkCount = input.readInt();
                List<ChunkEvidence> chunks = new ArrayList<>(chunkCount);
                for (int i = 0; i < chunkCount; i++) chunks.add(ChunkEvidence.read(input));
                return new Checkpoint(mode, dimension, target, writerIndex, writer, phase,
                        generatorConfig, List.copyOf(visibleBiomes), List.copyOf(chunks));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read FEATURES checkpoint " + path, exception);
            }
        }

        private Divergence firstDifference(Checkpoint isolated) {
            if (!dimension.equals(isolated.dimension)) return divergence(null, "dimension", dimension, isolated.dimension);
            if (!target.equals(isolated.target)) return divergence(null, "target", target.toString(), isolated.target.toString());
            if (writerIndex != isolated.writerIndex) return divergence(null, "writerIndex", "" + writerIndex, "" + isolated.writerIndex);
            if (!writer.equals(isolated.writer)) return divergence(null, "writer", writer.toString(), isolated.writer.toString());
            if (phase != isolated.phase) return divergence(null, "phase", phase.name(), isolated.phase.name());
            if (!generatorConfig.equals(isolated.generatorConfig)) {
                return divergence(null, "generatorConfig", generatorConfig, isolated.generatorConfig);
            }
            if (!visibleBiomes.equals(isolated.visibleBiomes)) {
                return divergence(writer, "featureVisibleBiomes", visibleBiomes.toString(), isolated.visibleBiomes.toString());
            }
            if (chunks.size() != isolated.chunks.size()) {
                return divergence(null, "cacheSize", "" + chunks.size(), "" + isolated.chunks.size());
            }
            for (int i = 0; i < chunks.size(); i++) {
                ChunkEvidence left = chunks.get(i);
                ChunkEvidence right = isolated.chunks.get(i);
                if (!left.pos.equals(right.pos)) return divergence(null, "chunkPos", left.pos.toString(), right.pos.toString());
                Divergence result = compareChunk(left, right);
                if (result != null) return result;
            }
            return null;
        }

        private List<String> allDifferences(Checkpoint isolated) {
            List<String> differences = new ArrayList<>();
            if (!generatorConfig.equals(isolated.generatorConfig)) {
                differences.add("generatorConfig native=" + generatorConfig + " isolated=" + isolated.generatorConfig);
            }
            if (!visibleBiomes.equals(isolated.visibleBiomes)) {
                differences.add("featureVisibleBiomes native=" + visibleBiomes + " isolated=" + isolated.visibleBiomes);
            }
            Map<ChunkPos, ChunkEvidence> rightByPos = new TreeMap<>(Z_THEN_X);
            for (ChunkEvidence value : isolated.chunks) rightByPos.put(value.pos, value);
            for (ChunkEvidence left : chunks) {
                ChunkEvidence right = rightByPos.get(left.pos);
                if (right == null) {
                    differences.add(left.pos + " missing in isolated");
                    continue;
                }
                add(differences, left.pos, "persistedStatus", left.persistedStatus, right.persistedStatus);
                add(differences, left.pos, "latestStatus", left.latestStatus, right.latestStatus);
                add(differences, left.pos, "blockHash", left.blockHash, right.blockHash);
                add(differences, left.pos, "biomeHash", left.biomeHash, right.biomeHash);
                add(differences, left.pos, "heightmapHash", left.heightmapHash, right.heightmapHash);
                add(differences, left.pos, "structureStartsHash", left.structureStartsHash, right.structureStartsHash);
                add(differences, left.pos, "structureReferencesHash", left.structureReferencesHash, right.structureReferencesHash);
                add(differences, left.pos, "carvingMaskHash", left.carvingMaskHash, right.carvingMaskHash);
                add(differences, left.pos, "storedBiomeMismatchCount", "" + left.biomeMismatchCount, "" + right.biomeMismatchCount);
                add(differences, left.pos, "firstStoredBiomeMismatch", left.firstBiomeMismatch, right.firstBiomeMismatch);
            }
            return List.copyOf(differences);
        }

        private static void add(List<String> values, ChunkPos pos, String field, String left, String right) {
            if (!left.equals(right)) values.add(pos + " " + field + " native=" + left + " isolated=" + right);
        }

        private Divergence compareChunk(ChunkEvidence left, ChunkEvidence right) {
            if (!left.persistedStatus.equals(right.persistedStatus)) return divergence(left.pos, "persistedStatus", left.persistedStatus, right.persistedStatus);
            if (!left.latestStatus.equals(right.latestStatus)) return divergence(left.pos, "latestStatus", left.latestStatus, right.latestStatus);
            if (!left.blockHash.equals(right.blockHash)) return divergence(left.pos, "blockHash", left.blockHash, right.blockHash);
            if (!left.biomeHash.equals(right.biomeHash)) return divergence(left.pos, "biomeHash", left.biomeHash, right.biomeHash);
            if (!left.heightmapHash.equals(right.heightmapHash)) return divergence(left.pos, "heightmapHash", left.heightmapHash, right.heightmapHash);
            if (!left.structureStartsHash.equals(right.structureStartsHash)) return divergence(left.pos, "structureStartsHash", left.structureStartsHash, right.structureStartsHash);
            if (!left.structureReferencesHash.equals(right.structureReferencesHash)) return divergence(left.pos, "structureReferencesHash", left.structureReferencesHash, right.structureReferencesHash);
            if (!left.carvingMaskHash.equals(right.carvingMaskHash)) return divergence(left.pos, "carvingMaskHash", left.carvingMaskHash, right.carvingMaskHash);
            if (left.biomeMismatchCount != right.biomeMismatchCount) return divergence(left.pos, "storedBiomeMismatchCount", "" + left.biomeMismatchCount, "" + right.biomeMismatchCount);
            if (!left.firstBiomeMismatch.equals(right.firstBiomeMismatch)) return divergence(left.pos, "firstStoredBiomeMismatch", left.firstBiomeMismatch, right.firstBiomeMismatch);
            return null;
        }

        private Divergence divergence(ChunkPos pos, String field, String nativeValue, String isolatedValue) {
            return new Divergence(writerIndex, phase, pos, field, nativeValue, isolatedValue);
        }
    }

    private static final class Session {
        private final Mode mode;
        private final Path root;
        private final ResourceKey<Level> dimension;
        private final ChunkPos target;
        private final Map<Long, Integer> writerIndices = new TreeMap<>();
        private final boolean[][] captured;
        private final List<FeatureWrite> featureWrites = new CopyOnWriteArrayList<>();
        private final ChunkPos stageProbe;
        private final BlockPos blockProbe;

        private Session(Mode mode, Path root, ResourceKey<Level> dimension, ChunkPos target, List<ChunkPos> writers) {
            this(mode, root, dimension, target, writers, null);
        }

        private Session(
                Mode mode,
                Path root,
                ResourceKey<Level> dimension,
                ChunkPos target,
                List<ChunkPos> writers,
                ChunkPos explicitStageProbe) {
            this.mode = mode;
            this.root = root;
            this.dimension = dimension;
            this.target = target;
            String probeX = System.getProperty("randomnibble6plus24generator.phase2c1r.stageProbeX");
            String probeZ = System.getProperty("randomnibble6plus24generator.phase2c1r.stageProbeZ");
            this.stageProbe = explicitStageProbe != null
                    ? explicitStageProbe
                    : probeX == null || probeZ == null
                            ? null
                            : new ChunkPos(Integer.parseInt(probeX), Integer.parseInt(probeZ));
            String blockX = System.getProperty("randomnibble6plus24generator.phase2c1r.stageProbeBlockX");
            String blockY = System.getProperty("randomnibble6plus24generator.phase2c1r.stageProbeBlockY");
            String blockZ = System.getProperty("randomnibble6plus24generator.phase2c1r.stageProbeBlockZ");
            this.blockProbe = blockX == null || blockY == null || blockZ == null
                    ? null
                    : new BlockPos(Integer.parseInt(blockX), Integer.parseInt(blockY), Integer.parseInt(blockZ));
            this.captured = new boolean[writers.size()][Phase.values().length];
            for (int i = 0; i < writers.size(); i++) {
                ChunkPos writer = writers.get(i);
                writerIndices.put(ChunkPos.pack(writer.x(), writer.z()), i);
            }
        }

        private synchronized void record(int writer, Phase phase) {
            if (captured[writer][phase.ordinal()]) {
                throw new IllegalStateException("Duplicate FEATURES evidence checkpoint writer="
                        + writer + " phase=" + phase);
            }
            captured[writer][phase.ordinal()] = true;
        }

        private synchronized void requireComplete() {
            for (int writer = 0; writer < captured.length; writer++) {
                for (Phase phase : Phase.values()) {
                    if (!captured[writer][phase.ordinal()]) {
                        throw new IllegalStateException("Missing FEATURES evidence checkpoint writer="
                                + writer + " phase=" + phase);
                    }
                }
            }
        }
    }
}
