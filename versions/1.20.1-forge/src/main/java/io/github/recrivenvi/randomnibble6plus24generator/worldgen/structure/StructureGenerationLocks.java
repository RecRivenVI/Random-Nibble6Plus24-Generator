package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.StructurePoolAccess;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.StructurePoolListAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.structures.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.fml.ModList;

/** Opt-in protection for audited 1.20.1 state; no cross-target code or seed cache. */
public final class StructureGenerationLocks {
    private static final Object STRONGHOLD = new Object(),
            FORTRESS = new Object(),
            END_CITY = new Object();
    private static final ThreadLocal<Object> ROTATION_OWNER = new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicInteger ROTATIONS =
            new java.util.concurrent.atomic.AtomicInteger();

    public static Object rotationOwner() {
        return ROTATION_OWNER.get();
    }

    public static Object beginRotation(Object owner) {
        var previous = ROTATION_OWNER.get();
        ROTATION_OWNER.set(owner);
        ROTATIONS.incrementAndGet();
        return previous;
    }

    public static void endRotation(Object previous) {
        if (previous == null) ROTATION_OWNER.remove();
        else ROTATION_OWNER.set(previous);
        if (ROTATIONS.decrementAndGet() < 0)
            throw new IllegalStateException("Unbalanced native rotation initializer");
    }

    public static int activeRotationInitializers() {
        return ROTATIONS.get();
    }

    private static final Set<Class<?>> SOURCES =
            Set.of(
                    MultiNoiseBiomeSource.class,
                    FixedBiomeSource.class,
                    CheckerboardColumnBiomeSource.class,
                    TheEndBiomeSource.class);
    private static final Set<Class<?>> STRUCTURES =
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
        if (!Boolean.getBoolean(ModConstants.MOD_ID + ".structure.narrowLocks")) return false;
        var mods = ModList.get();
        if (mods == null) return false;
        for (var mod : mods.getMods())
            if (!StructureLockEnvironment.knownMod(mod.getModId())) return false;
        return true;
    }

    public static boolean supports(StructureGenerationContext context) {
        if (!enabled()
                || context.generator().getClass() != NoiseBasedChunkGenerator.class
                || !((Object) context.generator() instanceof NativeStructureSynchronization)
                || !SOURCES.contains(context.generator().getBiomeSource().getClass())
                || context.host().getServer().getStructureManager().getClass()
                        != StructureTemplateManager.class) return false;
        for (var set : context.state().possibleStructureSets())
            for (var entry : set.value().structures())
                if (!STRUCTURES.contains(entry.structure().value().getClass())) return false;
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

    public static boolean supportsElement(StructurePoolElement element) {
        var type = element.getClass();
        if (type == SinglePoolElement.class
                || type == LegacySinglePoolElement.class
                || type == EmptyPoolElement.class
                || type == FeaturePoolElement.class) return true;
        if (type != ListPoolElement.class) return false;
        for (var child : ((StructurePoolListAccess) element).mosaic$elements())
            if (!supportsElement(child)) return false;
        return true;
    }

    public static Object sharedPieceLock(Structure structure) {
        var type = structure.getClass();
        // Protect reset and full native expansion, not just initialization.
        if (type == StrongholdStructure.class) return STRONGHOLD;
        if (type == NetherFortressStructure.class) return FORTRESS;
        if (type == EndCityStructure.class) return END_CITY;
        return null;
    }
}
