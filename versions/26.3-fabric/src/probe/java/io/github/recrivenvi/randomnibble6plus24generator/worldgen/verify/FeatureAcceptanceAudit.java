package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Independent acceptance reports: never replaces raw snapshots or their strict verdicts. */
final class FeatureAcceptanceAudit {
    private final Path root;
    private final boolean endCity;
    private final List<Object> comparisons = new ArrayList<>();
    private int checkpoints, crossJvmComparisons, exceptionsUsed;

    FeatureAcceptanceAudit(Path root) throws Exception {
        this(root, false);
    }

    FeatureAcceptanceAudit(Path root, boolean endCity) throws Exception {
        this.root = root;
        this.endCity = endCity;
        Files.createDirectories(root.resolve("acceptance"));
        verifyNativeEvidence();
    }

    private void verifyNativeEvidence() throws Exception {
        Path nativeRoot =
                root.getParent()
                        .getParent()
                        .getParent()
                        .resolve(endCity ? "native_shulker" : "native_repeat")
                        .resolve(root.getParent().getFileName());
        var pids = new HashSet<Long>();
        var worlds = new HashSet<String>();
        var first = FeatureStableSnapshot.read(nativeRoot.resolve("host_0/target.bin.gz"));
        var raw = readRaw(nativeRoot.resolve("host_0/entities-raw.nbt"));
        String trace = Files.readString(nativeRoot.resolve("host_0/trace.json"));
        for (int i = 0; i < 3; i++) {
            Path path = nativeRoot.resolve("host_" + i);
            var summary =
                    JsonParser.parseString(Files.readString(path.resolve("summary.json")))
                            .getAsJsonObject();
            var fixture = summary.getAsJsonObject("fixture");
            if (!summary.get("status").getAsString().equals("CAPTURED_NATIVE_ONLY")
                    || summary.get("experimentalGenerations").getAsLong() != 0
                    || summary.get("bindings").getAsLong() != 0
                    || !summary.get("generator")
                            .getAsString()
                            .equals("net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator")
                    || !summary.get("chunkMap")
                            .getAsString()
                            .equals("net.minecraft.server.level.ChunkMap")
                    || !pids.add(summary.get("pid").getAsLong())
                    || !worlds.add(summary.get("worldPath").getAsString())
                    || !fixture.get("dimension")
                            .getAsString()
                            .equals(endCity ? "minecraft:the_end" : "minecraft:overworld")
                    || fixture.get("x").getAsInt() != (endCity ? -77 : -14)
                    || fixture.get("z").getAsInt() != (endCity ? -79 : -13)
                    || fixture.get("seed").getAsLong() != (endCity ? 0 : 804143585224698994L)
                    || !trace.equals(Files.readString(path.resolve("trace.json"))))
                throw new IllegalStateException(
                        "Missing independent native evidence for finite acceptance");
            var snapshot = FeatureStableSnapshot.read(path.resolve("target.bin.gz"));
            if (!snapshot.hash().equals(summary.get("featureHash").getAsString()))
                throw new IllegalStateException("Native evidence hash changed");
            var currentRaw = readRaw(path.resolve("entities-raw.nbt"));
            if (currentRaw.size() != (endCity ? 3 : 1))
                throw new IllegalStateException("Missing native entity positive count");
            for (var tag : currentRaw)
                if (!(tag instanceof CompoundTag entity)
                        || !entity.getStringOr("id", "")
                                .equals(endCity ? "minecraft:shulker" : "minecraft:drowned"))
                    throw new IllegalStateException("Missing native entity positive");
            compare("native-" + i, first, raw, snapshot, currentRaw);
        }
    }

    static ListTag raw(FeatureCFlowVerifier.Result result) {
        var list = new ListTag();
        result.target().getEntities().forEach(e -> list.add(e.copy()));
        return list;
    }

    synchronized void checkpoint(String id, FeatureCFlowVerifier.Result result) throws Exception {
        Path directory = root.resolve("acceptance");
        var raw = raw(result);
        NativeEntityAcceptance.requireMatches(result.snapshot(), raw);
        result.snapshot().write(directory.resolve(id + ".bin.gz"));
        var tag = new CompoundTag();
        tag.put("entities", raw);
        NbtIo.writeCompressed(tag, directory.resolve(id + ".nbt"));
        checkpoints++;
        if (root.getFileName().toString().equals("host_1")) {
            Path previous = root.resolveSibling("host_0").resolve("acceptance");
            compare(
                    "cross-jvm-" + id,
                    FeatureStableSnapshot.read(previous.resolve(id + ".bin.gz")),
                    readRaw(previous.resolve(id + ".nbt")),
                    result.snapshot(),
                    raw);
            crossJvmComparisons++;
        }
    }

    synchronized void compare(
            String id, FeatureCFlowVerifier.Result a, FeatureCFlowVerifier.Result b) {
        if (!a.calls().equals(b.calls())
                || !a.trace().equals(b.trace())
                || !a.batches().equals(b.batches()))
            throw new IllegalStateException(id + " changed worldgen RNG/slots/outgoing data");
        compare(id, a.snapshot(), raw(a), b.snapshot(), raw(b));
    }

    synchronized void compare(
            String id,
            FeatureStableSnapshot a,
            ListTag rawA,
            FeatureStableSnapshot b,
            ListTag rawB) {
        var result = NativeEntityAcceptance.compare(a, rawA, b, rawB, endCity);
        comparisons.add(
                Map.of(
                        "id",
                        id,
                        "beforeHash",
                        a.hash(),
                        "afterHash",
                        b.hash(),
                        "result",
                        result,
                        "rawEntitiesBefore",
                        rawA.toString(),
                        "rawEntitiesAfter",
                        rawB.toString()));
        if (!result.strictParity() && result.accepted()) exceptionsUsed++;
        try {
            write(root.resolve("acceptance/comparisons.json"), comparisons);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot preserve acceptance evidence", error);
        }
        if (!result.accepted())
            throw new IllegalStateException(id + " finite acceptance FAIL: " + result);
    }

    synchronized Map<String, Object> summary() {
        return Map.of(
                "rule",
                endCity ? NativeEntityAcceptance.SHULKER_RULE : NativeEntityAcceptance.RULE,
                "comparisons",
                comparisons.size(),
                "checkpoints",
                checkpoints,
                "crossJvmComparisons",
                crossJvmComparisons,
                "exceptionsUsed",
                exceptionsUsed,
                "rawEvidencePreserved",
                true,
                "canonicalHashChanged",
                false,
                "nativeWorlds",
                3);
    }

    private static ListTag readRaw(Path path) throws Exception {
        var compound = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        if (!(compound.get("entities") instanceof ListTag list))
            throw new IllegalStateException("Missing raw entity list");
        return list;
    }

    private static void write(Path path, Object value) throws Exception {
        Files.writeString(
                path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }
}
