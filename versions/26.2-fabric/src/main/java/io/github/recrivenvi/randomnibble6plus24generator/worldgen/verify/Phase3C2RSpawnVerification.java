package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.GenerationChunkHolderAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;

/**
 * Test-only real-player spawn handoff assertion.  The observer is armed only by
 * an explicit JVM property and never participates in production chunk requests.
 */
public final class Phase3C2RSpawnVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3c2r.spawn.";
    private static boolean started;

    private Phase3C2RSpawnVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        if (started || System.getProperty(PREFIX + "verify", "").isBlank()) return;
        if (server.getPlayerList().getPlayers().isEmpty()) return;

        started = true;
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        if (!(player.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Spawn verification player has no ServerLevel");
        }
        if (MosaicWorldIdentity.runtimeContext(level).isEmpty()) {
            throw new IllegalStateException("Spawn verification requires a Mosaic world");
        }

        ChunkPos pos = player.chunkPosition();
        ChunkHolder holder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(pos.pack());
        if (holder == null) {
            throw new IllegalStateException("Joined player has no ChunkHolder at " + pos);
        }
        ChunkAccess latest = holder.getLatestChunk();
        FullChunkStatus fullStatus = holder.getFullStatus();
        if (!(latest instanceof LevelChunk)
                || holder.getPersistedStatus() != ChunkStatus.FULL
                || fullStatus != FullChunkStatus.ENTITY_TICKING) {
            throw new IllegalStateException("Spawn handoff did not reach a ticking LevelChunk: pos="
                    + pos + " status=" + holder.getPersistedStatus()
                    + " fullStatus=" + fullStatus + " latest="
                    + (latest == null ? null : latest.getClass().getName()));
        }

        String eventText = String.join("\n", Phase3C2RSpawnTrace.events())
                .toLowerCase(Locale.ROOT);
        requireTrace(eventText, "player_spawn", "PLAYER_SPAWN ticket");
        requireTrace(eventText, "spawn_search", "SPAWN_SEARCH ticket");
        requireTrace(eventText, "postprocess.begin", "postProcessGeneration");
        requireTrace(eventText, "status=entity_ticking", "ENTITY_TICKING promotion");

        if (GenerationContextRegistry.bindingCount() != 0
                || MosaicSpawnContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Spawn handoff leaked an isolated generation context");
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("dimension", level.dimension().identifier().toString());
        result.addProperty("player", player.getGameProfile().name());
        result.addProperty("chunkPos", pos.toString());
        result.addProperty("ticketLevel", holder.getTicketLevel());
        result.addProperty("generationStatus", ChunkLevel.generationStatus(holder.getTicketLevel()).getName());
        result.addProperty("highestAllowedStatus", holder instanceof GenerationChunkHolderAccessor accessor
                && accessor.randomnibble6plus24generator$getHighestAllowedStatus() != null
                ? accessor.randomnibble6plus24generator$getHighestAllowedStatus().getName()
                : null);
        result.addProperty("chunkStatus", holder.getPersistedStatus().getName());
        result.addProperty("fullChunkStatus", fullStatus.name());
        result.addProperty("materialized", true);
        result.addProperty("unloadedChunkTrace", false);
        result.addProperty("artifactCaptures", MosaicPhysicalMaterializer.artifactCaptures(level, pos));
        result.addProperty("generationContextBindings", GenerationContextRegistry.bindingCount());
        result.addProperty("spawnContextBindings", MosaicSpawnContextRegistry.bindingCount());
        write(result);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3C2R real-player spawn handoff PASS player={} pos={} fullStatus={}",
                player.getGameProfile().name(), pos, fullStatus);
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) {
            server.halt(false);
        }
    }

    private static void requireTrace(String text, String needle, String label) {
        if (!text.contains(needle)) {
            throw new IllegalStateException("Spawn trace is missing " + label + ": " + needle);
        }
    }

    private static void write(JsonObject result) {
        String output = System.getProperty(PREFIX + "output", "");
        if (output.isBlank()) return;
        Path path = Path.of(output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, result.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3C2R spawn result", exception);
        }
    }
}
