package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Native fresh-universe light storage before INITIALIZE_LIGHT; no engine mutation API escapes. */
public final class FreshPreFeatureLighting {
    private final LevelLightEngine engine;

    public FreshPreFeatureLighting(BlockGetter localInputs, boolean skyLight) {
        engine =
                new LevelLightEngine(
                        new LightChunkGetter() {
                            @Override
                            public LightChunk getChunkForLighting(int x, int z) {
                                throw new UnsupportedOperationException(
                                        "Pre-FEATURES reads cannot run light propagation");
                            }

                            @Override
                            public BlockGetter getLevel() {
                                return localInputs;
                            }
                        },
                        true,
                        skyLight);
    }

    public int brightness(LightLayer layer, BlockPos pos) {
        return engine.getLayerListener(layer).getLightValue(pos);
    }

    public int rawBrightness(BlockPos pos, int skyDarken) {
        return engine.getRawBrightness(pos, skyDarken);
    }
}
