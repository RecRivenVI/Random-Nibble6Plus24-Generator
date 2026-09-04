package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.GenerationChunkHolderAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapInvoker;

/** Test-only trace for the player-spawn ticket and physical post-processing boundary. */
public final class Phase3C2RSpawnTrace {

    private static final String PREFIX = "randomnibble6plus24generator.phase3c2r.spawn.";
    private static final ConcurrentLinkedQueue<Event> EVENTS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<GenerationChunkHolder, ServerLevel> HOLDER_LEVELS =
            new ConcurrentHashMap<>();
    // Debug-only call-site correlation; generation identity never uses this
    // thread-local and remains carried by the explicit session context.
    private static final ThreadLocal<ChunkPos> POST_PROCESS_TARGET = new ThreadLocal<>();

    private Phase3C2RSpawnTrace() {
    }

    public static boolean active() {
        return !System.getProperty(PREFIX + "trace", "").isBlank();
    }

    /** Holder/lookup tracing is intentionally opt-in because a spawn burst is noisy. */
    private static boolean verbose() {
        return active() && Boolean.getBoolean(PREFIX + "verbose");
    }

    public static void reset() {
        EVENTS.clear();
        HOLDER_LEVELS.clear();
        POST_PROCESS_TARGET.remove();
    }

    public static void recordHolderUpdate(ChunkMap chunkMap, ChunkHolder holder, String phase) {
        ServerLevel level = ((ChunkMapInvoker) chunkMap).randomnibble6plus24generator$getLevel();
        if (!active() || !isMosaic(level)) return;
        HOLDER_LEVELS.put(holder, level);
        if (!verbose()) return;
        GenerationChunkHolderAccessor accessor = (GenerationChunkHolderAccessor) holder;
        record(level, "holder." + phase
                + " pos=" + holder.getPos()
                + " ticketLevel=" + holder.getTicketLevel()
                + " fullStatus=" + holder.getFullStatus()
                + " generationStatus=" + ChunkLevel.generationStatus(holder.getTicketLevel())
                + " highestAllowed=" + accessor.randomnibble6plus24generator$getHighestAllowedStatus()
                + " startedWork=" + accessor.randomnibble6plus24generator$getStartedWork().get()
                + " persistedStatus=" + holder.getPersistedStatus()
                + " latestStatus=" + holder.getLatestStatus()
                + " latestClass=" + (holder.getLatestChunk() == null
                        ? null : holder.getLatestChunk().getClass().getSimpleName()));
    }

    public static void recordTicketLevel(ChunkHolder holder, int levelValue) {
        ServerLevel level = HOLDER_LEVELS.get(holder);
        if (!verbose() || level == null || !isMosaic(level)) return;
        record(level, "holder.ticket pos=" + holder.getPos()
                + " value=" + levelValue
                + " fullStatus=" + holder.getFullStatus()
                + " generationStatus=" + ChunkLevel.generationStatus(levelValue));
    }

    public static void recordPromotion(ChunkHolder holder, FullChunkStatus status) {
        ServerLevel level = HOLDER_LEVELS.get(holder);
        if ((!verbose() && status != FullChunkStatus.ENTITY_TICKING)
                || level == null || !isMosaic(level)) return;
        record(level, "holder.promotion pos=" + holder.getPos()
                + " status=" + status
                + " ticketLevel=" + holder.getTicketLevel()
                + " fullStatus=" + holder.getFullStatus()
                + " persistedStatus=" + holder.getPersistedStatus());
    }

    public static void recordGenerationScheduling(
            GenerationChunkHolder holder, ChunkStatus requested) {
        ServerLevel level = HOLDER_LEVELS.get(holder);
        if (!verbose() || level == null || !isMosaic(level)) return;
        GenerationChunkHolderAccessor accessor = (GenerationChunkHolderAccessor) holder;
        record(level, "holder.generation pos=" + holder.getPos()
                + " requested=" + requested
                + " ticketLevel=" + holder.getTicketLevel()
                + " fullStatus=" + holder.getFullStatus()
                + " highestAllowed=" + accessor.randomnibble6plus24generator$getHighestAllowedStatus()
                + " startedWork=" + accessor.randomnibble6plus24generator$getStartedWork().get()
                + " persistedStatus=" + holder.getPersistedStatus());
    }

    public static void beginPostProcess(ServerLevel level, ChunkPos target) {
        if (!active() || !isMosaic(level)) return;
        POST_PROCESS_TARGET.set(target);
        record(level, "postProcess.begin target=" + target);
    }

    public static void endPostProcess(ServerLevel level, ChunkPos target) {
        if (!active() || !isMosaic(level)) return;
        record(level, "postProcess.end target=" + target);
        POST_PROCESS_TARGET.remove();
    }

    public static void recordTicket(
            ServerLevel level, TicketType ticketType, ChunkPos center, int radius) {
        if (!active() || !isMosaic(level)) return;
        String typeName = ticketType == TicketType.PLAYER_SPAWN
                ? "PLAYER_SPAWN"
                : ticketType == TicketType.SPAWN_SEARCH
                        ? "SPAWN_SEARCH"
                        : ticketType.toString();
        record(level, "ticket type=" + typeName + " value=" + ticketType
                + " center=" + center + " radius=" + radius);
    }

    public static void recordLookup(
            ServerLevel level, ChunkPos requested, ChunkStatus status, boolean requireFull) {
        if (!verbose() || !isMosaic(level)) return;
        ChunkHolder holder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(requested.pack());
        ChunkAccess materialized = holder == null ? null : holder.getLatestChunk();
        ChunkStatus persisted = holder == null ? null : holder.getPersistedStatus();
        int ticketLevel = holder == null ? Integer.MAX_VALUE : holder.getTicketLevel();
        ChunkStatus generationStatus = holder == null ? null : ChunkLevel.generationStatus(ticketLevel);
        ChunkStatus highestAllowed = holder instanceof GenerationChunkHolderAccessor accessor
                ? accessor.randomnibble6plus24generator$getHighestAllowedStatus() : null;
        ChunkStatus startedWork = holder instanceof GenerationChunkHolderAccessor accessor
                ? accessor.randomnibble6plus24generator$getStartedWork().get() : null;
        String caller = caller();
        ChunkPos postTarget = POST_PROCESS_TARGET.get();
        int dx = postTarget == null ? 0 : requested.x() - postTarget.x();
        int dz = postTarget == null ? 0 : requested.z() - postTarget.z();
        record(level, "lookup requested=" + requested
                + " status=" + status
                + " requireFull=" + requireFull
                + " caller=" + caller
                + " postTarget=" + postTarget
                + " delta=" + dx + "," + dz
                + " holder=" + (holder != null)
                + " ticketLevel=" + ticketLevel
                + " generationStatus=" + generationStatus
                + " highestAllowed=" + highestAllowed
                + " startedWork=" + startedWork
                + " fullStatus=" + (holder == null ? null : holder.getFullStatus())
                + " persistedStatus=" + persisted
                + " materialized=" + (materialized != null)
                + " materializedClass=" + (materialized == null ? null : materialized.getClass().getSimpleName()));
    }

    public static List<String> events() {
        return List.copyOf(EVENTS.stream().map(Event::message).toList());
    }

    public static void dumpToLog() {
        if (!active()) return;
        for (String event : events()) {
            RandomNibble6Plus24Generator.LOGGER.info("Phase 3C2R trace {}", event);
        }
    }

    private static void record(ServerLevel level, String message) {
        EVENTS.add(new Event(level.dimension().identifier().toString(), message));
        RandomNibble6Plus24Generator.LOGGER.info("Phase 3C2R trace {} {}", level.dimension().identifier(), message);
    }

    private static boolean isMosaic(ServerLevel level) {
        return io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                .MosaicPhysicalMaterializer.isPhysicalMosaic(level);
    }

    private static String caller() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String name = element.getClassName();
            if (name.equals(Phase3C2RSpawnTrace.class.getName())
                    || name.equals(Thread.class.getName())) continue;
            if (name.startsWith("net.minecraft.")) {
                return name.substring(name.lastIndexOf('.') + 1) + "." + element.getMethodName();
            }
        }
        return "unknown";
    }

    private record Event(String dimension, String message) {
    }
}
