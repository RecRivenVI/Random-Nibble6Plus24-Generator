package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
@Mixin(JigsawPlacement.class)
abstract class LazyJigsawObservationMixin {
    @Inject(method="addPieces(Lnet/minecraft/world/level/levelgen/RandomState;IZLnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Ljava/util/List;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",at=@At("HEAD"))
    private static void expand(RandomState random,int depth,boolean hack,ChunkGenerator generator,StructureTemplateManager templates,
            LevelHeightAccessor height,RandomSource rng,Registry<StructureTemplatePool> pools,PoolElementStructurePiece center,
            List<PoolElementStructurePiece> pieces,VoxelShape shape,PoolAliasLookup aliases,LiquidSettings liquids,CallbackInfo ci){
        var scope=LazyScopes.get(random);if(scope!=null)scope.count("jigsawExpansions",1);
    }
}
