package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.*;
import org.junit.jupiter.api.*;

class FeatureContributionJournalTest {
    private static final TreeContribution.Scope SCOPE =
            new TreeContribution.Scope("test-world", "minecraft:overworld", "C-tree-experiment");
    private static final BlockPos P = new BlockPos(16, 80, 4);
    private static final ChunkPos RECEIVER = new ChunkPos(1, 0);

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    private static TreeContribution.Cell cell(
            BlockPos p, net.minecraft.world.level.block.Block block) {
        return new TreeContribution.Cell(p, block.defaultBlockState(), Optional.empty());
    }

    private static TreeContribution batch(int sourceX, long seed) {
        return new TreeContribution(
                new TreeContribution.Key(SCOPE, sourceX, 0, seed, "minecraft:birch", 9, 2, 0),
                -64,
                384,
                List.of(cell(P, sourceX == 0 ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES)),
                List.of(new SavedTick<>(Blocks.BIRCH_LEAVES, P, 5, TickPriority.HIGH)),
                List.of(new SavedTick<>(Fluids.WATER, P, 8, TickPriority.LOW)),
                List.of(P, P));
    }

    private static FeatureContributionJournal journal() {
        return new FeatureContributionJournal(SCOPE, RECEIVER, 999, -64, 384);
    }

    @Test
    void coniferTransportRoundTripDoesNotGrantOtherTreeOutputs() {
        var key = new TreeContribution.Key(SCOPE, 0, 0, 23, "minecraft:pine", 9, 2, 0);
        for (var block : List.of(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES)) {
            var b =
                    new TreeContribution(
                            key,
                            -64,
                            384,
                            List.of(cell(P, block)),
                            List.of(),
                            List.of(),
                            List.of());
            assertEquals(b, TreeContribution.decode(b.encode()));
            var j = journal();
            j.publish(b);
            j.supplyBase(List.of(cell(P, Blocks.AIR)));
            var restored = FeatureContributionJournal.decode(j.encode(), SCOPE, RECEIVER, 999);
            assertEquals(block.defaultBlockState(), restored.project().incoming().get(P).state());
        }
        for (var block : List.of(Blocks.STONE, Blocks.JUNGLE_LOG, Blocks.ACACIA_LEAVES))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new TreeContribution(
                                    key,
                                    -64,
                                    384,
                                    List.of(cell(P, block)),
                                    List.of(),
                                    List.of(),
                                    List.of()));
    }

    @Test
    void missingReceiverRemainsPendingAndReplayIsIdempotent() {
        var j = journal();
        var b = batch(0, 23);
        assertTrue(j.publish(b));
        assertFalse(j.publish(b));
        assertEquals(1, j.project().pendingPositions());
        assertTrue(j.project().incoming().isEmpty());
        var restored = FeatureContributionJournal.decode(j.encode(), SCOPE, RECEIVER, 999);
        restored.supplyBase(List.of(cell(P, Blocks.AIR)));
        assertEquals(1, restored.project().incoming().size());
        assertEquals(b.blockTicks(), restored.project().blockTicks());
        assertEquals(b.fluidTicks(), restored.project().fluidTicks());
        assertEquals(List.of(P, P), restored.project().postProcessing());
        assertFalse(restored.publish(b));
    }

    @Test
    void stableConflictReplaySurvivesParallelDeliveryAndRemoval() throws Exception {
        var a = batch(0, 23);
        var b = batch(2, 42);
        var serial = journal();
        serial.supplyBase(List.of(cell(P, Blocks.AIR)));
        serial.publish(a);
        serial.publish(b);
        var reverse = journal();
        reverse.publish(b);
        reverse.publish(a);
        reverse.supplyBase(List.of(cell(P, Blocks.AIR)));
        assertEquals(serial.project(), reverse.project());
        try (var executor = Executors.newFixedThreadPool(4)) {
            var parallel = journal();
            parallel.supplyBase(List.of(cell(P, Blocks.AIR)));
            var jobs = new ArrayList<Future<?>>();
            for (int i = 0; i < 32; i++) {
                var value = i % 2 == 0 ? a : b;
                jobs.add(executor.submit(() -> parallel.publish(value)));
            }
            for (var job : jobs) job.get();
            assertEquals(serial.encode(), parallel.encode());
        }
        serial.remove(a.key());
        assertEquals(
                Blocks.OAK_LEAVES.defaultBlockState(), serial.project().incoming().get(P).state());
        serial.remove(b.key());
        assertTrue(serial.project().incoming().isEmpty());
        assertTrue(serial.project().blockTicks().isEmpty());
        assertTrue(serial.project().fluidTicks().isEmpty());
    }

    @Test
    void solidsAndPlayerDugAirRejectBlockAndAssociatedEffectsAfterReload() {
        var solid = journal();
        solid.supplyBase(List.of(cell(P, Blocks.STONE)));
        solid.publish(batch(0, 23));
        assertTrue(solid.project().incoming().isEmpty());
        assertTrue(solid.project().blockTicks().isEmpty());
        var edited = journal();
        edited.publish(batch(0, 23));
        edited.protect(cell(P, Blocks.AIR));
        edited.supplyBase(List.of(cell(P, Blocks.AIR)));
        var restored = FeatureContributionJournal.decode(edited.encode(), SCOPE, RECEIVER, 999);
        assertTrue(restored.project().incoming().isEmpty());
        assertTrue(restored.project().postProcessing().isEmpty());
        assertTrue(restored.project().fluidTicks().isEmpty());
        assertEquals(
                Blocks.AIR.defaultBlockState(), restored.project().protectedEdits().get(P).state());
    }

    @Test
    void hiveNbtIsDetachedWithoutCanonicalFieldRemoval() {
        var tag = new CompoundTag();
        tag.putString("id", "minecraft:beehive");
        tag.putInt("x", P.getX());
        tag.putInt("y", P.getY());
        tag.putInt("z", P.getZ());
        tag.putString("custom_evidence", "preserve all fields");
        var cell =
                new TreeContribution.Cell(P, Blocks.BEE_NEST.defaultBlockState(), Optional.of(tag));
        var b =
                new TreeContribution(
                        batch(0, 23).key(),
                        -64,
                        384,
                        List.of(cell),
                        List.of(),
                        List.of(),
                        List.of());
        tag.putString("custom_evidence", "mutated");
        b.cells().getFirst().blockEntity().orElseThrow().remove("custom_evidence");
        assertEquals(
                "preserve all fields",
                b.cells()
                        .getFirst()
                        .blockEntity()
                        .orElseThrow()
                        .getString("custom_evidence")
                        .orElseThrow());
        assertEquals(b, TreeContribution.decode(b.encode()));
    }

    @Test
    void identityCorruptionDuplicateAndUnknownStateFailClosed() {
        var j = journal();
        var b = batch(0, 23);
        j.publish(b);
        assertThrows(IllegalArgumentException.class, () -> j.publish(batch(0, 24)));
        assertThrows(
                IllegalStateException.class,
                () ->
                        j.publish(
                                new TreeContribution(
                                        b.key(),
                                        -64,
                                        384,
                                        List.of(cell(P, Blocks.OAK_LEAVES)),
                                        List.of(),
                                        List.of(),
                                        List.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureContributionJournal.decode(j.encode(), SCOPE, RECEIVER, 998));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FeatureContributionJournal.decode(
                                j.encode(),
                                new TreeContribution.Scope(
                                        "foreign", SCOPE.dimension(), SCOPE.contract()),
                                RECEIVER,
                                999));
        var corrupt = b.encode();
        corrupt.putString("unexpected", "fail");
        assertThrows(IllegalArgumentException.class, () -> TreeContribution.decode(corrupt));
        var state = b.encode();
        var cells = state.getList("cells").orElseThrow();
        var encodedCell = (CompoundTag) cells.get(0);
        encodedCell.putString("state", "minecraft:no_such_block");
        cells.set(0, encodedCell);
        state.put("cells", cells);
        assertThrows(RuntimeException.class, () -> TreeContribution.decode(state));
    }

    @Test
    void finiteFrontierAndAttachedEffectsCannotBeBypassed() {
        var b = batch(0, 23);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TreeContribution(
                                b.key(),
                                -64,
                                384,
                                List.of(cell(new BlockPos(32, 80, 4), Blocks.BIRCH_LEAVES)),
                                List.of(),
                                List.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TreeContribution(
                                b.key(),
                                -64,
                                384,
                                b.cells(),
                                List.of(),
                                List.of(),
                                List.of(new BlockPos(32, 80, 4))));
        var j = journal();
        j.supplyBase(List.of(cell(P, Blocks.AIR)));
        assertThrows(
                IllegalArgumentException.class, () -> j.supplyBase(List.of(cell(P, Blocks.STONE))));
        assertThrows(
                IllegalArgumentException.class,
                () -> j.protect(cell(new BlockPos(0, 80, 0), Blocks.AIR)));
    }

    @Test
    void nativeEffectOnlyPositionsArePreservedAsEvidenceButHaveNoReceivingAuthority() {
        var b = batch(0, 23);
        var other = P.east();
        var raw =
                new TreeContribution(
                        b.key(),
                        -64,
                        384,
                        b.cells(),
                        List.of(new SavedTick<>(Blocks.SAND, other, 2, TickPriority.NORMAL)),
                        List.of(),
                        List.of(other));
        assertEquals(2, raw.unattachedEffects());
        assertEquals(raw, TreeContribution.decode(raw.encode()));
        var j = journal();
        j.publish(raw);
        j.supplyBase(List.of(cell(P, Blocks.AIR), cell(other, Blocks.SAND)));
        assertEquals(2, j.project().unattachedEffects());
        assertTrue(j.project().blockTicks().isEmpty());
        assertTrue(j.project().postProcessing().isEmpty());
    }

    @Test
    void partialBaseAndLateProtectionSurvivePendingReloadWithoutPlaceholders() {
        var second = P.east();
        var original = batch(0, 23);
        var both =
                new TreeContribution(
                        original.key(),
                        -64,
                        384,
                        List.of(cell(P, Blocks.BIRCH_LEAVES), cell(second, Blocks.BIRCH_LOG)),
                        original.blockTicks(),
                        original.fluidTicks(),
                        List.of(P, second));
        var j = journal();
        j.publish(both);
        j.supplyBase(List.of(cell(P, Blocks.AIR)));
        assertEquals(1, j.project().pendingPositions());
        assertEquals(Set.of(P), j.project().incoming().keySet());
        j = FeatureContributionJournal.decode(j.encode(), SCOPE, RECEIVER, 999);
        j.protect(cell(second, Blocks.AIR));
        j.supplyBase(List.of(cell(second, Blocks.AIR)));
        assertEquals(0, j.project().pendingPositions());
        assertEquals(Set.of(P), j.project().incoming().keySet());
        assertEquals(List.of(P), j.project().postProcessing());
        var saved = j.encode();
        j.remove(both.key());
        j = FeatureContributionJournal.decode(j.encode(), SCOPE, RECEIVER, 999);
        j.publish(both);
        assertEquals(saved, j.encode());
    }

    @Test
    void invalidBaseBatchIsAtomicAndCannotChangeExistingReceiverAuthority() {
        var j = journal();
        j.publish(batch(0, 23));
        j.supplyBase(List.of(cell(P, Blocks.AIR)));
        var before = j.encode();
        assertThrows(
                IllegalArgumentException.class,
                () -> j.supplyBase(List.of(cell(P.east(), Blocks.AIR), cell(P, Blocks.STONE))));
        assertEquals(before, j.encode());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        j.supplyBase(
                                List.of(
                                        cell(P.east(), Blocks.AIR),
                                        cell(new BlockPos(0, 80, 0), Blocks.AIR))));
        assertEquals(before, j.encode());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        j.supplyBase(
                                List.of(cell(P.east(), Blocks.AIR), cell(P.east(), Blocks.STONE))));
        assertEquals(before, j.encode());
    }

    @Test
    void protectionRacingBaseAndDuplicateDeliveryHasTheSameFinalState() throws Exception {
        var contribution = batch(0, 23);
        var expected = journal();
        expected.protect(cell(P, Blocks.AIR));
        expected.supplyBase(List.of(cell(P, Blocks.AIR)));
        expected.publish(contribution);
        var actual = journal();
        try (var pool = Executors.newFixedThreadPool(3)) {
            var jobs = new ArrayList<Future<?>>();
            for (int repeat = 0; repeat < 24; repeat++) {
                jobs.add(pool.submit(() -> actual.publish(contribution)));
                jobs.add(pool.submit(() -> actual.supplyBase(List.of(cell(P, Blocks.AIR)))));
                jobs.add(pool.submit(() -> actual.protect(cell(P, Blocks.AIR))));
            }
            for (var job : jobs) job.get();
        }
        assertEquals(expected.encode(), actual.encode());
        var restored = FeatureContributionJournal.decode(actual.encode(), SCOPE, RECEIVER, 999);
        assertEquals(expected.project(), restored.project());
        assertTrue(restored.project().blockTicks().isEmpty());
        assertTrue(restored.project().fluidTicks().isEmpty());
        assertTrue(restored.project().postProcessing().isEmpty());
    }

    @Test
    void conflictingHiveAndLeafKeepBlockEntityAndEffectsWithTheirWinningOwner() {
        var nbt = new CompoundTag();
        nbt.putString("id", "minecraft:beehive");
        nbt.putInt("x", P.getX());
        nbt.putInt("y", P.getY());
        nbt.putInt("z", P.getZ());
        nbt.putString("evidence", "all fields retained");
        var hive =
                new TreeContribution(
                        batch(0, 23).key(),
                        -64,
                        384,
                        List.of(
                                new TreeContribution.Cell(
                                        P, Blocks.BEE_NEST.defaultBlockState(), Optional.of(nbt))),
                        List.of(new SavedTick<>(Blocks.BEE_NEST, P, 3, TickPriority.LOW)),
                        List.of(),
                        List.of(P));
        var leaf = batch(2, 42);
        var j = journal();
        j.publish(leaf);
        j.publish(hive);
        j.supplyBase(List.of(cell(P, Blocks.AIR)));
        var restored = FeatureContributionJournal.decode(j.encode(), SCOPE, RECEIVER, 999);
        assertEquals(nbt, restored.project().incoming().get(P).blockEntity().orElseThrow());
        assertEquals(hive.blockTicks(), restored.project().blockTicks());
        assertTrue(restored.project().fluidTicks().isEmpty());
        assertEquals(hive.postProcessing(), restored.project().postProcessing());
        restored.remove(hive.key());
        assertTrue(restored.project().incoming().get(P).blockEntity().isEmpty());
        assertEquals(leaf.blockTicks(), restored.project().blockTicks());
        assertEquals(leaf.fluidTicks(), restored.project().fluidTicks());
        assertEquals(leaf.postProcessing(), restored.project().postProcessing());
        restored.publish(hive);
        assertEquals(j.encode(), restored.encode());
    }
}
