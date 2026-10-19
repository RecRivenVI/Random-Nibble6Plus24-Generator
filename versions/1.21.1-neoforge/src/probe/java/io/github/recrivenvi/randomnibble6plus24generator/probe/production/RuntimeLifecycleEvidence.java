package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapQueues;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.RuntimeLevelIdleAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.SpawnConstructorScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Actual 1.21.1 regional tickets and natural tick callbacks; never drives a generation stage. */
public final class RuntimeLifecycleEvidence {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static final AtomicInteger STATE_CREATIONS = new AtomicInteger();
    private static final AtomicInteger NARROW_CONTEXTS = new AtomicInteger();
    private static final java.util.concurrent.atomic.LongAdder REUSE_PREPARATIONS =
            new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder REUSE_HITS =
            new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder REUSE_FALLBACKS =
            new java.util.concurrent.atomic.LongAdder();
    private static final Map<BlockPos, BlockState> ORIGINALS = new LinkedHashMap<>();
    private static JsonObject baseline;
    private static ServerLevel level;
    private static ChunkPos owner;
    private static long began, nextProgress;
    private static long simulationStart = -1;
    private static boolean finished, crossed;
    private static int blockCallbacks, fluidCallbacks, furnaceCallbacks, entityCallbacks;
    private static int physicalBiomeChecks;
    private static int idleTimeBeforeFixtures = -1;
    private static BlockPos blockTick, fluidSource, fluidTarget, furnace;
    private static Fluid fluid;
    private static ItemEntity marker;

    private RuntimeLifecycleEvidence() {}

    public static boolean enabled() {
        return "integration-production_lifecycle".equals(System.getProperty("validation.name"))
                && Boolean.getBoolean(PREFIX + "runtimeProof");
    }

    public static void physicalStateCreated() {
        if (enabled()) STATE_CREATIONS.incrementAndGet();
    }

    public static void contextOpened(boolean narrow) {
        if (enabled() && narrow) NARROW_CONTEXTS.incrementAndGet();
    }

    public static void sourceReusePrepared(
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                            .ScopedStructureSources.Metrics
                    metrics) {
        if (!enabled()) return;
        if (metrics == null) {
            REUSE_FALLBACKS.increment();
            return;
        }
        if (!metrics.closed() || metrics.resident() != 0 || metrics.peakResident() > 441)
            throw new IllegalStateException(
                    "Native runtime B returned a live/unbounded source owner");
        REUSE_PREPARATIONS.increment();
        REUSE_HITS.add(metrics.hits());
    }

    public static void capture(JsonObject result) {
        if (!enabled()) return;
        if (baseline != null) throw new IllegalStateException("Duplicate FULL evidence boundary");
        baseline = result.deepCopy();
        result.addProperty("status", "RUNTIME_PENDING");
    }

    public static void tick(MinecraftServer server, Runnable request) {
        if (finished) return;
        try {
            advance(server, request);
        } catch (Throwable failure) {
            finished = true;
            JsonObject result = new JsonObject();
            result.addProperty("status", "FAIL");
            result.addProperty("failure", failure.toString());
            if (baseline != null) result.add("fullBoundary", baseline);
            if (level != null) {
                JsonObject diagnostics = new JsonObject();
                diagnostics.addProperty(
                        "emptyEntityTicks", ((RuntimeLevelIdleAccess) level).mosaic$emptyTime());
                diagnostics.addProperty("entityCallbacks", entityCallbacks);
                diagnostics.addProperty("blockCallbacks", blockCallbacks);
                diagnostics.addProperty("fluidCallbacks", fluidCallbacks);
                diagnostics.addProperty("furnaceCallbacks", furnaceCallbacks);
                diagnostics.addProperty("crossed", crossed);
                if (marker != null) {
                    diagnostics.addProperty("markerAlive", marker.isAlive());
                    diagnostics.addProperty(
                            "markerRegistered", level.getEntity(marker.getUUID()) == marker);
                    diagnostics.addProperty("markerTickCount", marker.tickCount);
                    diagnostics.addProperty("markerPosition", marker.position().toString());
                }
                result.add("runtimeDiagnostics", diagnostics);
            }
            try {
                if (level != null && state() != null)
                    result.add("physicalMetrics", new Gson().toJsonTree(state().metrics()));
            } catch (Throwable snapshotFailure) {
                failure.addSuppressed(snapshotFailure);
            }
            try {
                Files.writeString(
                        Path.of(System.getProperty(PREFIX + "output")), result.toString());
            } catch (Exception writing) {
                failure.addSuppressed(writing);
            }
            throw new IllegalStateException("Native runtime lifecycle probe failed", failure);
        }
    }

    private static void advance(MinecraftServer server, Runnable request) throws Exception {
        if (!server.isSameThread()) throw new IllegalStateException("Off-thread runtime probe");
        if (began == 0) began = System.nanoTime();
        if (System.nanoTime() - began > 180_000_000_000L)
            throw new IllegalStateException(
                    "Runtime ticket/simulation evidence exceeded original bound");
        if (level == null) {
            level =
                    server.getLevel(
                            ResourceKey.create(
                                    Registries.DIMENSION,
                                    ResourceLocation.parse(
                                            System.getProperty(PREFIX + "dimension"))));
            if (level == null) throw new IllegalStateException("Missing runtime dimension");
            owner =
                    new ChunkPos(
                            Integer.getInteger(PREFIX + "chunkX"),
                            Integer.getInteger(PREFIX + "chunkZ"));
            boolean vanilla = Boolean.getBoolean(PREFIX + "vanillaControl");
            if (MosaicWorldIdentity.runtimeContext(level).isEmpty() != vanilla)
                throw new IllegalStateException("Wrong runtime world identity");
            // This version also updates TickingTracker from addRegionTicket. Level 33-3
            // demands the real 7x7 FULL frontier, with both seam chunks ENTITY_TICKING.
            level.getChunkSource().addRegionTicket(TicketType.PLAYER, owner, 3, owner);
        }
        if (System.nanoTime() >= nextProgress) {
            System.out.println(
                    "MOSAIC_RUNTIME_PROGRESS elapsedMillis="
                            + (System.nanoTime() - began) / 1_000_000
                            + " metrics="
                            + (state() == null ? "Vanilla" : state().metrics()));
            nextProgress = System.nanoTime() + 30_000_000_000L;
        }
        var map = (ChunkMapQueues) level.getChunkSource().chunkMap;
        for (int z = owner.z - 3; z <= owner.z + 3; z++)
            for (int x = owner.x - 3; x <= owner.x + 3; x++) {
                var holder = map.mosaic$holder(new ChunkPos(x, z).toLong());
                if (holder == null) return;
                var full = holder.getFullChunkFuture().getNow(null);
                if (full == null || full.orElse(null) == null) return;
            }
        var adjacent = new ChunkPos(owner.x + 1, owner.z);
        var first = map.mosaic$holder(owner.toLong());
        var second = map.mosaic$holder(adjacent.toLong());
        var ready = first.getEntityTickingChunkFuture().getNow(null);
        var next = second.getEntityTickingChunkFuture().getNow(null);
        if (ready == null || next == null) return;
        var chunk = ready.orElse(null);
        var neighbor = next.orElse(null);
        if (chunk == null || neighbor == null) return;
        if (chunk != level.getChunkSource().getChunkNow(owner.x, owner.z)
                || neighbor != level.getChunkSource().getChunkNow(adjacent.x, adjacent.z))
            throw new IllegalStateException("Runtime promotion is not the real physical chunk");
        if (!chunk.isLightCorrect() || !neighbor.isLightCorrect())
            throw new IllegalStateException("Runtime promotion lost light");
        if (FeatureExecutionScope.current(level) != null
                || SeedLocalRegionScope.isBound()
                || SpawnConstructorScope.current() != null)
            throw new IllegalStateException("Isolated scope escaped into the simulation thread");
        if (state() != null && state().metrics().inFlight() != 0) return;
        if (FeatureExecutionScope.bindingCount() != 0) return;
        if (baseline == null) {
            for (int z = owner.z - 3; z <= owner.z + 3; z++)
                for (int x = owner.x - 3; x <= owner.x + 3; x++)
                    physicalBiomeChecks += verifyPhysicalBiomes(new ChunkPos(x, z));
            request.run();
            return;
        }
        if (simulationStart < 0) {
            installFixtures();
            simulationStart = level.getGameTime();
            return;
        }
        crossed |= marker.chunkPosition().equals(adjacent);
        if (level.getGameTime() - simulationStart < 220) return;
        long registrations =
                java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                        .filter(entity -> entity.getUUID().equals(marker.getUUID()))
                        .count();
        if (!crossed
                || registrations != 1
                || entityCallbacks <= 0
                || level.getEntity(marker.getUUID()) != marker)
            throw new IllegalStateException(
                    "Natural item entity did not cross the physical seam once");
        if (blockCallbacks <= 0
                || fluidCallbacks <= 0
                || !level.getFluidState(fluidTarget).getType().isSame(fluid))
            throw new IllegalStateException("Natural scheduled block/fluid ticks did not execute");
        if (!(level.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity be)
                || !be.getItem(2).is(Items.STONE)
                || furnaceCallbacks <= 0)
            throw new IllegalStateException("Natural furnace ticker did not cook its input");
        JsonObject proof = new JsonObject();
        proof.addProperty("schema", 3);
        proof.addProperty("nativeTickLoop", true);
        proof.addProperty("bothEntityTickingFuturesResolved", true);
        proof.addProperty("ticketFullFrontier", 49);
        proof.addProperty("quiescentFrontierBeforeFixtures", true);
        proof.addProperty("testFixturesNotCanonicalGenerationCoverage", true);
        proof.addProperty("simulationTicks", level.getGameTime() - simulationStart);
        proof.addProperty("emptyEntityTicksBeforeFixtures", idleTimeBeforeFixtures);
        proof.addProperty("idleTimerResetCalls", 1);
        proof.addProperty("physicalBiomeChecks", physicalBiomeChecks);
        proof.addProperty("physicalBiomeOwners", 49);
        proof.addProperty(
                "dependencyPyramid",
                "reload".equals(System.getProperty(PREFIX + "mode")) ? "loading" : "generation");
        proof.addProperty("entityCrossedPhysicalSeam", crossed);
        proof.addProperty("entityRegistrations", registrations);
        proof.addProperty("entityCallbacks", entityCallbacks);
        proof.addProperty("entityFinalChunk", marker.chunkPosition().toString());
        proof.addProperty("blockCallbacks", blockCallbacks);
        proof.addProperty("fluidCallbacks", fluidCallbacks);
        proof.addProperty("furnaceCallbacks", furnaceCallbacks);
        proof.addProperty("fluidCrossedPhysicalSeam", true);
        proof.addProperty("furnaceCooked", true);
        proof.addProperty("targetTicketLevel", first.getTicketLevel());
        proof.addProperty("neighborTicketLevel", second.getTicketLevel());
        proof.addProperty("mosaicStateCreations", STATE_CREATIONS.get());
        proof.addProperty("narrowContexts", NARROW_CONTEXTS.get());
        proof.addProperty("sourceReusePreparations", REUSE_PREPARATIONS.sum());
        proof.addProperty("sourceReuseHits", REUSE_HITS.sum());
        proof.addProperty("sourceReuseFallbacks", REUSE_FALLBACKS.sum());
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("elapsedMillis", (System.nanoTime() - began) / 1_000_000);
        var runtime = MosaicWorldIdentity.runtimeContext(level);
        if (runtime.isPresent()) {
            long local = runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), owner);
            long other = runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), adjacent);
            if (local == other) throw new IllegalStateException("Runtime seam seed collision");
            proof.addProperty("ownerLocalSeed", local);
            proof.addProperty("adjacentLocalSeed", other);
            var metrics = new Gson().toJsonTree(state().metrics()).getAsJsonObject();
            for (String key :
                    java.util.List.of(
                            "artifacts",
                            "publications",
                            "biomePreparations",
                            "biomePublications",
                            "poi",
                            "light",
                            "spawn",
                            "full"))
                if (!metrics.get(key).equals(baseline.getAsJsonObject("physicalMetrics").get(key)))
                    throw new IllegalStateException(
                            "Runtime repeated canonical/lifecycle work: " + key);
            proof.add("finalPhysicalMetrics", metrics);
        } else if (STATE_CREATIONS.get() != 0 || baseline.has("physicalMetrics")) {
            throw new IllegalStateException("Vanilla runtime created Mosaic physical state");
        }
        marker.discard();
        be.clearContent();
        ORIGINALS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINALS.clear();
        level.getChunkSource().removeRegionTicket(TicketType.PLAYER, owner, 3, owner);
        proof.addProperty("probeTicketsRemoved", true);
        proof.addProperty("generationBindings", FeatureExecutionScope.bindingCount());
        proof.addProperty("regionScopeBound", SeedLocalRegionScope.isBound());
        proof.addProperty("spawnScopeBound", SpawnConstructorScope.current() != null);
        baseline.add("runtimeProof", proof);
        baseline.addProperty("status", "PASS");
        Files.writeString(Path.of(System.getProperty(PREFIX + "output")), baseline.toString());
        finished = true;
        marker = null;
        level = null;
        server.halt(false);
    }

    private static MosaicPhysicalLevelState state() {
        return MosaicWorldIdentity.runtimeContext(level).isPresent()
                ? ((MosaicPhysicalLevelOwner) level).mosaic$physicalState()
                : null;
    }

    private static int verifyPhysicalBiomes(ChunkPos center) {
        var pyramid =
                "reload".equals(System.getProperty(PREFIX + "mode"))
                        ? ChunkPyramid.LOADING_PYRAMID
                        : ChunkPyramid.GENERATION_PYRAMID;
        var dependencies = pyramid.getStepTo(ChunkStatus.FULL).accumulatedDependencies();
        var map = (ChunkMapQueues) level.getChunkSource().chunkMap;
        int checked = 0;
        for (int z = -dependencies.getRadius(); z <= dependencies.getRadius(); z++)
            for (int x = -dependencies.getRadius(); x <= dependencies.getRadius(); x++) {
                var required = dependencies.get(Math.max(Math.abs(x), Math.abs(z)));
                if (required.isBefore(ChunkStatus.BIOMES)) continue;
                var position = new ChunkPos(center.x + x, center.z + z);
                var holder = map.mosaic$holder(position.toLong());
                var physical =
                        holder == null
                                ? null
                                : holder.getChunkIfPresentUnchecked(ChunkStatus.BIOMES);
                if (physical == null || physical.getPersistedStatus().isBefore(ChunkStatus.BIOMES))
                    throw new IllegalStateException(
                            "FULL exposed missing physical biome input " + position);
                checked++;
            }
        if (checked == 0) throw new IllegalStateException("No physical biome inputs checked");
        return checked;
    }

    private static void installFixtures() {
        // Empty dedicated levels stop entity/BE ticking after 300 ticks. A slow
        // cold frontier must not consume this fixture's 220-tick activity window.
        idleTimeBeforeFixtures = ((RuntimeLevelIdleAccess) level).mosaic$emptyTime();
        level.resetEmptyTime();
        int x = owner.getMaxBlockX(), z = owner.getMinBlockZ() + 5;
        int y = Math.max(level.getSeaLevel(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        for (int dx = -2; dx <= 9; dx++)
            for (int dz = -2; dz <= 2; dz++)
                y = Math.max(y, level.getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz));
        y += 8;
        if (y + 4 >= level.getMaxBuildHeight())
            throw new IllegalStateException("No safe fixture height");
        for (int dx = -2; dx <= 9; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -1; dy <= 2; dy++) {
                    var pos = new BlockPos(x + dx, y + dy, z + dz);
                    if (level.getBlockEntity(pos) != null)
                        throw new IllegalStateException("Would replace canonical BE");
                    boolean boundary = dx == -2 || dx == 9 || dz == -2 || dz == 2;
                    put(
                            pos,
                            dy == -1 || (boundary && dy < 2)
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
        fluid = level.dimension() == Level.NETHER ? Fluids.LAVA : Fluids.WATER;
        put(fluidSource, fluid.defaultFluidState().createLegacyBlock());
        level.scheduleTick(fluidSource, fluid, 1);
        furnace = new BlockPos(x - 1, y, z + 2);
        put(furnace, Blocks.FURNACE.defaultBlockState());
        var be = (AbstractFurnaceBlockEntity) level.getBlockEntity(furnace);
        if (be == null) throw new IllegalStateException("Furnace BE was not registered");
        be.setItem(0, new ItemStack(Items.COBBLESTONE));
        be.setItem(1, new ItemStack(Items.COAL));
        marker = new ItemEntity(level, x + .5D, y + 1.5D, z + 1.5D, new ItemStack(Items.PAPER));
        if (!level.noCollision(marker))
            throw new IllegalStateException("Runtime item fixture starts inside a solid block");
        marker.setNoGravity(true);
        marker.setDeltaMovement(.3D, 0D, 0D);
        if (!level.addFreshEntity(marker))
            throw new IllegalStateException("Item registration failed");
    }

    private static void put(BlockPos pos, BlockState state) {
        ORIGINALS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    public static void blockTick(ServerLevel host, BlockPos pos) {
        if (host == level && pos.equals(blockTick)) blockCallbacks++;
    }

    public static void fluidTick(ServerLevel host, BlockPos pos) {
        if (host == level && pos.equals(fluidSource)) fluidCallbacks++;
    }

    public static void furnaceTick(Level host, BlockPos pos) {
        if (host == level && pos.equals(furnace)) furnaceCallbacks++;
    }

    public static void entityTick(ServerLevel host, Entity entity) {
        if (host == level && entity == marker) {
            entityCallbacks++;
            crossed |= entity.chunkPosition().equals(new ChunkPos(owner.x + 1, owner.z));
        }
    }
}
