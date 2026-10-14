package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.feature.OreFeature;

/**
 * 26.3 ore geometry projected onto one chunk. Eligibility is checked by NeighborOreContributions.
 */
public final class OreContributionGeometry {
    private OreContributionGeometry() {}

    public record Evaluation(boolean intersects, int writes) {}

    @FunctionalInterface
    public interface HeightGate {
        boolean passes(int xStart, int zStart, int sizeXZ, int yStart);
    }

    public static int reach(int size) {
        return Mth.ceil(size / 8.0f) + Mth.ceil((size / 16.0f * 2.0f + 1.0f) / 2.0f);
    }

    public static Evaluation project(
            OreFeature ore,
            RandomSource random,
            BlockPos origin,
            ProtoChunk target,
            HeightGate heightGate) {
        NeighborOreContributions.requireSafeOre(ore);
        float dir = random.nextFloat() * (float) Math.PI;
        float spread = ore.size() / 8.0f;
        int radius = Mth.ceil((ore.size() / 16.0f * 2.0f + 1.0f) / 2.0f);
        double x0 = origin.getX() + Math.sin(dir) * spread;
        double x1 = origin.getX() - Math.sin(dir) * spread;
        double z0 = origin.getZ() + Math.cos(dir) * spread;
        double z1 = origin.getZ() - Math.cos(dir) * spread;
        double y0 = origin.getY() + random.nextInt(3) - 2;
        double y1 = origin.getY() + random.nextInt(3) - 2;
        int xStart = origin.getX() - Mth.ceil(spread) - radius;
        int yStart = origin.getY() - 2 - radius;
        int zStart = origin.getZ() - Mth.ceil(spread) - radius;
        int sizeXZ = 2 * (Mth.ceil(spread) + radius), sizeY = 2 * (2 + radius);
        if (!heightGate.passes(xStart, zStart, sizeXZ, yStart)) return new Evaluation(false, 0);
        ChunkPos c = target.getPos();
        if (xStart > c.getMaxBlockX()
                || xStart + sizeXZ < c.getMinBlockX()
                || zStart > c.getMaxBlockZ()
                || zStart + sizeXZ < c.getMinBlockZ()) {
            // For the supported rules, voxel evaluation consumes no RNG. Keep the native geometry
            // draws so the following placement attempt receives exactly the same random stream.
            for (int i = 0; i < ore.size(); i++) random.nextDouble();
            return new Evaluation(false, 0);
        }
        double[] spheres = new double[ore.size() * 4];
        for (int i = 0; i < ore.size(); i++) {
            float step = (float) i / ore.size();
            double s = random.nextDouble() * ore.size() / 16.0;
            spheres[i * 4] = Mth.lerp((double) step, x0, x1);
            spheres[i * 4 + 1] = Mth.lerp((double) step, y0, y1);
            spheres[i * 4 + 2] = Mth.lerp((double) step, z0, z1);
            spheres[i * 4 + 3] = ((Mth.sin((float) Math.PI * step) + 1.0f) * s + 1.0) / 2.0;
        }
        for (int i = 0; i < ore.size() - 1; i++) {
            if (spheres[i * 4 + 3] <= 0) continue;
            for (int j = i + 1; j < ore.size(); j++) {
                if (spheres[j * 4 + 3] <= 0) continue;
                double dr = spheres[i * 4 + 3] - spheres[j * 4 + 3];
                double dx = spheres[i * 4] - spheres[j * 4];
                double dy = spheres[i * 4 + 1] - spheres[j * 4 + 1];
                double dz = spheres[i * 4 + 2] - spheres[j * 4 + 2];
                if (dr * dr > dx * dx + dy * dy + dz * dz) spheres[(dr > 0 ? j : i) * 4 + 3] = -1.0;
            }
        }
        BitSet tested = new BitSet(sizeXZ * sizeY * sizeXZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int writes = 0;
        for (int i = 0; i < ore.size(); i++) {
            double r = spheres[i * 4 + 3];
            if (r < 0) continue;
            double xx = spheres[i * 4], yy = spheres[i * 4 + 1], zz = spheres[i * 4 + 2];
            int xmin = Math.max(Mth.floor(xx - r), xStart);
            int ymin = Math.max(Mth.floor(yy - r), yStart);
            int zmin = Math.max(Mth.floor(zz - r), zStart);
            int xmax = Math.max(Mth.floor(xx + r), xmin);
            int ymax = Math.max(Mth.floor(yy + r), ymin);
            int zmax = Math.max(Mth.floor(zz + r), zmin);
            for (int x = Math.max(xmin, c.getMinBlockX());
                    x <= Math.min(xmax, c.getMaxBlockX());
                    x++) {
                double xd = (x + 0.5 - xx) / r;
                if (!(xd * xd < 1)) continue;
                for (int y = ymin; y <= ymax; y++) {
                    double yd = (y + 0.5 - yy) / r;
                    if (!(xd * xd + yd * yd < 1)) continue;
                    for (int z = Math.max(zmin, c.getMinBlockZ());
                            z <= Math.min(zmax, c.getMaxBlockZ());
                            z++) {
                        double zd = (z + 0.5 - zz) / r;
                        int bit =
                                x - xStart + (y - yStart) * sizeXZ + (z - zStart) * sizeXZ * sizeY;
                        if (!(xd * xd + yd * yd + zd * zd < 1)
                                || target.isOutsideBuildHeight(y)
                                || tested.get(bit)) continue;
                        tested.set(bit);
                        pos.set(x, y, z);
                        var section = target.getSection(target.getSectionIndex(y));
                        var state = section.getBlockState(x & 15, y & 15, z & 15);
                        for (var replacement : ore.targetStates()) {
                            if (!ore.canPlaceOre(
                                    state, target::getBlockState, random, replacement, pos))
                                continue;
                            section.setBlockState(
                                    SectionPos.sectionRelative(x),
                                    SectionPos.sectionRelative(y),
                                    SectionPos.sectionRelative(z),
                                    replacement.state(),
                                    false);
                            writes++;
                            break;
                        }
                    }
                }
            }
        }
        return new Evaluation(true, writes);
    }
}
