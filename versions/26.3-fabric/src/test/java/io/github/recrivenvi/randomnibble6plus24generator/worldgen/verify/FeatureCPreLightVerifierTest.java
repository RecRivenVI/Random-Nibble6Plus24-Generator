package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

class FeatureCPreLightVerifierTest {
    @Test
    void nativeLifecycleEnvelopeIsSeparateAndInputIsNeverMutated() throws Exception {
        var before =
                TagParser.parseCompoundFully(
                        "{Status:'minecraft:features',LastUpdate:0L,isLightOn:0b,sections:[{Y:0b,block_states:{palette:[{Name:'minecraft:stone'}]}}]}");
        var after =
                TagParser.parseCompoundFully(
                        "{Status:'minecraft:light',LastUpdate:17L,isLightOn:1b,sections:[{Y:0b,block_states:{palette:[{Name:'minecraft:stone'}]},BlockLight:[B;1],SkyLight:[B;2]},{Y:1b,SkyLight:[B;3]}]}");
        var original = after.copy();
        assertEquals(
                FeatureCPreLightVerifier.content(before), FeatureCPreLightVerifier.content(after));
        assertNotEquals(
                FeatureCPreLightVerifier.lighting(before),
                FeatureCPreLightVerifier.lighting(after));
        assertEquals(original, after);
    }

    @Test
    void everyPersistedWorldgenFieldIncludingRawEntityUuidRemainsStrict() throws Exception {
        var before =
                TagParser.parseCompoundFully(
                        "{xPos:0,zPos:0,yPos:-4,sections:[{Y:0b,block_states:{palette:[{Name:'minecraft:stone'}]},biomes:{palette:['minecraft:plains']}}],entities:[{id:'minecraft:shulker',UUID:[I;1,2,3,4],Rotation:[1.0f,0.0f]}],block_entities:[{id:'minecraft:beehive',keepPacked:1b}],block_ticks:[{i:'minecraft:oak_leaves',t:1}],fluid_ticks:[{i:'minecraft:water',t:2}],PostProcessing:[[1s]],structures:{starts:{}},Heightmaps:{WORLD_SURFACE:[L;1L]},CarvingMasks:{AIR:[L;2L]}}");
        for (var field :
                List.of(
                        "xPos",
                        "zPos",
                        "yPos",
                        "sections",
                        "entities",
                        "block_entities",
                        "block_ticks",
                        "fluid_ticks",
                        "PostProcessing",
                        "structures",
                        "Heightmaps",
                        "CarvingMasks")) {
            var changed = before.copy();
            changed.remove(field);
            assertNotEquals(
                    FeatureCPreLightVerifier.content(before),
                    FeatureCPreLightVerifier.content(changed),
                    field);
        }
        var changed = before.copy();
        ((CompoundTag) changed.getListOrEmpty("entities").getFirst())
                .putIntArray("UUID", new int[] {1, 2, 3, 5});
        assertNotEquals(
                FeatureCPreLightVerifier.content(before),
                FeatureCPreLightVerifier.content(changed));
    }

    @Test
    void aSectionWithAnyNonLightMetadataCannotBeDropped() throws Exception {
        var before = TagParser.parseCompoundFully("{sections:[{Y:0b}]}");
        var after = TagParser.parseCompoundFully("{sections:[{Y:0b,unknown:1}]}");
        assertNotEquals(
                FeatureCPreLightVerifier.content(before), FeatureCPreLightVerifier.content(after));
    }

    @Test
    void lightArraysAndCorrectnessFlagRemainStrict() throws Exception {
        var before =
                TagParser.parseCompoundFully(
                        "{isLightOn:1b,sections:[{Y:0b,BlockLight:[B;1],SkyLight:[B;2]}]}");
        var block =
                TagParser.parseCompoundFully(
                        "{isLightOn:1b,sections:[{Y:0b,BlockLight:[B;3],SkyLight:[B;2]}]}");
        var sky =
                TagParser.parseCompoundFully(
                        "{isLightOn:1b,sections:[{Y:0b,BlockLight:[B;1],SkyLight:[B;3]}]}");
        var flag = before.copy();
        flag.putBoolean("isLightOn", false);
        for (var changed : List.of(block, sky, flag)) {
            assertNotEquals(
                    FeatureCPreLightVerifier.lighting(before),
                    FeatureCPreLightVerifier.lighting(changed));
        }
    }
}
