package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BDevelopmentWorldTest {
    @Test
    void legacyWorldNeverOptedInByPropertyAlone() {
        assertFalse(BDevelopmentWorld.requireIdentity(0, 3, false, true, true));
        assertFalse(BDevelopmentWorld.requireIdentity(0, 3, false, false, false));
        assertTrue(BDevelopmentWorld.requireIdentity(3, 3, true, true, true));
    }

    @Test
    void missingOrMixedIdentityFailsClosed() {
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(3, 3, false, true, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(3, 3, true, false, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(2, 3, true, true, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(0, 3, true, true, true));
        assertThrows(
                RuntimeException.class,
                () -> BDevelopmentWorld.requireIdentity(3, 3, true, true, false));
    }

    @Test
    void exactMarkerAccepted() {
        assertDoesNotThrow(
                () ->
                        BDevelopmentWorld.requireMarker(
                                "{\"schema\":1,\"pipeline\":\"26.3-lightweight-b-1\"}"));
    }

    @Test
    void unknownFieldRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BDevelopmentWorld.requireMarker(
                                "{\"schema\":1,\"pipeline\":\"26.3-lightweight-b-1\",\"extra\":true}"));
    }

    @Test
    void differentPipelineRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BDevelopmentWorld.requireMarker("{\"schema\":1,\"pipeline\":\"26.3-c-1\"}"));
    }

    @Test
    void coercedSchemaRejected() {
        for (var schema : new String[] {"\"1\"", "1.0", "true", "2", "null"})
            assertThrows(
                    RuntimeException.class,
                    () ->
                            BDevelopmentWorld.requireMarker(
                                    "{\"schema\":"
                                            + schema
                                            + ",\"pipeline\":\"26.3-lightweight-b-1\"}"));
    }

    @Test
    void duplicateOrMalformedMarkerRejected() {
        for (var text :
                new String[] {
                    "{\"schema\":1,\"schema\":1,\"pipeline\":\"26.3-lightweight-b-1\"}",
                    "{schema:1,pipeline:'26.3-lightweight-b-1'}",
                    "{\"schema\":1}",
                    "{\"schema\":1,\"pipeline\":\"26.3-lightweight-b-1\"} false"
                })
            assertThrows(RuntimeException.class, () -> BDevelopmentWorld.requireMarker(text));
    }
}
