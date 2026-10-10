package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Structure.class)
abstract class LazyStructureObservationMixin {
    @Inject(method = "generate", at = @At("HEAD"))
    private void begin(
            Holder<Structure> selected,
            ResourceKey<Level> dimension,
            RegistryAccess registries,
            ChunkGenerator generator,
            BiomeSource biomes,
            RandomState random,
            StructureTemplateManager templates,
            long seed,
            ChunkPos pos,
            int references,
            LevelHeightAccessor height,
            Predicate<Holder<Biome>> valid,
            CallbackInfoReturnable<StructureStart> ci) {
        var scope = LazyScopes.get(random);
        if (scope != null)
            scope.count("startAttempts." + selected.unwrapKey().orElseThrow().identifier(), 1);
    }

    @Inject(method = "generate", at = @At("RETURN"))
    private void end(
            Holder<Structure> selected,
            ResourceKey<Level> dimension,
            RegistryAccess registries,
            ChunkGenerator generator,
            BiomeSource biomes,
            RandomState random,
            StructureTemplateManager templates,
            long seed,
            ChunkPos pos,
            int references,
            LevelHeightAccessor height,
            Predicate<Holder<Biome>> valid,
            CallbackInfoReturnable<StructureStart> ci) {
        var scope = LazyScopes.get(random);
        if (scope != null && ci.getReturnValue().isValid()) {
            scope.count("validStarts", 1);
            scope.count("pieces", ci.getReturnValue().getPieces().size());
        }
    }
}
