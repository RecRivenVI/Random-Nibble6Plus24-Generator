package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BDevelopmentWorld;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.LevelResource;

/** Observes real client players. It never creates, moves, respawns or ticks a player itself. */
public final class ClientLifecycleEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".clientLifecycle.";
    private static final Map<MinecraftServer, State> STATES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private ClientLifecycleEvidence() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PREFIX + "enabled");
    }

    private static State state(MinecraftServer server) {
        require(server.isSameThread(), "Client observer left the native server thread");
        return STATES.computeIfAbsent(server, State::new);
    }

    public static void ticket(ServerLevel level, TicketType type) {
        if (!enabled()) return;
        state(level.getServer())
                .tickets
                .merge(
                        Objects.requireNonNull(BuiltInRegistries.TICKET_TYPE.getKey(type))
                                .toString(),
                        1,
                        Integer::sum);
    }

    public static void findSpawn(ServerLevel level) {
        if (enabled()) state(level.getServer()).findSpawnCalls++;
    }

    public static void postProcess(LevelChunk chunk) {
        if (enabled() && chunk.getLevel() instanceof ServerLevel level)
            state(level.getServer()).postProcessCalls++;
    }

    public static void tick(MinecraftServer server) {
        if (!enabled()) return;
        var observer = state(server);
        try {
            observer.tick();
        } catch (Exception | Error failure) {
            observer.report.put("status", "FAIL");
            observer.report.put("failure", failure.toString());
            observer.write();
            throw new IllegalStateException("Real client lifecycle observer failed", failure);
        }
    }

    public static void closed(MinecraftServer server) {
        if (!enabled()) return;
        var observer = STATES.remove(server);
        if (observer == null) return;
        require(server.isSameThread(), "Wrong close thread");
        observer.player = null;
        var close = new LinkedHashMap<String, Object>();
        close.put("serverRunId", observer.id);
        close.put("pid", ProcessHandle.current().pid());
        close.put("inputHash", System.getProperty(PREFIX + "inputHash"));
        close.put("save", observer.report.get("save"));
        close.put("inFlight", MosaicPhysicalMaterializer.metrics().inFlightCount());
        close.put("bindings", GenerationContextRegistry.bindingCount());
        close.put("spawnBindings", MosaicSpawnContextRegistry.bindingCount());
        close.put("playerCount", server.getPlayerList().getPlayerCount());
        close.put("observerRetained", STATES.containsKey(server));
        close.put("checkpoints", observer.records.size());
        close.put("failure", observer.report.get("failure"));
        write(observer.directory.resolve("close-" + observer.id + ".json"), close);
    }

    private static final class State {
        final MinecraftServer server;
        final String id = UUID.randomUUID().toString();
        final Path directory = Path.of(System.getProperty(PREFIX + "directory"));
        final Map<String, Object> report = new LinkedHashMap<>();
        final Map<String, Integer> tickets = new TreeMap<>();
        final List<Map<String, Object>> records = new ArrayList<>();
        final long initialCaptures = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
        final Map<String, CompoundTag> structures = new TreeMap<>();
        ServerPlayer player;
        int findSpawnCalls, postProcessCalls, deaths, respawns;
        boolean wasDead;
        String lastRequest = "";
        String readinessKey = "";
        int readyPlayerTick = -1;

        State(MinecraftServer server) {
            this.server = server;
            require(
                    directory
                            .toAbsolutePath()
                            .normalize()
                            .startsWith(
                                    Path.of(System.getProperty("user.dir"))
                                            .toAbsolutePath()
                                            .normalize()),
                    "External observer directory");
            boolean mosaic = MosaicWorldIdentity.runtimeContext(server).isPresent();
            require(
                    mosaic == Boolean.getBoolean(PREFIX + "mosaic"),
                    "Wrong real-client world identity");
            report.put("schema", 1);
            report.put("scope", "REAL_CLIENT_SERVER_OBSERVER_NO_PLAYER_OR_WORLD_MUTATION");
            report.put("status", "OBSERVING");
            report.put("serverRunId", id);
            report.put("pid", ProcessHandle.current().pid());
            report.put("inputHash", System.getProperty(PREFIX + "inputHash"));
            report.put("mosaic", mosaic);
            report.put(
                    "save",
                    server.getWorldPath(LevelResource.ROOT)
                            .toAbsolutePath()
                            .normalize()
                            .toString());
            report.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
            report.put("worldSeed", Long.toString(server.getWorldGenSettings().options().seed()));
            report.put("checkpoints", records);
            report.put("tickets", tickets);
            write();
        }

        void tick() throws Exception {
            var players = server.getPlayerList().getPlayers();
            require(players.size() <= 1, "Observer fixture contains another player");
            ServerPlayer actual = players.isEmpty() ? null : players.getFirst();
            if (actual != null) {
                require(
                        actual.getGameProfile().name().equals("MosaicAutomation"),
                        "Wrong real client player");
                if (actual != player) {
                    if (player != null && player.getUUID().equals(actual.getUUID()) && wasDead)
                        respawns++;
                    player = actual;
                    readinessKey = "";
                }
                boolean dead = actual.isDeadOrDying();
                if (dead && !wasDead) deaths++;
                wasDead = dead;
            }
            Path request = directory.resolve("request.json");
            if (!Files.isRegularFile(request)) return;
            require(Files.size(request) <= 4096, "Oversized observer request");
            JsonObject input = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
            if (!id.equals(input.get("serverRunId").getAsString())) return;
            String key = input.get("id").getAsString();
            if (key.equals(lastRequest)) return;
            require(key.matches("[a-z0-9_]{1,64}"), "Invalid observer request id");
            if (actual == null || actual.isDeadOrDying()) return;
            String dimension = actual.level().dimension().identifier().toString();
            if (!dimension.equals(input.get("dimension").getAsString())) return;
            var pos = actual.chunkPosition();
            var holder =
                    actual.level().getChunkSource().chunkMap.getUpdatingChunkIfPresent(pos.pack());
            if (holder == null || holder.getFullStatus() != FullChunkStatus.ENTITY_TICKING) return;
            var future = holder.getEntityTickingChunkFuture().getNow(null);
            if (future == null || future.orElse(null) == null) return;
            var physical = actual.level().getChunkSource().getChunkNow(pos.x(), pos.z());
            require(
                    physical == future.orElse(null)
                            && physical.getPersistedStatus() == ChunkStatus.FULL
                            && physical.isLightCorrect(),
                    "Player does not see native lit physical FULL chunk");
            String candidateKey = key + "/" + dimension + "/" + pos.pack();
            if (!candidateKey.equals(readinessKey)) {
                readinessKey = candidateKey;
                readyPlayerTick = actual.tickCount;
            }
            if (actual.tickCount - readyPlayerTick < 20) return;
            require(
                    actual.level().getEntity(actual.getUUID()) == actual,
                    "Real player entity manager mismatch");
            String owner = dimension + "/" + pos.x() + "/" + pos.z();
            CompoundTag raw =
                    SerializableChunkData.copyOf(actual.level(), physical).structureData().copy();
            var previous = structures.putIfAbsent(owner, raw);
            require(
                    previous == null || previous.equals(raw),
                    "Structure metadata changed on a visited owner");
            long captures =
                    Boolean.TRUE.equals(report.get("mosaic"))
                            ? MosaicPhysicalMaterializer.artifactCaptures(actual.level(), pos)
                            : 0;
            if (input.has("baseline")) {
                String baseline = input.get("baseline").getAsString();
                require(baseline.matches("[a-f0-9-]{36}"), "Invalid structure baseline id");
                var expected =
                        NbtIo.readCompressed(
                                directory.resolve("structures-" + baseline + ".nbt"),
                                NbtAccounter.unlimitedHeap());
                require(
                        raw.equals(expected.get(owner)),
                        "Reload changed structure metadata of the visited owner");
                require(captures == 0, "Reload regenerated a saved FULL owner");
            }
            var record = new LinkedHashMap<String, Object>();
            record.put("id", key);
            record.put("dimension", dimension);
            record.put("chunkX", pos.x());
            record.put("chunkZ", pos.z());
            record.put("x", actual.getX());
            record.put("y", actual.getY());
            record.put("z", actual.getZ());
            record.put("playerUuid", actual.getUUID().toString());
            record.put("playerInstance", System.identityHashCode(actual));
            record.put("naturalPlayerTicks", actual.tickCount - readyPlayerTick);
            record.put("physicalStatus", holder.getFullStatus().toString());
            record.put("lightCorrect", true);
            record.put("artifactCaptures", captures);
            record.put("deaths", deaths);
            record.put("respawns", respawns);
            record.put("findSpawnCalls", findSpawnCalls);
            record.put("postProcessCalls", postProcessCalls);
            if (input.has("marker")) {
                var marker = input.getAsJsonArray("marker");
                var location =
                        new net.minecraft.core.BlockPos(
                                marker.get(0).getAsInt(),
                                marker.get(1).getAsInt(),
                                marker.get(2).getAsInt());
                require(
                        new ChunkPos(location.getX() >> 4, location.getZ() >> 4).equals(pos),
                        "Marker is not in the observed player owner");
                String block =
                        BuiltInRegistries.BLOCK
                                .getKey(physical.getBlockState(location).getBlock())
                                .toString();
                require(
                        block.equals("minecraft:gold_block"),
                        "Saved player modification did not survive");
                record.put("marker", block);
            }
            var neighbors = new ArrayList<Map<String, Object>>();
            for (int z = pos.z() - 1; z <= pos.z() + 1; z++) {
                for (int x = pos.x() - 1; x <= pos.x() + 1; x++) {
                    var neighbor = actual.level().getChunkSource().getChunkNow(x, z);
                    require(
                            neighbor != null && neighbor.getPersistedStatus() == ChunkStatus.FULL,
                            "Runtime player lacks a real physical FULL neighbor");
                    var runtime = MosaicWorldIdentity.runtimeContext(actual.level());
                    neighbors.add(
                            Map.of(
                                    "x",
                                    x,
                                    "z",
                                    z,
                                    "localSeed",
                                    Long.toString(
                                            runtime.isPresent()
                                                    ? runtime.orElseThrow()
                                                            .resolveLocalWorldSeed(
                                                                    actual.level().dimension(),
                                                                    new ChunkPos(x, z))
                                                    : server.getWorldGenSettings()
                                                            .options()
                                                            .seed())));
                }
            }
            record.put("physicalNeighbors", neighbors);
            records.add(record);
            report.put("status", "CHECKPOINT");
            report.put("latest", record);
            report.put(
                    "artifactCapturesSinceLoad",
                    MosaicPhysicalMaterializer.metrics().artifactCaptureCount() - initialCaptures);
            report.put("developmentB", BDevelopmentWorld.metrics(server));
            require(
                    Boolean.TRUE.equals(report.get("mosaic"))
                            || (long) report.get("artifactCapturesSinceLoad") == 0,
                    "Vanilla entered Mosaic generation");
            var structureNbt = new CompoundTag();
            structures.forEach(structureNbt::put);
            NbtIo.writeCompressed(structureNbt, directory.resolve("structures-" + id + ".nbt"));
            lastRequest = key;
            readyPlayerTick = -1;
            write();
        }

        void write() {
            ClientLifecycleEvidence.write(directory.resolve("status.json"), report);
            ClientLifecycleEvidence.write(directory.resolve("status-" + id + ".json"), report);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot write real client observation", failure);
        }
    }
}
