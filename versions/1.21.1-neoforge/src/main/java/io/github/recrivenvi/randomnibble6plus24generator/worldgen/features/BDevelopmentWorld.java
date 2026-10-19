package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicIdentityValidationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** New-save opt-in; called only at an explicit world identity lifecycle boundary. */
public final class BDevelopmentWorld {
    public static final String MARKER = "mosaic-development-b.json";
    public static final String PIPELINE = ModConstants.MINECRAFT_VERSION + "-lightweight-b-1";
    public static final String PROPERTY = ModConstants.MOD_ID + ".developmentB";

    private BDevelopmentWorld() {}

    public static void validate(Path root, boolean mosaic) {
        Path marker = root.resolve(MARKER);
        boolean present = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
        if (!mosaic && !present) return;
        if (!mosaic || !present || !Boolean.getBoolean(PROPERTY))
            throw new MosaicIdentityValidationException(
                    "B requires matching generators, saved profile, marker and explicit opt-in");
        try {
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(marker) > 1024)
                throw new IllegalArgumentException("Invalid B save marker");
            requireMarker(Files.readString(marker));
        } catch (IOException | RuntimeException failure) {
            throw new MosaicIdentityValidationException("Unreadable B save identity", failure);
        }
    }

    static void requireMarker(String text) {
        try (var reader = new JsonReader(new StringReader(text))) {
            // This Target uses Gson's native strict reader, not the later Strictness API.
            reader.setLenient(false);
            reader.beginObject();
            var seen = new HashSet<String>();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!seen.add(key)) throw new IllegalArgumentException("Duplicate B marker field");
                if (key.equals("schema")) {
                    if (reader.peek() != JsonToken.NUMBER || !reader.nextString().equals("1"))
                        throw new IllegalArgumentException("Unsupported B marker schema");
                } else if (key.equals("pipeline")) {
                    if (reader.peek() != JsonToken.STRING || !reader.nextString().equals(PIPELINE))
                        throw new IllegalArgumentException("Foreign B pipeline identity");
                } else throw new IllegalArgumentException("Unknown B marker field");
            }
            reader.endObject();
            if (!seen.equals(Set.of("schema", "pipeline"))
                    || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Incomplete B marker");
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid B marker JSON", failure);
        }
    }
}
