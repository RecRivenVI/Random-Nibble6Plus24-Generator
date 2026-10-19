package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapQueues;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Native 1.21.1 POI storage is unpacked and mutated only on the chunk-source main queue. */
public final class MosaicPhysicalPoiReconciler {
    private MosaicPhysicalPoiReconciler() {}

    public static CompletableFuture<Void> reconcile(ServerLevel level, ChunkAccess chunk) {
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        if (chunk.getPersistedStatus().isBefore(ChunkStatus.FEATURES))
            throw new IllegalStateException("POI requires complete physical FEATURES");
        var lifecycle =
                ((MosaicGenerationLifecycleOwner) level)
                        .randomnibble6plus24generator$generationLifecycle();
        lifecycle.checkPreparing();
        return CompletableFuture.runAsync(
                () -> {
                    lifecycle.checkPreparing();
                    if (!level.getServer().isSameThread()
                            || MosaicWorldIdentity.runtimeContext(level).orElseThrow() != runtime)
                        throw new IllegalStateException(
                                "POI handoff lost its owning server lifecycle");
                    var sections = chunk.getSections();
                    for (int index = 0; index < sections.length; index++)
                        level.getPoiManager()
                                .checkConsistencyWithBlocks(
                                        SectionPos.of(
                                                chunk.getPos(),
                                                chunk.getSectionYFromSectionIndex(index)),
                                        sections[index]);
                },
                ((ChunkMapQueues) level.getChunkSource().chunkMap).mosaic$mainThreadExecutor());
    }
}
