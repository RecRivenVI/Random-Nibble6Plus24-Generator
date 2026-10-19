package io.github.recrivenvi.randomnibble6plus24generator.probe;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import net.neoforged.fml.common.Mod;

@Mod(ModConstants.MOD_ID + "_probe")
public final class WorldgenProbe {
    public WorldgenProbe() {
        if (Boolean.getBoolean("mosaic.profile.structureExperiment"))
            throw new IllegalStateException(
                    "NeoForge structure diagnostic modes are not validated");
    }
}
