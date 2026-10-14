package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PreFeaturesEvidenceTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void rejectsDecorationAndAllLaterStatuses() {
        assertDoesNotThrow(() -> PreFeaturesVerifier.requirePreFeatures(ChunkStatus.TERRAIN));
        for (ChunkStatus status : ChunkStatus.getStatusList()) {
            if (status.isOrAfter(ChunkStatus.FEATURES))
                assertThrows(
                        IllegalStateException.class,
                        () -> PreFeaturesVerifier.requirePreFeatures(status));
        }
    }

    @Test
    void codecFloatEvidenceSurvivesJsonSerialization() {
        var encoded = new com.google.gson.JsonPrimitive(0.1F);
        assertEquals(
                PreFeaturesVerifier.canonicalJson(encoded).toString(),
                PreFeaturesVerifier.canonicalJson(JsonParser.parseString(encoded.toString()))
                        .toString());
    }

    @Test
    void canonicalJsonIgnoresOnlyObjectKeyOrder() {
        var left = JsonParser.parseString("{\"z\":2,\"a\":{\"c\":3,\"b\":1}}");
        var right = JsonParser.parseString("{\"a\":{\"b\":1,\"c\":3},\"z\":2}");
        assertEquals(
                PreFeaturesVerifier.canonicalJson(left).toString(),
                PreFeaturesVerifier.canonicalJson(right).toString());
        assertNotEquals(
                PreFeaturesVerifier.canonicalJson(JsonParser.parseString("[1,2]")).toString(),
                PreFeaturesVerifier.canonicalJson(JsonParser.parseString("[2,1]")).toString());
    }

    @Test
    void nbtRetainsTypesAndDoesNotIgnoreUuid() {
        CompoundTag first = new CompoundTag();
        first.putInt("UUID", 1);
        CompoundTag changed = new CompoundTag();
        changed.putInt("UUID", 2);
        assertNotEquals(
                PreFeaturesVerifier.canonicalNbt(first), PreFeaturesVerifier.canonicalNbt(changed));
        CompoundTag otherType = new CompoundTag();
        otherType.putLong("UUID", 1L);
        assertNotEquals(
                PreFeaturesVerifier.canonicalNbt(first),
                PreFeaturesVerifier.canonicalNbt(otherType));
    }

    @Test
    void structuredDiffLocatesNbtFieldsAndListPositions() {
        CompoundTag left = new CompoundTag();
        left.putInt("seed", 1);
        CompoundTag right = new CompoundTag();
        right.putInt("seed", 2);
        var a = new com.google.gson.JsonObject();
        a.addProperty("nbt", PreFeaturesVerifier.canonicalNbt(left));
        var b = new com.google.gson.JsonObject();
        b.addProperty("nbt", PreFeaturesVerifier.canonicalNbt(right));
        var field = PreFeaturesVerifier.firstJsonDifference(a, b);
        assertEquals("$.nbt.seed", field.path());
        assertEquals("3:1", field.left().getAsString());
        assertEquals("3:2", field.right().getAsString());
        assertEquals(
                "$[1]",
                PreFeaturesVerifier.firstJsonDifference(
                                JsonParser.parseString("[1,2]"), JsonParser.parseString("[1,3]"))
                        .path());
        assertEquals(
                "$.missing",
                PreFeaturesVerifier.firstJsonDifference(
                                JsonParser.parseString("{}"),
                                JsonParser.parseString("{\"missing\":1}"))
                        .path());
        assertNull(PreFeaturesVerifier.firstJsonDifference(a, a.deepCopy()));
    }

    @Test
    void observationHooksAreDisabledOutsideTheirValidation() {
        String previous = System.getProperty("validation.name");
        try {
            var plugin =
                    new io.github.recrivenvi.randomnibble6plus24generator.probe
                            .ValidationMixinPlugin();
            System.clearProperty("validation.name");
            plugin.onLoad(
                    "io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin");
            assertFalse(plugin.shouldApplyMixin("any", "any"));
            System.setProperty("validation.name", "conformance-stronghold");
            plugin.onLoad(
                    "io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin");
            assertFalse(plugin.shouldApplyMixin("any", "any"));
            System.setProperty("validation.name", "conformance-prefeatures");
            plugin.onLoad(
                    "io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin");
            assertTrue(plugin.shouldApplyMixin("any", "any"));
        } finally {
            if (previous == null) System.clearProperty("validation.name");
            else System.setProperty("validation.name", previous);
        }
    }
}
