package io.github.recrivenvi.randomnibble6plus24generator.probe.terrain;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.NoiseChunkAccess;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.NoiseInterpolatorAccess;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseChunk;

/** Copies actual native interpolation slices, never re-evaluates a density function. */
public final class NoiseDensityEvidence implements AutoCloseable {
    private record Configuration(Path output, ChunkPos target) {}

    private static volatile Configuration configuration;
    private static final ThreadLocal<NoiseDensityEvidence> ACTIVE = new ThreadLocal<>();
    private static final AtomicInteger OPEN = new AtomicInteger();
    private static final AtomicInteger FILLS = new AtomicInteger();
    private final Configuration owner;
    private final MessageDigest digest;
    private final DataOutputStream output;
    private int slices;
    private long samples;

    private NoiseDensityEvidence(Configuration owner) throws IOException {
        this.owner = owner;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
        Files.createDirectories(owner.output());
        output =
                new DataOutputStream(
                        new BufferedOutputStream(
                                new DigestOutputStream(
                                        Files.newOutputStream(
                                                owner.output().resolve("density-slices.bin")),
                                        digest)));
    }

    public static void configure(Path output, ChunkPos target) {
        if (configuration != null || OPEN.get() != 0)
            throw new IllegalStateException("Density probe already active");
        FILLS.set(0);
        configuration = new Configuration(output, target);
    }

    public static NoiseDensityEvidence open(ChunkPos target) {
        var owner = configuration;
        if (owner == null || !owner.target().equals(target)) return null;
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested native noise fill");
        if (FILLS.incrementAndGet() != 1)
            throw new IllegalStateException("Duplicate target noise fill");
        try {
            var capture = new NoiseDensityEvidence(owner);
            ACTIVE.set(capture);
            OPEN.incrementAndGet();
            return capture;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static void capture(NoiseChunk chunk, boolean first, int cellX) {
        var capture = ACTIVE.get();
        if (capture == null) return;
        try {
            capture.output.writeBoolean(first);
            capture.output.writeInt(cellX);
            var interpolators = ((NoiseChunkAccess) chunk).mosaicInterpolators();
            capture.output.writeInt(interpolators.size());
            for (var interpolator : interpolators) {
                var access = (NoiseInterpolatorAccess) interpolator;
                double[][] rows = first ? access.mosaicSlice0() : access.mosaicSlice1();
                capture.output.writeInt(rows.length);
                for (double[] row : rows) {
                    capture.output.writeInt(row.length);
                    for (double value : row) {
                        capture.output.writeLong(Double.doubleToRawLongBits(value));
                        capture.samples++;
                    }
                }
            }
            capture.slices++;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            output.close();
            if (slices <= 0 || samples <= 0)
                throw new IllegalStateException("No native density slices were observed");
            var proof = new JsonObject();
            proof.addProperty("slices", slices);
            proof.addProperty("samples", samples);
            proof.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
            NativeStructureVerifier.write(owner.output().resolve("density-slices.json"), proof);
        } finally {
            ACTIVE.remove();
            OPEN.decrementAndGet();
        }
    }

    public static void clear() {
        configuration = null;
        if (OPEN.get() != 0) throw new IllegalStateException("Native density capture leaked");
    }
}
