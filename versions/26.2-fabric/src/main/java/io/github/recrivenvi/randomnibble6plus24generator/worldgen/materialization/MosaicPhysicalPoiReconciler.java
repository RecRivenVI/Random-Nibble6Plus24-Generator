package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;

/** Post-publication, idempotent POI derivation from one physical Chunk's own sections. */
public final class MosaicPhysicalPoiReconciler {

    private static final AtomicLong INVOCATIONS = new AtomicLong();
    private static final AtomicLong SECTIONS = new AtomicLong();
    private static final AtomicLong TOTAL_NANOS = new AtomicLong();
    private static volatile FaultPoint verificationFault = FaultPoint.NONE;

    private MosaicPhysicalPoiReconciler() {
    }

    /** Match Vanilla chunk loading: prefetch may be asynchronous, storage unpack/mutation may not. */
    public static CompletableFuture<Reconciliation> reconcileAsync(WorldGenContext context, ChunkAccess chunk) {
        ServerLevel level = context.level();
        var lifecycle = ((MosaicGenerationLifecycleOwner) level).randomnibble6plus24generator$generationLifecycle();
        lifecycle.checkPreparing();
        return level.getPoiManager().prefetch(chunk.getPos()).thenApplyAsync(ignored -> {
            lifecycle.checkPreparing();
            return reconcile(level, chunk);
        // Use the chunk-source event loop, not MinecraftServer's general task queue:
        // synchronous getChunk waits pump this queue (as does native scheduleChunkLoad).
        }, context.mainThreadExecutor());
    }

    public static Reconciliation reconcile(ServerLevel level, ChunkAccess chunk) {
        requireServerThread(level);
        if (!MosaicPhysicalMaterializer.isPhysicalMosaic(level)) {
            throw new IllegalArgumentException("POI reconciliation is Mosaic-only");
        }
        if (chunk.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
            throw new IllegalStateException("POI reconciliation requires physical FEATURES data at "
                    + chunk.getPos() + " ("
                    + MosaicPhysicalMaterializer.describePhysicalState(level, chunk.getPos()) + ")");
        }
        failIf(FaultPoint.BEFORE_RECONCILIATION);
        long started = System.nanoTime();
        PoiManager manager = level.getPoiManager();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            int sectionY = chunk.getSectionYFromSectionIndex(index);
            manager.checkConsistencyWithBlocks(SectionPos.of(chunk.getPos(), sectionY), sections[index]);
        }
        long elapsed = System.nanoTime() - started;
        INVOCATIONS.incrementAndGet();
        SECTIONS.addAndGet(sections.length);
        TOTAL_NANOS.addAndGet(elapsed);
        List<PoiEntry> actual = actualEntries(level, chunk);
        failIf(FaultPoint.AFTER_RECONCILIATION_BEFORE_INITIALIZE_LIGHT);
        return new Reconciliation(chunk.getPos(), sections.length, actual.size(), elapsed);
    }

    public static List<PoiEntry> expectedEntries(ChunkAccess chunk) {
        List<PoiEntry> result = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
                    BlockPos pos = cursor.set(x, y, z).immutable();
                    PoiTypes.forState(chunk.getBlockState(pos)).ifPresent(type -> result.add(
                            entry(pos, type, type.value().maxTickets())));
                }
            }
        }
        result.sort(PoiEntry.ORDER);
        return List.copyOf(result);
    }

    public static List<PoiEntry> actualEntries(ServerLevel level, ChunkAccess chunk) {
        requireServerThread(level);
        List<PoiEntry> result = level.getPoiManager()
                .getInChunk(ignored -> true, chunk.getPos(), PoiManager.Occupancy.ANY)
                .map(MosaicPhysicalPoiReconciler::entry)
                .sorted(PoiEntry.ORDER)
                .toList();
        return List.copyOf(result);
    }

    public static Metrics metrics() {
        return new Metrics(INVOCATIONS.get(), SECTIONS.get(), TOTAL_NANOS.get());
    }

    public static void resetVerificationState() {
        INVOCATIONS.set(0L);
        SECTIONS.set(0L);
        TOTAL_NANOS.set(0L);
        verificationFault = FaultPoint.NONE;
    }

    public static void setVerificationFault(FaultPoint point) {
        verificationFault = Objects.requireNonNull(point, "point");
    }

    public static void shutdown(MinecraftServer server) {
        // No persistent marker, executor, or server-owned state exists.
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Physical POI storage belongs to the server thread, not "
                    + Thread.currentThread().getName());
        }
    }

    private static PoiEntry entry(PoiRecord record) {
        return entry(record.getPos(), record.getPoiType(), record.getFreeTickets());
    }

    private static PoiEntry entry(BlockPos pos, Holder<PoiType> type, int freeTickets) {
        String id = type.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElseGet(() -> type.value().toString());
        return new PoiEntry(pos.immutable(), id, freeTickets, type.value().maxTickets());
    }

    private static void failIf(FaultPoint point) {
        if (verificationFault == point) {
            verificationFault = FaultPoint.NONE;
            throw new InjectedPoiFailure(point);
        }
    }

    public record PoiEntry(BlockPos pos, String type, int freeTickets, int maxTickets) {
        private static final Comparator<PoiEntry> ORDER = Comparator
                .comparingInt((PoiEntry value) -> value.pos().getY())
                .thenComparingInt(value -> value.pos().getZ())
                .thenComparingInt(value -> value.pos().getX())
                .thenComparing(PoiEntry::type);
    }

    public record Reconciliation(net.minecraft.world.level.ChunkPos pos, int sections, int poiCount, long nanos) {
    }

    public record Metrics(long invocationCount, long sectionCount, long totalNanos) {
    }

    public enum FaultPoint {
        NONE,
        BEFORE_RECONCILIATION,
        AFTER_RECONCILIATION_BEFORE_INITIALIZE_LIGHT
    }

    public static final class InjectedPoiFailure extends RuntimeException {
        public InjectedPoiFailure(FaultPoint point) {
            super("Injected Phase 3B POI failure at " + point);
        }
    }
}
