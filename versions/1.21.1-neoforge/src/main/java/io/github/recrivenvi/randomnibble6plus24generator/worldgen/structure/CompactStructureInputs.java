package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/** Native 1.21.1 finite structure input. Placement and piece generation are not optimized here. */
public final class CompactStructureInputs {
    private final ServerLevel host;
    private final ChunkPos target;
    private final long seed;
    private final Map<Long, StructureInputChunk> sources = new HashMap<>();
    private final StructureInputChunk owner;
    private final StructureManager manager;
    private final Metrics metrics;
    private boolean complete;

    public static CompactStructureInputs resolve(StructureGenerationContext context) {
        return resolve(context, null);
    }

    public static CompactStructureInputs resolve(
            StructureGenerationContext context, ScopedStructureSources reuse) {
        if (reuse != null) reuse.requireCompatible(context);
        return new CompactStructureInputs(context, reuse);
    }

    private CompactStructureInputs(
            StructureGenerationContext context, ScopedStructureSources reuse) {
        long began = System.nanoTime();
        host = context.host();
        target = context.target();
        seed = context.seed();
        owner = new StructureInputChunk(target, host);
        WorldGenLevel view =
                (WorldGenLevel)
                        Proxy.newProxyInstance(
                                WorldGenLevel.class.getClassLoader(),
                                new Class<?>[] {WorldGenLevel.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getChunk" -> {
                                                if (args == null
                                                        || args.length < 2
                                                        || !(args[0] instanceof Integer)
                                                        || !(args[1] instanceof Integer))
                                                    throw new UnsupportedOperationException(
                                                            method.toString());
                                                var pos =
                                                        new ChunkPos((int) args[0], (int) args[1]);
                                                if (args.length >= 3
                                                        && args[2] instanceof ChunkStatus status) {
                                                    if (status.isAfter(
                                                            ChunkStatus.STRUCTURE_REFERENCES))
                                                        throw new UnsupportedOperationException(
                                                                "Structure input has no terrain: "
                                                                        + status);
                                                    if (complete
                                                            && status
                                                                    == ChunkStatus
                                                                            .STRUCTURE_REFERENCES
                                                            && !pos.equals(target))
                                                        throw new IllegalArgumentException(
                                                                "References belong only to "
                                                                        + target);
                                                }
                                                yield sourceAt(pos);
                                            }
                                            case "registryAccess" -> host.registryAccess();
                                            case "getSeed" -> seed;
                                            case "getMinBuildHeight" -> host.getMinBuildHeight();
                                            case "getMaxBuildHeight" -> host.getMaxBuildHeight();
                                            case "getMinSection" -> host.getMinSection();
                                            case "getMaxSection" -> host.getMaxSection();
                                            case "getSectionsCount" -> host.getSectionsCount();
                                            case "getHeight" -> {
                                                if (args != null && args.length != 0)
                                                    throw new UnsupportedOperationException(
                                                            "No physical height query");
                                                yield host.getHeight();
                                            }
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "toString" ->
                                                    "CompactStructureInputs["
                                                            + target
                                                            + ", seed="
                                                            + seed
                                                            + "]";
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            "Unexpected native structure world access "
                                                                    + method);
                                        });
        manager =
                new StructureManager(
                        view,
                        new WorldOptions(
                                seed,
                                host.getServer()
                                        .getWorldData()
                                        .worldGenOptions()
                                        .generateStructures(),
                                false),
                        context.check());
        int considered = 0;
        if (manager.shouldGenerateStructures()) {
            // Retain this version's unoptimized native state as the initial Oracle-compatible
            // baseline. Await rings before taking the native shared template monitor.
            for (var set : context.state().possibleStructureSets())
                if (set.value().placement() instanceof ConcentricRingsStructurePlacement rings)
                    context.state().getRingPositionsFor(rings);
            int radius = referenceRadius();
            for (int x = target.x - radius; x <= target.x + radius; x++)
                for (int z = target.z - radius; z <= target.z + radius; z++) {
                    var pos = new ChunkPos(x, z);
                    StructureInputChunk carrier;
                    if (reuse == null) {
                        carrier = pos.equals(target) ? owner : new StructureInputChunk(pos, host);
                        generateSource(context, carrier);
                    } else {
                        var raw =
                                reuse.source(
                                        context,
                                        pos,
                                        () -> {
                                            var evaluated = new StructureInputChunk(pos, host);
                                            generateSource(context, evaluated);
                                            return evaluated;
                                        });
                        if (pos.equals(target)) {
                            // Keep the native owner's insertion/capacity rules, not putAll
                            // resizing.
                            raw.getAllStarts().forEach(owner::setStartForStructure);
                            carrier = owner;
                        } else carrier = raw;
                    }
                    sources.put(pos.toLong(), carrier);
                    context.check().onStructureLoad(pos, carrier.getAllStarts());
                    considered++;
                }
        }
        sources.putIfAbsent(target.toLong(), owner);
        context.generator().createReferences(view, manager, owner);
        int starts =
                sources.values().stream().mapToInt(source -> source.getAllStarts().size()).sum();
        int pieces =
                sources.values().stream()
                        .flatMap(source -> source.getAllStarts().values().stream())
                        .mapToInt(start -> start.getPieces().size())
                        .sum();
        var required = new LongOpenHashSet();
        required.add(target.toLong());
        owner.getAllReferences().values().forEach(required::addAll);
        sources.keySet().removeIf(position -> !required.contains(position.longValue()));
        complete = true;
        metrics =
                new Metrics(
                        referenceRadius(),
                        considered,
                        sources.size(),
                        starts,
                        pieces,
                        context.virtualStorageScans(),
                        System.nanoTime() - began);
    }

    private void generateSource(StructureGenerationContext context, StructureInputChunk carrier) {
        if (context.narrowTemplateLocks()) createSource(context, carrier);
        else
            synchronized (host.getServer().getStructureManager()) {
                createSource(context, carrier);
            }
    }

    public void handoffForDecoration(ScopedStructureSources reuse) {
        reuse.handoffForDecoration(target, Set.copyOf(sources.keySet()));
    }

    private void createSource(StructureGenerationContext context, StructureInputChunk carrier) {
        context.generator()
                .createStructures(
                        host.registryAccess(),
                        context.state(),
                        manager,
                        carrier,
                        host.getServer().getStructureManager());
    }

    public static int referenceRadius() {
        int radius =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.STRUCTURE_REFERENCES)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.STRUCTURE_STARTS);
        if (radius != 8)
            throw new IllegalStateException("Unaudited native reference radius " + radius);
        return radius;
    }

    private StructureInputChunk sourceAt(ChunkPos pos) {
        if (Math.abs((long) pos.x - target.x) > referenceRadius()
                || Math.abs((long) pos.z - target.z) > referenceRadius())
            throw new IllegalArgumentException("Outside evaluated native structure range " + pos);
        var source = sources.get(pos.toLong());
        if (source != null) return source;
        if (complete)
            throw new IllegalArgumentException("Unresolved native structure source " + pos);
        // Structures disabled: empty metadata is the exact native result, not an empty terrain.
        if (manager.shouldGenerateStructures())
            throw new IllegalStateException("Structure source has not been evaluated " + pos);
        return new StructureInputChunk(pos, host);
    }

    public StructureManager manager() {
        return manager;
    }

    public void requireCompatible(StructureGenerationContext context) {
        if (context.host() != host || context.seed() != seed || !context.target().equals(target))
            throw new IllegalArgumentException("Foreign world, seed or target structure inputs");
    }

    public Metrics metrics() {
        return metrics;
    }

    public StructureStart referencedStart(Structure structure, ChunkPos source) {
        return sourceAt(source).getStartForStructure(structure);
    }

    public void copyStartsTo(ChunkAccess chunk) {
        requireOwner(chunk);
        chunk.setAllStarts(new HashMap<>(owner.getAllStarts()));
    }

    public void copyReferencesTo(ChunkAccess chunk) {
        requireOwner(chunk);
        Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> copy = new HashMap<>();
        owner.getAllReferences()
                .forEach(
                        (structure, positions) -> copy.put(structure, copyReferenceSet(positions)));
        chunk.setAllReferences(copy);
    }

    static LongOpenHashSet copyReferenceSet(it.unimi.dsi.fastutil.longs.LongSet positions) {
        // Native reference sets are consumed in iteration order during decoration. Re-inserting
        // the same members with a smaller capacity can change that order; clone the native layout.
        if (positions.getClass() == LongOpenHashSet.class)
            return ((LongOpenHashSet) positions).clone();
        throw new UnsupportedOperationException(
                "Unaudited native reference set " + positions.getClass());
    }

    private void requireOwner(ChunkAccess chunk) {
        if (!target.equals(chunk.getPos()))
            throw new IllegalArgumentException("Foreign structure input owner");
    }

    public List<CompoundTag> captureStartTags(StructurePieceSerializationContext serialization) {
        Map<String, StructureStart> starts = new TreeMap<>();
        var registry =
                host.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        owner.getAllStarts()
                .forEach(
                        (structure, start) -> {
                            if (start.isValid())
                                starts.put(
                                        registry.getKey(structure) + "@" + start.getChunkPos(),
                                        start);
                        });
        owner.getAllReferences()
                .forEach(
                        (structure, positions) -> {
                            for (long position : positions) {
                                var start = referencedStart(structure, new ChunkPos(position));
                                if (start == null || !start.isValid())
                                    throw new IllegalStateException(
                                            "Missing native referenced start");
                                starts.put(
                                        registry.getKey(structure) + "@" + start.getChunkPos(),
                                        start);
                            }
                        });
        return starts.values().stream()
                .map(start -> start.createTag(serialization, start.getChunkPos()).copy())
                .toList();
    }

    public record Metrics(
            int referenceRadius,
            int evaluatedSources,
            int retainedCarriers,
            int starts,
            int pieces,
            int virtualStorageScans,
            long elapsedNanos) {}
}
