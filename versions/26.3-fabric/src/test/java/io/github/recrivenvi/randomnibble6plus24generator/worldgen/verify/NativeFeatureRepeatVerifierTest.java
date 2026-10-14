package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

class NativeFeatureRepeatVerifierTest {
    @Test
    void attributeIdentityViewExplainsOrderingButNeverHidesRawDifferences() throws Exception {
        var a =
                TagParser.parseCompoundFully(
                        "{attributes:[{id:'a',base:1.0d},{id:'b',base:2.0d}]}");
        var b =
                TagParser.parseCompoundFully(
                        "{attributes:[{id:'b',base:2.0d},{id:'a',base:1.0d}]}");
        assertFalse(NativeFeatureRepeatVerifier.differences(a, b).isEmpty());
        assertEquals(
                NativeFeatureRepeatVerifier.attributesById(a),
                NativeFeatureRepeatVerifier.attributesById(b));
        assertThrows(
                IllegalStateException.class,
                () ->
                        NativeFeatureRepeatVerifier.attributesById(
                                TagParser.parseCompoundFully("{attributes:[{id:'a'},{id:'a'}]}")));
    }

    @Test
    void reportsEveryFieldIncludingUuidAndNativeRandomAttributesWithoutMutatingInput()
            throws Exception {
        var a =
                TagParser.parseCompoundFully(
                        "{UUID:[I;1,2,3,4],attributes:[{id:'knockback_resistance',base:0.1d}],Health:20.0f}");
        var b =
                TagParser.parseCompoundFully(
                        "{UUID:[I;1,2,3,5],attributes:[{id:'knockback_resistance',base:0.2d}],Health:19.0f}");
        var original = a.copy();
        var diff = NativeFeatureRepeatVerifier.differences(a, b);
        assertEquals(
                List.of("$.Health", "$.UUID", "$.attributes[0].base"),
                diff.stream().map(NativeFeatureRepeatVerifier.Difference::path).toList());
        assertEquals(original, a);
        assertTrue(NativeFeatureRepeatVerifier.differences(a, a.copy()).isEmpty());
    }

    @Test
    void missingFieldsTypeDifferencesAndListOrderRemainVisible() throws Exception {
        var a = TagParser.parseCompoundFully("{x:1,entries:[{id:'a'},{id:'b'}],deleted:1}");
        var b =
                TagParser.parseCompoundFully(
                        "{x:1.0d,entries:[{id:'b'},{id:'a'},{id:'c'}],added:2}");
        var diff = NativeFeatureRepeatVerifier.differences(a, b);
        assertEquals(
                List.of(
                        "$.added",
                        "$.deleted",
                        "$.entries[0].id",
                        "$.entries[1].id",
                        "$.entries[2]",
                        "$.x"),
                diff.stream().map(NativeFeatureRepeatVerifier.Difference::path).toList());
        assertEquals("<absent>", diff.getFirst().before());
    }
}
