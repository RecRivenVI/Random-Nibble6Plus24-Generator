package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/** Test-only evidence for every native monster-room spawner fetch, including failed fetches. */
public final class MonsterRoomProbe {
    private static final Queue<Map<String, Object>> EVENTS = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> TRACES = new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<Map<String, Object>> CURRENT = new ThreadLocal<>();

    private MonsterRoomProbe() {}

    public static boolean begin(WorldGenLevel level, BlockPos origin) {
        if (!Set.of(
                        new BlockPos(10320, -19, -9954),
                        new BlockPos(9941, -26, -10352),
                        new BlockPos(9505, -38, -9645))
                .contains(origin)) return false;
        if (CURRENT.get() != null) throw new IllegalStateException("Nested monster-room audit");
        var trace = new LinkedHashMap<String, Object>();
        trace.put("origin", List.of(origin.getX(), origin.getY(), origin.getZ()));
        trace.put("writer", ((WorldGenRegion) level).getCenter().toString());
        trace.put("seed", Long.toString(level.getSeed()));
        trace.put("region", level.getClass().getSimpleName());
        trace.put("reads", new ArrayList<Object>());
        CURRENT.set(trace);
        return true;
    }

    @SuppressWarnings("unchecked")
    public static void read(String api, BlockPos pos, Object value) {
        var trace = CURRENT.get();
        if (trace != null)
            ((List<Object>) trace.get("reads"))
                    .add(
                            Map.of(
                                    "api",
                                    api,
                                    "pos",
                                    List.of(pos.getX(), pos.getY(), pos.getZ()),
                                    "value",
                                    value.toString()));
    }

    public static void end() {
        var trace = CURRENT.get();
        CURRENT.remove();
        if (trace != null) TRACES.add(trace);
    }

    public static List<Map<String, Object>> drainTraces() {
        var traces = new ArrayList<Map<String, Object>>();
        Map<String, Object> trace;
        while ((trace = TRACES.poll()) != null) traces.add(trace);
        return traces;
    }

    public static void observe(WorldGenLevel level, BlockPos pos, BlockEntity entity) {
        var state = level.getBlockState(pos);
        var writer = ((WorldGenRegion) level).getCenter();
        var event = new LinkedHashMap<String, Object>();
        event.put("writer", writer.toString());
        event.put("seed", Long.toString(level.getSeed()));
        event.put("position", List.of(pos.getX(), pos.getY(), pos.getZ()));
        event.put("insideWriter", writer.equals(ChunkPos.containing(pos)));
        event.put("state", state.toString());
        event.put("protected", state.is(BlockTags.FEATURES_CANNOT_REPLACE));
        event.put("entity", entity == null ? "null" : entity.getClass().getName());
        event.put("region", level.getClass().getSimpleName());
        event.put("spawner", entity instanceof SpawnerBlockEntity);
        EVENTS.add(Map.copyOf(event));
        if (!(entity instanceof SpawnerBlockEntity))
            System.out.println("MONSTER_ROOM_FETCH " + event);
    }

    public static List<Map<String, Object>> drain() {
        var result = new ArrayList<Map<String, Object>>();
        Map<String, Object> event;
        while ((event = EVENTS.poll()) != null) result.add(event);
        return result;
    }
}
