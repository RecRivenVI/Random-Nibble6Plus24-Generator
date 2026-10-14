package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.Test;

class TreeContributionSupportTest {
    private PlacedFeature placed(String name) {
        return MosaicTestWorlds.registries()
                .lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(
                        ResourceKey.create(
                                Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(name)))
                .value();
    }

    @Test
    void boundedConifersPassBothEvaluationAndTransportAudits() {
        for (String name : java.util.List.of("spruce_checked", "pine_checked", "trees_taiga")) {
            var feature = placed(name);
            assertNull(
                    TreeContributionSupport.unsupportedNeighbor(
                            feature, MosaicTestWorlds.registries()),
                    name);
            assertNotNull(
                    TreeContributionSupport.unsupported(feature, MosaicTestWorlds.registries()),
                    name);
            assertNull(
                    SelectiveTreeSupport.unsupported(feature, MosaicTestWorlds.registries()), name);
        }
        assertNull(
                TreeContributionSupport.unsupportedNeighbor(
                        placed("fallen_spruce_tree"), MosaicTestWorlds.registries()));
    }

    @Test
    void largeConifersAndUnauditedSelectorBranchesStayRejected() {
        for (String name :
                java.util.List.of(
                        "mega_spruce_checked",
                        "mega_pine_checked",
                        "trees_old_growth_spruce_taiga",
                        "trees_plains")) {
            assertNotNull(
                    TreeContributionSupport.unsupportedNeighbor(
                            placed(name), MosaicTestWorlds.registries()),
                    name);
            assertNotNull(
                    SelectiveTreeSupport.unsupported(placed(name), MosaicTestWorlds.registries()),
                    name);
        }
    }

    @Test
    void coniferReachAccountsForNativeRadiusGrowthAndClearance() {
        for (String field : java.util.List.of("radius", "height", "offset", "trunk", "clearance")) {
            var base = placed("pine_checked");
            var ops =
                    RegistryOps.create(
                            com.mojang.serialization.JsonOps.INSTANCE,
                            MosaicTestWorlds.registries());
            var json =
                    net.minecraft.world.level.levelgen.feature.Feature.DIRECT_CODEC
                            .encodeStart(ops, base.feature().value())
                            .getOrThrow()
                            .getAsJsonObject();
            if (field.equals("trunk"))
                json.getAsJsonObject("trunk_placer").addProperty("base_height", 24);
            else if (field.equals("clearance"))
                json.getAsJsonObject("minimum_size").addProperty("upper_size", 4);
            else json.getAsJsonObject("foliage_placer").addProperty(field, 8);
            var changed =
                    new PlacedFeature(
                            net.minecraft.core.Holder.direct(
                                    net.minecraft.world.level.levelgen.feature.Feature.DIRECT_CODEC
                                            .parse(ops, json)
                                            .getOrThrow()),
                            base.placement());
            assertNotNull(
                    TreeContributionSupport.unsupportedNeighbor(
                            changed, MosaicTestWorlds.registries()),
                    field);
            assertNotNull(
                    SelectiveTreeSupport.unsupported(changed, MosaicTestWorlds.registries()),
                    field);
        }
    }

    @Test
    void completeBirchSelectorBranchesAreAuditedNotDropped() {
        for (String name :
                java.util.List.of(
                        "trees_birch",
                        "birch_tall",
                        "birch_checked",
                        "birch_bees_0002",
                        "fallen_birch_tree"))
            assertNull(
                    TreeContributionSupport.unsupported(
                            placed(name), MosaicTestWorlds.registries()),
                    name);
    }

    @Test
    void unsupportedRootsAndDecoratorsDoNotSilentlyBecomePlainTrees() {
        for (String name :
                java.util.List.of(
                        "mangrove_checked",
                        "dark_oak_checked",
                        "birch_bees_0002_leaf_litter",
                        "trees_plains"))
            assertNotNull(
                    TreeContributionSupport.unsupported(
                            placed(name), MosaicTestWorlds.registries()),
                    name);
    }

    @Test
    void untrackedRuntimeHeightmapsAreNotAssumedToUpdateLikeWorldgenMaps() {
        var feature =
                new PlacedFeature(
                        placed("birch_checked").feature(),
                        java.util.List.of(
                                net.minecraft.world.level.levelgen.placement.HeightmapPlacement
                                        .onHeightmap(
                                                net.minecraft.world.level.levelgen.Heightmap.Types
                                                        .MOTION_BLOCKING_NO_LEAVES)));
        assertNotNull(TreeContributionSupport.unsupported(feature, MosaicTestWorlds.registries()));
        assertFalse(
                net.minecraft.world.level.chunk.status.ChunkStatus.TERRAIN
                        .heightmapsAfter()
                        .contains(
                                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG));
        assertFalse(
                net.minecraft.world.level.chunk.status.ChunkStatus.FEATURES
                        .heightmapsAfter()
                        .contains(
                                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG));
        assertTrue(
                net.minecraft.world.level.chunk.status.ChunkStatus.TERRAIN
                        .heightmapsAfter()
                        .contains(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR));
    }
}
