package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BDevelopmentWorld;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Native server player lifecycle with a probe transport, not client rendering/handshake proof. */
public final class PlayerLifecycleEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static State active;

    private PlayerLifecycleEvidence() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PREFIX + "playerLifecycle");
    }

    public static void tick(MinecraftServer server) {
        if (!enabled()) return;
        if (active == null) active = new State(server);
        var state = active;
        try {
            state.tick();
        } catch (Exception | Error failure) {
            state.result.put("status", "FAIL");
            state.result.put("failure", failure.toString());
            state.write();
            throw new IllegalStateException("Native player lifecycle probe failed", failure);
        }
    }

    public static void ticket(ServerLevel level, TicketType type) {
        var state = active;
        if (state != null && level.getServer() == state.server) {
            state.requireThread();
            String name =
                    Objects.requireNonNull(
                                    net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE
                                            .getKey(type),
                                    "Unregistered native ticket type")
                            .toString();
            state.tickets.merge(name, 1, Integer::sum);
        }
    }

    public static void findSpawn(ServerLevel level) {
        var state = active;
        if (state != null && level.getServer() == state.server) {
            state.requireThread();
            state.findSpawnCalls++;
        }
    }

    public static void postProcess(LevelChunk chunk) {
        var state = active;
        if (state != null
                && chunk.getLevel() instanceof ServerLevel level
                && level.getServer() == state.server) {
            state.requireThread();
            state.postProcessCalls++;
        }
    }

    public static void closed(MinecraftServer server) {
        if (!enabled() || active == null) return;
        var state = active;
        require(state.server == server, "Wrong closing server");
        var report = new LinkedHashMap<String, Object>();
        report.put("completed", state.finished);
        report.put("inputHash", System.getProperty(PREFIX + "playerInputHash"));
        report.put("pid", ProcessHandle.current().pid());
        report.put("playerRetained", state.player != null);
        report.put("prepareTaskRetained", state.prepare != null);
        report.put("channelRetained", state.channel != null);
        report.put("inFlight", MosaicPhysicalMaterializer.metrics().inFlightCount());
        report.put("bindings", GenerationContextRegistry.bindingCount());
        report.put("spawnBindings", MosaicSpawnContextRegistry.bindingCount());
        write(state.output.resolveSibling("player-close-" + state.mode + ".json"), report);
        active = null;
    }

    private static final class State {
        final MinecraftServer server;
        final Path output;
        final String mode;
        final boolean vanilla;
        final long began = System.nanoTime();
        final GameProfile profile =
                new GameProfile(
                        UUID.nameUUIDFromBytes(
                                (ModConstants.MOD_ID + ":player-lifecycle")
                                        .getBytes(StandardCharsets.UTF_8)),
                        "LifecycleProbe");
        final List<Map<String, Object>> phases = new ArrayList<>();
        final Map<String, Integer> tickets = new TreeMap<>();
        final Map<String, Object> result = new LinkedHashMap<>();
        final Map<String, CompoundTag> structureInputs = new TreeMap<>();
        final Map<String, Integer> outbound = new TreeMap<>();
        final java.util.Queue<Packet<?>> observedPackets =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        PrepareSpawnTask prepare;
        Connection connection;
        EmbeddedChannel channel;
        ServerPlayer player;
        int step, joins, findSpawnCalls, findBefore, postProcessCalls, encodedFrames;
        long phaseTick = -1;
        boolean disconnecting, finished;
        Vec3 firstPosition;
        BlockPos marker;

        State(MinecraftServer server) {
            this.server = server;
            output = Path.of(System.getProperty(PREFIX + "output"));
            mode = System.getProperty(PREFIX + "mode");
            vanilla = Boolean.getBoolean(PREFIX + "vanillaControl");
            require(
                    vanilla == MosaicWorldIdentity.runtimeContext(server).isEmpty(),
                    "Wrong native/Mosaic identity");
            require(
                    server.getPlayerList().getPlayers().isEmpty(),
                    "Probe requires an isolated player fixture");
            server.setRespawnData(
                    LevelData.RespawnData.of(
                            Level.OVERWORLD,
                            new BlockPos(
                                    Integer.getInteger(PREFIX + "chunkX") * 16 + 8,
                                    80,
                                    Integer.getInteger(PREFIX + "chunkZ") * 16 + 8),
                            0,
                            0));
            result.put("schema", 1);
            result.put(
                    "scope",
                    "NATIVE_SERVER_PLAYER_LIFECYCLE_PROBE_TRANSPORT_NOT_CLIENT_HANDSHAKE_OR_RENDER");
            result.put("pid", ProcessHandle.current().pid());
            result.put("mode", mode);
            result.put("normalVanilla", vanilla);
            result.put("inputHash", System.getProperty(PREFIX + "playerInputHash"));
            result.put(
                    "save",
                    server.getWorldPath(LevelResource.ROOT)
                            .toAbsolutePath()
                            .normalize()
                            .toString());
            result.put("worldSeed", Long.toString(server.getWorldGenSettings().options().seed()));
            result.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
            result.put("phases", phases);
            result.put("tickets", tickets);
            result.put("outboundPackets", outbound);
        }

        void requireThread() {
            require(server.isSameThread(), "Player lifecycle observer left server thread");
        }

        void tick() throws Exception {
            requireThread();
            if (finished) return;
            if (System.nanoTime() - began > 300_000_000_000L)
                throw new IllegalStateException(
                        "Player lifecycle exceeded bounded 300s: step="
                                + step
                                + " phases="
                                + phases);
            drain();
            if (step == 0 || step == 3) {
                boolean saved =
                        server.getPlayerList().loadPlayerData(new NameAndId(profile)).isPresent();
                require(
                        saved == (mode.equals("reload") || joins != 0),
                        "Unexpected player-data authority");
                findBefore = findSpawnCalls;
                prepare = new PrepareSpawnTask(server, new NameAndId(profile));
                prepare.start(
                        packet ->
                                outbound.merge("configuration:" + packet.type(), 1, Integer::sum));
                step++;
            } else if (step == 1 || step == 4) {
                prepare.keepAlive();
                if (!prepare.tick()) return;
                connect();
                player =
                        prepare.spawnPlayer(
                                connection, CommonListenerCookie.createInitial(profile, false));
                prepare.close();
                prepare = null;
                boolean fresh = joins == 0 && !mode.equals("reload");
                require(
                        fresh ? findSpawnCalls > findBefore : findSpawnCalls == findBefore,
                        "Native new/returning spawn search branch differs");
                if (joins == 0) {
                    firstPosition = player.position();
                    result.put("firstJoinUsedSpawnFinder", fresh);
                    result.put(
                            "firstPosition",
                            List.of(firstPosition.x, firstPosition.y, firstPosition.z));
                    result.put("firstChunk", player.chunkPosition().toString());
                } else
                    require(
                            player.position().distanceToSqr(firstPosition) < 1e-12,
                            "Returning player lost saved position");
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                joins++;
                step++;
                phaseTick = -1;
            } else if (step == 2 || step == 5) {
                if (!ready(step == 2 ? "first_join" : "returning_player")) return;
                if (step == 2) {
                    disconnect();
                    step = 6;
                } else {
                    var oldPlayer = player;
                    player.setGameMode(GameType.SURVIVAL);
                    require(
                            player.hurtServer(
                                            player.level(),
                                            player.damageSources().genericKill(),
                                            Float.MAX_VALUE)
                                    && player.isDeadOrDying(),
                            "Native player death did not occur");
                    player.connection.handleClientCommand(
                            new ServerboundClientCommandPacket(
                                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                    player = server.getPlayerList().getPlayer(profile.id());
                    require(
                            player != null
                                    && player != oldPlayer
                                    && player.getUUID().equals(oldPlayer.getUUID()),
                            "Native respawn did not replace player instance exactly once");
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    step = 7;
                    phaseTick = -1;
                }
            } else if (step == 6 || step == 13) {
                if (step == 13 && connection == null) {
                    finish();
                    return;
                }
                if (server.getPlayerList().getPlayer(profile.id()) != null
                        || server.getConnection().getConnections().contains(connection)) return;
                channel.finishAndReleaseAll();
                channel = null;
                connection = null;
                player = null;
                disconnecting = false;
                if (step == 6) {
                    var saved =
                            server.getPlayerList()
                                    .loadPlayerData(new NameAndId(profile))
                                    .orElseThrow();
                    var input =
                            TagValueInput.create(
                                    net.minecraft.util.ProblemReporter.DISCARDING,
                                    server.registryAccess(),
                                    saved);
                    firstPosition =
                            input.read(ServerPlayer.SavedPosition.MAP_CODEC)
                                    .orElseThrow()
                                    .position()
                                    .orElseThrow();
                    result.put(
                            "savedFirstPosition",
                            List.of(firstPosition.x, firstPosition.y, firstPosition.z));
                    step = 3;
                } else finish();
            } else if (step >= 7 && step <= 11) {
                String label =
                        List.of("respawn", "nether", "nether_return", "end", "end_return")
                                .get(step - 7);
                if (!ready(label)) return;
                if (step == 11) {
                    step = 12;
                    return;
                }
                var dimension =
                        switch (step) {
                            case 7 -> Level.NETHER;
                            case 9 -> Level.END;
                            default -> Level.OVERWORLD;
                        };
                var destination = Objects.requireNonNull(server.getLevel(dimension));
                var before = player.level();
                Vec3 pos = dimension == Level.OVERWORLD ? firstPosition : new Vec3(8.5, 100, 8.5);
                var returned =
                        player.teleport(
                                new TeleportTransition(
                                        destination,
                                        pos,
                                        Vec3.ZERO,
                                        0,
                                        0,
                                        TeleportTransition.PLACE_PORTAL_TICKET));
                require(
                        returned == player
                                && player.level() == destination
                                && before.getEntity(player.getUUID()) == null,
                        "Native dimension transfer left the old physical player registered");
                step++;
                phaseTick = -1;
            } else if (step == 12) {
                if (MosaicPhysicalMaterializer.metrics().inFlightCount() != 0
                        || GenerationContextRegistry.bindingCount() != 0
                        || MosaicSpawnContextRegistry.bindingCount() != 0) return;
                var owner = player.chunkPosition();
                marker =
                        new BlockPos(
                                owner.getMiddleBlockX(),
                                player.level().getMinY() + 8,
                                owner.getMiddleBlockZ());
                if (mode.equals("reload"))
                    require(
                            player.level().getBlockState(marker).is(Blocks.GOLD_BLOCK),
                            "Reload lost test modification");
                else
                    player.level().setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
                result.put(
                        "markerPresent",
                        player.level().getBlockState(marker).is(Blocks.GOLD_BLOCK));
                result.put("markerWritten", !mode.equals("reload"));
                disconnect();
                step = 13;
            }
        }

        void connect() {
            // EmbeddedChannel is in-memory but does not inherit LocalChannel. Declare that
            // transport fact so NeoForge follows the integrated-server config-sync branch.
            // World, player, tickets and packet handlers remain native; this is not handshake
            // proof.
            connection =
                    new Connection(PacketFlow.SERVERBOUND) {
                        @Override
                        public boolean isMemoryConnection() {
                            return true;
                        }
                    };
            channel =
                    new EmbeddedChannel(
                            new ChannelInitializer<Channel>() {
                                @Override
                                protected void initChannel(Channel ch) {
                                    Connection.configureInMemoryPipeline(
                                            ch.pipeline(), PacketFlow.SERVERBOUND);
                                    // This component starts after configuration, not at handshake.
                                    ch.pipeline()
                                            .replace(
                                                    "decoder",
                                                    "inbound_config",
                                                    new UnconfiguredPipelineHandler.Inbound());
                                    ch.pipeline()
                                            .addLast(
                                                    "probe_packet_observer",
                                                    new ChannelOutboundHandlerAdapter() {
                                                        @Override
                                                        public void write(
                                                                ChannelHandlerContext context,
                                                                Object message,
                                                                ChannelPromise promise) {
                                                            if (message instanceof Packet<?> packet)
                                                                observedPackets.add(packet);
                                                            context.write(message, promise);
                                                        }
                                                    });
                                    // Keep the native connection handler chain.
                                    connection.configurePacketHandler(ch.pipeline());
                                }
                            });
            // This probe begins after configuration; use NeoForge's own test-connection setup.
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(
                    connection);
            connection.setupOutboundProtocol(
                    GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                            RegistryFriendlyByteBuf.decorator(server.registryAccess())));
            server.getConnection().getConnections().add(connection);
        }

        void drain() {
            if (channel == null) return;
            channel.runPendingTasks();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                try {
                    require(
                            HiddenByteBuf.unpack(message) instanceof io.netty.buffer.ByteBuf,
                            "Unknown encoded probe transport frame " + message);
                    encodedFrames++;
                } finally {
                    ReferenceCountUtil.release(message);
                }
            }
            Packet<?> sent;
            while ((sent = observedPackets.poll()) != null) packet(sent);
            channel.checkException();
        }

        void packet(Object message) {
            if (message instanceof BundlePacket<?> bundle) {
                bundle.subPackets().forEach(this::packet);
                return;
            }
            if (!(message instanceof Packet<?> packet))
                throw new IllegalStateException("Unknown probe transport frame " + message);
            outbound.merge(packet.type().toString(), 1, Integer::sum);
            if (player == null) return;
            if (packet instanceof ClientboundPlayerPositionPacket pos) {
                require(
                        pos.relatives().isEmpty(),
                        "Relative teleport outside this probe transport's audited scope");
                player.connection.handleAcceptTeleportPacket(
                        new ServerboundAcceptTeleportationPacket(pos.id()));
            }
            if (packet instanceof ClientboundKeepAlivePacket alive)
                player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(alive.getId()));
            if (packet instanceof ClientboundChunkBatchFinishedPacket)
                player.connection.handleChunkBatchReceived(
                        new ServerboundChunkBatchReceivedPacket(16));
            if (packet instanceof ClientboundLoginPacket
                    || packet instanceof ClientboundRespawnPacket)
                player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            if (packet instanceof ClientboundDisconnectPacket && !disconnecting)
                throw new IllegalStateException(
                        "Native server disconnected the probe player: " + packet);
        }

        boolean ready(String label) throws Exception {
            var level = player.level();
            var pos = player.chunkPosition();
            require(
                    server.getPlayerList().getPlayer(profile.id()) == player
                            && level.getEntity(player.getUUID()) == player,
                    "Physical player identity/manager mismatch");
            long count =
                    server.getPlayerList().getPlayers().stream()
                            .filter(p -> p.getUUID().equals(profile.id()))
                            .count();
            require(count == 1, "Duplicate native player registration");
            var holder = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(pos.pack());
            if (holder == null || holder.getFullStatus() != FullChunkStatus.ENTITY_TICKING)
                return false;
            var result = holder.getEntityTickingChunkFuture().getNow(null);
            if (result == null || result.orElse(null) == null) return false;
            var chunk =
                    result.orElseThrow(
                            () -> new IllegalStateException("No physical ticking player chunk"));
            require(
                    chunk == level.getChunkSource().getChunkNow(pos.x(), pos.z())
                            && chunk.isLightCorrect()
                            && chunk.getPersistedStatus() == ChunkStatus.FULL,
                    "Native player did not use lit physical FULL chunk");
            if (phaseTick < 0) phaseTick = server.getTickCount();
            if (server.getTickCount() - phaseTick < 20) return false;
            String key = level.dimension().identifier() + "/" + pos.x() + "/" + pos.z();
            var structures = SerializableChunkData.copyOf(level, chunk).structureData().copy();
            var previous = structureInputs.putIfAbsent(key, structures);
            require(
                    previous == null || previous.equals(structures),
                    "Player lifecycle changed canonical structure metadata");
            if (label.equals("first_join") && !mode.equals("reload")) {
                // Observe only physically present native spawn-frontier owners; do not load more.
                for (int z = pos.z() - 3; z <= pos.z() + 3; z++)
                    for (int x = pos.x() - 3; x <= pos.x() + 3; x++) {
                        var present = level.getChunkSource().getChunkNow(x, z);
                        if (present != null)
                            structureInputs.putIfAbsent(
                                    level.dimension().identifier() + "/" + x + "/" + z,
                                    SerializableChunkData.copyOf(level, present)
                                            .structureData()
                                            .copy());
                    }
            }
            var record = new LinkedHashMap<String, Object>();
            record.put("phase", label);
            record.put("dimension", level.dimension().identifier().toString());
            record.put("chunkX", pos.x());
            record.put("chunkZ", pos.z());
            record.put("physicalStatus", holder.getFullStatus().toString());
            record.put("lightCorrect", true);
            record.put("naturalTicks", server.getTickCount() - phaseTick);
            record.put("playerInstance", System.identityHashCode(player));
            long captures = vanilla ? 0 : MosaicPhysicalMaterializer.artifactCaptures(level, pos);
            record.put("artifactCaptures", captures);
            var runtime = MosaicWorldIdentity.runtimeContext(level);
            record.put(
                    "localSeed",
                    Long.toString(
                            runtime.isPresent()
                                    ? runtime.orElseThrow()
                                            .resolveLocalWorldSeed(level.dimension(), pos)
                                    : server.getWorldGenSettings().options().seed()));
            if (mode.equals("reload"))
                require(captures == 0, "Reload regenerated a visited physical FULL chunk");
            phases.add(record);
            this.result.put("status", "RUNTIME_PENDING");
            write();
            return true;
        }

        void disconnect() {
            disconnecting = true;
            connection.disconnect(Component.literal("Native lifecycle probe phase complete"));
        }

        void finish() throws Exception {
            if (MosaicPhysicalMaterializer.metrics().inFlightCount() != 0
                    || GenerationContextRegistry.bindingCount() != 0
                    || MosaicSpawnContextRegistry.bindingCount() != 0) return;
            require(joins == 2 && phases.size() == 7, "Incomplete native player lifecycle phases");
            require(
                    tickets.entrySet().stream()
                            .anyMatch(
                                    e ->
                                            e.getKey()
                                                            .toLowerCase(Locale.ROOT)
                                                            .contains("player_spawn")
                                                    && e.getValue() > 0),
                    "Native PLAYER_SPAWN ticket missing");
            if (!mode.equals("reload"))
                require(
                        findSpawnCalls > 0
                                && postProcessCalls > 0
                                && tickets.entrySet().stream()
                                        .anyMatch(
                                                e ->
                                                        e.getKey()
                                                                        .toLowerCase(Locale.ROOT)
                                                                        .contains("spawn_search")
                                                                && e.getValue() > 0),
                        "Native cold spawn/post-processing chain missing");
            if (vanilla || mode.equals("reload"))
                require(
                        BDevelopmentWorld.metrics(server).get("generations") == 0
                                && MosaicPhysicalMaterializer.metrics().artifactCaptureCount() == 0,
                        "Vanilla/reload regenerated Mosaic data");
            var raw = new CompoundTag();
            structureInputs.forEach(raw::put);
            var structuresPath = output.resolveSibling("player-structures-" + mode + ".nbt");
            NbtIo.writeCompressed(raw, structuresPath);
            if (mode.equals("reload")) {
                var expected =
                        NbtIo.readCompressed(
                                output.resolveSibling("player-structures-full.nbt"),
                                NbtAccounter.unlimitedHeap());
                for (var key : raw.keySet())
                    require(
                            Objects.equals(raw.get(key), expected.get(key)),
                            "Reload changed visited-owner structure metadata: " + key);
            }
            result.put("status", "PASS");
            result.put("joins", joins);
            result.put("findSpawnCalls", findSpawnCalls);
            result.put("postProcessCalls", postProcessCalls);
            result.put("tickets", tickets);
            result.put("outboundPackets", outbound);
            result.put("encodedFrames", encodedFrames);
            result.put(
                    "artifactGenerations",
                    MosaicPhysicalMaterializer.metrics().artifactCaptureCount());
            result.put("developmentB", BDevelopmentWorld.metrics(server));
            result.put("inFlight", 0);
            result.put("bindings", 0);
            result.put("spawnBindings", 0);
            result.put(
                    "noPlayerOrChannelRetained",
                    player == null && channel == null && connection == null && prepare == null);
            finished = true;
            write();
            server.halt(false);
        }

        void write() {
            PlayerLifecycleEvidence.write(output, result);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void write(Path path, Object result) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot save player lifecycle evidence", failure);
        }
    }
}
