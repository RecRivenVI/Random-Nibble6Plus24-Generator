package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class FeatureContributionInputsTest {
    @Test
    void contributionViewDoesNotRetainClosedQueryCapabilities() {
        assertFalse(
                Arrays.stream(StrictFeatureBaseline.Result.class.getRecordComponents())
                        .anyMatch(
                                c ->
                                        c.getType() == LocalFeatureInputs.class
                                                || c.getType() == LocalTerrainColumns.class));
        assertEquals(
                StrictFeatureBaseline.Result.class,
                Arrays.stream(LightweightFeatureBaseline.Result.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("contributionInputs"))
                        .findFirst()
                        .orElseThrow()
                        .getReturnType());
    }

    @Test
    void exactQueriesHaveExplicitResourceOwnership() {
        assertTrue(AutoCloseable.class.isAssignableFrom(NeighborOreContributions.Query.class));
        assertTrue(AutoCloseable.class.isAssignableFrom(TreeProjectionRegion.class));
    }
}
