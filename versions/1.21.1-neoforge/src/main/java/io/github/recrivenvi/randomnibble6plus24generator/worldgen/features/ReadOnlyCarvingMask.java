package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;

/** Delegates every native read, including the additional predicate; no neighbor mutation. */
final class ReadOnlyCarvingMask extends CarvingMask {
    private final CarvingMask source;
    private final Consumer<String> rejected;

    ReadOnlyCarvingMask(CarvingMask source, Consumer<String> rejected) {
        // No local bits are an authority: all public reads below use the real source mask.
        super(0, 0);
        this.source = java.util.Objects.requireNonNull(source);
        this.rejected = java.util.Objects.requireNonNull(rejected);
    }

    @Override
    public boolean get(int x, int y, int z) {
        return source.get(x, y, z);
    }

    @Override
    public Stream<BlockPos> stream(ChunkPos chunk) {
        return source.stream(chunk);
    }

    @Override
    public long[] toArray() {
        return source.toArray().clone();
    }

    @Override
    public void set(int x, int y, int z) {
        rejected.accept("chunk_carving_mask");
    }

    @Override
    public void setAdditionalMask(CarvingMask.Mask mask) {
        rejected.accept("chunk_carving_mask_filter");
    }
}
