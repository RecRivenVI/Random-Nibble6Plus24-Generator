package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

class NativeEntityAcceptanceTest {
    @Test
    void catRuleOnlyReordersWholeAttributesAndKeepsAllValues() throws Exception {
        var a = entities("{id:'minecraft:cat',attributes:[{id:'a',base:1.0d},{id:'b',base:2.0d}]}");
        var b = entities("{id:'minecraft:cat',attributes:[{id:'b',base:2.0d},{id:'a',base:1.0d}]}");
        var raw = b.copy();
        assertFalse(NativeEntityAcceptance.compare(a, b).accepted());
        assertTrue(NativeEntityAcceptance.compare(a, b, false, true).accepted());
        assertEquals(raw, b);
        b.getCompoundOrEmpty(0)
                .getListOrEmpty("attributes")
                .getCompoundOrEmpty(0)
                .putDouble("base", 3);
        assertFalse(NativeEntityAcceptance.compare(a, b, false, true).accepted());
        var duplicate =
                entities("{id:'minecraft:cat',attributes:[{id:'a',base:1.0d},{id:'a',base:2.0d}]}");
        assertThrows(
                IllegalStateException.class,
                () -> NativeEntityAcceptance.compare(duplicate, duplicate, false, true));
    }

    private static final String ENTITY =
            """
            {id:'minecraft:drowned',UUID:[I;1,2,3,4],Pos:[1.0d,2.0d,3.0d],Health:20.0f,
             attributes:[{id:'minecraft:spawn_reinforcements',base:0.02d},
             {id:'minecraft:knockback_resistance',base:0.0d,modifiers:[{id:'minecraft:random_spawn_bonus',amount:0.01d,operation:'add_value'}]},
             {id:'minecraft:follow_range',base:35.0d,modifiers:[{id:'minecraft:random_spawn_bonus',amount:0.02d,operation:'add_multiplied_base'}]}]}
            """;

    private static ListTag entities(String snbt) throws Exception {
        var list = new ListTag();
        list.add(TagParser.parseCompoundFully(snbt));
        return list;
    }

    @Test
    void acceptsOnlyProvenValuesAndAttributeOrderWithoutChangingEvidence() throws Exception {
        var a = entities(ENTITY);
        var b =
                entities(
                        ENTITY.replace("base:0.02d", "base:0.07d")
                                .replace("amount:0.01d", "amount:0.04d")
                                .replace("1,2,3,4", "5,6,7,8"));
        var attributes = b.getCompoundOrEmpty(0).getListOrEmpty("attributes");
        attributes.add(attributes.remove(0));
        var rawA = a.copy();
        var rawB = b.copy();
        var result = NativeEntityAcceptance.compare(a, b);
        assertTrue(result.accepted());
        assertFalse(result.rawDifferences().isEmpty());
        assertTrue(result.unexpectedDifferences().isEmpty());
        assertEquals(rawA, a);
        assertEquals(rawB, b);
    }

    @Test
    void nearbyFieldsAndOtherEntitiesRemainStrict() throws Exception {
        for (var changed :
                new String[] {
                    ENTITY.replace("amount:0.02d", "amount:0.03d"),
                    ENTITY.replace("base:0.0d", "base:0.1d"),
                    ENTITY.replace("add_value", "add_multiplied_base"),
                    ENTITY.replace("Health:20.0f", "Health:19.0f"),
                    ENTITY.replace("1.0d,2.0d,3.0d", "1.0d,2.0d,4.0d"),
                    ENTITY.replace("base:0.02d", "missing:0.02d")
                }) {
            assertFalse(
                    NativeEntityAcceptance.compare(entities(ENTITY), entities(changed)).accepted(),
                    changed);
        }
        var zombie = ENTITY.replace("minecraft:drowned", "minecraft:zombie");
        assertFalse(
                NativeEntityAcceptance.compare(
                                entities(zombie),
                                entities(zombie.replace("base:0.02d", "base:0.03d")))
                        .accepted());
        assertFalse(
                NativeEntityAcceptance.compare(
                                entities("{id:'minecraft:shulker',Rotation:[0.0f,0.0f]}"),
                                entities("{id:'minecraft:shulker',Rotation:[90.0f,0.0f]}"))
                        .accepted());
    }

    @Test
    void malformedOrOutOfRangeFieldsFailClosed() throws Exception {
        for (var value : new String[] {"-0.01d", "0.2d", "0.02f", "'bad'"}) {
            var invalid = entities(ENTITY.replace("base:0.02d", "base:" + value));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> NativeEntityAcceptance.compare(invalid, invalid));
        }
        var duplicate =
                entities(
                        ENTITY.replace("minecraft:follow_range", "minecraft:spawn_reinforcements"));
        assertThrows(
                IllegalStateException.class,
                () -> NativeEntityAcceptance.compare(duplicate, duplicate));
        var malformedUuid = entities(ENTITY.replace("1,2,3,4", "1,2,3"));
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeEntityAcceptance.compare(malformedUuid, malformedUuid));
    }

    @Test
    void entityCountOrderAndNestedUuidRemainCompared() throws Exception {
        var a = entities(ENTITY);
        var b = a.copy();
        b.add(TagParser.parseCompoundFully("{id:'minecraft:pig'}"));
        assertFalse(NativeEntityAcceptance.compare(a, b).accepted());
        var c = entities("{id:'minecraft:pig',Owner:{UUID:[I;1,2,3,4]}}");
        var d = entities("{id:'minecraft:pig',Owner:{UUID:[I;1,2,3,5]}}");
        assertFalse(NativeEntityAcceptance.compare(c, d).accepted());
    }

    private static final String SHULKER =
            "{id:'minecraft:shulker',UUID:[I;1,2,3,4],Rotation:[1.0f,0.0f],Pos:[1.0d,2.0d,3.0d],Health:30.0f,AttachFace:0b}";

    @Test
    void shulkerYawRequiresSeparateExplicitRuleAndPreservesRawData() throws Exception {
        var a = entities(SHULKER);
        var b = entities(SHULKER.replace("Rotation:[1.0f", "Rotation:[6.0f"));
        var saved = b.copy();
        assertFalse(NativeEntityAcceptance.compare(a, b).accepted());
        var accepted = NativeEntityAcceptance.compare(a, b, true);
        assertTrue(accepted.accepted());
        assertEquals(1, accepted.rawDifferences().size());
        assertEquals("$[0].Rotation[0]", accepted.rawDifferences().getFirst().path());
        assertEquals(saved, b);
        // The End City rule does not also grant the Drowned exceptions.
        assertFalse(
                NativeEntityAcceptance.compare(
                                entities(ENTITY),
                                entities(ENTITY.replace("base:0.02d", "base:0.07d")),
                                true)
                        .accepted());
    }

    @Test
    void shulkerOtherFieldsCountAndEntityOrderRemainStrict() throws Exception {
        for (var changed :
                new String[] {
                    SHULKER.replace("1.0f,0.0f", "1.0f,1.0f"),
                    SHULKER.replace("Health:30.0f", "Health:29.0f"),
                    SHULKER.replace("AttachFace:0b", "AttachFace:1b"),
                    SHULKER.replace("3.0d", "4.0d"),
                    SHULKER.replace("Rotation", "missingRotation")
                })
            assertFalse(
                    NativeEntityAcceptance.compare(entities(SHULKER), entities(changed), true)
                            .accepted());
        var a = entities(SHULKER);
        var b = a.copy();
        b.add(TagParser.parseCompoundFully(SHULKER.replace("3.0d", "4.0d")));
        assertFalse(NativeEntityAcceptance.compare(a, b, true).accepted());
        a = b.copy();
        b.add(b.removeFirst());
        assertFalse(NativeEntityAcceptance.compare(a, b, true).accepted());
        var other = SHULKER.replace("minecraft:shulker", "minecraft:pig");
        assertFalse(
                NativeEntityAcceptance.compare(
                                entities(other),
                                entities(other.replace("1.0f,0.0f", "2.0f,0.0f")),
                                true)
                        .accepted());
    }

    @Test
    void shulkerYawIsNotAnArbitraryAngleOrNumericTolerance() throws Exception {
        for (var value : new String[] {"-1.0f", "6.2831855f", "90.0f", "1.0d", "'invalid'"}) {
            var invalid = entities(SHULKER.replace("Rotation:[1.0f", "Rotation:[" + value));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> NativeEntityAcceptance.compare(invalid, invalid, true));
        }
        for (float value :
                new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            var invalid = entities(SHULKER);
            invalid.getCompoundOrEmpty(0)
                    .getListOrEmpty("Rotation")
                    .set(0, FloatTag.valueOf(value));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> NativeEntityAcceptance.compare(invalid, invalid, true));
        }
        var wrongSize = entities(SHULKER.replace("1.0f,0.0f", "1.0f,0.0f,0.0f"));
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeEntityAcceptance.compare(wrongSize, wrongSize, true));
    }
}
