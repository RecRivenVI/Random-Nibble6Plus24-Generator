package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.material.condition.BiomeCondition;
import net.minecraft.world.level.levelgen.material.rule.*;
import org.junit.jupiter.api.*;

class LocalTerrainColumnsTest {
    @Test
    void dailyCountersHaveNoFullColumnEvidence() {
        for (var component : LocalTerrainColumns.Counters.class.getRecordComponents()) {
            assertNotEquals("evidence", component.getName());
            assertNotEquals("densityHash", component.getName());
            assertNotEquals(List.class, component.getType());
        }
        assertDoesNotThrow(() -> LocalTerrainColumns.class.getMethod("metrics"));
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void solidNativeRuleKeepsHeightAndCanBeQueried() {
        assertNull(
                LocalTerrainColumns.unsupportedRule(
                        new SequenceRule(
                                List.of(
                                        new BlockRule(Blocks.STONE.defaultBlockState()),
                                        new BlockRule(Blocks.GRASS_BLOCK.defaultBlockState()))),
                        0));
    }

    @Test
    void heightChangingOrLifecycleOutputsHaveExplicitFallback() {
        for (var block : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.CHEST))
            assertNotNull(
                    LocalTerrainColumns.unsupportedRule(
                            new BlockRule(block.defaultBlockState()), 0));
        for (var block : List.of(Blocks.WATER, Blocks.LAVA, Blocks.POWDER_SNOW))
            assertNull(
                    LocalTerrainColumns.unsupportedRule(
                            new BlockRule(block.defaultBlockState()), 0));
    }

    @Test
    void oceanFloorShortcutRequiresNativeHeightmapTags() {
        // Bootstrap alone has no datapack tags; even stone cannot be assumed floor-opaque.
        // Positive shortcuts are checked with loaded native registries in the runtime Oracle.
        for (var block : List.of(Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.END_STONE))
            assertFalse(
                    LocalTerrainColumns.preservesFloor(
                            new BlockRule(block.defaultBlockState()), Set.of(), 0));
        for (var block :
                List.of(Blocks.WATER, Blocks.LAVA, Blocks.POWDER_SNOW, Blocks.AIR, Blocks.CHEST))
            assertFalse(
                    LocalTerrainColumns.preservesFloor(
                            new BlockRule(block.defaultBlockState()), Set.of(), 0));
        assertFalse(
                LocalTerrainColumns.preservesFloor(
                        new SequenceRule(
                                List.of(
                                        new BlockRule(Blocks.STONE.defaultBlockState()),
                                        new BlockRule(Blocks.WATER.defaultBlockState()))),
                        Set.of(),
                        0));
        assertFalse(
                LocalTerrainColumns.preservesFloor(
                        new BlockRule(Blocks.STONE.defaultBlockState()), Set.of(), 65));
        assertTrue(
                LocalTerrainColumns.preservesFloor(
                        new ConditionRule(
                                new BiomeCondition(HolderSet.direct()),
                                new BlockRule(Blocks.WATER.defaultBlockState())),
                        Set.of(),
                        0));
        assertFalse(LocalTerrainColumns.preservesFloor(BandlandsRule.INSTANCE, Set.of(), 0));
    }

    @Test
    void depthGuardCannotBeBypassedByNestedRules() {
        MaterialRule rule = new BlockRule(Blocks.STONE.defaultBlockState());
        for (int i = 0; i < 65; i++) rule = new SequenceRule(List.of(rule));
        assertNotNull(LocalTerrainColumns.unsupportedRule(rule, 0));
    }

    @Test
    void impossibleBiomeBranchRequiresStoredPaletteProof() {
        var rule =
                new ConditionRule(
                        new BiomeCondition(HolderSet.direct()),
                        new BlockRule(Blocks.AIR.defaultBlockState()));
        assertNotNull(LocalTerrainColumns.unsupportedRule(rule, 0));
        assertNull(LocalTerrainColumns.unsupportedRule(rule, 0, Set.of()));
    }
}
