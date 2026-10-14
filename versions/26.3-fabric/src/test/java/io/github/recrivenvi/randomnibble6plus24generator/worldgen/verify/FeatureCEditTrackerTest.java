package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.*;

class FeatureCEditTrackerTest {
    private static final ChunkPos OWNER = new ChunkPos(-1, 2);
    private static final BlockPos P = new BlockPos(-3, 90, 33);

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void immutablePositionsStableOrderAndRepeatedEdits() {
        var a = new FeatureCEditTracker.Ledger("save|overworld", 7, OWNER);
        var b = new FeatureCEditTracker.Ledger("save|overworld", 7, OWNER);
        var mutable = P.mutable();
        a.record(mutable);
        mutable.move(0, 1, 0);
        a.record(mutable);
        a.record(P);
        b.record(P.above());
        b.record(P);
        assertEquals(a.encode(), b.encode());
        assertEquals(
                a.encode(),
                FeatureCEditTracker.Ledger.decode(a.encode(), "save|overworld", 7, OWNER).encode());
        assertEquals(2, a.encode().getLongArray("positions").orElseThrow().length);
    }

    @Test
    void ledgerRejectsOtherWorldDimensionSeedAndOwner() {
        var ledger = new FeatureCEditTracker.Ledger("save|overworld", 7, OWNER);
        ledger.record(P);
        for (String world : new String[] {"other-save|overworld", "save|nether"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FeatureCEditTracker.Ledger.decode(ledger.encode(), world, 7, OWNER));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FeatureCEditTracker.Ledger.decode(
                                ledger.encode(), "save|overworld", 8, OWNER));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FeatureCEditTracker.Ledger.decode(
                                ledger.encode(), "save|overworld", 7, new ChunkPos(0, 2)));
        assertThrows(IllegalArgumentException.class, () -> ledger.record(P.east(16)));
    }

    @Test
    void corruptOrUnknownLedgerFieldsFailClosed() {
        var ledger = new FeatureCEditTracker.Ledger("save|overworld", 7, OWNER);
        ledger.record(P);
        var duplicate = ledger.encode();
        duplicate.putLongArray("positions", new long[] {P.asLong(), P.asLong()});
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCEditTracker.Ledger.decode(duplicate, "save|overworld", 7, OWNER));
        var unknown = ledger.encode();
        unknown.putBoolean("assume_safe", true);
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCEditTracker.Ledger.decode(unknown, "save|overworld", 7, OWNER));
        var foreign = ledger.encode();
        foreign.putLongArray("positions", new long[] {P.east(16).asLong()});
        assertThrows(
                IllegalArgumentException.class,
                () -> FeatureCEditTracker.Ledger.decode(foreign, "save|overworld", 7, OWNER));
    }

    @Test
    void permitExemptsOnlyExactFirstMutationNotNestedUpdates() {
        var permit = new FeatureCEditTracker.Permit(P, Blocks.OAK_LOG.defaultBlockState());
        assertFalse(permit.claim(P.east(), Blocks.OAK_LOG.defaultBlockState()));
        assertFalse(permit.claim(P, Blocks.AIR.defaultBlockState()));
        assertTrue(permit.claim(P, Blocks.OAK_LOG.defaultBlockState()));
        assertFalse(permit.claim(P, Blocks.OAK_LOG.defaultBlockState()));
        assertFalse(permit.claim(P, Blocks.AIR.defaultBlockState()));
        assertEquals(0, FeatureCEditTracker.bindingCount());
    }
}
