package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Observes the existing verifier's joins; never requests, completes or replaces a future. */
public final class OrdinaryRequestEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    private OrdinaryRequestEvidence() {}

    public static void begin(MinecraftServer server) {
        if (!server.isSameThread() || ACTIVE.get() != null)
            throw new IllegalStateException("Nested/wrong-thread ordinary request observer");
        ACTIVE.set(new Frame(server));
    }

    public static void joined(Object value) {
        Frame frame = current();
        if (value instanceof ChunkResult<?> result
                && result.orElse(null) instanceof LevelChunk chunk) frame.chunks.add(chunk);
        // A failed native result is left untouched; the existing verifier reports its error.
    }

    public static void checkDeadline() {
        if (System.nanoTime() >= current().deadline)
            throw new IllegalStateException(
                    "Ordinary request exceeded the 300-second observation bound");
    }

    public static void captured(JsonObject raw) {
        Frame frame = current();
        if (!raw.get("status").getAsString().equals("PASS")) return;
        if (frame.proof != null) throw new IllegalStateException("Duplicate request result");
        String mode = System.getProperty(PREFIX + "mode", "");
        if (!List.of("concurrent", "pair", "patch").contains(mode)
                || !mode.equals(raw.get("mode").getAsString()))
            throw new IllegalStateException("Unknown ordinary request proof mode");
        String input = System.getProperty(PREFIX + "inputHash", "");
        if (!input.matches("[A-F0-9]{64}"))
            throw new IllegalStateException("Request proof has no source identity");
        var server = frame.server;
        var level =
                server.getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION,
                                Identifier.parse(raw.get("dimension").getAsString())));
        if (level == null) throw new IllegalStateException("Missing requested physical dimension");
        var target =
                new ChunkPos(
                        Integer.getInteger(PREFIX + "chunkX", 125),
                        Integer.getInteger(PREFIX + "chunkZ", -37));
        List<ChunkPos> positions = new ArrayList<>();
        if (mode.equals("patch")) {
            for (int z = target.z() - 1; z <= target.z() + 1; z++)
                for (int x = target.x() - 1; x <= target.x() + 1; x++)
                    positions.add(new ChunkPos(x, z));
        } else {
            positions.add(target);
            if (mode.equals("pair")) positions.add(new ChunkPos(target.x() + 1, target.z()));
        }
        int requests =
                mode.equals("concurrent")
                        ? Integer.getInteger(PREFIX + "repeats", 16)
                        : positions.size();
        if (frame.chunks.size() != requests
                || raw.get("ordinaryRequestCount").getAsInt() != requests
                || raw.get("returnedFullChunks").getAsInt() != requests)
            throw new IllegalStateException("Incomplete actual native join observations");
        var identities = new IdentityHashMap<LevelChunk, Boolean>();
        for (int i = 0; i < frame.chunks.size(); i++) {
            var chunk = frame.chunks.get(i);
            if (!chunk.getPos().equals(mode.equals("concurrent") ? target : positions.get(i)))
                throw new IllegalStateException("Native join returned another physical owner");
            identities.put(chunk, Boolean.TRUE);
        }
        if (identities.size() != positions.size())
            throw new IllegalStateException("Native repeated FULL joins returned distinct chunks");
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        if (runtime.profile().featureOrderingAlgorithmVersion() != 2)
            throw new IllegalStateException("Request proof requires the explicit B contract");
        var owners = new JsonArray();
        for (var position : positions) {
            long captures = MosaicPhysicalMaterializer.artifactCaptures(level, position);
            long publications = MosaicPhysicalMaterializer.publishes(level, position);
            if (captures != 1 || publications != 1)
                throw new IllegalStateException("Physical owner was not materialized once");
            var owner = new JsonObject();
            owner.addProperty("x", position.x());
            owner.addProperty("z", position.z());
            owner.addProperty(
                    "localSeed",
                    Long.toString(runtime.resolveLocalWorldSeed(level.dimension(), position)));
            owner.addProperty("artifactCaptures", captures);
            owner.addProperty("publications", publications);
            owners.add(owner);
        }
        frame.output = Path.of(System.getProperty(PREFIX + "output")).toAbsolutePath().normalize();
        var proof = new JsonObject();
        proof.addProperty("schema", 1);
        proof.addProperty("sidecarVersion", 1);
        proof.addProperty("scope", "ORDINARY_FULL_REQUESTS_NOT_TICKING_OR_PLAYER_ENTRY");
        proof.addProperty("inputHash", input);
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("logicalProcessors", Runtime.getRuntime().availableProcessors());
        proof.addProperty(
                "save",
                server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
        proof.addProperty("rawResult", frame.output.toString());
        proof.addProperty("distinctChunkObjects", identities.size());
        proof.addProperty("observedJoins", frame.chunks.size());
        proof.addProperty("bindings", GenerationContextRegistry.bindingCount());
        proof.addProperty("spawnBindings", MosaicSpawnContextRegistry.bindingCount());
        proof.addProperty("serverThread", server.isSameThread());
        proof.add("owners", owners);
        frame.proof = proof;
    }

    public static void close(boolean bodyReturned) {
        Frame frame = ACTIVE.get();
        ACTIVE.remove();
        if (frame == null) throw new IllegalStateException("No ordinary request observer");
        frame.chunks.clear();
        frame.server = null;
        if (!bodyReturned || frame.proof == null) return;
        frame.proof.addProperty("bodyReturned", true);
        frame.proof.addProperty("retainedChunks", frame.chunks.size());
        frame.proof.addProperty("retainedServer", frame.server != null);
        try {
            frame.proof.addProperty(
                    "rawResultSha256",
                    HexFormat.of()
                            .withUpperCase()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(Files.readAllBytes(frame.output))));
            Files.writeString(
                    Path.of(frame.output + ".requests.json"),
                    frame.proof.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Frame current() {
        Frame frame = ACTIVE.get();
        if (frame == null || !frame.server.isSameThread())
            throw new IllegalStateException("Wrong native request observation thread");
        return frame;
    }

    private static final class Frame {
        MinecraftServer server;
        final long deadline =
                System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(300);
        final List<LevelChunk> chunks = new ArrayList<>();
        JsonObject proof;
        Path output;

        Frame(MinecraftServer server) {
            this.server = server;
        }
    }
}
