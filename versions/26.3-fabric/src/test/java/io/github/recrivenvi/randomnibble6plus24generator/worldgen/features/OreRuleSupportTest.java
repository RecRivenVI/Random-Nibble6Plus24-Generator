package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.junit.jupiter.api.Test;

class OreRuleSupportTest {
    @Test
    void nativeHeightCombinationsKeepOriginalBoundaryAndRngBehavior() {
        MosaicTestWorlds.registries();
        RuleTest rule =
                RuleTest.either(
                        new BlockMatchTest(Blocks.STONE),
                        HeightMatchTest.min(0),
                        new BlockMatchTest(Blocks.DEEPSLATE));
        assertDoesNotThrow(() -> OreRuleSupport.requireSafe(rule));
        for (int y : List.of(-64, -1, 0, 8, 9, 320))
            for (var block : List.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.AIR)) {
                RandomSource random = RandomSource.create(263), control = RandomSource.create(263);
                assertEquals(
                        block == Blocks.DEEPSLATE || block == Blocks.STONE && y >= 0,
                        rule.test(block.defaultBlockState(), new BlockPos(0, y, 0), random));
                assertEquals(
                        control.nextLong(),
                        random.nextLong(),
                        "native combination consumes no random draws");
            }
    }

    @Test
    void heightConditionalAirAndNestedRandomRulesStayRejected() {
        MosaicTestWorlds.registries();
        assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleSupport.requireSafe(HeightMatchTest.min(100)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OreRuleSupport.requireSafe(
                                RuleTest.allOf(
                                        List.of(
                                                new BlockMatchTest(Blocks.AIR),
                                                HeightMatchTest.min(100)))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        OreRuleSupport.requireSafe(
                                RuleTest.allOf(
                                        List.of(
                                                new BlockMatchTest(Blocks.STONE),
                                                new RandomBlockMatchTest(Blocks.STONE, .5f)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleSupport.requireSafe(RuleTest.allOf(List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreRuleSupport.requireSafe(new NotRuleTest(HeightMatchTest.min(100))));
    }

    @Test
    void nativeDefaultHeightDependentOresAreSupportedWithoutExposureRelaxation() {
        var registry = MosaicTestWorlds.registries().lookupOrThrow(Registries.PLACED_FEATURE);
        for (String key :
                List.of(
                        "ore_coal_upper",
                        "ore_iron_upper",
                        "ore_iron_middle",
                        "ore_iron_small",
                        "ore_copper",
                        "ore_redstone",
                        "ore_redstone_lower",
                        "ore_lapis")) {
            var placed =
                    registry.getOrThrow(
                                    ResourceKey.create(
                                            Registries.PLACED_FEATURE,
                                            Identifier.withDefaultNamespace(key)))
                            .value();
            assertNull(NeighborOreContributions.unsupported(placed), key);
        }
        var buried =
                registry.getOrThrow(
                                ResourceKey.create(
                                        Registries.PLACED_FEATURE,
                                        Identifier.withDefaultNamespace("ore_diamond_buried")))
                        .value();
        assertNotNull(NeighborOreContributions.unsupported(buried));
        assertTrue(((OreFeature) buried.feature().value()).discardChanceOnAirExposure() > 0);
    }
}
