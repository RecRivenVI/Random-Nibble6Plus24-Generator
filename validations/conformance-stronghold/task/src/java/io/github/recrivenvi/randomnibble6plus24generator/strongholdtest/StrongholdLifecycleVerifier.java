package io.github.recrivenvi.randomnibble6plus24generator.strongholdtest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;

/** Production FULL request / save / independent JVM reload; never drives worldgen stages by hand. */
public final class StrongholdLifecycleVerifier {
    private static boolean started;
    private StrongholdLifecycleVerifier() {}

    public static void run(MinecraftServer server) {
        if (started) return;
        started = true;
        JsonObject result = new JsonObject();
        try {
            Path output = Path.of(System.getProperty("mosaic.stronghold.test.output"));
            boolean reload = "reload".equals(System.getProperty("mosaic.stronghold.test.lifecycle"));
            JsonObject previous = reload ? JsonParser.parseString(Files.readString(
                    Path.of(System.getProperty("mosaic.stronghold.test.reference")))).getAsJsonObject() : null;
            ServerLevel level = server.overworld();
            if (!MosaicWorldIdentity.isMosaic(level)) throw new AssertionError("Lifecycle fixture is not Mosaic");
            ChunkPos target = reload
                    ? new ChunkPos(previous.get("chunkX").getAsInt(), previous.get("chunkZ").getAsInt())
                    : findTarget(level);
            long artifactsBefore = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
            int spawnsBefore = PhysicalMosaicTrace.snapshot().spawnSeeds().size();
            var future = level.getChunkSource().getChunkFuture(target.x(), target.z(), ChunkStatus.FULL, true);
            server.managedBlock(future::isDone);
            ChunkAccess loaded = future.join().orElseThrow(() -> new AssertionError("Missing production FULL result"));
            if (!(loaded instanceof LevelChunk chunk) || !chunk.isLightCorrect()) throw new AssertionError("Not lighted physical FULL");
            var stronghold = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.STRONGHOLD);
            List<StructureStart> starts = MosaicStructureOverlayStore.startsForOwner(level, target).stream()
                    .filter(start -> start.getStructure() == stronghold.value()).toList();
            if (starts.isEmpty()) throw new AssertionError("Real Stronghold projection missing after materialization");
            List<String> nbt = starts.stream().map(start -> start.createTag(StructurePieceSerializationContext.fromLevel(level), start.getChunkPos()).toString()).sorted().toList();
            JsonArray all = new JsonArray(); nbt.forEach(all::add); result.add("strongholdStarts", all);
            JsonObject references = new JsonObject();
            chunk.getAllReferences().forEach((structure, refs) -> references.add(
                    level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure).toString(),
                    new Gson().toJsonTree(Arrays.stream(refs.toLongArray()).sorted().toArray())));
            result.add("references", references);
            BlockPos marker = new BlockPos(target.getMinBlockX(), level.getMinY() + 3, target.getMinBlockZ());
            if (reload) {
                if (!chunk.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)) throw new AssertionError("Saved player edit overwritten");
                if (!previous.get("strongholdStarts").equals(all) || !previous.get("references").equals(references)) {
                    throw new AssertionError("Stronghold metadata changed after restart");
                }
            } else {
                chunk.setBlockState(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 0);
                chunk.markUnsaved();
            }
            int loadedBeforeLocate = level.getChunkSource().getLoadedChunksCount();
            var locate = MosaicStructureIndexStore.findNearest(level, HolderSet.direct(stronghold), marker, 0).orElseThrow(
                    () -> new AssertionError("Generated-region Stronghold locate missing"));
            BlockPos anchor = locate.getFirst();
            if (starts.stream().flatMap(start -> start.getPieces().stream()).noneMatch(piece -> piece.getBoundingBox().isInside(anchor))) {
                throw new AssertionError("Index points outside actual Stronghold projection");
            }
            if (level.getChunkSource().getLoadedChunksCount() != loadedBeforeLocate) throw new AssertionError("Locate loaded chunks");
            result.addProperty("locator", anchor.toShortString());
            if (reload && !previous.get("locator").equals(result.get("locator"))) throw new AssertionError("Locate changed after restart");
            long artifacts = MosaicPhysicalMaterializer.metrics().artifactCaptureCount() - artifactsBefore;
            int spawns = PhysicalMosaicTrace.snapshot().spawnSeeds().size() - spawnsBefore;
            if (reload && (artifacts != 0 || spawns != 0)) throw new AssertionError("FULL reload regenerated Artifact/SPAWN");
            if (GenerationContextRegistry.bindingCount() != 0 || MosaicSpawnContextRegistry.bindingCount() != 0) throw new AssertionError("Context leak");
            result.addProperty("chunkX", target.x()); result.addProperty("chunkZ", target.z());
            result.addProperty("localSeed", MosaicWorldIdentity.runtimeContext(level).orElseThrow().resolveLocalWorldSeed(Level.OVERWORLD, target));
            result.addProperty("artifacts", artifacts); result.addProperty("spawns", spawns);
            result.addProperty("reload", reload); result.addProperty("markerPreserved", reload);
            result.addProperty("status", "PASS");
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, result.toString());
            System.out.println("STRONGHOLD LIFECYCLE PASS " + target + " reload=" + reload);
        } catch (Throwable failure) {
            result.addProperty("status", "FAIL"); result.addProperty("failure", failure.toString()); failure.printStackTrace();
            try { Files.writeString(Path.of(System.getProperty("mosaic.stronghold.test.output")), result.toString()); }
            catch (Exception io) { throw new IllegalStateException(io); }
        }
        server.halt(false);
    }

    private static ChunkPos findTarget(ServerLevel level) {
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        var mosaic = (MosaicChunkGenerator) level.getChunkSource().getGenerator();
        var generator = new NoiseBasedChunkGenerator(mosaic.getBiomeSource(), mosaic.generatorSettings());
        var definitions = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var stronghold = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.STRONGHOLD);
        for (int i = 0; i < 20000; i++) {
            ChunkPos target = new ChunkPos(85 + i % 128, -80 + i / 128);
            long seed = runtime.resolveLocalWorldSeed(Level.OVERWORLD, target);
            var random = RandomState.create(generator.generatorSettings().value(), level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            var state = generator.createState(definitions, random, seed);
            var scope = ConcentricRingScope.forV2Target(target, seed, generator.getBiomeSource(), state.possibleStructureSets());
            ((ConcentricRingStateAccess) state).randomnibble6plus24generator$setRingScope(scope);
            for (var set : state.possibleStructureSets()) {
                if (!(set.value().placement() instanceof ConcentricRingsStructurePlacement rings)) continue;
                for (ChunkPos source : state.getRingPositionsFor(rings)) {
                    if (Math.abs(source.x() - target.x()) > 1 || Math.abs(source.z() - target.z()) > 1) continue;
                    StructureStart start = stronghold.value().generate(stronghold, Level.OVERWORLD, level.registryAccess(), generator,
                            generator.getBiomeSource(), random, level.getServer().getStructureManager(), seed, source, 0, level,
                            stronghold.value().biomes()::contains);
                    if (start.isValid() && start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().intersects(
                            target.getMinBlockX(), target.getMinBlockZ(), target.getMaxBlockX(), target.getMaxBlockZ()))) {
                        System.out.println("STRONGHOLD PHYSICAL FIXTURE target=" + target + " source=" + source + " localSeed=" + seed + " scanned=" + (i + 1));
                        return target;
                    }
                }
            }
            if (i % 1000 == 0) System.out.println("STRONGHOLD PHYSICAL SCAN " + i);
        }
        throw new AssertionError("No suitable physical Stronghold fixture in bounded scan");
    }
}
