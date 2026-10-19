package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;

/** Passive fixture discovery from actual publications; does not request or supply any chunks. */
public final class PublicationInventory {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static final ConcurrentHashMap<Long, CompoundTag> rows = new ConcurrentHashMap<>();
    private static volatile RuntimeException failure;
    private static MinecraftServer server;

    private PublicationInventory() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PREFIX + "publicationInventory");
    }

    public static void install(MinecraftServer host) {
        if (!enabled()) return;
        if (NaturalRuntimeEvidence.enabled())
            throw new IllegalStateException("Independent publication observers required");
        server = host;
        MosaicPhysicalMaterializer.setVerificationObserver(PublicationInventory::capture);
    }

    private static void capture(MosaicPhysicalMaterializer.Publication publication) {
        if (publication.key().server() != server
                || !publication
                        .prepared()
                        .artifact()
                        .dimension()
                        .equals(System.getProperty(PREFIX + "dimension"))) return;
        try {
            var prepared = publication.prepared();
            var chunk = prepared.chunk();
            var row = new CompoundTag();
            row.putInt("x", chunk.getPos().x());
            row.putInt("z", chunk.getPos().z());
            row.putString("localSeed", Long.toString(prepared.artifact().localWorldSeed()));
            row.putString("artifact", prepared.artifact().rawFingerprint());
            var entities = new ListTag();
            chunk.getEntities().forEach(nbt -> entities.add(nbt.copy()));
            row.put("entities", entities);
            var blockEntities = new ListTag();
            chunk.getBlockEntitiesPos().stream()
                    .sorted(
                            Comparator.<net.minecraft.core.BlockPos>comparingInt(pos -> pos.getY())
                                    .thenComparingInt(net.minecraft.core.BlockPos::getZ)
                                    .thenComparingInt(net.minecraft.core.BlockPos::getX))
                    .forEach(
                            pos -> {
                                var nbt =
                                        chunk.getBlockEntityNbtForSaving(
                                                pos, server.registryAccess());
                                if (nbt == null)
                                    throw new IllegalStateException(
                                            "Published natural BE has no saving data");
                                var observation = new CompoundTag();
                                observation.put("nbt", nbt.copy());
                                observation.putString(
                                        "block",
                                        BuiltInRegistries.BLOCK
                                                .getKey(chunk.getBlockState(pos).getBlock())
                                                .toString());
                                observation.putBoolean(
                                        "pending", !chunk.getBlockEntities().containsKey(pos));
                                blockEntities.add(observation);
                            });
            row.put("block_entities", blockEntities);
            if (rows.putIfAbsent(chunk.getPos().pack(), row) != null)
                throw new IllegalStateException("Duplicate owner publication in discovery");
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        }
    }

    public static void checkFailure() {
        if (enabled() && failure != null) throw failure;
    }

    public static void close() {
        if (!enabled() || server == null) return;
        checkFailure();
        var root = new CompoundTag();
        var summary = new JsonObject();
        String hash = System.getProperty(PREFIX + "inventoryInputHash");
        String dimension = System.getProperty(PREFIX + "dimension");
        root.putString("inputHash", hash);
        root.putString("dimension", dimension);
        var data = new ListTag();
        var positives = new JsonArray();
        rows.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            var row = entry.getValue();
                            data.add(row);
                            if (!row.getListOrEmpty("block_entities").isEmpty()) {
                                var candidate = new JsonObject();
                                candidate.addProperty("x", row.getIntOr("x", 0));
                                candidate.addProperty("z", row.getIntOr("z", 0));
                                candidate.addProperty(
                                        "localSeed", row.getStringOr("localSeed", ""));
                                candidate.addProperty(
                                        "blockEntities",
                                        row.getListOrEmpty("block_entities").size());
                                var blocks = new JsonArray();
                                for (var nbt : row.getListOrEmpty("block_entities"))
                                    blocks.add(((CompoundTag) nbt).getStringOr("block", ""));
                                candidate.add("blocks", blocks);
                                positives.add(candidate);
                            }
                        });
        root.put("owners", data);
        summary.addProperty("schema", 1);
        summary.addProperty(
                "scope", "PASSIVE_PUBLISHED_INPUT_DISCOVERY_NOT_POSITIVE_ACTIVATION_PROOF");
        summary.addProperty("inputHash", hash);
        summary.addProperty("dimension", dimension);
        summary.addProperty("masterSeed", server.getWorldGenSettings().options().seed());
        summary.addProperty("pid", ProcessHandle.current().pid());
        summary.addProperty(
                "save",
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize()
                        .toString());
        summary.addProperty("publications", rows.size());
        summary.add("candidates", positives);
        MosaicPhysicalMaterializer.setVerificationObserver(null);
        rows.clear();
        server = null;
        failure = null;
        summary.addProperty("retainedOwners", rows.size());
        summary.addProperty("serverRetained", server != null);
        Path folder = Path.of(System.getProperty(PREFIX + "output")).toAbsolutePath().getParent();
        String mode = System.getProperty(PREFIX + "mode");
        try {
            NbtIo.writeCompressed(root, folder.resolve("publication-inventory-" + mode + ".nbt"));
            Files.writeString(
                    folder.resolve("publication-inventory-" + mode + ".json"), summary.toString());
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }
}
