package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.*;
import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/** Five real physical owners; native requests only, no scheduler or generation-stage driver. */
final class NativePhysicalTargets {
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("mosaic_multi_probe", Comparator.comparingLong(ChunkPos::toLong));
    private static ServerLevel level;
    private static ChunkPos center;
    private static List<ChunkPos> order;
    private static final Map<
                    ChunkPos,
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
            REQUESTS = new LinkedHashMap<>();
    private static final Set<ChunkPos> HELD = new HashSet<>();
    private static final java.util.concurrent.ConcurrentLinkedQueue<ChunkPos> COMPLETED =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final AtomicInteger PENDING = new AtomicInteger(), PEAK = new AtomicInteger();
    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> complete;
    private static final AtomicLong FIRST_COMPLETION = new AtomicLong(Long.MAX_VALUE);
    private static long allIssued;

    private NativePhysicalTargets() {}

    static boolean enabled() {
        return NativePhysicalProbe.enabled()
                && !System.getProperty(NativePhysicalProbe.PREFIX + "targetOrder", "").isBlank();
    }

    static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> start(
            ServerLevel host, ChunkPos target) {
        level = host;
        center = target;
        var positions =
                new ArrayList<>(
                        List.of(
                                target,
                                new ChunkPos(target.x + 1, target.z),
                                new ChunkPos(target.x - 1, target.z),
                                new ChunkPos(target.x, target.z + 1),
                                new ChunkPos(target.x, target.z - 1)));
        String mode = System.getProperty(NativePhysicalProbe.PREFIX + "targetOrder");
        if (!Set.of("forward", "reverse", "parallel").contains(mode))
            throw new IllegalArgumentException("Unknown native target order");
        if (mode.equals("reverse")) Collections.reverse(positions);
        order = List.copyOf(positions);
        complete = new CompletableFuture<>();
        return complete;
    }

    static void advance() {
        if (!enabled() || complete.isDone()) return;
        require(level.getServer().isSameThread(), "Multi-target observation off server thread");
        boolean parallel =
                System.getProperty(NativePhysicalProbe.PREFIX + "targetOrder").equals("parallel");
        if (parallel && REQUESTS.isEmpty()) {
            for (var pos : order) issue(pos);
            allIssued = System.nanoTime();
        } else if (!parallel
                && REQUESTS.size() < order.size()
                && (REQUESTS.isEmpty() || REQUESTS.get(order.get(REQUESTS.size() - 1)).isDone())) {
            if (!REQUESTS.isEmpty()) ready(REQUESTS.get(order.get(REQUESTS.size() - 1)));
            issue(order.get(REQUESTS.size()));
        }
        if (REQUESTS.size() != order.size()
                || REQUESTS.values().stream().anyMatch(f -> !f.isDone())) return;
        REQUESTS.values().forEach(NativePhysicalTargets::ready);
        complete.complete(Either.left(ready(REQUESTS.get(center))));
    }

    private static void issue(ChunkPos pos) {
        int radius =
                ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(ChunkStatus.FULL);
        level.getChunkSource().addRegionTicket(TICKET, pos, radius, pos);
        HELD.add(pos);
        PEAK.accumulateAndGet(PENDING.incrementAndGet(), Math::max);
        var future =
                CompletableFuture.supplyAsync(
                                () -> {
                                    require(
                                            !level.getServer().isSameThread(),
                                            "Native target request managed-blocked server thread");
                                    return level.getChunkSource()
                                            .getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true);
                                })
                        .thenCompose(java.util.function.Function.identity())
                        .whenComplete(
                                (chunk, error) -> {
                                    FIRST_COMPLETION.accumulateAndGet(System.nanoTime(), Math::min);
                                    COMPLETED.add(pos);
                                    PENDING.decrementAndGet();
                                });
        REQUESTS.put(pos, future);
    }

    private static LevelChunk ready(
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> request) {
        var chunk =
                request.join()
                        .left()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Native multi-target request unloaded"));
        require(
                chunk instanceof LevelChunk
                        && chunk.getStatus() == ChunkStatus.FULL
                        && chunk.isLightCorrect(),
                "Missing real lit multi-target LevelChunk");
        return (LevelChunk) chunk;
    }

    static Map<ChunkPos, ChunkAccess> completedChunks() {
        require(complete.isDone() && PENDING.get() == 0, "Unfinished physical target request");
        var chunks = new LinkedHashMap<ChunkPos, ChunkAccess>();
        REQUESTS.forEach((pos, future) -> chunks.put(pos, ready(future)));
        return Collections.unmodifiableMap(chunks);
    }

    static void completed(
            Path common,
            boolean reload,
            Map<ChunkPos, CanonicalChunkArtifact> artifacts,
            JsonObject proof)
            throws IOException {
        if (!enabled()) return;
        require(complete.isDone() && PENDING.get() == 0, "Unfinished physical target request");
        String mode = System.getProperty(NativePhysicalProbe.PREFIX + "targetOrder");
        if (!reload && mode.equals("parallel"))
            require(
                    PEAK.get() == 5 && allIssued < FIRST_COMPLETION.get(),
                    "Five target requests did not overlap");
        var rows = new JsonArray();
        var seeds = new HashSet<Long>();
        for (var pos : order) {
            var chunk = ready(REQUESTS.get(pos));
            var marker =
                    new BlockPos(
                            pos.getMinBlockX() + 8,
                            level.getMaxBuildHeight() - 4,
                            pos.getMinBlockZ() + 8);
            if (!reload && !chunk.getBlockState(marker).is(Blocks.GOLD_BLOCK))
                require(
                        level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 2),
                        "Multi-target marker placement failed");
            require(
                    chunk.getBlockState(marker).is(Blocks.GOLD_BLOCK),
                    "Multi-target saved edit was overwritten");
            var folder = common.resolve("targets").resolve(pos.x + "_" + pos.z);
            Files.createDirectories(folder);
            var raw = ChunkSerializer.write(level, chunk);
            var state = new CompoundTag();
            for (var key :
                    List.of(
                            "sections",
                            "Heightmaps",
                            "structures",
                            "block_entities",
                            "block_ticks",
                            "fluid_ticks",
                            "PostProcessing")) {
                require(raw.contains(key), "Missing actual physical state " + key);
                if (key.equals("sections")) {
                    var sections = new ListTag();
                    for (var value : raw.getList(key, Tag.TAG_COMPOUND)) {
                        var section = (CompoundTag) value;
                        if (!section.contains("block_states") && !section.contains("biomes"))
                            continue;
                        var copy = new CompoundTag();
                        copy.putByte("Y", section.getByte("Y"));
                        copy.put("block_states", section.getCompound("block_states").copy());
                        copy.put("biomes", section.getCompound("biomes").copy());
                        sections.add(copy);
                    }
                    state.put(key, sections);
                } else state.put(key, raw.get(key).copy());
            }
            var saved = folder.resolve("physical.nbt").toFile();
            if (reload)
                require(
                        state.equals(NbtIo.readCompressed(saved)),
                        "Multi-target physical state changed on reload " + pos);
            else NbtIo.writeCompressed(state, saved);
            var row = new JsonObject();
            row.addProperty("x", pos.x);
            row.addProperty("z", pos.z);
            row.addProperty("status", chunk.getStatus().toString());
            row.addProperty("lightCorrect", chunk.isLightCorrect());
            var runtime = MosaicWorldIdentity.runtimeContext(level);
            long seed =
                    runtime.isPresent()
                            ? runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), pos)
                            : level.getSeed();
            seeds.add(seed);
            row.addProperty("localSeed", seed);
            if (runtime.isPresent() && !reload) {
                var artifact =
                        Objects.requireNonNull(
                                artifacts.get(pos),
                                "Target lacks exactly-once canonical Artifact " + pos);
                require(artifact.localWorldSeed() == seed, "Multi-target seed provenance changed");
                Files.write(folder.resolve("artifact.nbt"), artifact.encodedPayloadCopy());
                NbtIo.writeCompressed(
                        artifact.pendingBlockEntitiesCopy(),
                        folder.resolve("pending.nbt").toFile());
                var instantiated = new CompoundTag();
                instantiated.putLongArray(
                        "positions", artifact.instantiatedBlockEntityPositionsCopy());
                NbtIo.writeCompressed(instantiated, folder.resolve("instantiated.nbt").toFile());
            }
            rows.add(row);
        }
        if (MosaicWorldIdentity.runtimeContext(level).isPresent())
            require(seeds.size() == 5, "Multi-target local seeds not distinct");
        var result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("scope", "FIVE_NATIVE_FULL_TARGETS_NOT_TICKING_OR_PLAYER_ENTRY");
        result.addProperty("order", mode);
        result.addProperty("count", order.size());
        result.addProperty("peakPending", PEAK.get());
        result.addProperty("pending", PENDING.get());
        result.addProperty(
                "allIssuedBeforeFirstCompletion",
                allIssued != 0 && allIssued < FIRST_COMPLETION.get());
        result.add(
                "requestedOrder",
                new Gson().toJsonTree(REQUESTS.keySet().stream().map(ChunkPos::toString).toList()));
        result.add(
                "completionOrder",
                new Gson().toJsonTree(COMPLETED.stream().map(ChunkPos::toString).toList()));
        result.add("targets", rows);
        proof.add("multipleTargets", result);
        close();
    }

    static void close() {
        if (level == null) return;
        int radius =
                ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(ChunkStatus.FULL);
        for (var pos : HELD) level.getChunkSource().removeRegionTicket(TICKET, pos, radius, pos);
        HELD.clear();
    }

    static void reset() {
        REQUESTS.clear();
        COMPLETED.clear();
        HELD.clear();
        level = null;
        center = null;
        order = null;
        complete = null;
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalStateException(error);
    }
}
