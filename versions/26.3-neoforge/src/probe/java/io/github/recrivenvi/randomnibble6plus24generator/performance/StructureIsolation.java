package io.github.recrivenvi.randomnibble6plus24generator.performance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.storage.LevelResource;

/** Destructive-to-parity experiment. Only loaded by the opt-in research test mod. */
public final class StructureIsolation {
    private static final boolean ENABLED = Boolean.getBoolean("mosaic.profile.structureExperiment");
    private static final String MODE = System.getProperty("mosaic.profile.structures", "baseline");
    private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();
    private static final Set<Structure> ALLOWED = ConcurrentHashMap.newKeySet();
    private static volatile boolean armed;

    private StructureIsolation() {}

    public static void arm(MinecraftServer server) {
        if (!ENABLED) return;
        if (!Set.of("baseline", "no-stronghold", "no-other", "none").contains(MODE))
            throw new IllegalArgumentException("Unknown structure experiment " + MODE);
        try {
            Path root = Path.of(System.getProperty("mosaic.profile.structureRoot")).toRealPath();
            Path world = server.getWorldPath(LevelResource.ROOT).toRealPath();
            if (!world.startsWith(root)
                    || world.equals(root)
                    || !Files.isRegularFile(world.resolve("DISPOSABLE_STRUCTURE_RESEARCH.json")))
                throw new IllegalStateException(
                        "Structure experiment refused non-research save: " + world);
            if (!PerfWorkload.cli())
                throw new IllegalStateException(
                        "Structure experiment requires explicit CLI workload");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        armed = true;
    }

    private static boolean allow(boolean rings) {
        return switch (MODE) {
            case "baseline" -> true;
            case "no-stronghold" -> !rings;
            case "no-other" -> rings;
            case "none" -> false;
            default -> throw new IllegalStateException(MODE);
        };
    }

    private static void count(String key, long amount) {
        COUNTS.computeIfAbsent(key, k -> new LongAdder()).add(amount);
    }

    public static List<Holder<StructureSet>> filter(List<Holder<StructureSet>> source) {
        if (!ENABLED) return source;
        if (!armed)
            throw new IllegalStateException(
                    "Structure state was created before research save validation");
        count("states", 1);
        var kept = new java.util.ArrayList<Holder<StructureSet>>();
        for (var holder : source) {
            boolean rings = holder.value().placement() instanceof ConcentricRingsStructurePlacement;
            String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("inline");
            boolean allowed = allow(rings);
            count((allowed ? "keptSet." : "removedSet.") + id, 1);
            if (allowed) {
                kept.add(holder);
                holder.value()
                        .structures()
                        .forEach(entry -> ALLOWED.add(entry.structure().value()));
            }
        }
        return MODE.equals("baseline") ? source : List.copyOf(kept);
    }

    public static void ringPreparation(ConcentricRingsStructurePlacement placement) {
        if (!ENABLED) return;
        if (!allow(true)) throw new IllegalStateException("Disabled ring computation executed");
        count("ringPreparations", 1);
        count("ringBiomeSearchJobs", placement.count());
    }

    public static void placement(StructurePlacement placement) {
        if (!ENABLED) return;
        boolean ring = placement instanceof ConcentricRingsStructurePlacement;
        if (!allow(ring))
            throw new IllegalStateException(
                    "Disabled structure placement executed: " + placement.getClass());
        count(ring ? "ringPlacementEvaluations" : "otherPlacementEvaluations", 1);
    }

    public static void generating(Holder<Structure> holder) {
        if (!ENABLED) return;
        if (!ALLOWED.contains(holder.value()))
            throw new IllegalStateException("Disabled structure start attempted: " + holder);
        count(
                "startAttempts."
                        + holder.unwrapKey().map(k -> k.identifier().toString()).orElse("inline"),
                1);
    }

    public static void generated(Holder<Structure> holder, boolean valid) {
        if (ENABLED && valid)
            count(
                    "validStarts."
                            + holder.unwrapKey()
                                    .map(k -> k.identifier().toString())
                                    .orElse("inline"),
                    1);
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> result = new TreeMap<>();
        result.put("enabled", ENABLED);
        result.put("mode", MODE);
        result.put("armed", armed);
        COUNTS.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }
}
