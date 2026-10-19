package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapQueues;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.AsyncChunkRequestAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Real LIGHT shutdown/restart, with post-population component reads labelled separately. */
public final class ColdSpawnEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".coldSpawn.";
    private static ServerLevel level;
    private static CompletableFuture<ChunkResult<ChunkAccess>> requested;
    private static CompletableFuture<ChunkAccess> held;
    private static JsonObject warm;
    private static JsonObject comparison;
    private static String loadedStatus;
    private static long started;
    private static int targetPrepares;
    private static int populationCalls;
    private static boolean stopping;
    private static boolean expectedCancellation;
    private static int closingNotifications;
    private static int heldCancellationCompletions;
    private static boolean finished;

    public interface Inputs {
        Map<ChunkAccess, SpawnBiomeSnapshot> coldProof$biomes();

        Map<ChunkAccess, SpawnCollisionSnapshot> coldProof$collisions();
    }

    public interface RegionInputs {
        Map<ChunkPos, ChunkAccess> coldProof$sources();

        SpawnCollisionSnapshot coldProof$carriedCollisions();
    }

    private ColdSpawnEvidence() {}

    public static boolean enabled() {
        return "integration-production_lifecycle".equals(System.getProperty("validation.name"))
                && !System.getProperty(PREFIX + "mode", "").isBlank();
    }

    private static boolean staging() {
        return "stage".equals(System.getProperty(PREFIX + "mode"));
    }

    private static ChunkPos target() {
        return new ChunkPos(
                Integer.getInteger(PREFIX + "x", 125), Integer.getInteger(PREFIX + "z", -37));
    }

    public static void tick(MinecraftServer server) {
        if (!enabled() || finished || stopping) return;
        try {
            if (level == null) {
                if (!staging() && !"resume".equals(System.getProperty(PREFIX + "mode")))
                    throw new IllegalArgumentException("Unknown cold population phase");
                level = server.overworld();
                if (MosaicWorldIdentity.runtimeContext(level).isEmpty())
                    throw new IllegalStateException("Cold proof requires validated Mosaic B");
                started = System.nanoTime();
                var pos = target();
                level.getChunkSource().addRegionTicket(TicketType.PLAYER, pos, 0, pos);
                requested =
                        ((AsyncChunkRequestAccess) level.getChunkSource())
                                .mosaic$request(pos.x, pos.z, ChunkStatus.FULL, true);
            }
            if (System.nanoTime() - started > 180_000_000_000L)
                throw new IllegalStateException(
                        "Cold population request exceeded its fixed correctness bound");
            if (staging()) {
                if (warm == null) return;
                if (requested.isDone() || held == null || held.isDone())
                    throw new IllegalStateException("LIGHT checkpoint advanced through population");
                stopping = true;
                write("stage-before.json", base("STAGED_NATIVE_LIGHT_NOT_FULL"));
                server.halt(false);
                return;
            }
            if (!requested.isDone()) return;
            var chunk =
                    requested
                            .join()
                            .orElseThrow(() -> new IllegalStateException("Cold FULL was unloaded"));
            if (!(chunk instanceof LevelChunk)
                    || !chunk.isLightCorrect()
                    || chunk.getPersistedStatus() != ChunkStatus.FULL
                    || !ChunkStatus.LIGHT.toString().equals(loadedStatus)
                    || targetPrepares != 0
                    || populationCalls != 1
                    || comparison == null)
                throw new IllegalStateException(
                        "Saved LIGHT did not resume exactly once without canonical regeneration");
            var result = base("PASS");
            result.add("coldInputs", comparison);
            result.addProperty("fullChunkClass", chunk.getClass().getName());
            result.addProperty("fullStatus", chunk.getPersistedStatus().toString());
            write("resume.json", result);
            finished = true;
            level.getChunkSource().removeRegionTicket(TicketType.PLAYER, target(), 0, target());
            server.halt(false);
        } catch (Throwable failure) {
            fail(failure);
            throw new IllegalStateException("Cold population proof failed", failure);
        }
    }

    public static synchronized CompletableFuture<ChunkAccess> pause(
            ServerLevel host, ChunkAccess chunk) {
        if (!enabled() || !staging() || host != level || !chunk.getPos().equals(target()))
            return null;
        if (held != null
                || chunk.getPersistedStatus() != ChunkStatus.LIGHT
                || !chunk.isLightCorrect())
            throw new IllegalStateException("Duplicate/non-LIGHT population checkpoint");
        held = new CompletableFuture<>();
        ((ChunkMapQueues) level.getChunkSource().chunkMap)
                .mosaic$mainThreadExecutor()
                .execute(
                        () -> {
                            try {
                                var inputs =
                                        (Inputs)
                                                (Object)
                                                        ((MosaicPhysicalLevelOwner) level)
                                                                .mosaic$physicalState();
                                var biomes =
                                        java.util.Objects.requireNonNull(
                                                inputs.coldProof$biomes().get(chunk));
                                var collisions =
                                        java.util.Objects.requireNonNull(
                                                inputs.coldProof$collisions().get(chunk));
                                collisions.requireProvenance(biomes);
                                var saved = new JsonObject();
                                saved.addProperty(
                                        "biomes",
                                        biomeHash(
                                                pos ->
                                                        biomes.biome(
                                                                        new ChunkPos(
                                                                                QuartPos.toSection(
                                                                                        pos.getX()),
                                                                                QuartPos.toSection(
                                                                                        pos
                                                                                                .getZ())),
                                                                        pos.getX(),
                                                                        pos.getY(),
                                                                        pos.getZ())
                                                                .unwrapKey()
                                                                .orElseThrow()
                                                                .location()
                                                                .toString()));
                                saved.addProperty("collisions", collisionHash(collisions::view));
                                saved.addProperty("targetBlocks", blockHash(chunk));
                                saved.addProperty(
                                        "localSeed",
                                        MosaicWorldIdentity.runtimeContext(level)
                                                .orElseThrow()
                                                .resolveLocalWorldSeed(
                                                        level.dimension(), target()));
                                saved.addProperty("status", chunk.getPersistedStatus().toString());
                                write("warm-inputs.json", saved);
                                warm = saved;
                            } catch (Throwable failure) {
                                fail(failure);
                                MinecraftServer.setFatalException(
                                        new IllegalStateException(
                                                "Cold checkpoint observation failed", failure));
                            }
                        });
        return held;
    }

    public static void preparing(ChunkPos pos) {
        if (enabled() && pos.equals(target())) targetPrepares++;
    }

    public static void loaded(ChunkAccess chunk) {
        if (enabled() && !staging() && chunk.getPos().equals(target())) {
            if (loadedStatus != null)
                throw new IllegalStateException("Target disk input observed twice");
            loadedStatus = chunk.getPersistedStatus().toString();
            warm = read("warm-inputs.json");
            if (!warm.get("targetBlocks").getAsString().equals(blockHash(chunk)))
                throw new IllegalStateException("Native saved LIGHT terrain changed on reload");
        }
    }

    public static void afterPopulation(WorldGenRegion region) {
        if (!enabled()
                || staging()
                || region.getLevel() != level
                || !region.getCenter().equals(target())) return;
        try {
            populationCalls++;
            if (region.getSeed() != warm.get("localSeed").getAsLong())
                throw new IllegalStateException("Cold population lost its local seed authority");
            var inputs = (RegionInputs) region;
            if (inputs.coldProof$carriedCollisions() != null)
                throw new IllegalStateException(
                        "Restart unexpectedly retained a transient collision snapshot");
            int naturalSources = inputs.coldProof$sources().size();
            var biomes =
                    biomeHash(
                            pos ->
                                    region.getNoiseBiome(pos.getX(), pos.getY(), pos.getZ())
                                            .unwrapKey()
                                            .orElseThrow()
                                            .location()
                                            .toString());
            var collisions = collisionHash(pos -> region.getChunkForCollisions(pos.x, pos.z));
            comparison = new JsonObject();
            comparison.addProperty("biomesEqual", warm.get("biomes").getAsString().equals(biomes));
            comparison.addProperty(
                    "collisionsEqual", warm.get("collisions").getAsString().equals(collisions));
            comparison.addProperty("naturalPopulationCollisionSources", naturalSources);
            comparison.addProperty("postPopulationComponentReads", 8);
            comparison.addProperty("coldSourcesAfterComponent", inputs.coldProof$sources().size());
            comparison.addProperty("transientCollisionSnapshotAbsent", true);
            comparison.addProperty(
                    "scope", "POST_POPULATION_COMPONENT_READS_NOT_NATURAL_BOUNDARY_COVERAGE");
            comparison.addProperty("biomes", biomes);
            comparison.addProperty("collisions", collisions);
            if (!comparison.get("biomesEqual").getAsBoolean()
                    || !comparison.get("collisionsEqual").getAsBoolean())
                throw new IllegalStateException(
                        "Cold reconstructed same-seed inputs differ from the carried native inputs");
        } catch (Throwable failure) {
            fail(failure);
            throw new IllegalStateException("Cold input component mismatch", failure);
        }
    }

    public static void closing(MosaicPhysicalLevelState state) {
        if (!enabled()
                || level == null
                || state != ((MosaicPhysicalLevelOwner) level).mosaic$physicalState()
                || held == null) return;
        var life =
                ((MosaicPhysicalLevelOwner) level)
                        .randomnibble6plus24generator$generationLifecycle();
        var cancellation = life.cancellation();
        closingNotifications++;
        if (held.completeExceptionally(cancellation)) {
            expectedCancellation = life.isExpected(cancellation);
            heldCancellationCompletions++;
        }
    }

    public static void cleared(MosaicPhysicalLevelState state) {
        if (!enabled()
                || level == null
                || state != ((MosaicPhysicalLevelOwner) level).mosaic$physicalState()) return;
        if (state.metrics().inFlight() != 0)
            throw new IllegalStateException("Cold proof shutdown retained canonical futures");
        JsonObject result;
        if (staging()) {
            if (warm == null
                    || !stopping
                    || !expectedCancellation
                    || heldCancellationCompletions != 1)
                throw new IllegalStateException(
                        "Staged shutdown did not release its real pending request");
            result = base("PASS");
        } else {
            if (!finished || comparison == null) return;
            result = read("resume.json");
        }
        result.addProperty("afterNativeLevelClose", true);
        result.addProperty("inFlightAfterClose", state.metrics().inFlight());
        result.addProperty(
                "workersAfterClose",
                ((MosaicPhysicalLevelOwner) level)
                        .randomnibble6plus24generator$generationLifecycle()
                        .snapshot()
                        .workers());
        write(staging() ? "stage.json" : "resume.json", result);
        finished = true;
    }

    private static JsonObject base(String status) {
        var result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("status", status);
        result.addProperty("mode", System.getProperty(PREFIX + "mode"));
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("inputHash", System.getProperty(PREFIX + "inputHash"));
        result.addProperty("target", target().toString());
        result.addProperty(
                "dimension", level == null ? "unknown" : level.dimension().location().toString());
        result.addProperty("targetCanonicalPreparations", targetPrepares);
        result.addProperty("populationCalls", populationCalls);
        result.addProperty("loadedStatus", loadedStatus);
        result.addProperty("expectedShutdownCancellation", expectedCancellation);
        result.addProperty("closingNotifications", closingNotifications);
        result.addProperty("heldCancellationCompletions", heldCancellationCompletions);
        result.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1.0e9);
        return result;
    }

    private static String biomeHash(java.util.function.Function<BlockPos, String> getter) {
        var hash = digest();
        for (var pos : SpawnBiomeSnapshot.neighbors(target()))
            for (int y = QuartPos.fromBlock(level.getMinBuildHeight());
                    y < QuartPos.fromBlock(level.getMaxBuildHeight());
                    y++)
                for (int z = 0; z < 4; z++)
                    for (int x = 0; x < 4; x++)
                        update(hash, getter.apply(new BlockPos(pos.x * 4 + x, y, pos.z * 4 + z)));
        return HexFormat.of().formatHex(hash.digest());
    }

    private static String collisionHash(java.util.function.Function<ChunkPos, BlockGetter> getter) {
        var hash = digest();
        for (var pos : SpawnBiomeSnapshot.neighbors(target()))
            update(hash, blockHash(getter.apply(pos), pos));
        return HexFormat.of().formatHex(hash.digest());
    }

    private static String blockHash(ChunkAccess chunk) {
        return blockHash(chunk, chunk.getPos());
    }

    private static String blockHash(BlockGetter getter, ChunkPos pos) {
        var hash = digest();
        var names = new HashMap<net.minecraft.world.level.block.state.BlockState, String>();
        var cursor = new BlockPos.MutableBlockPos();
        for (int y = getter.getMinBuildHeight() - 1;
                y <= getter.getMinBuildHeight() + getter.getHeight();
                y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    var block =
                            getter.getBlockState(
                                    cursor.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z));
                    update(hash, names.computeIfAbsent(block, Object::toString));
                }
        return HexFormat.of().formatHex(hash.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static Path path(String name) {
        return Path.of(System.getProperty(PREFIX + "output")).resolve(name);
    }

    private static JsonObject read(String name) {
        try {
            return JsonParser.parseString(Files.readString(path(name))).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static void write(String name, JsonObject result) {
        try {
            Files.createDirectories(path(name).getParent());
            Files.writeString(
                    path(name),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static void fail(Throwable failure) {
        finished = true;
        var result = base("FAIL");
        if (comparison != null) result.add("coldInputs", comparison);
        result.addProperty("failure", failure.toString());
        write(staging() ? "stage.json" : "resume.json", result);
    }
}
