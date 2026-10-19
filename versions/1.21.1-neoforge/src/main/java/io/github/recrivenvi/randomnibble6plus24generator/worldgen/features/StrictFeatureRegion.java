package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;

/** Native same-seed read behavior and strict target-only B generation write authority. */
public class StrictFeatureRegion extends WorldGenRegion {
    private final ChunkPos writer;
    private final Map<Long, ChunkAccess> inputs;
    private final Consumer<String> rejected;
    private final ServerLevel resourceHost;
    private final ChunkStep nativeStep;
    private final net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator;
    private final net.minecraft.world.level.levelgen.RandomState randomState;
    private final Map<Long, ChunkAccess> outerInputs = new java.util.HashMap<>();
    private final Map<Long, net.minecraft.world.level.chunk.ProtoChunk> outerRawInputs =
            new java.util.HashMap<>();
    private java.util.function.BiFunction<
                    Integer, Integer, net.minecraft.world.level.chunk.ProtoChunk>
            outerResolver;
    private final Map<String, Long> escapes = new java.util.TreeMap<>();
    private net.minecraft.world.level.StructureManager structureQueries;

    private boolean closed;
    private FreshPreFeatureLighting lightQueries;

    void bindStructureQueries(net.minecraft.world.level.StructureManager manager) {
        if (structureQueries != null)
            throw new IllegalStateException("B structure queries already bound");
        structureQueries = java.util.Objects.requireNonNull(manager);
    }

    public java.util.Optional<net.minecraft.world.level.StructureManager> structureQueries(
            BlockPos position) {
        requireOpen();
        if (!new ChunkPos(position).equals(writer))
            throw new IllegalArgumentException("B structure predicate outside owner " + position);
        return java.util.Optional.ofNullable(structureQueries);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Closed strict B region");
    }

    public void close() {
        closed = true;
        lightQueries = null;
    }

    public StrictFeatureRegion(
            ServerLevel level,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkStep step,
            ChunkAccess target,
            Map<Long, ? extends ChunkAccess> inputs,
            Consumer<String> rejected,
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        super(level, cache, step, target);
        this.writer = target.getPos();
        this.inputs = Map.copyOf(inputs);
        this.rejected = rejected;
        resourceHost = level;
        nativeStep = step;
        this.generator = java.util.Objects.requireNonNull(generator);
        this.randomState = java.util.Objects.requireNonNull(randomState);
    }

    public void bindOuterReadSource(
            java.util.function.BiFunction<
                            Integer, Integer, net.minecraft.world.level.chunk.ProtoChunk>
                    resolver) {
        if (outerResolver != null) throw new IllegalStateException("Outer reads already bound");
        outerResolver = java.util.Objects.requireNonNull(resolver);
    }

    public Map<Long, net.minecraft.world.level.chunk.ProtoChunk> outerReadInputs() {
        return Map.copyOf(outerRawInputs);
    }

    public net.minecraft.world.level.chunk.CarvingMask carvingMask(
            ChunkPos pos, net.minecraft.world.level.levelgen.GenerationStep.Carving stage) {
        requireOpen();
        // PlacementContext uses getChunk(x,z), whose native requirement is EMPTY, not CARVERS.
        var chunk = getChunk(pos.x, pos.z, ChunkStatus.EMPTY, true);
        if (!(chunk instanceof StrictFeatureChunk capability))
            throw new IllegalStateException("Foreign carving-mask input");
        return capability.carvingMask(stage);
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
        requireOpen();
        if (status.isAfter(ChunkStatus.CARVERS))
            throw new IllegalArgumentException("B cannot request " + status);
        var source = inputs.get(ChunkPos.asLong(x, z));
        if (source == null && outerResolver != null) {
            long distance = Math.max(Math.abs((long) x - writer.x), Math.abs((long) z - writer.z));
            if (distance >= nativeStep.directDependencies().size()
                    || status.isAfter(nativeStep.directDependencies().get((int) distance)))
                throw new IllegalArgumentException(
                        "Native FEATURES cannot provide requested outer status " + status);
            source =
                    outerInputs.computeIfAbsent(
                            ChunkPos.asLong(x, z),
                            key -> {
                                var chunk = outerResolver.apply(x, z);
                                var expected = FeatureOuterInputs.status(writer, x, z);
                                if (!chunk.getPos().equals(new ChunkPos(x, z))
                                        || chunk.getPersistedStatus() != expected)
                                    throw new IllegalStateException(
                                            "Outer input owner/stage differs from native prerequisite");
                                outerRawInputs.put(key, chunk);
                                return new StrictFeatureChunk(
                                        chunk,
                                        writer,
                                        resourceHost
                                                .registryAccess()
                                                .registryOrThrow(
                                                        net.minecraft.core.registries.Registries
                                                                .BIOME),
                                        rejected,
                                        resourceHost.registryAccess());
                            });
        }
        if (source == null)
            throw new IllegalArgumentException(
                    "Outside B finite Pre-FEATURES frontier " + x + "," + z);
        return source;
    }

    @Override
    public boolean hasChunk(int x, int z) {
        requireOpen();
        if (inputs.containsKey(ChunkPos.asLong(x, z))) return true;
        long distance = Math.max(Math.abs((long) x - writer.x), Math.abs((long) z - writer.z));
        return outerResolver != null && distance < nativeStep.accumulatedDependencies().size();
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        requireOpen();
        if (new ChunkPos(pos).equals(writer)) return true;
        rejected.accept("region_block");
        return false;
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        if (!new ChunkPos(entity.blockPosition()).equals(writer)) {
            rejected.accept("region_entity");
            return false;
        }
        getChunk(writer.x, writer.z).addEntity(entity);
        return true;
    }

    @Override
    public LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
        return guardedTicks(super.getBlockTicks(), "region_block_tick");
    }

    @Override
    public LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
        return guardedTicks(super.getFluidTicks(), "region_fluid_tick");
    }

    private <T> LevelTickAccess<T> guardedTicks(LevelTickAccess<T> input, String api) {
        return new LevelTickAccess<>() {
            @Override
            public void schedule(ScheduledTick<T> tick) {
                // Reject before native WorldGenTickAccess looks up a potentially distant chunk.
                if (new ChunkPos(tick.pos()).equals(writer)) input.schedule(tick);
                else rejected.accept(api);
            }

            @Override
            public boolean hasScheduledTick(BlockPos pos, T type) {
                return input.hasScheduledTick(pos, type);
            }

            @Override
            public boolean willTickThisTick(BlockPos pos, T type) {
                return input.willTickThisTick(pos, type);
            }

            @Override
            public int count() {
                return input.count();
            }
        };
    }

    @Override
    public boolean isOldChunkAround(ChunkPos pos, int radius) {
        requireOpen();
        return false;
    }

    @Override
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
        requireOpen();
        escapes.merge("forbidden:physicalLightEngine", 1L, Long::sum);
        throw new UnsupportedOperationException("B has no initialized local light engine");
    }

    @Override
    public int getBrightness(net.minecraft.world.level.LightLayer layer, BlockPos position) {
        requireOpen();
        return readPreFeatureBrightness(layer, position);
    }

    @Override
    public int getRawBrightness(BlockPos position, int skyDarken) {
        requireOpen();
        return readPreFeatureRawBrightness(position, skyDarken);
    }

    protected int readPreFeatureBrightness(
            net.minecraft.world.level.LightLayer layer, BlockPos pos) {
        return localLightQueries().brightness(layer, pos);
    }

    protected int readPreFeatureRawBrightness(BlockPos pos, int skyDarken) {
        return localLightQueries().rawBrightness(pos, skyDarken);
    }

    private FreshPreFeatureLighting localLightQueries() {
        if (lightQueries == null) {
            if (resourceHost.getLightEngine().getClass()
                    != net.minecraft.server.level.ThreadedLevelLightEngine.class) {
                escapes.merge(
                        "forbidden:hostLightEngine:"
                                + resourceHost.getLightEngine().getClass().getName(),
                        1L,
                        Long::sum);
                throw new UnsupportedOperationException(
                        "Unaudited host light-engine implementation");
            }
            lightQueries = new FreshPreFeatureLighting(this, dimensionType().hasSkyLight());
        }
        return lightQueries;
    }

    @Override
    public ServerLevel getLevel() {
        requireOpen();
        String caller =
                StackWalker.getInstance()
                        .walk(
                                frames ->
                                        frames.filter(
                                                        frame ->
                                                                !frame.getClassName()
                                                                        .equals(
                                                                                StrictFeatureRegion
                                                                                        .class
                                                                                        .getName()))
                                                .findFirst()
                                                .map(
                                                        frame ->
                                                                frame.getClassName()
                                                                        + "."
                                                                        + frame.getMethodName())
                                                .orElse("unknown"));
        boolean known =
                java.util.Set.of(
                                "net.minecraft.world.level.levelgen.feature.FossilFeature.place",
                                "net.minecraft.world.level.levelgen.feature.SpikeFeature.placeSpike",
                                "net.minecraft.world.level.levelgen.structure.structures.EndCityPieces$EndCityPiece.handleDataMarker",
                                "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftCorridor.createChest",
                                "net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$OceanMonumentPiece.spawnElder",
                                "net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces$OceanRuinPiece.handleDataMarker",
                                "net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece.postProcess",
                                "net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece.spawnCat",
                                "net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$WoodlandMansionPiece.handleDataMarker",
                                "net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.createEntityIgnoreException",
                                "net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor.finalizeProcessing",
                                "net.minecraft.world.entity.animal.Cat.finalizeSpawn")
                        .contains(caller);
        escapes.merge((known ? "" : "forbidden:") + caller, 1L, Long::sum);
        if (!known)
            throw new UnsupportedOperationException(
                    "Unaudited 1.21.1 B physical level escape " + caller);
        return resourceHost;
    }

    public void assertNoForbiddenEscapes() {
        // Native template entity loading catches Exception. A swallowed guard must still fail B.
        if (escapes.keySet().stream().anyMatch(key -> key.startsWith("forbidden:")))
            throw new IllegalStateException(
                    "Native code swallowed a forbidden B world access: " + escapes);
    }

    public void deferPoiNotification(
            net.minecraft.world.level.block.state.BlockState before,
            net.minecraft.world.level.block.state.BlockState after) {
        requireOpen();
        if (!net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(before)
                .equals(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(after)))
            rejected.accept("deferred_physical_poi_notification");
    }

    @Override
    public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(
            int x, int y, int z) {
        requireOpen();
        return generator.getBiomeSource().getNoiseBiome(x, y, z, randomState.sampler());
    }

    public Map<String, Long> levelEscapeCounts() {
        return Map.copyOf(escapes);
    }
}
