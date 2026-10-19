package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.BiomePaletteChunk;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Vanilla population on the lit physical target, with the V2 same-S(C) stored-biome authority. */
public final class MosaicPhysicalSpawner {
    private MosaicPhysicalSpawner() {}

    public static CompletableFuture<ChunkAccess> spawn(
            WorldGenContext world,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess target) {
        var level = world.level();
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        if (!(world.generator() instanceof MosaicChunkGenerator generator)
                || target.getPersistedStatus().isBefore(ChunkStatus.LIGHT)
                || !target.isLightCorrect())
            throw new IllegalStateException(
                    "SPAWN requires this Mosaic world's physical LIGHT target");
        var state = ((MosaicPhysicalLevelOwner) level).mosaic$physicalState();
        long seed = runtime.resolveLocalWorldSeed(level.dimension(), target.getPos());
        var biomes = state.takeSpawnBiomes(target);
        var collisions = state.takeSpawnCollisions(target);
        if (biomes == null) biomes = coldBiomes(level, seed, target.getPos(), runtime.profile());
        biomes.validate(level.dimension(), target.getPos(), seed, runtime.profile());
        if (collisions != null) collisions.requireProvenance(biomes);
        var noise = generator.newLocalGenerator();
        var random =
                RandomState.create(
                        generator.generatorSettings().value(),
                        level.registryAccess().lookupOrThrow(Registries.NOISE),
                        seed);
        var inputs = biomes;
        var region =
                SpawnConstructorScope.construct(
                        level,
                        seed,
                        random,
                        () ->
                                new Region(
                                        level,
                                        cache,
                                        step,
                                        target,
                                        seed,
                                        inputs,
                                        collisions,
                                        noise,
                                        random));
        if (region.getSeed() != seed)
            throw new IllegalStateException("SPAWN observed the physical master seed");
        try {
            if (!target.isUpgrading()) noise.spawnOriginalMobs(region);
        } finally {
            region.closeCollisionInputs();
        }
        state.spawned();
        return CompletableFuture.completedFuture(target);
    }

    private static SpawnBiomeSnapshot coldBiomes(
            ServerLevel level,
            long seed,
            ChunkPos pos,
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile
                    profile) {
        // A saved LIGHT/FEATURES chunk has no transient snapshot. Recreate only exact native
        // BIOMES; never terrain, FEATURES or Artifact, and never use an unrelated physical palette.
        Map<ChunkPos, ChunkAccess> palettes = new HashMap<>();
        try (var root = new StructureGenerationContext(level, seed, pos)) {
            for (var neighbor : SpawnBiomeSnapshot.neighbors(pos)) {
                try (var context = root.fork(neighbor)) {
                    var structures = CompactStructureInputs.resolve(context);
                    var palette =
                            new BiomePaletteChunk(
                                    neighbor,
                                    level,
                                    level.registryAccess().registryOrThrow(Registries.BIOME));
                    context.generator()
                            .createBiomes(
                                    context.randomState(),
                                    Blender.empty(),
                                    structures.manager(),
                                    palette)
                            .join();
                    palettes.put(neighbor, palette);
                }
            }
            return SpawnBiomeSnapshot.capture(level.dimension(), pos, seed, profile, palettes::get);
        }
    }

    private static final class Region extends WorldGenRegion {
        private final ChunkAccess target;
        private final long seed;
        private final SpawnBiomeSnapshot palettes;
        private final BiomeManager biomes;
        private final NoiseBasedChunkGenerator generator;
        private final RandomState random;
        private final SpawnCollisionSnapshot collisions;
        private final ServerLevel host;
        private final int collisionRadius;
        private final Map<ChunkPos, ChunkAccess> coldCollisionInputs = new HashMap<>();

        Region(
                ServerLevel level,
                StaticCache2D<GenerationChunkHolder> cache,
                ChunkStep step,
                ChunkAccess target,
                long seed,
                SpawnBiomeSnapshot palettes,
                SpawnCollisionSnapshot collisions,
                NoiseBasedChunkGenerator generator,
                RandomState random) {
            super(level, cache, step, target);
            this.target = target;
            this.seed = seed;
            this.palettes = palettes;
            this.generator = generator;
            this.random = random;
            this.collisions = collisions;
            this.host = level;
            collisionRadius = step.directDependencies().getRadiusOf(ChunkStatus.BIOMES);
            if (collisionRadius != 1)
                throw new IllegalStateException("Unaudited native population frontier");
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
                    || status.isAfter(ChunkStatus.LIGHT))
                throw new IllegalStateException(
                        "Unproven non-target SPAWN block/height read "
                                + x
                                + ","
                                + z
                                + " at "
                                + status);
            return target;
        }

        @Override
        public BlockGetter getChunkForCollisions(int x, int z) {
            var position = new ChunkPos(x, z);
            if (position.equals(target.getPos())) return target;
            if (position.getChessboardDistance(target.getPos()) > collisionRadius)
                throw new IllegalStateException(
                        "Population collision outside native dependency frontier " + position);
            var owner = ((MosaicPhysicalLevelOwner) host).mosaic$physicalState();
            owner.populationCollisionRead();
            if (collisions != null) return collisions.view(position);
            // Saved pre-SPAWN chunks carry no transient input. Compute only an actual requested
            // source with the native pre-FEATURES stages; never substitute physical S(N) or air.
            return coldCollisionInputs.computeIfAbsent(
                    position,
                    requested -> {
                        owner.populationColdCollisionSource();
                        try (var root =
                                        new StructureGenerationContext(
                                                host, seed, target.getPos());
                                var context = root.fork(requested)) {
                            var structures = CompactStructureInputs.resolve(context);
                            return new LightweightTerrainGenerator(context, structures)
                                    .generateSharingWorld((stage, chunk) -> {})
                                    .target();
                        }
                    });
        }

        private void closeCollisionInputs() {
            coldCollisionInputs.clear();
        }

        @Override
        public boolean addFreshEntity(Entity entity) {
            if (!entity.chunkPosition().equals(target.getPos()))
                throw new IllegalStateException("SPAWN entity escaped its physical target");
            return super.addFreshEntity(entity);
        }
    }
}
