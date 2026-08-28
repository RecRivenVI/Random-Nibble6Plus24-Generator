package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Routes CappedProcessor's direct physical ServerLevel seed read to the local universe. */
@Mixin(CappedProcessor.class)
abstract class CappedProcessorMixin {

    @Redirect(
            method = "finalizeProcessing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long randomnibble6plus24generator$useIsolatedSeed(
            ServerLevel physicalLevel,
            ServerLevelAccessor level,
            BlockPos position,
            BlockPos referencePos,
            List<StructureTemplate.StructureBlockInfo> originalBlockInfoList,
            List<StructureTemplate.StructureBlockInfo> processedBlockInfoList,
            StructurePlaceSettings settings) {
        if (level instanceof WorldGenRegion region) {
            var context = GenerationContextRegistry.find(region);
            if (context.isPresent()) {
                context.get().recordCappedProcessorSeedRedirect();
                return context.get().worldSeed();
            }
        }
        return physicalLevel.getSeed();
    }
}
