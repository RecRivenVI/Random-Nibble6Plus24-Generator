package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlay;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureManager.class)
abstract class StructureManagerMixin {

    @Shadow @Final private LevelAccessor level;

    @Inject(
            method = "startsForStructure(IILjava/util/function/Predicate;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomnibble6plus24generator$overlayStarts(
            int chunkX,
            int chunkZ,
            Predicate<Structure> matcher,
            CallbackInfoReturnable<List<StructureStart>> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(
                    MosaicStructureOverlay.startsForStructure(
                            level, new ChunkPos(chunkX, chunkZ), matcher));
        }
    }

    @Inject(
            method =
                    "startsForStructure(IILnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomnibble6plus24generator$overlaySectionStarts(
            int chunkX,
            int chunkZ,
            Structure structure,
            CallbackInfoReturnable<List<StructureStart>> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(
                    MosaicStructureOverlay.startsForStructure(
                            level, SectionPos.of(chunkX, 0, chunkZ), structure));
        }
    }

    @Inject(
            method =
                    "getStructureAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomnibble6plus24generator$overlayStructureAt(
            BlockPos blockPos,
            Structure structure,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.structureAt(level, blockPos, structure));
        }
    }

    @Inject(
            method =
                    "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/tags/TagKey;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtTag(
            BlockPos blockPos,
            TagKey<Structure> tag,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(
                    MosaicStructureOverlay.structureWithPieceAt(level, blockPos, tag));
        }
    }

    @Inject(
            method =
                    "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/HolderSet;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtSet(
            BlockPos blockPos,
            HolderSet<Structure> structures,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(
                    MosaicStructureOverlay.structureWithPieceAt(level, blockPos, structures));
        }
    }

    @Inject(
            method =
                    "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtPredicate(
            BlockPos blockPos,
            Predicate<Holder<Structure>> matcher,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(
                    MosaicStructureOverlay.structureWithPieceAt(level, blockPos, matcher));
        }
    }

    @Inject(
            method =
                    "getStructureWithPieceAt(IIILnet/minecraft/world/level/levelgen/structure/Structure;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtStructure(
            int x,
            int y,
            int z,
            Structure structure,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(
                    MosaicStructureOverlay.structureWithPieceAt(
                            level, new BlockPos(x, y, z), structure));
        }
    }

    @Inject(method = "getAllStructuresAt(III)Ljava/util/Map;", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayAllStructures(
            int x, int y, int z, CallbackInfoReturnable<Map<Structure, LongSet>> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(
                    MosaicStructureOverlay.allStructuresAt(level, new BlockPos(x, y, z)));
        }
    }

    @Inject(method = "fillStartsForStructure", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayFillStarts(
            Structure structure,
            LongSet references,
            Consumer<StructureStart> consumer,
            CallbackInfo callback) {
        if (MosaicStructureOverlay.active(level)) {
            MosaicStructureOverlay.fillStartsForStructure(level, structure, references, consumer);
            callback.cancel();
        }
    }

    @Inject(method = "checkStructurePresence", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$refuseRemoteLocate(
            ChunkPos chunkPos,
            Structure structure,
            StructurePlacement placement,
            boolean createReference,
            CallbackInfoReturnable<StructureCheckResult> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.refuseLocate(level, chunkPos));
        }
    }
}
