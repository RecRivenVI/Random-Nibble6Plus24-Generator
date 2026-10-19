package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.structures.*;

/** Locks only native shared mutable state, never a target's complete structure search. */
public final class StructureGenerationLocks {
    private static final Object STRONGHOLD = new Object();
    private static final Object FORTRESS = new Object();
    private static final Object END_CITY = new Object();
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

    public static Object sharedPieceLock(Structure structure) {
        return sharedPieceLock(structure.getClass());
    }

    static Object sharedPieceLock(Class<?> type) {
        // These 26.3 implementations mutate static piece weights or generator state. A
        // server/seed-local lock would not protect simultaneous worlds in the same JVM.
        if (StrongholdStructure.class.isAssignableFrom(type)) return STRONGHOLD;
        if (NetherFortressStructure.class.isAssignableFrom(type)) return FORTRESS;
        if (EndCityStructure.class.isAssignableFrom(type)) return END_CITY;
        return null;
    }

    public static boolean supports(IsolatedGenerationContext context) {
        for (var set : context.structureState().possibleStructureSets()) {
            for (var entry : set.value().structures()) {
                if (!AUDITED.contains(entry.structure().value().getClass())) return false;
            }
        }
        // A vanilla Jigsaw structure can use a non-vanilla pool element. Do not infer
        // thread safety from registry keys or from the enclosing structure's class.
        for (var pool :
                context.hostLevel().registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL)) {
            if (!supportsPool(pool)) return false;
        }
        return true;
    }

    public static boolean supportsPool(StructureTemplatePool pool) {
        if (pool.getClass() != StructureTemplatePool.class) return false;
        return pool.getTemplates().stream().allMatch(entry -> supportsElement(entry.getFirst()));
    }

    static boolean supportsElement(StructurePoolElement element) {
        Class<?> type = element.getClass();
        if (type == SinglePoolElement.class
                || type == LegacySinglePoolElement.class
                || type == EmptyPoolElement.class
                || type == FeaturePoolElement.class) return true;
        if (type != ListPoolElement.class) return false;
        return ((ListPoolElement) element)
                .getElements().stream().allMatch(StructureGenerationLocks::supportsElement);
    }
}
