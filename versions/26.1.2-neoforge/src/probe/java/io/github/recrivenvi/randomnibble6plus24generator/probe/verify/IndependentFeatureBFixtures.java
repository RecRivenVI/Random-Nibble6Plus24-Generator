package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Coordinates remain positive assertions against this loader's own fresh native controls. */
final class IndependentFeatureBFixtures {
    private IndependentFeatureBFixtures() {}

    record Fixture(String id, String dimension, long seed, int x, int z) {}

    static List<Fixture> fixtures() {
        var resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        return List.of(
                new Fixture("origin", "overworld", 123456789, 0, 0),
                new Fixture("nonorigin", "overworld", -5161763991829980711L, 125, -37),
                new Fixture("village", "overworld", 0, 17, 59),
                new Fixture("village-edge", "overworld", 0, 12, 59),
                new Fixture("mineshaft", "overworld", 0, -13, -7),
                new Fixture("mineshaft-edge", "overworld", 0, -15, -13),
                new Fixture("trial", "overworld", 0, 14, 5),
                new Fixture("monument", "overworld", 0, 86, -12),
                new Fixture("stronghold", "overworld", 0, -13, -106),
                new Fixture("nether", "the_nether", 123456789, -11, -6),
                new Fixture("fortress", "the_nether", 0, 15, 2),
                new Fixture("end-spike", "the_end", 123456789, 2, 0),
                new Fixture("end-city", "the_end", 0, -77, -79),
                new Fixture("spike-outside", "the_end", 123456789, 1, 0),
                new Fixture("origin-alt", "overworld", 1, 0, 0),
                new Fixture(
                        "spawn-village-cat",
                        "overworld",
                        resolver.resolveLocalWorldSeed(
                                123456789, Level.OVERWORLD, new ChunkPos(0, 2)),
                        0,
                        2),
                new Fixture("negative-coordinates", "overworld", -4896871951352006295L, -1, 1),
                new Fixture("carved-surface", "overworld", -195955039870900853L, -20000, 30000));
    }
}
