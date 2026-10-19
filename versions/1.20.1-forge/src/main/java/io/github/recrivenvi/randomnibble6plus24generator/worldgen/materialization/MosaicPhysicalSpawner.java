package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.BiomePaletteChunk;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Native population uses the lit physical target and V2 seed-local stored-biome/collision inputs.
 */
public final class MosaicPhysicalSpawner {
    private MosaicPhysicalSpawner() {}

    public static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> spawn(
            ServerLevel level, ChunkAccess target) {
        var lifecycle =
                (MosaicPhysicalLevelOwner.forLevel(level))
                        .randomnibble6plus24generator$generationLifecycle();
        synchronized (lifecycle) {
            if (lifecycle.closing()) return ChunkHolder.UNLOADED_CHUNK_FUTURE;
            lifecycle.workerAccepted();
        }
        try {
            return populate(level, target, lifecycle);
        } catch (RuntimeException | Error error) {
            if (!lifecycle.isExpected(error)) {
                (MosaicPhysicalLevelOwner.forLevel(level))
                        .mosaic$physicalState()
                        .retainFailure(error);
                throw error;
            }
            lifecycle.recordExpectedTermination();
            return ChunkHolder.UNLOADED_CHUNK_FUTURE;
        } finally {
            lifecycle.workerFinished();
        }
    }

    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> populate(
            ServerLevel level, ChunkAccess target, MosaicGenerationLifecycle lifecycle) {
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext
                runtime;
        synchronized (lifecycle) {
            lifecycle.checkPreparing();
            runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        }
        if (!(level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator mosaic)
                || !target.getStatus().isOrAfter(ChunkStatus.LIGHT)
                || !target.isLightCorrect())
            throw new IllegalStateException(
                    "Population requires this world's physical LIGHT target");
        var state = (MosaicPhysicalLevelOwner.forLevel(level)).mosaic$physicalState();
        var seed = runtime.resolveLocalWorldSeed(level.dimension(), target.getPos());
        var biomes = state.takeBiomes(target);
        var collisions = state.takeCollisions(target);
        if (biomes == null) biomes = coldBiomes(level, seed, target.getPos(), runtime.profile());
        biomes.validate(level.dimension(), target.getPos(), seed, runtime.profile());
        if (collisions != null) collisions.requireProvenance(biomes);
        var generator = mosaic.newLocalGenerator();
        var random =
                RandomState.create(
                        mosaic.generatorSettings().value(),
                        level.registryAccess().lookupOrThrow(Registries.NOISE),
                        seed);
        var palettes = biomes;
        var terrain = collisions;
        var region =
                SpawnConstructorScope.construct(
                        level,
                        seed,
                        random,
                        () ->
                                new Region(
                                        level, target, seed, palettes, terrain, generator, random));
        try {
            if (region.getSeed() != seed)
                throw new IllegalStateException("Population escaped to the master seed");
            synchronized (lifecycle) {
                lifecycle.checkPreparing();
                if (MosaicWorldIdentity.runtimeContext(level).orElseThrow() != runtime)
                    throw new IllegalStateException("Replaced population authority");
                if (!target.isUpgrading()) generator.spawnOriginalMobs(region);
            }
        } finally {
            region.cold.clear();
        }
        if (target instanceof ProtoChunk proto) proto.setStatus(ChunkStatus.SPAWN);
        state.spawned();
        return CompletableFuture.completedFuture(Either.left(target));
    }

    private static SpawnBiomeSnapshot coldBiomes(
            ServerLevel host,
            long seed,
            ChunkPos target,
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile
                    profile) {
        Map<ChunkPos, ChunkAccess> chunks = new HashMap<>();
        for (var position : SpawnBiomeSnapshot.neighbors(target)) {
            try (var context = new StructureGenerationContext(host, seed, position)) {
                var structures = CompactStructureInputs.resolve(context);
                var palette =
                        new BiomePaletteChunk(
                                position,
                                host,
                                host.registryAccess().registryOrThrow(Registries.BIOME));
                context.generator()
                        .createBiomes(
                                Runnable::run,
                                context.randomState(),
                                Blender.empty(),
                                structures.manager(),
                                palette)
                        .join();
                chunks.put(position, palette);
            }
        }
        return SpawnBiomeSnapshot.capture(host.dimension(), target, seed, profile, chunks::get);
    }

    private static final class Region extends WorldGenRegion {
        private final ServerLevel host;
        private final ChunkAccess target;
        private final long seed;
        private final SpawnBiomeSnapshot palettes;
        private final SpawnCollisionSnapshot collisions;
        private final net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator;
        private final RandomState random;
        private final BiomeManager biomes;
        private final Map<ChunkPos, ChunkAccess> cold = new HashMap<>();

        Region(
                ServerLevel host,
                ChunkAccess target,
                long seed,
                SpawnBiomeSnapshot palettes,
                SpawnCollisionSnapshot collisions,
                net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator,
                RandomState random) {
            super(host, List.of(target), ChunkStatus.SPAWN, -1);
            this.host = host;
            this.target = target;
            this.seed = seed;
            this.palettes = palettes;
            this.collisions = collisions;
            this.generator = generator;
            this.random = random;
            biomes = new BiomeManager(this::getNoiseBiome, BiomeManager.obfuscateSeed(seed));
        }

        @Override
        public long getSeed() {
            return seed;
        }

        @Override
        public BiomeManager getBiomeManager() {
            return biomes;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            var pos = new ChunkPos(QuartPos.toSection(x), QuartPos.toSection(z));
            return pos.equals(target.getPos())
                    ? target.getNoiseBiome(x, y, z)
                    : palettes.biome(pos, x, y, z);
        }

        @Override
        public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
            return generator.getBiomeSource().getNoiseBiome(x, y, z, random.sampler());
        }

        @Override
        public ChunkAccess getChunk(int x, int z) {
            return getChunk(x, z, ChunkStatus.EMPTY, true);
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
            if (x != target.getPos().x
                    || z != target.getPos().z
                    || status.isOrAfter(ChunkStatus.FULL))
                throw new IllegalStateException(
                        "Unproven non-target population block read " + x + "," + z + " " + status);
            return target;
        }

        @Override
        public BlockGetter getChunkForCollisions(int x, int z) {
            var pos = new ChunkPos(x, z);
            if (pos.equals(target.getPos())) return target;
            if (Math.max(
                            Math.abs((long) x - target.getPos().x),
                            Math.abs((long) z - target.getPos().z))
                    > ChunkStatus.SPAWN.getRange())
                throw new IllegalStateException("Population collision outside native frontier");
            var state = ((MosaicPhysicalLevelOwner) host).mosaic$physicalState();
            state.collisionRead();
            if (collisions != null) return collisions.view(pos);
            return cold.computeIfAbsent(
                    pos,
                    source -> {
                        state.coldCollisionSource();
                        try (var context = new StructureGenerationContext(host, seed, source)) {
                            return new LightweightTerrainGenerator(
                                            context, CompactStructureInputs.resolve(context))
                                    .generate((s, c) -> {})
                                    .target();
                        }
                    });
        }
    }
}
