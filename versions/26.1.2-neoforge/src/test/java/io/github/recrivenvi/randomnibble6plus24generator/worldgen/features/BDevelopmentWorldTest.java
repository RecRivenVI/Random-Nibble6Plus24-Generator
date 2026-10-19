package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BDevelopmentWorldTest {
    @Test
    void vanillaAndLegacyIdentityStayOutsideB() {
        assertFalse(BDevelopmentWorld.requireIdentity(0, 3, false, false, false));
        assertFalse(BDevelopmentWorld.requireIdentity(0, 3, false, true, false));
    }

    @Test
    void allIdentityPartsAreRequiredWithoutFallback() {
        assertTrue(BDevelopmentWorld.requireIdentity(3, 3, true, true, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(2, 3, true, true, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(3, 3, false, true, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(3, 3, true, false, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(3, 3, true, true, false));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(0, 3, false, true, true));
    }

    @Test
    void markerIsStrictAndTargetSpecific() {
        assertFalse(BDevelopmentWorld.PIPELINE.contains("${"));
        String valid = "{\"schema\":1,\"pipeline\":\"" + BDevelopmentWorld.PIPELINE + "\"}";
        assertDoesNotThrow(() -> BDevelopmentWorld.requireMarker(valid));
        for (String invalid :
                new String[] {
                    "{}",
                    valid + "{}",
                    valid.replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
                    valid.replace("\"schema\":1", "\"schema\":\"1\""),
                    valid.replace(BDevelopmentWorld.PIPELINE, "foreign-pipeline"),
                    valid.replace("\"schema\":1", "\"extra\":1,\"schema\":1")
                })
            assertThrows(
                    IllegalArgumentException.class, () -> BDevelopmentWorld.requireMarker(invalid));
    }
}
