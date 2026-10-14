package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.junit.jupiter.api.Test;

class LocalFeatureInputsTest {
    @Test
    void physicalHandoffCarriesOnlyLightweightReadCounters() {
        var reads =
                java.util.Arrays.stream(BFeaturePreparation.Result.class.getRecordComponents())
                        .filter(c -> c.getName().equals("reads"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(LocalFeatureInputs.Counters.class, reads.getType());
        var columns =
                java.util.Arrays.stream(LocalFeatureInputs.Counters.class.getRecordComponents())
                        .filter(c -> c.getName().equals("columns"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(LocalTerrainColumns.Counters.class, columns.getType());
    }

    @Test
    void everyNativeSectionReadAndWriteIsExplicitlyRouted() throws Exception {
        var query = Class.forName(LocalFeatureInputs.class.getName() + "$Neighbor$QuerySection");
        for (var method : LevelChunkSection.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            // This void method only recounts private constructor containers. All public queries
            // of those counts and every container accessor route to exact immutable inputs.
            if (method.getName().equals("recalcBlockCounts")) continue;
            assertEquals(
                    query,
                    query.getMethod(method.getName(), method.getParameterTypes())
                            .getDeclaringClass(),
                    method.toString());
        }
    }

    @Test
    void experimentHasNoPersistedProductionDispatch() {
        assertTrue(AutoCloseable.class.isAssignableFrom(LocalFeatureInputs.class));
        assertFalse(
                Arrays.stream(LightweightFeatureBaseline.class.getDeclaredFields())
                        .anyMatch(
                                f ->
                                        Modifier.isStatic(f.getModifiers())
                                                && !Modifier.isFinal(f.getModifiers())));
    }
}
