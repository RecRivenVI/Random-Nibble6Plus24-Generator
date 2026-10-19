package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

/** Native region behavior with strictly target-local B write authority. */
public class StrictFeatureRegion extends WorldGenRegion {
    private final ChunkPos writer;
    private final Map<Long, ChunkAccess> inputs;
    private final Consumer<String> rejected;
    private final ServerLevel resourceHost;
    private final ChunkStep nativeStep;
    private final Map<Long, ChunkAccess> outerInputs = new java.util.HashMap<>();
    private final Map<Long, net.minecraft.world.level.chunk.ProtoChunk> outerRawInputs =
            new java.util.HashMap<>();
    private java.util.function.BiFunction<
                    Integer, Integer, net.minecraft.world.level.chunk.ProtoChunk>
            outerResolver;
    private final Map<String, Long> escapes = new java.util.TreeMap<>();
    private net.minecraft.world.level.StructureManager structureQueries;

    void bindStructureQueries(
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                            .CompactStructureInputs
                    structures) {
        var context =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                        .GenerationContextRegistry.find(this)
                        .orElseThrow();
        structures.requireCompatible(context);
        if (structureQueries != null)
            throw new IllegalStateException("B structure queries already bound");
        structureQueries = structures.manager();
    }

    public java.util.Optional<net.minecraft.world.level.StructureManager> structureQueries(
            BlockPos position) {
        if (structureQueries == null) return java.util.Optional.empty();
        if (io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                .GenerationContextRegistry.find(this)
                .isEmpty()) throw new IllegalStateException("Closed B structure queries");
        if (!ChunkPos.containing(position).equals(writer))
            throw new IllegalArgumentException("B structure predicate outside owner " + position);
        return java.util.Optional.of(structureQueries);
    }

    public StrictFeatureRegion(
            ServerLevel level,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkStep step,
            ChunkAccess target,
            Map<Long, ? extends ChunkAccess> inputs,
            Consumer<String> rejected) {
        super(level, cache, step, target);
        this.writer = target.getPos();
        this.inputs = Map.copyOf(inputs);
        this.rejected = rejected;
        resourceHost = level;
        nativeStep = step;
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

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
        if (status.isAfter(ChunkStatus.CARVERS))
            throw new IllegalArgumentException("B cannot request " + status);
        var source = inputs.get(ChunkPos.pack(x, z));
        if (source == null && outerResolver != null) {
            long distance =
                    Math.max(Math.abs((long) x - writer.x()), Math.abs((long) z - writer.z()));
            if (distance >= nativeStep.directDependencies().size()
                    || status.isAfter(nativeStep.directDependencies().get((int) distance)))
                throw new IllegalArgumentException(
                        "Native FEATURES cannot provide requested outer status " + status);
            source =
                    outerInputs.computeIfAbsent(
                            ChunkPos.pack(x, z),
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
                                        resourceHost.palettedContainerFactory(),
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
        if (inputs.containsKey(ChunkPos.pack(x, z))) return true;
        long distance = Math.max(Math.abs((long) x - writer.x()), Math.abs((long) z - writer.z()));
        return outerResolver != null && distance < nativeStep.accumulatedDependencies().size();
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        if (ChunkPos.containing(pos).equals(writer)) return true;
        rejected.accept("region_block");
        return false;
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        if (!ChunkPos.containing(entity.blockPosition()).equals(writer)) {
            rejected.accept("region_entity");
            return false;
        }
        getChunk(writer.x(), writer.z()).addEntity(entity);
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
                if (ChunkPos.containing(tick.pos()).equals(writer)) input.schedule(tick);
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
    public ServerLevel getLevel() {
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
                // 26.1.2 FossilFeature.place only obtains server-owned template resources here;
                // placement and terrain reads still use this bounded WorldGenLevel.
                caller.equals("net.minecraft.world.level.levelgen.feature.FossilFeature.place")
                        // TemplateFeature also obtains only the shared template manager here.
                        || caller.equals(
                                "net.minecraft.world.level.levelgen.feature.TemplateFeature.place")
                        || java.util.List.of(
                                        "net.minecraft.world.level.levelgen.feature.EndSpikeFeature.",
                                        "net.minecraft.world.level.levelgen.feature.treedecorators.PaleMossDecorator.",
                                        "net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor.",
                                        "net.minecraft.world.level.levelgen.structure.structures.EndCityPieces$",
                                        "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$",
                                        "net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$",
                                        "net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces$",
                                        "net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece.",
                                        "net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$",
                                        "net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.")
                                .stream()
                                .anyMatch(caller::startsWith);
        if (!known)
            throw new UnsupportedOperationException("Unknown B physical level escape " + caller);
        escapes.merge(caller, 1L, Long::sum);
        return super.getLevel();
    }

    public Map<String, Long> levelEscapeCounts() {
        return Map.copyOf(escapes);
    }

    public static GenerationChunkHolder constructorCarrier(ChunkAccess target) {
        return new GenerationChunkHolder(target.getPos()) {
            @Override
            public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                return target;
            }

            @Override
            protected void addSaveDependency(CompletableFuture<?> future) {
                throw new UnsupportedOperationException("No physical lifecycle");
            }

            @Override
            public int getTicketLevel() {
                return 0;
            }

            @Override
            public int getQueueLevel() {
                return 0;
            }
        };
    }
}
