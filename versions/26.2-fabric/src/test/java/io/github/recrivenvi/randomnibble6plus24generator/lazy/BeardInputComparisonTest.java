package io.github.recrivenvi.randomnibble6plus24generator.lazy;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;

/** Research diagnostic regression, not a production canonicalization rule. */
class BeardInputComparisonTest {
    private static final String LOW =
            "Rigid[box=BoundingBox{minX=0, minY=0, minZ=0, maxX=1, maxY=1, maxZ=1}, terrainAdjustment=BEARD_THIN, groundLevelDelta=0]";
    private static final String HIGH =
            "Rigid[box=BoundingBox{minX=0, minY=64, minZ=0, maxX=1, maxY=65, maxZ=1}, terrainAdjustment=BEARD_THIN, groundLevelDelta=0]";
    private static final String AREA =
            "box=BoundingBox{minX=-24, minY=-24, minZ=-24, maxX=25, maxY=89, maxZ=25}";

    private static JsonObject input(String dump) {
        var out = new JsonObject();
        out.addProperty("[0, 0]", dump);
        return out;
    }

    @Test
    void differentNativeOrderRequiresBitExactDensity() {
        var result =
                BeardInputComparison.compare(
                        input(LOW + ";" + HIGH + ";" + AREA),
                        input(HIGH + ";" + LOW + ";" + AREA),
                        LevelHeightAccessor.create(-64, 192));
        assertEquals(1, result.getAsJsonArray("nativeMapOrderDifferentChunks").size());
        assertEquals(17L * 17 * 193, result.get("rawDoubleSamplesCompared").getAsLong());
        assertEquals(0, result.get("differentRawDoubles").getAsInt());
    }

    @Test
    void neverIgnoresChangedGeometryOrDuplicateCount() {
        var height = LevelHeightAccessor.create(-64, 192);
        assertThrows(
                IllegalStateException.class,
                () ->
                        BeardInputComparison.compare(
                                input(LOW + ";" + AREA), input(HIGH + ";" + AREA), height));
        assertThrows(
                IllegalStateException.class,
                () ->
                        BeardInputComparison.compare(
                                input(LOW + ";" + AREA),
                                input(LOW + ";" + LOW + ";" + AREA),
                                height));
    }

    @Test
    void missingQueryIsNotAnEmptyStructure() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        BeardInputComparison.compare(
                                input("box=null"),
                                new JsonObject(),
                                LevelHeightAccessor.create(-64, 192)));
    }
}
