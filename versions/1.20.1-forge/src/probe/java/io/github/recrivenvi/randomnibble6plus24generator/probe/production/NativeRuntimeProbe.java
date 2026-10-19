package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin.PhysicalHolderAccess;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin.RuntimeIdleAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Native 1.20.1 regional promotion and natural simulation, not player-entry evidence. */
public final class NativeRuntimeProbe {
    private static final Map<BlockPos, BlockState> ORIGINALS = new LinkedHashMap<>();
    private static ServerLevel level;
    private static ChunkPos owner;
    private static boolean held, crossed, finished;
    private static long simulationStart = -1;
    private static int blockCallbacks, fluidCallbacks, furnaceCallbacks, entityCallbacks;
    private static int idleBefore, dependencyChecks;
    private static BlockPos blockTick, fluidSource, fluidTarget, furnace;
    private static Fluid fluid;
    private static ItemEntity marker;
    private static MosaicPhysicalLevelState.Metrics baseline;

    private NativeRuntimeProbe() {}

    public static boolean enabled() {
        return NativePhysicalProbe.enabled()
                && Boolean.getBoolean(NativePhysicalProbe.PREFIX + "runtimeProof");
    }

    public static void start(ServerLevel host, ChunkPos target) {
        if (!enabled()) return;
        require(host.getServer().isSameThread(), "Runtime ticket from a worker");
        level = host;
        owner = target;
        // This version updates both loading tickets and TickingTracker. Level FULL-3
        // supplies the real 7x7 FULL frontier and ENTITY_TICKING on both seam owners.
        level.getChunkSource().addRegionTicket(TicketType.PLAYER, owner, 3, owner);
        held = true;
    }

    public static boolean advance(LevelChunk root, long requestStarted, JsonObject result) {
        if (!enabled()) return true;
        if (finished) return true;
        require(level.getServer().isSameThread(), "Off-thread runtime observation");
        require(
                System.nanoTime() - requestStarted <= 300_000_000_000L,
                "Native runtime promotion/simulation exceeded 300 seconds");
        var map = (PhysicalHolderAccess) level.getChunkSource().chunkMap;
        for (int z = owner.z - 3; z <= owner.z + 3; z++)
            for (int x = owner.x - 3; x <= owner.x + 3; x++) {
                var holder = map.mosaic$holder(new ChunkPos(x, z).toLong());
                if (holder == null) return false;
                var full = holder.getFullChunkFuture().getNow(null);
                if (full == null || full.left().isEmpty()) return false;
            }
        var adjacent = new ChunkPos(owner.x + 1, owner.z);
        var first = map.mosaic$holder(owner.toLong());
        var second = map.mosaic$holder(adjacent.toLong());
        var tickA = first.getTickingChunkFuture().getNow(null);
        var tickB = second.getTickingChunkFuture().getNow(null);
        if (tickA == null || tickB == null || tickA.left().isEmpty() || tickB.left().isEmpty())
            return false;
        var a = first.getEntityTickingChunkFuture().getNow(null);
        var b = second.getEntityTickingChunkFuture().getNow(null);
        if (a == null || b == null || a.left().isEmpty() || b.left().isEmpty()) return false;
        require(
                a.left().orElseThrow() == root
                        && root == level.getChunkSource().getChunkNow(owner.x, owner.z)
                        && b.left().orElseThrow()
                                == level.getChunkSource().getChunkNow(adjacent.x, adjacent.z),
                "Promotion did not expose real physical seam chunks");
        require(
                root.isLightCorrect() && b.left().orElseThrow().isLightCorrect(),
                "Promoted physical light is not ready");
        require(
                FeatureExecutionScope.bindingCount() == 0
                        && !SeedLocalRegionScope.isBound()
                        && SpawnConstructorScope.current() == null,
                "Generation scope escaped into natural simulation");
        var state = ((MosaicPhysicalLevelOwner) level).mosaic$physicalState();
        if (state.metrics().inFlight() != 0) return false;
        if (simulationStart < 0) {
            dependencyChecks = verifyDependencies();
            baseline = state.metrics();
            if (MosaicWorldIdentity.runtimeContext(level).isEmpty())
                require(
                        baseline.generations() == 0
                                && baseline.publications() == 0
                                && baseline.spawn() == 0,
                        "Vanilla entered Mosaic generation");
            installFixtures();
            simulationStart = level.getGameTime();
            return false;
        }
        crossed |= marker.chunkPosition().equals(adjacent);
        if (level.getGameTime() - simulationStart < 220) return false;
        long registrations =
                java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                        .filter(e -> e.getUUID().equals(marker.getUUID()))
                        .count();
        require(
                crossed
                        && entityCallbacks > 0
                        && registrations == 1
                        && level.getEntity(marker.getUUID()) == marker,
                "Native entity did not tick/register/cross the physical seam exactly once");
        require(
                blockCallbacks > 0
                        && fluidCallbacks > 0
                        && level.getFluidState(fluidTarget).getType().isSame(fluid),
                "Native scheduled ticks or cross-seam fluid did not execute");
        require(
                level.getBlockEntity(furnace) instanceof AbstractFurnaceBlockEntity,
                "Native furnace BE vanished");
        var be = (AbstractFurnaceBlockEntity) level.getBlockEntity(furnace);
        require(
                be.getItem(2).is(Items.STONE) && furnaceCallbacks > 0,
                "Native BE ticker did not cook the input");
        require(
                baseline.equals(state.metrics()),
                "Runtime unexpectedly repeated generation/lifecycle work");
        var proof = new JsonObject();
        proof.addProperty("schema", 1);
        proof.addProperty("nativeTickLoop", true);
        proof.addProperty("scope", "REGIONAL_TICKING_NOT_PLAYER_ENTRY_OR_PACKET");
        proof.addProperty("ticketFullFrontier", 49);
        proof.addProperty("bothEntityTickingFuturesResolved", true);
        proof.addProperty("bothBlockTickingFuturesResolved", true);
        proof.addProperty("allCallbacksServerThread", true);
        proof.addProperty("quiescentBeforeFixtures", true);
        proof.addProperty("testFixturesNotCanonicalCoverage", true);
        proof.addProperty("simulationTicks", level.getGameTime() - simulationStart);
        proof.addProperty("idleBeforeFixtures", idleBefore);
        proof.addProperty("nativeIdleResetCalls", 1);
        proof.addProperty("dependencyChecks", dependencyChecks);
        proof.addProperty("entityCrossedPhysicalSeam", crossed);
        proof.addProperty("entityRegistrations", registrations);
        proof.addProperty("entityCallbacks", entityCallbacks);
        proof.addProperty("fluidCrossedPhysicalSeam", true);
        proof.addProperty("blockCallbacks", blockCallbacks);
        proof.addProperty("fluidCallbacks", fluidCallbacks);
        proof.addProperty("furnaceCallbacks", furnaceCallbacks);
        proof.addProperty("furnaceCooked", true);
        proof.addProperty("targetTicketLevel", first.getTicketLevel());
        proof.addProperty("neighborTicketLevel", second.getTicketLevel());
        proof.add("stablePhysicalMetrics", new Gson().toJsonTree(baseline));
        var runtime = MosaicWorldIdentity.runtimeContext(level);
        if (runtime.isPresent()) {
            long seed = runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), owner);
            long other = runtime.orElseThrow().resolveLocalWorldSeed(level.dimension(), adjacent);
            require(seed != other, "Runtime seam does not have different local seeds");
            proof.addProperty("ownerLocalSeed", seed);
            proof.addProperty("adjacentLocalSeed", other);
        }
        marker.discard();
        be.clearContent();
        ORIGINALS.forEach((pos, block) -> level.setBlock(pos, block, 3));
        ORIGINALS.clear();
        close();
        proof.addProperty("probeTicketsRemoved", !held);
        proof.addProperty("generationBindings", FeatureExecutionScope.bindingCount());
        result.add("runtimeProof", proof);
        finished = true;
        return true;
    }

    private static int verifyDependencies() {
        Map<ChunkPos, ChunkStatus> required = new HashMap<>();
        boolean reload = "reload".equals(System.getProperty(NativePhysicalProbe.PREFIX + "role"));
        for (int z = owner.z - 3; z <= owner.z + 3; z++)
            for (int x = owner.x - 3; x <= owner.x + 3; x++) {
                var position = new ChunkPos(x, z);
                if (reload) required.put(position, ChunkStatus.FULL);
                else
                    MosaicPhysicalGenerationPlan.derive(position, ChunkStatus.FULL)
                            .requirements()
                            .forEach(
                                    (pos, status) ->
                                            required.merge(
                                                    pos,
                                                    status,
                                                    (old, next) ->
                                                            old.isOrAfter(next) ? old : next));
            }
        int checked = 0;
        var map = (PhysicalHolderAccess) level.getChunkSource().chunkMap;
        for (var entry : required.entrySet()) {
            if (!entry.getValue().isOrAfter(ChunkStatus.BIOMES)) continue;
            var holder = map.mosaic$holder(entry.getKey().toLong());
            require(holder != null, "Runtime lacks physical dependency holder " + entry.getKey());
            var future = holder.getFutureIfPresentUnchecked(entry.getValue()).getNow(null);
            require(
                    future != null
                            && future.left().isPresent()
                            && future.left()
                                    .orElseThrow()
                                    .getStatus()
                                    .isOrAfter(ChunkStatus.BIOMES),
                    "Runtime lacks real physical biome dependency " + entry.getKey());
            checked++;
        }
        require(checked > 0, "No native runtime dependencies checked");
        return checked;
    }

    private static void installFixtures() {
        idleBefore = ((RuntimeIdleAccess) level).mosaic$emptyTime();
        level.resetEmptyTime();
        int x = owner.getMaxBlockX(), z = owner.getMinBlockZ() + 5;
        int y = level.getSeaLevel();
        for (int dx = -2; dx <= 9; dx++)
            for (int dz = -2; dz <= 2; dz++)
                y = Math.max(y, level.getHeight(Heightmap.Types.WORLD_SURFACE, x + dx, z + dz));
        y += 8;
        require(y + 4 < level.getMaxBuildHeight(), "No safe runtime fixture height");
        for (int dx = -2; dx <= 9; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -1; dy <= 2; dy++) {
                    var pos = new BlockPos(x + dx, y + dy, z + dz);
                    require(level.getBlockEntity(pos) == null, "Would replace canonical BE");
                    boolean wall = dx == -2 || dx == 9 || dz == -2 || dz == 2;
                    put(
                            pos,
                            dy == -1 || (wall && dy < 2)
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
        require(be != null, "Native furnace was not registered");
        be.setItem(0, new ItemStack(Items.COBBLESTONE));
        be.setItem(1, new ItemStack(Items.COAL));
        marker = new ItemEntity(level, x + .5D, y + 1.5D, z + 1.5D, new ItemStack(Items.PAPER));
        marker.setNoGravity(true);
        marker.setDeltaMovement(.3D, 0D, 0D);
        require(
                level.noCollision(marker) && level.addFreshEntity(marker),
                "Native item could not register");
    }

    private static void put(BlockPos pos, BlockState block) {
        ORIGINALS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, block, 3);
    }

    public static void close() {
        if (held) {
            level.getChunkSource().removeRegionTicket(TicketType.PLAYER, owner, 3, owner);
            held = false;
        }
    }

    public static void blockTick(ServerLevel host, BlockPos pos) {
        if (host == level && pos.equals(blockTick)) {
            require(host.getServer().isSameThread(), "Worker executed a native block tick");
            blockCallbacks++;
        }
    }

    public static void fluidTick(ServerLevel host, BlockPos pos) {
        if (host == level && pos.equals(fluidSource)) {
            require(host.getServer().isSameThread(), "Worker executed a native fluid tick");
            fluidCallbacks++;
        }
    }

    public static void furnaceTick(Level host, BlockPos pos) {
        if (host == level && pos.equals(furnace)) {
            require(level.getServer().isSameThread(), "Worker executed a native BE tick");
            furnaceCallbacks++;
        }
    }

    public static void entityTick(ServerLevel host, Entity entity) {
        if (host == level && entity == marker) {
            require(host.getServer().isSameThread(), "Worker executed a native entity tick");
            entityCallbacks++;
            crossed |= entity.chunkPosition().equals(new ChunkPos(owner.x + 1, owner.z));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
