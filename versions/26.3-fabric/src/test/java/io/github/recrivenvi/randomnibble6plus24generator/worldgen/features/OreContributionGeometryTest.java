package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.junit.jupiter.api.Test;

class OreContributionGeometryTest {
    private static PalettedContainerFactory factory() {
        Holder<Biome> biome =
                MosaicTestWorlds.registries()
                        .lookupOrThrow(Registries.BIOME)
                        .getOrThrow(Biomes.PLAINS);
        IdMapper<Holder<Biome>> ids = new IdMapper<>();
        ids.add(biome);
        return new PalettedContainerFactory(
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                Blocks.AIR.defaultBlockState(),
                null,
                Strategy.createForBiomes(ids),
                biome,
                null);
    }

    private static ProtoChunk chunk(ChunkPos pos) {
        var c =
                new ProtoChunk(
                        pos,
                        UpgradeData.EMPTY,
                        LevelHeightAccessor.create(-64, 384),
                        factory(),
                        null);
        for (int y = 0; y < 64; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    c.getSection(c.getSectionIndex(y))
                            .setBlockState(x, y & 15, z, Blocks.STONE.defaultBlockState(), false);
        return c;
    }

    @Test
    void projectedGeometryMatchesNativeVoxelsAndFollowingRngAcrossEdgesAndCorners() {
        MosaicTestWorlds.registries();
        var origins =
                List.of(
                        new BlockPos(-1, 20, 8),
                        new BlockPos(16, 20, 8),
                        new BlockPos(8, 20, -1),
                        new BlockPos(8, 20, 16),
                        new BlockPos(-1, 20, -1),
                        new BlockPos(16, 20, 16),
                        new BlockPos(-16, 20, 8),
                        new BlockPos(31, 20, 8),
                        new BlockPos(8, 300, 8));
        for (RuleTest rule :
                List.of(
                        new BlockMatchTest(Blocks.STONE),
                        RuleTest.either(
                                new BlockMatchTest(Blocks.STONE),
                                HeightMatchTest.min(19),
                                new BlockMatchTest(Blocks.DEEPSLATE))))
            for (int size : List.of(1, 8, 17, 33, 64))
                for (int i = 0; i < origins.size(); i++) {
                    Map<Long, ProtoChunk> chunks = new HashMap<>();
                    for (int z = -2; z <= 2; z++)
                        for (int x = -2; x <= 2; x++) {
                            var p = new ChunkPos(x, z);
                            chunks.put(p.pack(), chunk(p));
                        }
                    WorldGenLevel nativeLevel =
                            (WorldGenLevel)
                                    Proxy.newProxyInstance(
                                            WorldGenLevel.class.getClassLoader(),
                                            new Class[] {WorldGenLevel.class},
                                            (proxy, method, args) -> {
                                                return switch (method.getName()) {
                                                    case "getHeight" ->
                                                            args == null || args.length == 0
                                                                    ? 384
                                                                    : 64;
                                                    case "ensureCanWrite" -> true;
                                                    case "getChunk" ->
                                                            Objects.requireNonNull(
                                                                    chunks.get(
                                                                            ChunkPos.pack(
                                                                                    (int) args[0],
                                                                                    (int)
                                                                                            args[
                                                                                                    1])));
                                                    case "getBlockState" -> {
                                                        var p = (BlockPos) args[0];
                                                        yield chunks.get(
                                                                        ChunkPos.containing(p)
                                                                                .pack())
                                                                .getBlockState(p);
                                                    }
                                                    case "getMinY" -> -64;
                                                    case "getHeightAccessorForGeneration" ->
                                                            LevelHeightAccessor.create(-64, 384);
                                                    default -> {
                                                        if (method.isDefault())
                                                            yield InvocationHandler.invokeDefault(
                                                                    proxy, method, args);
                                                        throw new UnsupportedOperationException(
                                                                method.toString());
                                                    }
                                                };
                                            });
                    var ore = new OreFeature(rule, Blocks.DIAMOND_ORE.defaultBlockState(), size);
                    RandomSource nativeRandom = RandomSource.create(263L + size * 31L + i),
                            projectedRandom = RandomSource.create(263L + size * 31L + i);
                    ore.place(nativeLevel, null, nativeRandom, origins.get(i));
                    var projected = chunk(new ChunkPos(0, 0));
                    OreContributionGeometry.project(
                            ore,
                            projectedRandom,
                            origins.get(i),
                            projected,
                            (x, z, n, y) -> y <= 64);
                    for (int y = 0; y < 64; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++) {
                                var p = new BlockPos(x, y, z);
                                assertEquals(
                                        chunks.get(new ChunkPos(0, 0).pack()).getBlockState(p),
                                        projected.getBlockState(p),
                                        size + ":" + origins.get(i) + ":" + p);
                            }
                    assertEquals(
                            nativeRandom.nextLong(),
                            projectedRandom.nextLong(),
                            "following placement RNG");
                }
    }

    @Test
    void unsafeFamiliesAndModifiersAreExplicitlyRejected() {
        MosaicTestWorlds.registries();
        var targets =
                List.of(
                        new BlockReplacement(
                                new BlockMatchTest(Blocks.STONE),
                                Blocks.COAL_ORE.defaultBlockState()));
        assertThrows(
                IllegalArgumentException.class,
                () -> NeighborOreContributions.requireSafeOre(new OreFeature(targets, 17, .5f)));
        assertThrows(
                IllegalArgumentException.class,
                () -> NeighborOreContributions.requireSafeOre(new OreFeature(targets, 65)));
        var safe = new OreFeature(targets, 17);
        assertNotNull(
                NeighborOreContributions.unsupported(
                        new PlacedFeature(
                                Holder.direct(safe),
                                List.of(
                                        HeightmapPlacement.onHeightmap(
                                                net.minecraft.world.level.levelgen.Heightmap.Types
                                                        .WORLD_SURFACE_WG)))));
        assertNull(
                NeighborOreContributions.unsupported(
                        new PlacedFeature(
                                Holder.direct(safe),
                                List.of(InSquarePlacement.spread(), BiomeFilter.biome()))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NeighborOreContributions.requireSafeOre(
                                new OreFeature(
                                        List.of(
                                                new BlockReplacement(
                                                        new RandomBlockMatchTest(Blocks.STONE, .5f),
                                                        Blocks.COAL_ORE.defaultBlockState())),
                                        17)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        NeighborOreContributions.requireSafeOre(
                                new OreFeature(
                                        new BlockMatchTest(Blocks.AIR),
                                        Blocks.COAL_ORE.defaultBlockState(),
                                        17)));
    }

    @Test
    void coarseHeightGuardOnlySkipsProvablyUnreachableNativeRanges() {
        var level =
                (WorldGenLevel)
                        Proxy.newProxyInstance(
                                WorldGenLevel.class.getClassLoader(),
                                new Class[] {WorldGenLevel.class},
                                (proxy, method, args) -> {
                                    return switch (method.getName()) {
                                        case "getHeightAccessorForGeneration" ->
                                                LevelHeightAccessor.create(-64, 384);
                                        case "getHeight" -> 384;
                                        case "getMinY" -> -64;
                                        default -> {
                                            if (method.isDefault())
                                                yield InvocationHandler.invokeDefault(
                                                        proxy, method, args);
                                            throw new UnsupportedOperationException(
                                                    method.toString());
                                        }
                                    };
                                });
        var context =
                new PlacementContext(
                        level, MosaicTestWorlds.normalOverworldGenerator(), Optional.empty());
        var ore =
                Holder.<Feature>direct(
                        new OreFeature(
                                new BlockMatchTest(Blocks.STONE),
                                Blocks.COAL_ORE.defaultBlockState(),
                                64));
        var low = net.minecraft.world.level.levelgen.VerticalAnchor.absolute(100);
        var high = net.minecraft.world.level.levelgen.VerticalAnchor.absolute(200);
        for (var provider :
                List.of(
                        net.minecraft.world.level.levelgen.heightproviders.ConstantHeight.of(low),
                        net.minecraft.world.level.levelgen.heightproviders.UniformHeight.of(
                                low, high),
                        net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight.of(
                                low, high))) {
            var placed =
                    new PlacedFeature(
                            ore,
                            List.of(InSquarePlacement.spread(), HeightRangePlacement.of(provider)));
            assertTrue(NeighborOreContributions.verticallyIrrelevant(placed, context, 63));
            assertFalse(
                    NeighborOreContributions.verticallyIrrelevant(placed, context, 93),
                    "boundary remains eligible");
        }
        var inverted =
                new PlacedFeature(
                        ore,
                        List.of(
                                InSquarePlacement.spread(),
                                HeightRangePlacement.of(
                                        net.minecraft.world.level.levelgen.heightproviders
                                                .UniformHeight.of(high, low))));
        assertFalse(
                NeighborOreContributions.verticallyIrrelevant(inverted, context, 63),
                "invalid range must not infer a bound");
    }
}
