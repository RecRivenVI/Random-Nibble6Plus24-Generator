package io.github.recrivenvi.randomnibble6plus24generator.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class FabricMetadataTest {

    @Test
    void fabricMetadataDeclaresTheFrozenIdentityAndCommonEntrypoint() {
        JsonObject metadata = resourceJson("/fabric.mod.json");

        assertEquals("randomnibble6plus24generator", metadata.get("id").getAsString());
        assertEquals("0.0.0-dev", metadata.get("version").getAsString());
        assertEquals("Random Nibble6Plus24 Generator", metadata.get("name").getAsString());
        assertEquals("RecRivenVI", metadata.getAsJsonArray("authors").get(0).getAsString());
        assertEquals(
                "io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator",
                metadata.getAsJsonObject("entrypoints").getAsJsonArray("main").get(0).getAsString());
        assertEquals(
                "randomnibble6plus24generator.mixins.json",
                metadata.getAsJsonArray("mixins").get(0).getAsString());
        assertFalse(metadata.getAsJsonObject("depends").has("fabric-api"));
        assertTrue(metadata.getAsJsonObject("depends").has("fabric-registry-sync-v0"));
    }

    @Test
    void mixinConfigContainsOnlyApprovedIdentitySeedIsolationAndNativeCaptureHooks() {
        JsonObject mixins = resourceJson("/randomnibble6plus24generator.mixins.json");

        assertTrue(mixins.get("required").getAsBoolean());
        assertEquals(5, mixins.getAsJsonArray("mixins").size());
        assertTrue(mixins.getAsJsonArray("mixins").contains(new com.google.gson.JsonPrimitive("ChunkStatusTasksMixin")));
        assertTrue(mixins.getAsJsonArray("mixins").contains(new com.google.gson.JsonPrimitive("ChunkStepMixin")));
        assertTrue(mixins.getAsJsonArray("mixins").contains(new com.google.gson.JsonPrimitive("MinecraftServerMixin")));
        assertTrue(mixins.getAsJsonArray("mixins").contains(new com.google.gson.JsonPrimitive("SeedCommandMixin")));
        assertTrue(mixins.getAsJsonArray("mixins").contains(new com.google.gson.JsonPrimitive("WorldGenRegionMixin")));
    }

    @Test
    void englishTranslationContainsChunkWorldSeedKey() {
        JsonObject language = resourceJson("/assets/randomnibble6plus24generator/lang/en_us.json");
        assertTrue(language.has("commands.randomnibble6plus24generator.seed.chunk_world_seed"));
    }

    private static JsonObject resourceJson(String path) {
        try (var stream = FabricMetadataTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
