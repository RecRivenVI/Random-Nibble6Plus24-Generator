package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.Test;

class SelectiveTreeSupportTest {
    private PlacedFeature placed(String name) {
        return MosaicTestWorlds.registries()
                .lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(
                        ResourceKey.create(
                                Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(name)))
                .value();
    }

    @Test
    void auditedNaturalBirchAndFallenTreeSlotsRemainEligible() {
        for (String name :
                java.util.List.of(
                        "trees_birch",
                        "birch_tall",
                        "birch_checked",
                        "birch_bees_0002",
                        "fallen_birch_tree"))
            assertNull(
                    SelectiveTreeSupport.unsupported(placed(name), MosaicTestWorlds.registries()),
                    name);
    }

    @Test
    void wholeMixedSelectorAndUnknownDecorationsStayStrict() {
        for (String name :
                java.util.List.of(
                        "trees_plains",
                        "dark_oak_checked",
                        "mangrove_checked",
                        "birch_bees_0002_leaf_litter",
                        "ore_dirt"))
            assertNotNull(
                    SelectiveTreeSupport.unsupported(placed(name), MosaicTestWorlds.registries()),
                    name);
    }

    @Test
    void datapackProviderCannotExportUnauditedStates() {
        for (String name : java.util.List.of("birch_checked", "pine_checked", "spruce_checked"))
            rejectStoneProvider(name);
    }

    private void rejectStoneProvider(String name) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, MosaicTestWorlds.registries());
        var base = placed(name);
        var json =
                Feature.DIRECT_CODEC
                        .encodeStart(ops, base.feature().value())
                        .getOrThrow()
                        .getAsJsonObject();
        json.add(
                "trunk_provider",
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.CODEC
                        .encodeStart(
                                ops,
                                net.minecraft.world.level.levelgen.feature.stateproviders
                                        .BlockStateProvider.holderOf(
                                        net.minecraft.world.level.block.Blocks.STONE))
                        .getOrThrow());
        var changed =
                new PlacedFeature(
                        Holder.direct(Feature.DIRECT_CODEC.parse(ops, json).getOrThrow()),
                        base.placement());
        assertNull(
                TreeContributionSupport.unsupportedNeighbor(
                        changed, MosaicTestWorlds.registries()));
        assertNotNull(SelectiveTreeSupport.unsupported(changed, MosaicTestWorlds.registries()));
    }
}
