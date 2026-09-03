package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.LongSet;
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

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlay;

@Mixin(StructureManager.class)
abstract class StructureManagerMixin {

    @Shadow
    @Final
    private LevelAccessor level;

    @Inject(method = "startsForStructure(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayStarts(
            ChunkPos chunkPos,
            Predicate<Structure> matcher,
            CallbackInfoReturnable<List<StructureStart>> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.startsForStructure(level, chunkPos, matcher));
        }
    }

    @Inject(method = "startsForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlaySectionStarts(
            SectionPos sectionPos,
            Structure structure,
            CallbackInfoReturnable<List<StructureStart>> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.startsForStructure(level, sectionPos, structure));
        }
    }

    @Inject(method = "getStructureAt", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayStructureAt(
            BlockPos blockPos,
            Structure structure,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.structureAt(level, blockPos, structure));
        }
    }

    @Inject(method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/tags/TagKey;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtTag(
            BlockPos blockPos,
            TagKey<Structure> tag,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.structureWithPieceAt(level, blockPos, tag));
        }
    }

    @Inject(method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/HolderSet;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtSet(
            BlockPos blockPos,
            HolderSet<Structure> structures,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.structureWithPieceAt(level, blockPos, structures));
        }
    }

    @Inject(method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtPredicate(
            BlockPos blockPos,
            Predicate<Holder<Structure>> matcher,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.structureWithPieceAt(level, blockPos, matcher));
        }
    }

    @Inject(method = "getStructureWithPieceAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;",
            at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayPieceAtStructure(
            BlockPos blockPos,
            Structure structure,
            CallbackInfoReturnable<StructureStart> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.structureWithPieceAt(level, blockPos, structure));
        }
    }

    @Inject(method = "hasAnyStructureAt", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayAnyStructure(
            BlockPos blockPos,
            CallbackInfoReturnable<Boolean> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.hasAnyStructureAt(level, blockPos));
        }
    }

    @Inject(method = "getAllStructuresAt", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$overlayAllStructures(
            BlockPos blockPos,
            CallbackInfoReturnable<Map<Structure, LongSet>> callback) {
        if (MosaicStructureOverlay.active(level)) {
            callback.setReturnValue(MosaicStructureOverlay.allStructuresAt(level, blockPos));
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
