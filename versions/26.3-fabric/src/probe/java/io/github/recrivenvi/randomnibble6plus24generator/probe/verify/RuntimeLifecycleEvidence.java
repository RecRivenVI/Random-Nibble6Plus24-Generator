package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BDevelopmentWorld;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Probe-only runtime fixtures, using ordinary tickets and the server tick loop, never callbacks.
 */
public final class RuntimeLifecycleEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static JsonObject baseline;
    private static ServerLevel level;
    private static ChunkPos owner;
    private static long began;
    private static long simulationStart = -1;
    private static boolean finished;
    private static final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private static BlockPos blockTick, fluidSource, fluidTarget, furnace;
    private static Fluid fluid;
    private static ItemEntity marker;
    private static boolean crossed;
    private static int blockCallbacks, fluidCallbacks, furnaceCallbacks, entityCallbacks;
    private static int physicalBiomeChecks, physicalBiomeCells, physicalBiomeSections;
    private static final Map<Long, String> biomeHashes = new HashMap<>();
    private static final Map<String, String> ownerBiomeHashes = new LinkedHashMap<>();

    private RuntimeLifecycleEvidence() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PREFIX + "runtimeProof");
    }

    public static void capture(JsonObject result) {
        if (!enabled()) return;
        if (baseline != null) throw new IllegalStateException("Duplicate FULL evidence boundary");
        baseline = result.deepCopy();
        result.addProperty("status", "RUNTIME_PENDING");
    }

    public static void tick(MinecraftServer server, Runnable request) {
        if (finished) return;
        if (!server.isSameThread()) throw new IllegalStateException("Off-thread runtime probe");
        NaturalRuntimeEvidence.checkFailure();
        PublicationInventory.checkFailure();
        if (began == 0) {
            began = System.nanoTime();
            System.setProperty(PREFIX + "autoStop", "false");
        }
        if (System.nanoTime() - began > 180_000_000_000L)
            throw new IllegalStateException(
                    "Runtime ticket/simulation evidence timed out: "
                            + (level == null
                                    ? "no level"
                                    : MosaicPhysicalMaterializer.describePhysicalState(
                                            level, owner))
                            + " metrics="
                            + MosaicPhysicalMaterializer.metrics());
        if (level == null) {
            level =
                    server.getLevel(
                            ResourceKey.create(
                                    Registries.DIMENSION,
                                    Identifier.parse(System.getProperty(PREFIX + "dimension"))));
            if (level == null) throw new IllegalStateException("Missing runtime dimension");
            owner =
                    new ChunkPos(
                            Integer.getInteger(PREFIX + "chunkX"),
                            Integer.getInteger(PREFIX + "chunkZ"));
            // Actual native loading and simulation tickets; bounded, non-persistent and removed.
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, owner, 3);
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_SIMULATION, owner, 3);
        }
        // TicketStorage derives FULL level minus radius. Observe every demanded FULL future,
        // without requesting extra chunks, so the restart proof has no unfinished frontier.
        for (int z = owner.z() - 3; z <= owner.z() + 3; z++)
            for (int x = owner.x() - 3; x <= owner.x() + 3; x++) {
                var holder =
                        level.getChunkSource()
                                .chunkMap
                                .getUpdatingChunkIfPresent(new ChunkPos(x, z).pack());
                if (holder == null) return;
                var full = holder.getFullChunkFuture().getNow(null);
                if (full == null || full.orElse(null) == null) return;
            }
        var first = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(owner.pack());
        var adjacent = new ChunkPos(owner.x() + 1, owner.z());
        var second = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(adjacent.pack());
        if (first == null || second == null) return;
        var ready = first.getEntityTickingChunkFuture().getNow(null);
        var next = second.getEntityTickingChunkFuture().getNow(null);
        if (ready == null || next == null) return;
        var chunk = ready.orElse(null);
        var neighbor = next.orElse(null);
        if (chunk == null || neighbor == null) return;
        if (chunk != level.getChunkSource().getChunkNow(owner.x(), owner.z())
                || neighbor != level.getChunkSource().getChunkNow(adjacent.x(), adjacent.z()))
            throw new IllegalStateException("Runtime promotion returned a non-physical chunk");
        if (!chunk.isLightCorrect() || !neighbor.isLightCorrect())
            throw new IllegalStateException("Runtime promotion lost physical light");
        if (first.getFullStatus() != net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING
                || second.getFullStatus()
                        != net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING)
            throw new IllegalStateException("Runtime holders are not currently ENTITY_TICKING");
        // Other targets may legitimately generate while physical chunks tick. Global binding
        // counts are a quiescent-frontier cleanup check, not a runtime thread-local invariant.
        if (simulationStart < 0
                && (MosaicPhysicalMaterializer.metrics().inFlightCount() != 0
                        || GenerationContextRegistry.bindingCount() != 0
                        || MosaicSpawnContextRegistry.bindingCount() != 0)) return;
        if (baseline == null) {
            int loaded = level.getChunkSource().getLoadedChunksCount();
            long generations = BDevelopmentWorld.metrics(server).get("generations");
            long captures = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
            for (int z = owner.z() - 3; z <= owner.z() + 3; z++)
                for (int x = owner.x() - 3; x <= owner.x() + 3; x++) {
                    var position = new ChunkPos(x, z);
                    physicalBiomeChecks += verifyPhysicalBiomes(position);
                    ownerBiomeHashes.put(position.toString(), biomeHashes.get(position.pack()));
                }
            if (loaded != level.getChunkSource().getLoadedChunksCount()
                    || generations != BDevelopmentWorld.metrics(server).get("generations")
                    || captures != MosaicPhysicalMaterializer.metrics().artifactCaptureCount())
                throw new IllegalStateException("Stored biome observation started physical work");
            // The ordinary single-request observer asserts a settled handoff. Do not run it
            // while the ticket-driven neighboring FULL requests are still completing.
            request.run();
            return;
        }
        if (simulationStart < 0) {
            if (!NaturalRuntimeEvidence.ready(level, owner)) return;
            installFixtures();
            simulationStart = level.getGameTime();
            return;
        }
        crossed |= marker.chunkPosition().equals(adjacent);
        if (level.getGameTime() - simulationStart < 220) return;
        if (MosaicPhysicalMaterializer.metrics().inFlightCount() != 0
                || GenerationContextRegistry.bindingCount() != 0
                || MosaicSpawnContextRegistry.bindingCount() != 0) return;
        long registrations =
                java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                        .filter(entity -> entity.getUUID().equals(marker.getUUID()))
                        .count();
        if (!crossed
                || registrations != 1
                || entityCallbacks <= 0
                || level.getEntity(marker.getUUID()) != marker)
            throw new IllegalStateException("Native item entity did not move across the seam once");
        if (blockCallbacks <= 0
                || fluidCallbacks <= 0
                || !level.getFluidState(fluidTarget).getType().isSame(fluid))
            throw new IllegalStateException("Native scheduled ticks/fluid seam did not execute");
        if (!(level.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity be)
                || !be.getItem(2).is(Items.STONE)
                || furnaceCallbacks <= 0)
            throw new IllegalStateException("Native furnace ticker did not cook its real input");
        if (!PhysicalMosaicTrace.snapshot().lifecycleDuplicates().isEmpty())
            throw new IllegalStateException("Duplicate physical lifecycle callbacks");
        if (baseline.has("normalVanilla") && baseline.get("normalVanilla").getAsBoolean()) {
            var metrics = MosaicPhysicalMaterializer.metrics();
            var trace = PhysicalMosaicTrace.snapshot();
            if (metrics.artifactCaptureCount() != 0
                    || metrics.planCount() != 0
                    || !trace.physicalStatusCalls().isEmpty()
                    || !trace.generatorCalls().isEmpty()
                    || BDevelopmentWorld.metrics(server).get("generations") != 0L)
                throw new IllegalStateException("Vanilla runtime entered Mosaic worldgen");
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("schema", 2);
        proof.addProperty("nativeTickLoop", true);
        proof.addProperty("bothEntityTickingFuturesResolved", true);
        proof.addProperty("ticketFullFrontier", 49);
        proof.addProperty("quiescentFrontierBeforeFixtures", true);
        proof.addProperty("testFixturesNotCanonicalGenerationCoverage", true);
        proof.addProperty("simulationTicks", level.getGameTime() - simulationStart);
        proof.addProperty("physicalBiomeChecks", physicalBiomeChecks);
        proof.addProperty("physicalBiomeOwners", ownerBiomeHashes.size());
        proof.addProperty("physicalBiomeChunks", biomeHashes.size());
        proof.addProperty("physicalBiomeCells", physicalBiomeCells);
        proof.addProperty("physicalBiomeSections", physicalBiomeSections);
        proof.addProperty("biomeQuartCoverageVersion", 1);
        proof.add("ownerBiomeHashes", new com.google.gson.Gson().toJsonTree(ownerBiomeHashes));
        proof.addProperty("biomeQueriesPreservedLoadedCountAndGeneration", true);
        proof.addProperty(
                "dependencyPyramid",
                "reload".equals(System.getProperty(PREFIX + "mode")) ? "loading" : "generation");
        proof.addProperty("entityCrossedPhysicalSeam", crossed);
        proof.addProperty("entityRegistrations", registrations);
        proof.addProperty("entityCallbacks", entityCallbacks);
        proof.addProperty("entityFinalChunk", marker.chunkPosition().toString());
        var runtime = MosaicWorldIdentity.runtimeContext(level);
        if (runtime.isPresent()) {
            long firstSeed = runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), owner);
            long secondSeed =
                    runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), adjacent);
            if (firstSeed == secondSeed)
                throw new IllegalStateException("Runtime seam seed collision");
            proof.addProperty("ownerLocalSeed", firstSeed);
            proof.addProperty("adjacentLocalSeed", secondSeed);
        }
        proof.addProperty("blockCallbacks", blockCallbacks);
        proof.addProperty("fluidCallbacks", fluidCallbacks);
        proof.addProperty("furnaceCallbacks", furnaceCallbacks);
        proof.addProperty("fluidCrossedPhysicalSeam", true);
        proof.addProperty("furnaceCooked", true);
        proof.addProperty("targetTicketLevel", first.getTicketLevel());
        proof.addProperty("neighborTicketLevel", second.getTicketLevel());
        proof.addProperty("targetFullChunkStatus", first.getFullStatus().name());
        proof.addProperty("neighborFullChunkStatus", second.getFullStatus().name());
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("elapsedMillis", (System.nanoTime() - began) / 1_000_000);
        if (NaturalRuntimeEvidence.enabled()) proof.add("natural", NaturalRuntimeEvidence.finish());
        marker.discard();
        originals.forEach((pos, state) -> level.setBlock(pos, state, 3));
        level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_SIMULATION, owner, 3);
        level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, owner, 3);
        originals.clear();
        proof.addProperty("probeTicketsRemoved", true);
        var gson = new com.google.gson.Gson();
        proof.add("finalMaterializer", gson.toJsonTree(MosaicPhysicalMaterializer.metrics()));
        proof.add("finalDevelopmentB", gson.toJsonTree(BDevelopmentWorld.metrics(server)));
        proof.addProperty("generationBindings", GenerationContextRegistry.bindingCount());
        proof.addProperty("spawnBindings", MosaicSpawnContextRegistry.bindingCount());
        baseline.add("runtimeProof", proof);
        baseline.addProperty("status", "PASS");
        try {
            Files.writeString(Path.of(System.getProperty(PREFIX + "output")), baseline.toString());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        finished = true;
        biomeHashes.clear();
        ownerBiomeHashes.clear();
        marker = null;
        level = null;
        server.halt(false);
    }

    private static int verifyPhysicalBiomes(ChunkPos center) {
        var pyramid =
                "reload".equals(System.getProperty(PREFIX + "mode"))
                        ? ChunkPyramid.LOADING_PYRAMID
                        : ChunkPyramid.GENERATION_PYRAMID;
        var dependencies = pyramid.getStepTo(ChunkStatus.FULL).accumulatedDependencies();
        int checked = 0;
        for (int dz = -dependencies.getRadius(); dz <= dependencies.getRadius(); dz++)
            for (int dx = -dependencies.getRadius(); dx <= dependencies.getRadius(); dx++) {
                var required = dependencies.get(Math.max(Math.abs(dx), Math.abs(dz)));
                if (required.isBefore(ChunkStatus.BIOMES)) continue;
                var position = new ChunkPos(center.x() + dx, center.z() + dz);
                var holder =
                        level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(position.pack());
                var physical =
                        holder == null
                                ? null
                                : holder.getChunkIfPresentUnchecked(ChunkStatus.BIOMES);
                if (physical == null || physical.getPersistedStatus().isBefore(ChunkStatus.BIOMES))
                    throw new IllegalStateException(
                            "FULL owner biome dependency not ready: owner="
                                    + center
                                    + ", dependency="
                                    + position
                                    + ", required="
                                    + required);
                if (!biomeHashes.containsKey(position.pack()))
                    biomeHashes.put(position.pack(), storedBiomeHash(position, physical));
                checked++;
            }
        if (checked == 0) throw new IllegalStateException("Native FULL exposed no biome inputs");
        return checked;
    }

    private static String storedBiomeHash(ChunkPos position, ChunkAccess physical) {
        var visible =
                level.getChunkSource()
                        .getChunk(position.x(), position.z(), ChunkStatus.BIOMES, false);
        if (visible == null || visible.getPersistedStatus().isBefore(ChunkStatus.BIOMES))
            throw new IllegalStateException(
                    "Native biome lookup would use an uncached fallback: " + position);
        var text =
                new StringBuilder()
                        .append(physical.getMinY())
                        .append(':')
                        .append(physical.getHeight())
                        .append('\n');
        physicalBiomeSections += physical.getSections().length;
        // 26.3 getMaxY() includes its top block; do not omit the last quart layer.
        for (int y = Math.floorDiv(physical.getMinY(), 4);
                y <= Math.floorDiv(physical.getMaxY(), 4);
                y++)
            for (int z = 0; z < 4; z++)
                for (int x = 0; x < 4; x++) {
                    int qx = position.x() * 4 + x, qz = position.z() * 4 + z;
                    var stored = physical.getNoiseBiome(qx, y, qz);
                    if (!stored.equals(level.getNoiseBiome(qx, y, qz)))
                        throw new IllegalStateException(
                                "Runtime biome authority differs from physical palette: "
                                        + position
                                        + " quart="
                                        + qx
                                        + ","
                                        + y
                                        + ","
                                        + qz);
                    text.append(stored.unwrapKey().orElseThrow().identifier()).append('\n');
                    physicalBiomeCells++;
                }
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            text.toString()
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Missing mandatory biome digest", failure);
        }
    }

    private static void installFixtures() {
        int x = owner.getMaxBlockX(), z = owner.getMinBlockZ() + 5;
        int y = Math.max(level.getSeaLevel(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        for (int dx = -2; dx <= 9; dx++)
            for (int dz = -2; dz <= 2; dz++)
                y = Math.max(y, level.getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz));
        y += 8;
        if (y + 4 >= level.getMaxY())
            throw new IllegalStateException("No safe runtime fixture height");
        for (int dx = -2; dx <= 9; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -1; dy <= 2; dy++) {
                    var pos = new BlockPos(x + dx, y + dy, z + dz);
                    if (level.getBlockEntity(pos) != null)
                        throw new IllegalStateException(
                                "Runtime fixture would replace a canonical BE");
                    put(
                            pos,
                            dy == -1
                                    ? Blocks.STONE.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState());
                }
        blockTick = new BlockPos(x - 1, y, z - 1);
        put(blockTick, Blocks.STONE.defaultBlockState());
        level.scheduleTick(blockTick, Blocks.STONE, 1);
        fluidSource = new BlockPos(x, y, z);
        fluidTarget = fluidSource.east();
        put(fluidSource.west(), Blocks.STONE.defaultBlockState());
        put(fluidSource.north(), Blocks.STONE.defaultBlockState());
        put(fluidSource.south(), Blocks.STONE.defaultBlockState());
        fluid =
                level.dimension() == net.minecraft.world.level.Level.NETHER
                        ? Fluids.LAVA
                        : Fluids.WATER;
        put(fluidSource, fluid.defaultFluidState().createLegacyBlock());
        level.scheduleTick(fluidSource, fluid, 1);
        furnace = new BlockPos(x - 1, y, z + 2);
        put(furnace, Blocks.FURNACE.defaultBlockState());
        var be = (AbstractFurnaceBlockEntity) level.getBlockEntity(furnace);
        if (be == null) throw new IllegalStateException("Native furnace BE was not registered");
        be.setItem(0, new ItemStack(Items.COBBLESTONE));
        be.setItem(1, new ItemStack(Items.COAL));
        marker = new ItemEntity(level, x + .5D, y + .5D, z + 1.5D, new ItemStack(Items.PAPER));
        marker.setNoGravity(true);
        marker.setDeltaMovement(.3D, 0D, 0D);
        if (!level.addFreshEntity(marker))
            throw new IllegalStateException("Item manager registration failed");
    }

    private static void put(BlockPos pos, BlockState state) {
        originals.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    public static void blockTick(ServerLevel host, BlockPos pos) {
        if (host == level && pos.equals(blockTick)) blockCallbacks++;
    }

    public static void fluidTick(ServerLevel host, BlockPos pos) {
        if (host == level && pos.equals(fluidSource)) fluidCallbacks++;
    }

    public static void furnaceTick(net.minecraft.world.level.Level host, BlockPos pos) {
        if (host == level && pos.equals(furnace)) furnaceCallbacks++;
    }

    public static void entityTick(ServerLevel host, Entity entity) {
        if (host == level && entity == marker) {
            entityCallbacks++;
            crossed |= entity.chunkPosition().equals(new ChunkPos(owner.x() + 1, owner.z()));
        }
    }
}
