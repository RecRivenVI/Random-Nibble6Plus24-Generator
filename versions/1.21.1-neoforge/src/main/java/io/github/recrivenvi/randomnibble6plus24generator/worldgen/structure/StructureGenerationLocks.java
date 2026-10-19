package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.StructurePoolAccess;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.StructurePoolListAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.structures.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.fml.ModList;

/** Candidate protection for 1.21.1's actual shared generation state, not another scheduler. */
public final class StructureGenerationLocks {
    private static final Object STRONGHOLD = new Object();
    private static final Object FORTRESS = new Object();
    private static final Object END_CITY = new Object();
    private static final Set<String> NATIVE_MODS =
            Set.of(
                    "minecraft",
                    "neoforge",
                    "javafml",
                    "mixinextras",
                    ModConstants.MOD_ID,
                    ModConstants.MOD_ID + "_probe");
    private static final Set<Class<?>> AUDITED =
            Set.of(
                    JigsawStructure.class,
                    BuriedTreasureStructure.class,
                    ShipwreckStructure.class,
                    OceanRuinStructure.class,
                    OceanMonumentStructure.class,
                    JungleTempleStructure.class,
                    SwampHutStructure.class,
                    IglooStructure.class,
                    StrongholdStructure.class,
                    NetherFossilStructure.class,
                    DesertPyramidStructure.class,
                    EndCityStructure.class,
                    NetherFortressStructure.class,
                    WoodlandMansionStructure.class,
                    RuinedPortalStructure.class,
                    MineshaftStructure.class);

    private StructureGenerationLocks() {}

    public static boolean enabled() {
        return Boolean.getBoolean(ModConstants.MOD_ID + ".structure.narrowLocks")
                && supportsMods(
                        ModList.get().getMods().stream().map(mod -> mod.getModId()).toList());
    }

    static boolean supportsMods(Iterable<String> mods) {
        for (String mod : mods) if (!NATIVE_MODS.contains(mod)) return false;
        return true;
    }

    public static Object sharedPieceLock(Structure structure) {
        return sharedPieceLock(structure.getClass());
    }

    static Object sharedPieceLock(Class<?> type) {
        // These native classes reset and mutate static piece weights/generator state.
        // The monitor must protect reset and complete expansion together across local seeds.
        if (type == StrongholdStructure.class) return STRONGHOLD;
        if (type == NetherFortressStructure.class) return FORTRESS;
        if (type == EndCityStructure.class) return END_CITY;
        return null;
    }

    public static boolean supports(StructureGenerationContext context) {
        if (!enabled()
                || context.generator().getClass() != NoiseBasedChunkGenerator.class
                || context.host().getServer().getStructureManager().getClass()
                        != StructureTemplateManager.class) return false;
        for (var set : context.state().possibleStructureSets())
            for (var entry : set.value().structures())
                if (!AUDITED.contains(entry.structure().value().getClass())) return false;
        for (var pool : context.host().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL))
            if (!supportsPool(pool)) return false;
        return true;
    }

    public static boolean supportsPool(StructureTemplatePool pool) {
        if (pool.getClass() != StructureTemplatePool.class) return false;
        for (var entry : ((StructurePoolAccess) pool).mosaic$rawTemplates())
            if (!supportsElement(entry.getFirst())) return false;
        return true;
    }

    static boolean supportsElement(StructurePoolElement element) {
        Class<?> type = element.getClass();
        if (type == SinglePoolElement.class
                || type == LegacySinglePoolElement.class
                || type == EmptyPoolElement.class
                || type == FeaturePoolElement.class) return true;
        if (type != ListPoolElement.class) return false;
        for (var child : ((StructurePoolListAccess) element).mosaic$elements())
            if (!supportsElement(child)) return false;
        return true;
    }
}
