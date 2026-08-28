package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HexFormat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

public final class CarverStageSnapshot {

    private final SurfaceStageSnapshot base;
    private final boolean carvingMaskPresent;
    private final long[] carvingMask;
    private final String hash;

    private CarverStageSnapshot(
            SurfaceStageSnapshot base,
            boolean carvingMaskPresent,
            long[] carvingMask) {
        this.base = base;
        this.carvingMaskPresent = carvingMaskPresent;
        this.carvingMask = carvingMask;
        this.hash = calculateHash();
    }

    public static CarverStageSnapshot capture(ChunkAccess chunk, RegistryAccess registryAccess) {
        if (!(chunk instanceof ProtoChunk protoChunk)) {
            throw new IllegalArgumentException(
                    "Stage-exact CARVERS capture requires ProtoChunk, found " + chunk.getClass().getName());
        }
        var mask = protoChunk.getCarvingMask();
        return new CarverStageSnapshot(
                SurfaceStageSnapshot.capture(chunk, registryAccess),
                mask != null,
                mask == null ? new long[0] : mask.toArray().clone());
    }

    public void assertEquivalentTo(CarverStageSnapshot expected) {
        base.assertEquivalentTo(expected.base);
        if (carvingMaskPresent != expected.carvingMaskPresent) {
            throw new SurfaceParityMismatchException(
                    "CarvingMask presence mismatch; expected="
                            + expected.carvingMaskPresent
                            + ", actual="
                            + carvingMaskPresent);
        }
        if (!Arrays.equals(carvingMask, expected.carvingMask)) {
            BitSet difference = BitSet.valueOf(carvingMask);
            difference.xor(BitSet.valueOf(expected.carvingMask));
            int bit = difference.nextSetBit(0);
            int localX = bit & 15;
            int localZ = bit >> 4 & 15;
            int y = base.minY() + (bit >> 8);
            throw new SurfaceParityMismatchException(
                    "CarvingMask first difference at "
                            + (base.chunkPos().getMinBlockX() + localX)
                            + ","
                            + y
                            + ","
                            + (base.chunkPos().getMinBlockZ() + localZ));
        }
        if (!hash.equals(expected.hash)) {
            throw new SurfaceParityMismatchException(
                    "CARVERS hash mismatch; expected=" + expected.hash + ", actual=" + hash);
        }
    }

    public SurfaceStageSnapshot base() {
        return base;
    }

    public boolean carvingMaskPresent() {
        return carvingMaskPresent;
    }

    public long[] carvingMask() {
        return carvingMask.clone();
    }

    public int carvingMaskBitCount() {
        return BitSet.valueOf(carvingMask).cardinality();
    }

    public String hash() {
        return hash;
    }

    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(base.hash().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) (carvingMaskPresent ? 1 : 0));
            for (long value : carvingMask) {
                digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
