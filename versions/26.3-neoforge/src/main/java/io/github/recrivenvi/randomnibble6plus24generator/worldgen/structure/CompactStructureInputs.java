package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

/**
 * Finite seed-local structure input, detached from the session's chunk graph. Native placement,
 * weighted selection, start generation, Jigsaw and reference intersection remain unchanged.
 */
public final class CompactStructureInputs {
    private final ChunkPos target;
    private final long seed;
    private final ServerLevel ownerHost;
    private final Map<Long, StructureInputChunk> sources = new HashMap<>();
    private final StructureInputChunk owner;
    private final StructureManager manager;
    private final Metrics metrics;
    private final boolean narrowTemplateLocks;
    private boolean complete;

    private CompactStructureInputs(
            IsolatedGenerationContext context, ScopedStructureSources reuse) {
        long began = System.nanoTime();
        this.target = context.target();
        this.seed = context.worldSeed();
        ServerLevel host = context.hostLevel();
        this.ownerHost = host;
        this.narrowTemplateLocks = reuse != null && StructureGenerationLocks.supports(context);
        this.owner = new StructureInputChunk(target, host);
        WorldGenLevel view =
                (WorldGenLevel)
                        Proxy.newProxyInstance(
                                WorldGenLevel.class.getClassLoader(),
                                new Class<?>[] {WorldGenLevel.class},
                                (proxy, method, arguments) ->
                                        switch (method.getName()) {
                                            case "getChunk" -> {
                                                if (arguments == null
                                                        || arguments.length < 2
                                                        || !(arguments[0] instanceof Integer)
                                                        || !(arguments[1] instanceof Integer))
                                                    throw new UnsupportedOperationException(
                                                            method.toString());
                                                if (arguments.length >= 3
                                                        && arguments[2]
                                                                instanceof ChunkStatus status
                                                        && status.isAfter(
                                                                ChunkStatus.STRUCTURE_REFERENCES))
                                                    throw new UnsupportedOperationException(
                                                            "Structure view cannot provide "
                                                                    + status);
                                                ChunkPos pos =
                                                        new ChunkPos(
                                                                (int) arguments[0],
                                                                (int) arguments[1]);
                                                if (complete
                                                        && arguments.length >= 3
                                                        && arguments[2]
                                                                == ChunkStatus.STRUCTURE_REFERENCES
                                                        && !pos.equals(target))
                                                    throw new IllegalArgumentException(
                                                            "References only resolved for owner "
                                                                    + target);
                                                yield sourceAt(pos);
                                            }
                                            case "registryAccess" -> host.registryAccess();
                                            case "getSeed" -> seed;
                                            case "getMinY" -> host.getMinY();
                                            case "getHeight" -> host.getHeight();
                                            case "toString" ->
                                                    "CompactStructureInputs["
                                                            + target
                                                            + ", seed="
                                                            + seed
                                                            + "]";
                                            case "hashCode" -> System.identityHashCode(proxy);
                                            case "equals" -> proxy == arguments[0];
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            "Unexpected structure world access: "
                                                                    + method);
                                        });
        this.manager =
                new StructureManager(
                        view,
                        new WorldOptions(
                                seed, context.structureManager().shouldGenerateStructures(), false),
                        context.structureCheck());
        int radius = referenceRadius();
        int considered = 0;
        if (manager.shouldGenerateStructures()) {
            for (var set : context.structureState().possibleStructureSets()) {
                if (set.value().placement()
                        instanceof
                        net.minecraft.world.level.levelgen.structure.placement
                                        .ConcentricRingsStructurePlacement
                                rings) context.structureState().getRingPositionsFor(rings);
            }
            for (int x = target.x() - radius; x <= target.x() + radius; x++) {
                for (int z = target.z() - radius; z <= target.z() + radius; z++) {
                    ChunkPos source = new ChunkPos(x, z);
                    StructureInputChunk carrier;
                    if (reuse == null) {
                        carrier =
                                source.equals(target)
                                        ? owner
                                        : new StructureInputChunk(source, host);
                        generateSource(context, carrier);
                    } else {
                        var raw =
                                reuse.source(
                                        context,
                                        source,
                                        () -> {
                                            var evaluated = new StructureInputChunk(source, host);
                                            generateSource(context, evaluated);
                                            return evaluated;
                                        });
                        if (source.equals(target)) {
                            // Native insertion avoids bulk-copy resizing the owner's start map.
                            raw.getAllStarts().forEach(owner::setStartForStructure);
                            carrier = owner;
                        } else carrier = raw;
                    }
                    considered++;
                    // Preserve even invalid/native metadata if a future native implementation emits
                    // it.
                    if (!carrier.getAllStarts().isEmpty() || source.equals(target))
                        sources.put(ChunkPos.pack(x, z), carrier);
                }
            }
        }
        sources.putIfAbsent(ChunkPos.pack(target.x(), target.z()), owner);
        // Native 26.3 createReferences owns bounding-box inflation and intersection semantics.
        // Missing sources are evaluated empties, not fabricated default terrain.
        context.generator().createReferences(view, manager, owner);
        int starts = sources.values().stream().mapToInt(chunk -> chunk.getAllStarts().size()).sum();
        int pieces =
                sources.values().stream()
                        .flatMap(chunk -> chunk.getAllStarts().values().stream())
                        .mapToInt(start -> start.getPieces().size())
                        .sum();
        LongOpenHashSet required = new LongOpenHashSet();
        required.add(ChunkPos.pack(target.x(), target.z()));
        owner.getAllReferences().values().forEach(required::addAll);
        sources.keySet().removeIf(position -> !required.contains(position.longValue()));
        complete = true;
        this.metrics =
                new Metrics(
                        radius,
                        considered,
                        sources.size(),
                        starts,
                        pieces,
                        System.nanoTime() - began,
                        ((StructureCandidateStateAccess) context.structureState())
                                .randomnibble6plus24generator$candidateScope()
                                .metrics(),
                        ((ConcentricRingStateAccess) context.structureState())
                                .randomnibble6plus24generator$ringScope()
                                .metrics(),
                        narrowTemplateLocks);
    }

    public static CompactStructureInputs resolve(IsolatedGenerationContext context) {
        return resolve(context, null);
    }

    public static CompactStructureInputs resolve(
            IsolatedGenerationContext context, ScopedStructureSources reuse) {
        Objects.requireNonNull(context);
        if (context.mode()
                != io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                        .IsolatedGenerationMode.ISOLATED_MOSAIC)
            throw new IllegalArgumentException(
                    "Compact resolver requires the seed-local candidate scopes");
        if (reuse != null) reuse.requireCompatible(context);
        return new CompactStructureInputs(context, reuse);
    }

    private void generateSource(IsolatedGenerationContext context, StructureInputChunk carrier) {
        if (narrowTemplateLocks) {
            createSource(context, carrier);
            return;
        }
        // Keep the reference path and unaudited structure/pool implementations conservative.
        synchronized (ownerHost.getServer().getStructureTemplateManager()) {
            createSource(context, carrier);
        }
    }

    private void createSource(IsolatedGenerationContext context, StructureInputChunk carrier) {
        context.generator()
                .createStructures(
                        ownerHost.registryAccess(),
                        context.structureState(),
                        manager,
                        carrier,
                        context.worldGenContext().structureManager(),
                        context.dimension());
    }

    public void handoffForDecoration(ScopedStructureSources reuse) {
        reuse.handoffForDecoration(target, Set.copyOf(sources.keySet()));
    }

    public static int referenceRadius() {
        int radius =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.STRUCTURE_REFERENCES)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.STRUCTURE_STARTS);
        // createReferences itself still has a literal eight in 26.3. Metadata disagreement is
        // an unsupported version, never an excuse to guess a smaller safe radius.
        if (radius != 8)
            throw new IllegalStateException("Unaudited native reference radius " + radius);
        return radius;
    }

    private StructureInputChunk sourceAt(ChunkPos position) {
        if (Math.abs((long) position.x() - target.x()) > referenceRadius()
                || Math.abs((long) position.z() - target.z()) > referenceRadius())
            throw new IllegalArgumentException("Outside evaluated structure frontier " + position);
        StructureInputChunk source = sources.get(ChunkPos.pack(position.x(), position.z()));
        if (source != null) return source;
        if (complete) throw new IllegalArgumentException("Unresolved structure input " + position);
        // Empty metadata is known from native createStructures, never used as terrain input.
        return new StructureInputChunk(position, owner.getHeightAccessorForGeneration());
    }

    public StructureStart referencedStart(Structure structure, ChunkPos position) {
        return sourceAt(position).getStartForStructure(structure);
    }

    public StructureManager manager() {
        return manager;
    }

    public void requireCompatible(IsolatedGenerationContext context) {
        if (!target.equals(context.target())
                || seed != context.worldSeed()
                || ownerHost != context.hostLevel())
            throw new IllegalArgumentException(
                    "Structure input belongs to another target, seed or world instance");
    }

    public Metrics metrics() {
        return metrics;
    }

    /** Complete, detached target-owned plus referenced start NBT; never limited to 3x3 writers. */
    public List<CompoundTag> captureStartTags(StructurePieceSerializationContext serialization) {
        Map<String, StructureStart> starts = new java.util.TreeMap<>();
        owner.getAllStarts()
                .forEach(
                        (structure, start) -> {
                            if (start.isValid())
                                starts.put(
                                        manager.registryAccess()
                                                        .lookupOrThrow(
                                                                net.minecraft.core.registries
                                                                        .Registries.STRUCTURE)
                                                        .getKey(structure)
                                                + "@"
                                                + start.getChunkPos(),
                                        start);
                        });
        owner.getAllReferences()
                .forEach(
                        (structure, positions) -> {
                            for (long position : positions) {
                                StructureStart start =
                                        referencedStart(structure, ChunkPos.unpack(position));
                                if (start == null || !start.isValid())
                                    throw new IllegalStateException(
                                            "Missing compact referenced start");
                                starts.put(
                                        manager.registryAccess()
                                                        .lookupOrThrow(
                                                                net.minecraft.core.registries
                                                                        .Registries.STRUCTURE)
                                                        .getKey(structure)
                                                + "@"
                                                + start.getChunkPos(),
                                        start);
                            }
                        });
        List<CompoundTag> result = new ArrayList<>();
        starts.values()
                .forEach(
                        start ->
                                result.add(
                                        start.createTag(serialization, start.getChunkPos())
                                                .copy()));
        return List.copyOf(result);
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
                        (structure, positions) ->
                                copy.put(structure, new LongOpenHashSet(positions)));
        chunk.setAllReferences(copy);
    }

    private void requireOwner(ChunkAccess chunk) {
        if (!target.equals(chunk.getPos()))
            throw new IllegalArgumentException("Wrong compact input owner " + chunk.getPos());
    }

    public record Metrics(
            int referenceRadius,
            int evaluatedSources,
            int retainedCarriers,
            int starts,
            int pieces,
            long elapsedNanos,
            StructureCandidateScope.Metrics candidates,
            ConcentricRingScope.Metrics rings,
            boolean narrowTemplateLocks) {}
}
