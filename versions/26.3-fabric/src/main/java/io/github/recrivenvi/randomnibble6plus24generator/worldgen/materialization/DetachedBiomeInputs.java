package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import java.util.function.BiConsumer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Exact native BIOMES data, detached from the live noise/sampler preparation. */
public final class DetachedBiomeInputs {
    private DetachedBiomeInputs() {}

    public static Result generate(
            IsolatedGenerationContext context,
            CompactStructureInputs structures,
            BiConsumer<ChunkStatus, ChunkAccess> checkpoint) {
        long began = System.nanoTime();
        structures.requireCompatible(context);
        var host = context.hostLevel();
        var staging =
                new ProtoChunk(
                        context.target(),
                        UpgradeData.EMPTY,
                        host,
                        host.palettedContainerFactory(),
                        null);
        structures.copyStartsTo(staging);
        staging.setPersistedStatus(ChunkStatus.STRUCTURE_STARTS);
        checkpoint.accept(ChunkStatus.STRUCTURE_STARTS, staging);
        structures.copyNativeReferencesTo(staging);
        staging.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
        checkpoint.accept(ChunkStatus.STRUCTURE_REFERENCES, staging);
        long sampling = System.nanoTime();
        // Native createBiomes owns cached climate semantics. Never substitute direct sampling.
        context.generator()
                .createBiomes(context.randomState(), Blender.empty(), structures.manager(), staging)
                .join();
        staging.setPersistedStatus(ChunkStatus.BIOMES);
        long biomeNanos = System.nanoTime() - sampling;

        // 26.3 doCreateBiomes releases its density-buffer pool in finally and does not attach a
        // NoiseChunk to ChunkAccess. The completed native chunk itself is the detached data;
        // do not copy a second chunk to imitate an older version's ownership model.
        checkpoint.accept(ChunkStatus.BIOMES, staging);
        return new Result(staging, new Metrics(1, biomeNanos, System.nanoTime() - began));
    }

    public record Result(ProtoChunk target, Metrics metrics) {}

    public record Metrics(int protoChunks, long nativeBiomeNanos, long elapsedNanos) {}
}
