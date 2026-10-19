package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

public interface MosaicPhysicalLevelOwner extends MosaicGenerationLifecycleOwner {
    static MosaicPhysicalLevelOwner forLevel(net.minecraft.server.level.ServerLevel level) {
        return (MosaicPhysicalLevelOwner) level;
    }

    MosaicPhysicalLevelState mosaic$physicalState();
}
