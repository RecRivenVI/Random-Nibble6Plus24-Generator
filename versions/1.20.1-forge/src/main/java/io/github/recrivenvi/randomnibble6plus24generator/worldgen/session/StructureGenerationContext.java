package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
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
    private final AtomicInteger scans = new AtomicInteger();
    private boolean closed;
    private final boolean narrowTemplateLocks;

    public StructureGenerationContext(ServerLevel host, long seed, ChunkPos target) {
        this.host = host;
        this.target = target;
        this.seed = seed;
        var source = host.getChunkSource().getGenerator();
        NoiseBasedChunkGenerator noise;
        if (source
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator
                                .MosaicChunkGenerator
                        mosaic) noise = mosaic.newLocalGenerator();
        else if (source.getClass() == NoiseBasedChunkGenerator.class)
            noise = (NoiseBasedChunkGenerator) source;
        else
            throw new IllegalArgumentException(
                    "Unaudited native structure generator " + source.getClass().getName());
        generator = new NoiseBasedChunkGenerator(noise.getBiomeSource(), noise.generatorSettings());
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
        // This Target's unproven candidate is opt-in; physical Vanilla states never bind it.
        if (Boolean.getBoolean(
                io.github.recrivenvi.randomnibble6plus24generator.ModConstants.MOD_ID
                        + ".structure.lazyRings")) {
            var scope =
                    io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                            .ConcentricRingScope.forV2Target(
                            target,
                            seed,
                            generator.getBiomeSource(),
                            state.possibleStructureSets());
            if (scope.metrics().fallback()
                    == io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                            .ConcentricRingScope.Fallback.NONE)
                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                        .ConcentricRingStateAccess)
                                state)
                        .mosaic$setRingScope(scope);
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
        narrowTemplateLocks =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                        .StructureGenerationLocks.supports(this);
    }

    public boolean narrowTemplateLocks() {
        requireOpen();
        return narrowTemplateLocks;
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

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Closed seed-local structure context");
    }

    /** Poll only between native calls, outside Vanilla's worldgen exception handlers. */
    public void checkPreparing() {
        requireOpen();
        var lifecycle =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                        .MosaicPhysicalLevelOwner.forLevel(host)
                        .randomnibble6plus24generator$generationLifecycle();
        // A feature's own exception handlers may wrap or consume cancellation. Its caller
        // already checks the lifecycle before publishing, so let that native call finish.
        if (lifecycle.active()
                && io.github.recrivenvi.randomnibble6plus24generator.worldgen.features
                                .FeatureExecutionScope.current(host)
                        == null) lifecycle.checkPreparing();
    }

    @Override
    public void close() {
        closed = true;
    }
}
