package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.MultiNoiseParametersAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricBiomeTail;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.*;

/** Native climate primitives and actual candidate; no default-world difference is inferred. */
final class RingClimateProof {
    private RingClimateProof() {}

    static void verify(ServerLevel host, Path output) {
        var registry = host.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> aBiome = registry.getHolderOrThrow(Biomes.PLAINS);
        Holder<Biome> bBiome = registry.getHolderOrThrow(Biomes.DESERT);
        var aParameter = Climate.parameters(-1, 0, 0, 0, 0, 0, 0);
        var bParameter = Climate.parameters(1, 0, 0, 0, 0, 0, 0);
        var values = List.of(Pair.of(aParameter, aBiome), Pair.of(bParameter, bBiome));
        var parameters = new Climate.ParameterList<>(values);
        var source = MultiNoiseBiomeSource.createFromList(parameters);
        var tail = new ConcentricBiomeTail(source);
        var cursor = cursor(source);
        var a = Climate.target(-1, 0, 0, 0, 0, 0);
        var b = Climate.target(1, 0, 0, 0, 0, 0);
        var tie = Climate.target(0, 0, 0, 0, 0, 0);
        var rows = new JsonArray();
        for (boolean forward : new boolean[] {true, false}) {
            var entry = forward ? a : b;
            var body = forward ? b : a;
            for (boolean ambiguous : new boolean[] {false, true}) {
                var last = ambiguous ? tie : body;
                source.getNoiseBiome(entry);
                nativeReads(source, body, last);
                Object nativeExit = cursor.get();
                var nativeNext = source.getNoiseBiome(tie);
                source.getNoiseBiome(entry);
                var skippedNext = source.getNoiseBiome(tie);
                require(!nativeNext.equals(skippedNext), "Missing native skip counterexample");
                source.getNoiseBiome(entry);
                Object before = cursor.get();
                boolean replayed = tail.replay(last);
                require(replayed != ambiguous, "Incorrect unique-tail/fallback decision");
                if (!replayed) {
                    require(cursor.get() == before, "Certification changed native entry leaf");
                    nativeReads(source, body, last);
                }
                require(cursor.get() == nativeExit, "Native exit leaf identity differs");
                require(
                        source.getNoiseBiome(tie).equals(nativeNext),
                        "Native continuation differs");
                var row = new JsonObject();
                row.addProperty("direction", forward ? "A_to_B" : "B_to_A");
                row.addProperty("ambiguous", ambiguous);
                row.addProperty("fallback", !replayed);
                row.addProperty("originalSkipDiffers", true);
                row.addProperty("entryPreservedOnFallback", true);
                row.addProperty("exitLeafIdentityEqual", true);
                row.addProperty("continuationIdentical", true);
                rows.add(row);
            }
        }
        source.getNoiseBiome(a);
        Object entry = cursor.get();
        require(
                !tail.replay(new Climate.TargetPoint(Long.MAX_VALUE, 0, 0, 0, 0, 0)),
                "Unbounded fitness accepted");
        require(cursor.get() == entry, "Unbounded query changed entry");
        var duplicate =
                MultiNoiseBiomeSource.createFromList(
                        new Climate.ParameterList<>(
                                List.of(Pair.of(aParameter, aBiome), Pair.of(aParameter, aBiome))));
        duplicate.getNoiseBiome(a);
        Object duplicateEntry = cursor(duplicate).get();
        require(!new ConcentricBiomeTail(duplicate).replay(a), "Duplicate minima accepted");
        require(cursor(duplicate).get() == duplicateEntry, "Duplicate fallback changed entry");
        var unknown = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(values) {});
        require(!new ConcentricBiomeTail(unknown).replay(b), "Unknown parameter list accepted");
        var result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("scope", "NATIVE_CLIMATE_COMPONENT_NOT_DEFAULT_WORLD_PARITY");
        result.addProperty("bidirectionalCases", 4);
        result.addProperty("unsafeInputRejected", true);
        result.addProperty("duplicateMinimaRejected", true);
        result.addProperty("unknownParameterListRejected", true);
        result.add("rows", rows);
        NativeStructureVerifier.write(output.resolve("ring-climate-tail.json"), result);
    }

    private static void nativeReads(
            MultiNoiseBiomeSource source, Climate.TargetPoint body, Climate.TargetPoint last) {
        for (int index = 0; index < 57 * 57 - 1; index++) source.getNoiseBiome(body);
        source.getNoiseBiome(last);
    }

    private static ThreadLocal<?> cursor(MultiNoiseBiomeSource source) {
        try {
            var parameters = ((MultiNoiseParametersAccess) source).mosaic$parameters();
            Field index =
                    Arrays.stream(Climate.ParameterList.class.getDeclaredFields())
                            .filter(field -> field.getType().getSimpleName().equals("RTree"))
                            .findFirst()
                            .orElseThrow();
            index.setAccessible(true);
            Object tree = index.get(parameters);
            Field state =
                    Arrays.stream(tree.getClass().getDeclaredFields())
                            .filter(field -> field.getType() == ThreadLocal.class)
                            .findFirst()
                            .orElseThrow();
            state.setAccessible(true);
            return (ThreadLocal<?>) state.get(tree);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native component cursor unavailable", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
