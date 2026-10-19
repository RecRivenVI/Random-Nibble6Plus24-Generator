package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import java.util.Set;

/** Bootstrap-safe capability facts; no Minecraft class is loaded by the Mixin plugin. */
public final class StructureLockEnvironment {
    private static final Set<String> MODS =
            Set.of(
                    "minecraft",
                    "forge",
                    "javafml",
                    "mclanguage",
                    "lowcodefml",
                    "mixinextras",
                    ModConstants.MOD_ID,
                    ModConstants.MOD_ID + "_probe");

    private StructureLockEnvironment() {}

    public static boolean knownMod(String id) {
        return MODS.contains(id);
    }
}
