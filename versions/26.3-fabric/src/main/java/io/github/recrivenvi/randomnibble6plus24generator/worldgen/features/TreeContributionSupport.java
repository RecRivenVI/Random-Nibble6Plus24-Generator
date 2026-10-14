package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.*;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import net.minecraft.world.level.levelgen.feature.trunkplacers.*;
import net.minecraft.world.level.levelgen.placement.*;

/** A bounded capability audit, not a replacement for native tree/selector/RNG algorithms. */
public final class TreeContributionSupport {
    private TreeContributionSupport() {}

    public static String unsupported(PlacedFeature placed, HolderLookup.Provider registries) {
        return unsupported(placed, registries, false);
    }

    /** Bounded local evaluation; C must additionally audit every transport output. */
    public static String unsupportedNeighbor(
            PlacedFeature placed, HolderLookup.Provider registries) {
        return unsupported(placed, registries, true);
    }

    private static String unsupported(
            PlacedFeature placed, HolderLookup.Provider registries, boolean conifers) {
        try {
            audit(placed, registries, 0, conifers);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static void audit(
            PlacedFeature placed, HolderLookup.Provider registries, int depth, boolean conifers) {
        if (depth > 16) throw new IllegalArgumentException("unaudited selector depth");
        long squares =
                placed.placement().stream()
                        .filter(p -> p.getClass() == InSquarePlacement.class)
                        .count();
        if (squares > 1 || depth > 0 && squares != 0)
            throw new IllegalArgumentException("unbounded repeated InSquare");
        for (var p : placed.placement()) {
            Class<?> type = p.getClass();
            if (type == HeightmapPlacement.class
                    && !java.util.Set.of(
                                    net.minecraft.world.level.levelgen.Heightmap.Types
                                            .OCEAN_FLOOR_WG,
                                    net.minecraft.world.level.levelgen.Heightmap.Types
                                            .WORLD_SURFACE_WG,
                                    net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,
                                    net.minecraft.world.level.levelgen.Heightmap.Types
                                            .WORLD_SURFACE)
                            .contains(((HeightmapPlacement) p).heightmap()))
                throw new IllegalArgumentException("unaudited tree heightmap lifecycle");
            if (type == BlockPredicateFilter.class)
                predicate(((BlockPredicateFilter) p).predicate(), registries);
            else if (type != CountPlacement.class
                    && type != InSquarePlacement.class
                    && type != HeightmapPlacement.class
                    && type != SurfaceWaterDepthFilter.class
                    && type != BiomeFilter.class
                    && type != RarityFilter.class)
                throw new IllegalArgumentException(
                        "unaudited tree modifier " + type.getSimpleName());
        }
        var feature = placed.feature().value();
        if (feature.getClass() == RandomSelectorFeature.class) {
            var selector = (RandomSelectorFeature) feature;
            for (var weighted : selector.features())
                audit(weighted.feature().value(), registries, depth + 1, conifers);
            audit(selector.defaultFeature().value(), registries, depth + 1, conifers);
        } else if (feature.getClass() == TreeFeature.class) {
            var tree = (TreeFeature) feature;
            Class<?> foliageType = tree.foliagePlacer().getClass();
            if (foliageType != BlobFoliagePlacer.class
                    && (!conifers
                            || (foliageType != SpruceFoliagePlacer.class
                                    && foliageType != PineFoliagePlacer.class)))
                throw new IllegalArgumentException("unaudited foliage geometry");
            if (tree.trunkPlacer().getClass() != StraightTrunkPlacer.class
                    || tree.minimumSize().getClass() != TwoLayersFeatureSize.class
                    || tree.rootPlacer().isPresent())
                throw new IllegalArgumentException("requires straight tree without roots");
            var trunk =
                    TrunkPlacer.CODEC
                            .encodeStart(JsonOps.INSTANCE, tree.trunkPlacer())
                            .getOrThrow()
                            .getAsJsonObject();
            int height =
                    trunk.get("base_height").getAsInt()
                            + trunk.get("height_rand_a").getAsInt()
                            + trunk.get("height_rand_b").getAsInt();
            if (height > 32) throw new IllegalArgumentException("unaudited tree height");
            var foliage =
                    FoliagePlacer.CODEC
                            .encodeStart(JsonOps.INSTANCE, tree.foliagePlacer())
                            .getOrThrow()
                            .getAsJsonObject();
            if (tree.foliagePlacer().getClass() == BlobFoliagePlacer.class) {
                if (!foliage.get("radius").isJsonPrimitive()
                        || foliage.get("radius").getAsInt() > 2
                        || !foliage.get("offset").isJsonPrimitive()
                        || foliage.get("offset").getAsInt() != 0
                        || foliage.get("height").getAsInt() > 3)
                    throw new IllegalArgumentException("unaudited blob geometry");
            } else if (conifers
                    && (tree.foliagePlacer().getClass() == SpruceFoliagePlacer.class
                            || tree.foliagePlacer().getClass() == PineFoliagePlacer.class)) {
                // StraightTrunkPlacer has one unshifted, single-trunk attachment. Pine adds
                // nextInt(trunkHeight - foliageHeight + 1) to its radius; radius alone is not
                // a safe reach bound. Keep that total <= 8, plus the native leaf-distance halo.
                if (height > 10 || !tree.decorators().isEmpty())
                    throw new IllegalArgumentException("unaudited conifer height/decorators");
                var size =
                        net.minecraft.world.level.levelgen.feature.featuresize.FeatureSize.CODEC
                                .encodeStart(JsonOps.INSTANCE, tree.minimumSize())
                                .getOrThrow()
                                .getAsJsonObject();
                for (String key : java.util.List.of("lower_size", "upper_size"))
                    if (size.has(key) && size.get(key).getAsInt() > 2)
                        throw new IllegalArgumentException("unaudited conifer clearance");
                boundedProvider(foliage.get("offset"), 0, 2);
                if (tree.foliagePlacer().getClass() == SpruceFoliagePlacer.class) {
                    boundedProvider(foliage.get("radius"), 0, 3);
                    boundedProvider(foliage.get("trunk_height"), 1, 2);
                } else {
                    boundedProvider(foliage.get("radius"), 0, 1);
                    boundedProvider(foliage.get("height"), 3, 4);
                }
            } else throw new IllegalArgumentException("unaudited foliage geometry");
            provider(tree.trunkProvider().value(), registries, 0);
            provider(tree.foliageProvider().value(), registries, 0);
            provider(tree.belowTrunkProvider().value(), registries, 0);
            for (var d : tree.decorators()) decorator(d, registries);
        } else if (feature.getClass() == FallenTreeFeature.class) {
            var fallen = (FallenTreeFeature) feature;
            if (fallen.logLength().maxInclusive() > 15 || !fallen.stumpDecorators().isEmpty())
                throw new IllegalArgumentException("unaudited fallen tree reach/stump decorators");
            provider(fallen.trunkProvider().value(), registries, 0);
            for (var d : fallen.logDecorators()) decorator(d, registries);
        } else
            throw new IllegalArgumentException(
                    "unaudited tree feature " + feature.getClass().getSimpleName());
    }

    private static void boundedProvider(JsonElement encoded, int min, int max) {
        var provider =
                net.minecraft.util.valueproviders.IntProviders.CODEC
                        .parse(JsonOps.INSTANCE, encoded)
                        .getOrThrow();
        if ((provider.getClass() != net.minecraft.util.valueproviders.ConstantInt.class
                        && provider.getClass()
                                != net.minecraft.util.valueproviders.UniformInt.class)
                || provider.minInclusive() < min
                || provider.maxInclusive() > max)
            throw new IllegalArgumentException("unaudited conifer integer provider");
    }

    private static void decorator(TreeDecorator decorator, HolderLookup.Provider registries) {
        if (decorator.getClass() == BeehiveDecorator.class) return;
        if (decorator.getClass() == AttachedToLogsDecorator.class) {
            var encoded =
                    TreeDecorator.CODEC
                            .encodeStart(
                                    RegistryOps.create(JsonOps.INSTANCE, registries), decorator)
                            .getOrThrow()
                            .getAsJsonObject();
            provider(
                    BlockStateProvider.CODEC
                            .parse(
                                    RegistryOps.create(JsonOps.INSTANCE, registries),
                                    encoded.get("block_provider"))
                            .getOrThrow()
                            .value(),
                    registries,
                    0);
            return;
        }
        throw new IllegalArgumentException(
                "unaudited tree decorator " + decorator.getClass().getSimpleName());
    }

    private static void provider(
            BlockStateProvider provider, HolderLookup.Provider registries, int depth) {
        if (depth > 16) throw new IllegalArgumentException("unaudited provider depth");
        if (provider.getClass() == SimpleStateProvider.class) {
            if (((SimpleStateProvider) provider).state().hasBlockEntity())
                throw new IllegalArgumentException("provider creates unaudited BlockEntity");
        } else if (provider.getClass() == WeightedStateProvider.class) {
            for (var state : ((WeightedStateProvider) provider).weightedList().unwrap())
                if (state.value().hasBlockEntity())
                    throw new IllegalArgumentException("weighted provider creates BlockEntity");
        } else if (provider.getClass() == RuleBasedStateProvider.class) {
            var rule = (RuleBasedStateProvider) provider;
            if (rule.fallback() != null) provider(rule.fallback().value(), registries, depth + 1);
            for (var r : rule.rules()) {
                predicate(r.ifTrue(), registries);
                provider(r.then().value(), registries, depth + 1);
            }
        } else
            throw new IllegalArgumentException(
                    "unaudited state provider " + provider.getClass().getSimpleName());
    }

    private static void predicate(BlockPredicate predicate, HolderLookup.Provider registries) {
        auditPredicate(
                BlockPredicate.CODEC
                        .encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), predicate)
                        .getOrThrow(),
                0);
    }

    private static void auditPredicate(JsonElement element, int depth) {
        if (depth > 16) throw new IllegalArgumentException("unaudited predicate depth");
        var obj = element.getAsJsonObject();
        String type = obj.get("type").getAsString();
        if (!java.util.Set.of(
                        "minecraft:matching_blocks",
                        "minecraft:matching_block_tag",
                        "minecraft:would_survive",
                        "minecraft:not",
                        "minecraft:all_of",
                        "minecraft:any_of",
                        "minecraft:true")
                .contains(type))
            throw new IllegalArgumentException("unaudited tree predicate " + type);
        if (obj.has("offset"))
            for (var n : obj.getAsJsonArray("offset"))
                if (Math.abs(n.getAsInt()) > 1)
                    throw new IllegalArgumentException("unbounded predicate offset");
        if (obj.has("predicate")) auditPredicate(obj.get("predicate"), depth + 1);
        if (obj.has("predicates"))
            for (var child : obj.getAsJsonArray("predicates")) auditPredicate(child, depth + 1);
    }
}
