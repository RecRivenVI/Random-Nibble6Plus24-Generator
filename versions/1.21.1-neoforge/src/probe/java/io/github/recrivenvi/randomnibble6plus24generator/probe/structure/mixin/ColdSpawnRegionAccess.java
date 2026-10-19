package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.ColdSpawnEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
        targets =
                "io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalSpawner$Region",
        remap = false)
public interface ColdSpawnRegionAccess extends ColdSpawnEvidence.RegionInputs {
    @Override
    @Accessor("coldCollisionInputs")
    Map<ChunkPos, ChunkAccess> coldProof$sources();

    @Override
    @Accessor("collisions")
    SpawnCollisionSnapshot coldProof$carriedCollisions();
}
