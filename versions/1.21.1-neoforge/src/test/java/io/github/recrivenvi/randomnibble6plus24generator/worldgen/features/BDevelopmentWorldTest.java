package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BDevelopmentWorldTest {
    private static final String VALID =
            "{\"schema\":1,\"pipeline\":\"" + BDevelopmentWorld.PIPELINE + "\"}";

    @Test
    void acceptsOnlyThisVersionsExactIdentity() {
        assertDoesNotThrow(() -> BDevelopmentWorld.requireMarker(VALID));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BDevelopmentWorld.requireMarker(
                                VALID.replace(BDevelopmentWorld.PIPELINE, "26.3-lightweight-b-1")));
    }

    @Test
    void duplicateUnknownMissingAndTrailingFieldsAreRejected() {
        for (var invalid :
                new String[] {
                    VALID.replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
                    VALID.replace("\"schema\":1", "\"schema\":1,\"foreign\":0"),
                    VALID.replace("\"schema\":1,", ""),
                    VALID + " {}",
                    VALID.replace("\"schema\":1", "schema:1"),
                    VALID.replace("\"schema\":1", "\"schema\":1.0")
                })
            assertThrows(RuntimeException.class, () -> BDevelopmentWorld.requireMarker(invalid));
    }
}
