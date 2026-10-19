package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingStateAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureCheck;

/** Native seed-local structure evaluation; no physical chunk or region storage authority. */
public final class StructureGenerationContext implements AutoCloseable {
    private final ServerLevel host;
    private final ChunkPos target;
    private final long seed;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState state;
    private final StructureCheck check;
    private final AtomicInteger scans;
    private final boolean narrowTemplateLocks;
    private final StructureGenerationContext worldOwner;
    private final io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                    .MosaicGenerationLifecycle
            lifecycle;
    private volatile boolean closed;

    public StructureGenerationContext(ServerLevel host, long seed, ChunkPos target) {
        scans = new AtomicInteger();
        worldOwner = null;
        this.host = host;
        lifecycle =
                host
                                instanceof
                                io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                                .materialization.MosaicGenerationLifecycleOwner
                                        owner
                        ? owner.randomnibble6plus24generator$generationLifecycle()
                        : null;
        this.target = target;
        this.seed = seed;
        var source = host.getChunkSource().getGenerator();
        if (source
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                                .MosaicChunkGenerator
                        mosaic) {
            generator = mosaic.newLocalGenerator();
        } else if (source.getClass() == NoiseBasedChunkGenerator.class) {
            var noise = (NoiseBasedChunkGenerator) source;
            generator =
                    new NoiseBasedChunkGenerator(noise.getBiomeSource(), noise.generatorSettings());
        } else {
            throw new IllegalArgumentException(
                    "Unaudited native structure generator " + source.getClass().getName());
        }
        randomState =
                RandomState.create(
                        generator.generatorSettings().value(),
                        host.registryAccess().lookupOrThrow(Registries.NOISE),
                        seed);
        state =
                generator.createState(
                        host.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                        randomState,
                        seed);
        // This Target's candidate remains opt-in until its own Oracle and paired FULL gate pass.
        if (Boolean.getBoolean(ModConstants.MOD_ID + ".structure.lazyRings")) {
            var scope =
                    ConcentricRingScope.forV2Target(
                            target,
                            seed,
                            generator.getBiomeSource(),
                            state.possibleStructureSets());
            // Unsupported environments must also retain other mods' original operation chain.
            if (scope.metrics().fallback() == ConcentricRingScope.Fallback.NONE)
                ((ConcentricRingStateAccess) state)
                        .randomnibble6plus24generator$setRingScope(scope);
        }
        check =
                new StructureCheck(
                        (pos, visitor) -> {
                            scans.incrementAndGet();
                            return CompletableFuture.completedFuture(null);
                        },
                        host.registryAccess(),
                        host.getServer().getStructureManager(),
                        host.dimension(),
                        generator,
                        randomState,
                        host,
                        generator.getBiomeSource(),
                        seed,
                        host.getServer().getFixerUpper());
        narrowTemplateLocks = StructureGenerationLocks.supports(this);
    }

    private StructureGenerationContext(StructureGenerationContext parent, ChunkPos target) {
        parent.requireOpen();
        var owner = parent.worldOwner == null ? parent : parent.worldOwner;
        if (Math.max(
                        Math.abs((long) target.x - owner.target.x),
                        Math.abs((long) target.z - owner.target.z))
                > sharedPreparationRadius())
            throw new IllegalArgumentException("Outside the audited same-world B preparation ring");
        host = owner.host;
        seed = owner.seed;
        this.target = target;
        generator = owner.generator;
        randomState = owner.randomState;
        state = owner.state;
        check = owner.check;
        scans = owner.scans;
        worldOwner = owner;
        lifecycle = owner.lifecycle;
        narrowTemplateLocks = owner.narrowTemplateLocks;
    }

    /** Borrows one same-S(C) world's native state, never another physical target's universe. */
    public StructureGenerationContext fork(ChunkPos target) {
        return new StructureGenerationContext(this, java.util.Objects.requireNonNull(target));
    }

    /** FEATURES' exact terrain ring plus each terrain owner's native surface palette ring. */
    public static int sharedPreparationRadius() {
        int terrain =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.FEATURES)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.CARVERS);
        int palettes =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.SURFACE)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.BIOMES);
        if (terrain != 1 || palettes != 1)
            throw new IllegalStateException("Unaudited same-world preparation metadata");
        return terrain + palettes;
    }

    public ServerLevel host() {
        requireOpen();
        return host;
    }

    public ChunkPos target() {
        requireOpen();
        return target;
    }

    public long seed() {
        requireOpen();
        return seed;
    }

    public NoiseBasedChunkGenerator generator() {
        requireOpen();
        return generator;
    }

    public RandomState randomState() {
        requireOpen();
        return randomState;
    }

    public ChunkGeneratorStructureState state() {
        requireOpen();
        return state;
    }

    public StructureCheck check() {
        requireOpen();
        return check;
    }

    public int virtualStorageScans() {
        return scans.get();
    }

    public boolean narrowTemplateLocks() {
        requireOpen();
        return narrowTemplateLocks;
    }

    private void requireOpen() {
        // Native structure/template calls may be long-running. Between those calls stop the
        // owning physical world's cancelled preparation, not an unrelated runtime exception.
        if (lifecycle != null && lifecycle.active()) lifecycle.checkPreparing();
        if (closed || (worldOwner != null && worldOwner.closed))
            throw new IllegalStateException("Closed seed-local structure context");
    }

    @Override
    public void close() {
        closed = true;
    }
}
