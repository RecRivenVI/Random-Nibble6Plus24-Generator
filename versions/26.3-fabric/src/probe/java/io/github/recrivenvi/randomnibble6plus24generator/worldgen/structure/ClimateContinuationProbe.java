package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Climate;

/** Native climate calls around the actual source cache; not a structure/worldgen Oracle. */
public final class ClimateContinuationProbe {
    private ClimateContinuationProbe() {}

    public static JsonObject run(MinecraftServer server, String inputHash) throws Exception {
        var report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("scope", "NATIVE_CLIMATE_CACHE_COMPONENT_NOT_STRUCTURE_ORACLE");
        report.addProperty("inputHash", inputHash);
        report.addProperty("pid", ProcessHandle.current().pid());
        report.addProperty("processors", Runtime.getRuntime().availableProcessors());
        report.addProperty(
                "save",
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .toString());
        int loaded = server.overworld().getChunkSource().getLoadedChunksCount();
        var dimensions = new JsonArray();
        for (var level : server.getAllLevels()) {
            var source = level.getChunkSource().getGenerator().getBiomeSource();
            var state = NativeBiomeSearchState.from(source);
            if (state == null) {
                String detail = level.dimension().identifier() + "/" + source.getClass().getName();
                if (source instanceof net.minecraft.world.level.biome.MultiNoiseBiomeSource) {
                    var parameters =
                            ((io.github.recrivenvi.randomnibble6plus24generator.mixin
                                                    .MultiNoiseBiomeSourceAccessor)
                                            source)
                                    .randomnibble6plus24generator$parameters();
                    detail +=
                            "/"
                                    + parameters.getClass().getName()
                                    + "/owner="
                                    + (parameters instanceof NativeBiomeSearchState.Owner);
                    if (parameters instanceof NativeBiomeSearchState.Owner owner)
                        detail += "/cursor=" + (owner.mosaic$nativeBiomeCursor() != null);
                }
                throw new IllegalStateException(
                        "Native dimension search state unavailable " + detail);
            }
            var dimension = new JsonObject();
            dimension.addProperty("dimension", level.dimension().identifier().toString());
            dimension.addProperty("source", source.getClass().getName());
            dimension.addProperty("climateState", state.hasTree());
            dimensions.add(dimension);
        }
        require(dimensions.size() == 3, "Missing native dimension capability proof");
        report.add("dimensions", dimensions);
        var rows = new JsonArray();
        for (int children : new int[] {19, 2}) {
            for (int direction : new int[] {-1, 1}) {
                var values = List.of(Pair.of(point(-10000), "cold"), Pair.of(point(10000), "warm"));
                var parameters =
                        new Climate.ParameterList<>(values).rebuildWithChildrenPerNode(children);
                var cursor = cursor(parameters);
                require(
                        parameters instanceof NativeBiomeSearchState.Owner owner
                                && owner.mosaic$nativeBiomeCursor() == cursor,
                        "26.3 native constructor bridge missed an index shape");
                String first = parameters.findValue(target(direction * 10000));
                String second = parameters.findValue(target(-direction * 10000));
                require(!first.equals(second), "Native prefixes did not select different leaves");
                var world = LevelHeightAccessor.create(-64, 384);
                var labels = new IdentityHashMap<StructureInputChunk, String>();
                var evaluations = new AtomicInteger();
                try (var scope =
                        new ScopedStructureSources(
                                world, 17, ChunkPos.ZERO, 1, new NativeBiomeSearchState(cursor))) {
                    parameters.findValue(target(direction * 10000));
                    var cached =
                            scope.source(
                                    world,
                                    17,
                                    ChunkPos.ZERO,
                                    ChunkPos.ZERO,
                                    () -> {
                                        var result = new StructureInputChunk(ChunkPos.ZERO, world);
                                        labels.put(result, parameters.findValue(target(0)));
                                        evaluations.incrementAndGet();
                                        return result;
                                    });
                    parameters.findValue(target(-direction * 10000));
                    String direct = parameters.findValue(target(0));
                    parameters.findValue(target(-direction * 10000));
                    var hit =
                            scope.source(
                                    world,
                                    17,
                                    new ChunkPos(1, 0),
                                    ChunkPos.ZERO,
                                    () -> {
                                        var result = new StructureInputChunk(ChunkPos.ZERO, world);
                                        labels.put(result, parameters.findValue(target(0)));
                                        evaluations.incrementAndGet();
                                        return result;
                                    });
                    require(
                            cached != hit
                                    && evaluations.get() == 2
                                    && scope.metrics().stateMismatches() == 1,
                            "Changed native entry reused incompatible source data");
                    require(
                            labels.get(cached).equals(first)
                                    && labels.get(hit).equals(direct)
                                    && direct.equals(second),
                            "Native entry-aware result differs");
                    var row = new JsonObject();
                    row.addProperty("childrenPerNode", children);
                    row.addProperty("firstPrefix", first);
                    row.addProperty("secondPrefix", second);
                    row.addProperty("cachedValue", labels.get(hit));
                    row.addProperty("directSameInputValue", direct);
                    row.addProperty("entryMismatch", !labels.get(hit).equals(direct));
                    row.addProperty("stateMismatches", scope.metrics().stateMismatches());
                    row.addProperty("evaluations", evaluations.get());
                    rows.add(row);
                }
                try (var scope =
                        new ScopedStructureSources(
                                world, 17, ChunkPos.ZERO, 1, new NativeBiomeSearchState(cursor))) {
                    parameters.findValue(target(direction * 10000));
                    Object input = cursor.get();
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> {
                                parameters.findValue(target(-direction * 10000));
                                return new StructureInputChunk(ChunkPos.ZERO, world);
                            });
                    Object exit = cursor.get();
                    String expectedNext = parameters.findValue(target(0));
                    parameters.findValue(target(direction * 10000));
                    require(
                            cursor.get() == input,
                            "Same native entry was not restored by its prefix");
                    scope.source(
                            world,
                            17,
                            new ChunkPos(0, 1),
                            ChunkPos.ZERO,
                            () -> {
                                throw new IllegalStateException("Same-entry cache did not hit");
                            });
                    boolean exitLost = cursor.get() != exit;
                    String actualNext = parameters.findValue(target(0));
                    require(
                            !exitLost
                                    && expectedNext.equals(second)
                                    && actualNext.equals(second)
                                    && scope.metrics().stateReplays() == 1,
                            "Cached native exit/continuation differs");
                    var row = new JsonObject();
                    row.addProperty("childrenPerNode", children);
                    row.addProperty("firstPrefix", first);
                    row.addProperty("secondPrefix", second);
                    row.addProperty("sameEntry", true);
                    row.addProperty("exitLost", exitLost);
                    row.addProperty("nativeNext", expectedNext);
                    row.addProperty("cachedNext", actualNext);
                    row.addProperty("stateReplays", scope.metrics().stateReplays());
                    rows.add(row);
                }
            }
        }
        require(rows.size() == 8, "Missing native bidirectional cases");
        require(
                loaded == server.overworld().getChunkSource().getLoadedChunksCount(),
                "Component audit loaded physical terrain");
        report.add("cases", rows);
        report.addProperty("loadedBefore", loaded);
        report.addProperty("loadedAfter", loaded);
        report.addProperty("status", "GUARDED_COMPONENT_PASS");
        return report;
    }

    private static Climate.ParameterPoint point(long temperature) {
        var zero = new Climate.Parameter(0, 0);
        return new Climate.ParameterPoint(
                new Climate.Parameter(temperature, temperature), zero, zero, zero, zero, zero, 0);
    }

    private static Climate.TargetPoint target(long temperature) {
        return new Climate.TargetPoint(temperature, 0, 0, 0, 0, 0);
    }

    private static ThreadLocal<?> cursor(Climate.ParameterList<?> parameters)
            throws ReflectiveOperationException {
        // Probe-only access to the actual 26.3 tree, never a production reflective fast path.
        var index = Climate.ParameterList.class.getDeclaredField("index");
        index.setAccessible(true);
        var tree = index.get(parameters);
        var last = tree.getClass().getDeclaredField("lastResult");
        last.setAccessible(true);
        return (ThreadLocal<?>) last.get(tree);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
