package io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.chunk.ChunkGenerator;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

class MosaicChunkGeneratorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void directCodecRoundTripsIdentityAndConfiguration() {
        MosaicChunkGenerator generator = MosaicTestWorlds.serializableMosaicGenerator(MosaicWorldProfile.current());
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, MosaicTestWorlds.registries());
        JsonElement encoded = MosaicChunkGenerator.CODEC.codec().encodeStart(ops, generator).getOrThrow();
        JsonObject object = encoded.getAsJsonObject();

        assertEquals("randomnibble6plus24generator:mosaic", MosaicChunkGenerator.CODEC_ID.toString());
        org.junit.jupiter.api.Assertions.assertTrue(object.has("biome_source"));
        org.junit.jupiter.api.Assertions.assertTrue(object.has("settings"));
        org.junit.jupiter.api.Assertions.assertTrue(object.has("mosaic_profile"));

        MosaicChunkGenerator decoded = MosaicChunkGenerator.CODEC.codec().parse(ops, encoded).getOrThrow();
        assertEquals(generator.profile(), decoded.profile());
        assertEquals(generator.generatorSettings(), decoded.generatorSettings());
        assertEquals(
                encoded,
                MosaicChunkGenerator.CODEC.codec().encodeStart(ops, decoded).getOrThrow());
    }

    @Test
    void harmlessDimensionIntrospectionComesFromSerializedSettings() {
        MosaicChunkGenerator generator = MosaicTestWorlds.mosaicGenerator(MosaicWorldProfile.current());

        assertEquals(generator.generatorSettings().value().noiseSettings().height(), generator.getGenDepth());
        assertEquals(generator.generatorSettings().value().noiseSettings().minY(), generator.getMinY());
        assertEquals(generator.generatorSettings().value().seaLevel(), generator.getSeaLevel());
    }

    @Test
    void everyVanillaGenerationStageFailsBeforeUsingItsArguments() {
        MosaicChunkGenerator generator = MosaicTestWorlds.mosaicGenerator(MosaicWorldProfile.current());

        assertUnavailable("createBiomes", () -> generator.createBiomes(null, null, null, null));
        assertUnavailable("createStructures", () -> generator.createStructures(null, null, null, null, null, null));
        assertUnavailable("createReferences", () -> generator.createReferences(null, null, null));
        assertUnavailable("fillFromNoise", () -> generator.fillFromNoise(null, null, null, null));
        assertUnavailable("buildSurface", () -> generator.buildSurface(null, null, null, null));
        assertUnavailable("applyCarvers", () -> generator.applyCarvers(null, 0L, null, null, null, null));
        assertUnavailable("applyBiomeDecoration", () -> generator.applyBiomeDecoration(null, null, null));
        assertUnavailable("spawnOriginalMobs", () -> generator.spawnOriginalMobs(null));
        assertUnavailable("getBaseHeight", () -> generator.getBaseHeight(0, 0, null, null, null));
        assertUnavailable("getBaseColumn", () -> generator.getBaseColumn(0, 0, null, null));
        assertUnavailable("addDebugScreenInfo", () -> generator.addDebugScreenInfo(null, null, null));
    }

    @Test
    void generatorDoesNotStoreADelegateThatCouldProduceFallbackTerrain() {
        assertFalse(Arrays.stream(MosaicChunkGenerator.class.getDeclaredFields())
                .anyMatch(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && ChunkGenerator.class.isAssignableFrom(field.getType())));
    }

    private static void assertUnavailable(String operation, org.junit.jupiter.api.function.Executable executable) {
        MosaicGenerationUnavailableException exception = assertThrows(
                MosaicGenerationUnavailableException.class,
                executable);
        assertTrue(exception.getMessage().contains(operation));
        assertInstanceOf(IllegalStateException.class, exception);
    }
}
