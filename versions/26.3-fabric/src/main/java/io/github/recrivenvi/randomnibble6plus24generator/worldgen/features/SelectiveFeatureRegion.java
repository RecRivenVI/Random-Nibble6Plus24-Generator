package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.placement.*;

/**
 * Explicit, transient C capability. No saved profile or production scheduler creates this region.
 */
public class SelectiveFeatureRegion extends StrictFeatureRegion implements AutoCloseable {
    protected final IsolatedGenerationContext context;
    private final ProtoChunk target;
    private final Supplier<Map<Long, ProtoChunk>> terrain;
    private final CompactStructureInputs structures;
    private final TreeContribution.Scope scope;
    private final Map<PlacedFeature, String> audited = new IdentityHashMap<>();
    private final List<TreeContribution> contributions = new ArrayList<>();
    private final List<Call> calls = new ArrayList<>();
    private int index = -1, step = -1;
    private long decorationSeed;
    private boolean closed, evaluating, seeded;

    public SelectiveFeatureRegion(
            IsolatedGenerationContext context,
            ProtoChunk target,
            Map<Long, ? extends ChunkAccess> inputs,
            Supplier<Map<Long, ProtoChunk>> terrain,
            CompactStructureInputs structures,
            TreeContribution.Scope scope,
            Consumer<String> drop) {
        super(
                context.hostLevel(),
                carrier(context, target),
                ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES),
                target,
                inputs,
                drop);
        if (!scope.dimension().equals(context.hostLevel().dimension().identifier().toString()))
            throw new IllegalArgumentException("C scope dimension mismatch");
        this.context = context;
        this.target = target;
        this.terrain = terrain;
        this.structures = structures;
        this.scope = scope;
        GenerationContextRegistry.bind(this, context);
    }

    private static StaticCache2D<GenerationChunkHolder> carrier(
            IsolatedGenerationContext c, ProtoChunk t) {
        var cache =
                StaticCache2D.create(
                        c.target().x(),
                        c.target().z(),
                        0,
                        (x, z) -> StrictFeatureRegion.constructorCarrier(t));
        GenerationContextRegistry.bind(cache, c);
        return cache;
    }

    public void recordSlot(long seed, int index, int step) {
        ensureOpen();
        if (evaluating) throw new IllegalStateException("Nested C decoration slot");
        this.decorationSeed = seed;
        this.index = index;
        this.step = step;
        seeded = true;
    }

    public boolean place(
            PlacedFeature feature, RandomSource random, BlockPos origin, BooleanSupplier strict) {
        ensureOpen();
        if (!seeded
                || evaluating
                || origin.getX() != target.getPos().getMinBlockX()
                || origin.getZ() != target.getPos().getMinBlockZ())
            throw new IllegalStateException("C requires a native target decoration slot");
        seeded = false;
        String name =
                context.hostLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.PLACED_FEATURE)
                        .getKey(feature)
                        .toString();
        String unsupported =
                audited.computeIfAbsent(
                        feature,
                        f ->
                                Objects.requireNonNullElse(
                                        SelectiveTreeSupport.unsupported(
                                                f, context.hostLevel().registryAccess()),
                                        ""));
        int before = ((WorldgenRandom) random).getCount();
        evaluating = true;
        try {
            boolean result;
            if (unsupported.isEmpty()) {
                var key =
                        new TreeContribution.Key(
                                scope,
                                target.getPos().x(),
                                target.getPos().z(),
                                context.worldSeed(),
                                name,
                                step,
                                index,
                                0);
                var evaluated = evaluateTree(feature, random, origin, key);
                result = evaluated.success();
                contributions.add(evaluated.contribution());
            } else result = strict.getAsBoolean();
            calls.add(
                    new Call(
                            name,
                            decorationSeed,
                            step,
                            index,
                            unsupported,
                            result,
                            ((WorldgenRandom) random).getCount() - before));
            return result;
        } finally {
            evaluating = false;
        }
    }

    protected StrictFeatureBaseline.Result baseline() {
        return new StrictFeatureBaseline.Result(
                target, this, terrain.get(), structures, Map.of(), 0);
    }

    protected Evaluation evaluateTree(
            PlacedFeature feature, RandomSource random, BlockPos origin, TreeContribution.Key key) {
        // All attempts in this native slot share scratch feedback. The next slot starts from
        // Pre-FEATURES outside the target, so tree permission cannot leak to another decoration.
        try (var trees = new TreeProjectionRegion(context, baseline(), null, this)) {
            boolean success =
                    new FeaturePlacer(trees, context.generator())
                            .placeWithBiomeCheck(feature, random, origin);
            return new Evaluation(success, trees.contribution(key));
        }
    }

    protected record Evaluation(boolean success, TreeContribution contribution) {
        public Evaluation {}
    }

    public record Call(
            String feature,
            long decorationSeed,
            int step,
            int index,
            String strictReason,
            boolean success,
            int randomDraws) {}

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public List<TreeContribution> contributions() {
        return List.copyOf(contributions);
    }

    private void ensureOpen() {
        if (closed || GenerationContextRegistry.find(this).orElse(null) != context)
            throw new IllegalStateException("Closed C decoration capability");
    }

    @Override
    public void close() {
        closed = true;
    }
}
