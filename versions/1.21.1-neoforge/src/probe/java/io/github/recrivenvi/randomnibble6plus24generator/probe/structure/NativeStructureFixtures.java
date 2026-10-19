package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Discovers actual default-registry positives, never substitutes handcrafted structure pieces. */
public final class NativeStructureFixtures {
    private NativeStructureFixtures() {}

    public static void export(MinecraftServer server, Path root) {
        JsonArray fixtures = new JsonArray();
        var registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String key :
                List.of(
                        "village_plains",
                        "mineshaft",
                        "trial_chambers",
                        "monument",
                        "fortress",
                        "end_city",
                        "stronghold")) {
            ServerLevel level =
                    server.getLevel(
                            key.equals("fortress")
                                    ? Level.NETHER
                                    : key.equals("end_city") ? Level.END : Level.OVERWORLD);
            var holder =
                    registry.getHolder(ResourceLocation.withDefaultNamespace(key)).orElseThrow();
            var located =
                    level.getChunkSource()
                            .getGenerator()
                            .findNearestMapStructure(
                                    level, HolderSet.direct(holder), BlockPos.ZERO, 100, false);
            if (located == null)
                throw new IllegalStateException("No native positive within search bound: " + key);
            ChunkPos startPos =
                    new ChunkPos(
                            Math.floorDiv(located.getFirst().getX(), 16),
                            Math.floorDiv(located.getFirst().getZ(), 16));
            var chunk = level.getChunk(startPos.x, startPos.z, ChunkStatus.STRUCTURE_STARTS, true);
            var start = chunk.getStartForStructure(holder.value());
            if (start == null || !start.isValid())
                throw new IllegalStateException(
                        "Locate did not yield a valid native start: " + key + " " + startPos);
            fixtures.add(
                    fixture(key + "-start", level, startPos, key, startPos, level.getSeed(), 0));
            ChunkPos neighbor = null;
            // Prefer a genuinely more distant projection (not the old FEATURES writer ring).
            for (int distance = 8; distance >= 1 && neighbor == null; distance--) {
                for (int dx = -distance; dx <= distance && neighbor == null; dx++) {
                    for (int dz = -distance; dz <= distance && neighbor == null; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                        ChunkPos candidate = new ChunkPos(startPos.x + dx, startPos.z + dz);
                        if (start.getBoundingBox()
                                        .intersects(
                                                candidate.getMinBlockX(),
                                                candidate.getMinBlockZ(),
                                                candidate.getMaxBlockX(),
                                                candidate.getMaxBlockZ())
                                && start.getPieces().stream()
                                        .anyMatch(
                                                piece ->
                                                        piece.getBoundingBox()
                                                                .intersects(
                                                                        candidate.getMinBlockX(),
                                                                        candidate.getMinBlockZ(),
                                                                        candidate.getMaxBlockX(),
                                                                        candidate.getMaxBlockZ())))
                            neighbor = candidate;
                    }
                }
            }
            if (neighbor == null)
                throw new IllegalStateException("No cross-chunk positive: " + key);
            fixtures.add(
                    fixture(key + "-neighbor", level, neighbor, key, startPos, level.getSeed(), 0));
            if (key.equals("village_plains") || key.equals("trial_chambers")) {
                ChunkPos halo = findTerrainOnlyTarget(level, startPos, start);
                if (halo == null)
                    throw new IllegalStateException("Missing terrain-only positive " + key);
                JsonObject input =
                        fixture(key + "-halo", level, halo, key, startPos, level.getSeed(), 0);
                input.addProperty("terrainOnly", true);
                fixtures.add(input);
            }
        }
        ServerLevel overworld = server.overworld();
        long master = 123456789L;
        var resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        for (ChunkPos position : List.of(ChunkPos.ZERO, new ChunkPos(125, -37))) {
            fixtures.add(
                    fixture(
                            position.equals(ChunkPos.ZERO) ? "mosaic-origin" : "mosaic-blocker",
                            overworld,
                            position,
                            "",
                            position,
                            resolver.resolveLocalWorldSeed(master, Level.OVERWORLD, position),
                            master));
        }
        NativeStructureVerifier.write(root.resolve("fixtures.json"), fixtures);
    }

    private static ChunkPos findTerrainOnlyTarget(
            ServerLevel level,
            ChunkPos startPos,
            net.minecraft.world.level.levelgen.structure.StructureStart start) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                ChunkPos target = new ChunkPos(startPos.x + dx, startPos.z + dz);
                if (!start.getBoundingBox()
                        .intersects(
                                target.getMinBlockX(),
                                target.getMinBlockZ(),
                                target.getMaxBlockX(),
                                target.getMaxBlockZ())) continue;
                if (start.getPieces().stream()
                        .anyMatch(
                                piece ->
                                        piece.getBoundingBox()
                                                .intersects(
                                                        target.getMinBlockX(),
                                                        target.getMinBlockZ(),
                                                        target.getMaxBlockX(),
                                                        target.getMaxBlockZ()))) continue;
                level.getChunk(target.x, target.z, ChunkStatus.STRUCTURE_REFERENCES, true);
                var beard =
                        net.minecraft.world.level.levelgen.Beardifier.forStructuresInChunk(
                                level.structureManager(), target);
                for (int x = target.getMinBlockX(); x <= target.getMaxBlockX(); x++) {
                    for (int z = target.getMinBlockZ(); z <= target.getMaxBlockZ(); z++) {
                        for (int y = level.getMinBuildHeight();
                                y < level.getMaxBuildHeight();
                                y++) {
                            if (beard.compute(
                                            new net.minecraft.world.level.levelgen.DensityFunction
                                                    .SinglePointContext(x, y, z))
                                    != 0) return target;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static JsonObject fixture(
            String id,
            ServerLevel level,
            ChunkPos position,
            String structure,
            ChunkPos start,
            long local,
            long master) {
        JsonObject fixture = new JsonObject();
        fixture.addProperty("id", id);
        fixture.addProperty("master", Long.toString(master));
        fixture.addProperty("local", Long.toString(local));
        fixture.addProperty("dimension", level.dimension().location().toString());
        fixture.addProperty("x", position.x);
        fixture.addProperty("z", position.z);
        fixture.addProperty("structure", structure);
        fixture.addProperty("startX", start.x);
        fixture.addProperty("startZ", start.z);
        return fixture;
    }
}
