package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapInvoker;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureIndexStore;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayStore;

/** Test-only V1 /locate probe. It searches only the persisted Mosaic index. */
public final class Phase3C3BLocateVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3c3b.locate.";
    private static volatile boolean started;

    private Phase3C3BLocateVerification() {
    }

    public static boolean skipInitialSpawnIfRequested() {
        return !System.getProperty(PREFIX + "mode", "").isBlank();
    }

    public static boolean skipPrepareLevelsIfRequested() {
        return skipInitialSpawnIfRequested();
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "mode", "");
        if (mode.isBlank() || started) return;
        started = true;
        if (MosaicWorldIdentity.runtimeContext(server).isEmpty()) {
            throw new IllegalStateException("Phase 3C3B locate probe requires a Mosaic world");
        }
        MosaicPhysicalMaterializer.Metrics startupBaseline = MosaicPhysicalMaterializer.metrics();
        List<Fixture> fixtures = loadFixtures(server, fixtureSpec());
        MosaicPhysicalMaterializer.Metrics queryBaseline = MosaicPhysicalMaterializer.metrics();
        if (mode.equals("reload")
                && queryBaseline.artifactCaptureCount() != startupBaseline.artifactCaptureCount()) {
            throw new IllegalStateException("Mosaic /locate reload regenerated canonical data while loading fixtures");
        }
        int loadedBefore = loadedChunks(server);
        int indexBefore = fixtures.stream().mapToInt(fixture ->
                MosaicStructureIndexStore.indexedEntryCount(fixture.level())).sum();
        JsonArray locations = new JsonArray();
        for (Fixture fixture : fixtures) {
            locations.add(locate(fixture));
        }
        JsonObject tagQuery = locateVillageTag(fixtures);
        JsonObject commandPath = commandPathProof(server, fixtures);
        int loadedAfter = loadedChunks(server);
        MosaicPhysicalMaterializer.Metrics after = MosaicPhysicalMaterializer.metrics();
        long artifactDelta = after.artifactCaptureCount() - queryBaseline.artifactCaptureCount();
        if (artifactDelta != 0L) {
            throw new IllegalStateException("Mosaic /locate generated a canonical Artifact: " + artifactDelta);
        }
        if (loadedAfter != loadedBefore) {
            throw new IllegalStateException("Mosaic /locate changed loaded Chunk count: "
                    + loadedBefore + " -> " + loadedAfter);
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("mode", mode);
        result.addProperty("worldSeed", server.getWorldGenSettings().options().seed());
        result.addProperty("fixtureSpec", fixtureSpec());
        result.addProperty("fixtureCount", fixtures.size());
        result.addProperty("indexedEntriesBefore", indexBefore);
        result.addProperty("indexedEntriesAfter", fixtures.stream().mapToInt(fixture ->
                MosaicStructureIndexStore.indexedEntryCount(fixture.level())).sum());
        result.addProperty("loadedChunksBefore", loadedBefore);
        result.addProperty("loadedChunksAfter", loadedAfter);
        result.addProperty("artifactGenerations", artifactDelta);
        result.add("locations", locations);
        result.add("tagQuery", tagQuery);
        result.add("commandPath", commandPath);
        write(result);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3C3B Mosaic /locate PASS fixtures={} mode={}", fixtures.size(), mode);
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) {
            releaseProbeHolders(server);
            server.halt(false);
        }
    }

    private static JsonObject locate(Fixture fixture) {
        Registry<Structure> registry = fixture.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.wrapAsHolder(fixture.structure());
        HolderSet<Structure> requested = HolderSet.direct(holder);
        int before = loadedChunks(fixture.level().getServer());
        var located = fixture.level().getChunkSource().getGenerator().findNearestMapStructure(
                fixture.level(), requested, fixture.point(), 100, false);
        if (located == null || located.getSecond().value() != fixture.structure()) {
            throw new IllegalStateException("Mosaic /locate did not find indexed " + fixture.structureId()
                    + " at " + fixture.owner());
        }
        ChunkPos locatedOwner = ChunkPos.containing(located.getFirst());
        if (!locatedOwner.equals(fixture.owner())) {
            throw new IllegalStateException("Mosaic /locate returned a different physical owner: expected "
                    + fixture.owner() + ", found " + locatedOwner);
        }
        StructureManager manager = fixture.level().structureManager();
        StructureStart projection = manager.getStructureWithPieceAt(located.getFirst(), fixture.structure());
        if (!projection.isValid() || projection.getStructure() != fixture.structure()) {
            throw new IllegalStateException("Mosaic /locate anchor is not inside a physical projection: "
                    + located.getFirst());
        }
        if (loadedChunks(fixture.level().getServer()) != before) {
            throw new IllegalStateException("Mosaic /locate loaded a Chunk for " + fixture.structureId());
        }
        JsonObject result = new JsonObject();
        result.addProperty("structure", fixture.structureId());
        result.addProperty("owner", fixture.owner().toString());
        result.addProperty("anchor", located.getFirst().toString());
        result.addProperty("startChunk", projection.getChunkPos().toString());
        result.addProperty("pieces", projection.getPieces().size());
        result.addProperty("holderSetQuery", true);
        return result;
    }

    private static JsonObject locateVillageTag(List<Fixture> fixtures) {
        Fixture village = fixtures.stream()
                .filter(fixture -> fixture.structureId().startsWith("minecraft:village_"))
                .findFirst().orElse(null);
        JsonObject result = new JsonObject();
        if (village == null) {
            result.addProperty("tested", false);
            return result;
        }
        Registry<Structure> registry = village.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        TagKey<Structure> tag = TagKey.create(
                Registries.STRUCTURE, Identifier.withDefaultNamespace("village"));
        HolderSet<Structure> requested = registry.get(tag).orElseThrow(
                () -> new IllegalStateException("Missing vanilla #minecraft:village structure tag"));
        var located = village.level().getChunkSource().getGenerator().findNearestMapStructure(
                village.level(), requested, village.point(), 100, false);
        if (located == null || !requested.contains(located.getSecond())) {
            throw new IllegalStateException("Mosaic /locate tag query did not return a village projection");
        }
        result.addProperty("tested", true);
        result.addProperty("tag", "#minecraft:village");
        result.addProperty("structure", String.valueOf(registry.getKey(located.getSecond().value())));
        result.addProperty("anchor", located.getFirst().toString());
        return result;
    }

    private static JsonObject commandPathProof(MinecraftServer server, List<Fixture> fixtures) {
        Fixture village = fixtures.stream()
                .filter(fixture -> fixture.structureId().startsWith("minecraft:village_"))
                .findFirst().orElseThrow();
        RecordingSource recording = new RecordingSource();
        CommandSourceStack source = server.createCommandSourceStack()
                .withSource(recording)
                .withLevel(village.level())
                .withPosition(Vec3.atCenterOf(village.point()))
                .withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(
                source, "locate structure " + village.structureId());
        if (recording.messages.isEmpty()) {
            throw new IllegalStateException("Vanilla /locate command path emitted no feedback");
        }
        boolean success = recording.messages.stream()
                .anyMatch(message -> message.getString().contains(village.structureId()));
        if (!success) {
            throw new IllegalStateException("Vanilla /locate command path did not report the indexed structure: "
                    + recording.messages);
        }
        recording.messages.clear();
        server.getCommands().performPrefixedCommand(source, "locate structure minecraft:stronghold");
        boolean scopedMiss = recording.messages.stream()
                .anyMatch(message -> message.getString().contains("generated Mosaic chunks")
                        || message.getString().contains(
                                "commands.randomnibble6plus24generator.locate.generated_area_not_found"));
        if (!scopedMiss) {
            throw new IllegalStateException("Mosaic /locate miss did not use the generated-area diagnostic: "
                    + recording.messages);
        }
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("messages", recording.messages.size());
        result.addProperty("vanillaStructurePath", true);
        result.addProperty("generatedAreaMiss", true);
        return result;
    }

    private static List<Fixture> loadFixtures(MinecraftServer server, String encoded) {
        if (encoded.isBlank()) throw new IllegalStateException("Phase 3C3B fixtureSpec is empty");
        Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Fixture> result = new ArrayList<>();
        for (String item : encoded.split(";")) {
            String[] parts = item.split("\\|", -1);
            if (parts.length != 4) throw new IllegalStateException("Malformed Phase 3C3B fixture: " + item);
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(parts[1]));
            ServerLevel level = server.getLevel(dimension);
            if (level == null) throw new IllegalStateException("Missing Phase 3C3B dimension " + parts[1]);
            Structure structure = registry.get(Identifier.parse(parts[0])).orElseThrow(
                    () -> new IllegalStateException("Missing structure " + parts[0])).value();
            ChunkPos owner = new ChunkPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            LevelChunk chunk = requestFull(server, level, owner);
            StructureStart start = MosaicStructureOverlayStore.startsForOwner(level, owner).stream()
                    .filter(candidate -> candidate.getStructure() == structure)
                    .findFirst().orElse(null);
            if (start == null || !start.isValid()) {
                throw new IllegalStateException("No indexed structure projection at " + item);
            }
            net.minecraft.core.BlockPos point = piecePoint(start, owner, level);
            if (point == null) throw new IllegalStateException("Structure has no physical piece at " + item);
            result.add(new Fixture(level, owner, structure, parts[0], point, chunk));
        }
        return List.copyOf(result);
    }

    private static LevelChunk requestFull(MinecraftServer server, ServerLevel level, ChunkPos pos) {
        var future = level.getChunkSource().getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true);
        server.managedBlock(future::isDone);
        ChunkResult<ChunkAccess> result = future.join();
        ChunkAccess chunk = result.orElseThrow(() ->
                new IllegalStateException("Failed to load Phase 3C3B fixture " + pos + ": " + result.getError()));
        if (!(chunk instanceof LevelChunk levelChunk)) {
            throw new IllegalStateException("Phase 3C3B fixture did not reach FULL: " + pos);
        }
        return levelChunk;
    }

    private static net.minecraft.core.BlockPos piecePoint(
            StructureStart start, ChunkPos owner, ServerLevel level) {
        for (var piece : start.getPieces()) {
            var box = piece.getBoundingBox();
            int minX = Math.max(box.minX(), owner.getMinBlockX());
            int maxX = Math.min(box.maxX(), owner.getMaxBlockX());
            int minZ = Math.max(box.minZ(), owner.getMinBlockZ());
            int maxZ = Math.min(box.maxZ(), owner.getMaxBlockZ());
            int minY = Math.max(box.minY(), level.getMinY());
            int maxY = Math.min(box.maxY(), level.getMaxY() - 1);
            if (minX <= maxX && minZ <= maxZ && minY <= maxY) {
                return new net.minecraft.core.BlockPos(
                        (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
            }
        }
        return null;
    }

    private static int loadedChunks(MinecraftServer server) {
        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            total += level.getChunkSource().getLoadedChunksCount();
        }
        return total;
    }

    private static void releaseProbeHolders(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ChunkMapInvoker invoker = (ChunkMapInvoker) level.getChunkSource().chunkMap;
            List<ChunkHolder> holders = invoker
                    .randomnibble6plus24generator$invokeAllChunksWithAtLeastStatus(ChunkStatus.EMPTY)
                    .toList();
            for (ChunkHolder holder : holders) {
                invoker.randomnibble6plus24generator$invokeUpdateChunkScheduling(
                        holder.getPos().pack(), ChunkLevel.MAX_LEVEL + 1,
                        holder, holder.getTicketLevel());
            }
            invoker.randomnibble6plus24generator$invokePromoteChunkMap();
        }
    }

    private static String fixtureSpec() {
        return System.getProperty(PREFIX + "fixtureSpec",
                "minecraft:village_desert|minecraft:overworld|-10|11;"
                        + "minecraft:mineshaft|minecraft:overworld|-4|-2;"
                        + "minecraft:trial_chambers|minecraft:overworld|-13|-14;"
                        + "minecraft:monument|minecraft:overworld|52|-247;"
                        + "minecraft:fortress|minecraft:the_nether|-11|-6;"
                        + "minecraft:end_city|minecraft:the_end|1004|1006");
    }

    private static void write(JsonObject result) {
        String output = System.getProperty(PREFIX + "output", "");
        if (output.isBlank()) return;
        Path path = Path.of(output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, result.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3C3B locate result", exception);
        }
    }

    private record Fixture(
            ServerLevel level,
            ChunkPos owner,
            Structure structure,
            String structureId,
            net.minecraft.core.BlockPos point,
            LevelChunk chunk) {
    }

    private static final class RecordingSource implements CommandSource {
        private final List<net.minecraft.network.chat.Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(net.minecraft.network.chat.Component component) {
            messages.add(component);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
