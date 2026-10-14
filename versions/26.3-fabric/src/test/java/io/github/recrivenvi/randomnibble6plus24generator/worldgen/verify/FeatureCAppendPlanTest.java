package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.*;
import org.junit.jupiter.api.*;

class FeatureCAppendPlanTest {
    private static final TreeContribution.Scope SCOPE =
            new TreeContribution.Scope("append-test", "minecraft:overworld", "test");
    private static final BlockPos P = new BlockPos(16, 80, 4), Q = P.east();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    private static TreeContribution.Cell cell(
            BlockPos p, net.minecraft.world.level.block.Block block) {
        return new TreeContribution.Cell(p, block.defaultBlockState(), Optional.empty());
    }

    private static TreeContribution batch(int x, List<TreeContribution.Cell> cells, int delay) {
        return new TreeContribution(
                new TreeContribution.Key(SCOPE, x, 0, 23 + x, "minecraft:birch", 9, 0, 0),
                -64,
                384,
                cells,
                List.of(
                        new SavedTick<>(
                                Blocks.BIRCH_LEAVES,
                                cells.getFirst().pos(),
                                delay,
                                TickPriority.NORMAL)),
                List.of(),
                List.of());
    }

    private static FeatureContributionJournal journal() {
        var j = new FeatureContributionJournal(SCOPE, new ChunkPos(1, 0), 99, -64, 384);
        j.supplyBase(List.of(cell(P, Blocks.AIR), cell(Q, Blocks.AIR)));
        return j;
    }

    @Test
    void stableExistingCellAllowsOnlyNewPositionAndDuplicateHasNoDelta() {
        var j = journal();
        j.publish(batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2));
        var old = j.project();
        j.publish(batch(2, List.of(cell(Q, Blocks.OAK_LEAVES)), 3));
        var delta = FeatureCAppendPlan.between(old, j.project());
        assertEquals(Set.of(Q), delta.additions());
        assertTrue(delta.conflicts().isEmpty());
        assertTrue(FeatureCAppendPlan.between(j.project(), j.project()).additions().isEmpty());
    }

    @Test
    void earlierWinnerChangingOldBlockRemainsConflictEvenWithSafeExtraPosition() {
        var j = journal();
        j.publish(batch(2, List.of(cell(P, Blocks.OAK_LEAVES)), 2));
        var old = j.project();
        j.publish(batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES), cell(Q, Blocks.BIRCH_LEAVES)), 2));
        var delta = FeatureCAppendPlan.between(old, j.project());
        assertEquals(List.of(P), delta.conflicts());
        assertEquals(Set.of(Q), delta.additions());
    }

    @Test
    void identicalBlockWithDifferentTickIsStillConflict() {
        var j = journal();
        j.publish(batch(2, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2));
        var old = j.project();
        j.publish(batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES)), 7));
        assertEquals(List.of(P), FeatureCAppendPlan.between(old, j.project()).conflicts());
    }

    @Test
    void identicalBlockAndEffectsCanChangeProvenanceWithoutPhysicalReactivation() {
        var j = journal();
        j.publish(batch(2, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2));
        var old = j.project();
        j.publish(batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2));
        var delta = FeatureCAppendPlan.between(old, j.project());
        assertTrue(delta.conflicts().isEmpty());
        assertTrue(delta.additions().isEmpty());
    }

    @Test
    void protectedDugAirNeverBecomesAReplacementDelta() {
        var j = journal();
        j.publish(batch(2, List.of(cell(P, Blocks.OAK_LEAVES)), 2));
        var old = j.project();
        j.publish(batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES), cell(Q, Blocks.BIRCH_LEAVES)), 2));
        j.protect(cell(P, Blocks.AIR));
        var delta = FeatureCAppendPlan.between(old, j.project());
        assertTrue(delta.conflicts().isEmpty());
        assertEquals(Set.of(Q), delta.additions());
    }

    @Test
    void deletingActivatedInputIsNotAnAppend() {
        var j = journal();
        var b = batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2);
        j.publish(b);
        var old = j.project();
        j.remove(b.key());
        assertEquals(List.of(P), FeatureCAppendPlan.between(old, j.project()).conflicts());
    }

    @Test
    void sameBlockWithChangedBlockEntityPayloadIsConflict() {
        var j = journal();
        var one = new CompoundTag();
        one.putString("id", "minecraft:beehive");
        one.putInt("x", P.getX());
        one.putInt("y", P.getY());
        one.putInt("z", P.getZ());
        one.putInt("test_value", 1);
        var two = one.copy();
        two.putInt("test_value", 2);
        j.publish(
                new TreeContribution(
                        batch(2, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2).key(),
                        -64,
                        384,
                        List.of(
                                new TreeContribution.Cell(
                                        P, Blocks.BEE_NEST.defaultBlockState(), Optional.of(one))),
                        List.of(),
                        List.of(),
                        List.of()));
        var old = j.project();
        j.publish(
                new TreeContribution(
                        batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2).key(),
                        -64,
                        384,
                        List.of(
                                new TreeContribution.Cell(
                                        P, Blocks.BEE_NEST.defaultBlockState(), Optional.of(two))),
                        List.of(),
                        List.of(),
                        List.of()));
        assertEquals(List.of(P), FeatureCAppendPlan.between(old, j.project()).conflicts());
    }

    @Test
    void sameBlockWithChangedFluidTickIsConflict() {
        var j = journal();
        j.publish(
                new TreeContribution(
                        batch(2, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2).key(),
                        -64,
                        384,
                        List.of(cell(P, Blocks.BIRCH_LEAVES)),
                        List.of(),
                        List.of(new SavedTick<>(Fluids.WATER, P, 2, TickPriority.NORMAL)),
                        List.of()));
        var old = j.project();
        j.publish(
                new TreeContribution(
                        batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2).key(),
                        -64,
                        384,
                        List.of(cell(P, Blocks.BIRCH_LEAVES)),
                        List.of(),
                        List.of(new SavedTick<>(Fluids.WATER, P, 7, TickPriority.NORMAL)),
                        List.of()));
        assertEquals(List.of(P), FeatureCAppendPlan.between(old, j.project()).conflicts());
    }

    @Test
    void sameBlockWithChangedPostProcessingIsConflict() {
        var j = journal();
        j.publish(
                new TreeContribution(
                        batch(2, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2).key(),
                        -64,
                        384,
                        List.of(cell(P, Blocks.BIRCH_LEAVES)),
                        List.of(),
                        List.of(),
                        List.of(P)));
        var old = j.project();
        j.publish(
                new TreeContribution(
                        batch(0, List.of(cell(P, Blocks.BIRCH_LEAVES)), 2).key(),
                        -64,
                        384,
                        List.of(cell(P, Blocks.BIRCH_LEAVES)),
                        List.of(),
                        List.of(),
                        List.of()));
        assertEquals(List.of(P), FeatureCAppendPlan.between(old, j.project()).conflicts());
    }
}
