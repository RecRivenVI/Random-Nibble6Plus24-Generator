package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapQueues;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.AsyncChunkRequestAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.SpawnConstructorScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/**
 * Observes ordinary ticket-backed FULL requests; it never drives an individual generation stage.
 */
public final class ProductionChunkVerifier {
    private static final String PREFIX = ModConstants.MOD_ID + ".phase3c2.production.";
    private static final List<CompletableFuture<ChunkResult<ChunkAccess>>> REQUESTS =
            new ArrayList<>();
    private static final List<ChunkPos> TICKETS = new ArrayList<>();
    private static ServerLevel level;
    private static long started;
    private static long nextProgress;
    private static boolean finished;

    private ProductionChunkVerifier() {}

    public static boolean enabled() {
        return "integration-production_lifecycle".equals(System.getProperty("validation.name"))
                && !System.getProperty(PREFIX + "mode", "").isBlank();
    }

    public static void tick(MinecraftServer server) {
        if (!enabled() || finished) return;
        try {
            var mode = System.getProperty(PREFIX + "mode");
            if (level == null) {
                PhysicalPlanEvidence.verify();
                if (!List.of("full", "reload", "concurrent", "patch", "pair").contains(mode))
                    throw new IllegalArgumentException("Unsupported production probe mode " + mode);
                level =
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION,
                                        ResourceLocation.parse(
                                                System.getProperty(PREFIX + "dimension"))));
                if (level == null) throw new IllegalStateException("Missing requested dimension");
                boolean vanilla = Boolean.getBoolean(PREFIX + "vanillaControl");
                if (MosaicWorldIdentity.runtimeContext(level).isEmpty() != vanilla)
                    throw new IllegalStateException("Wrong world identity for production control");
                var target = target();
                CompletionHandoffEvidence.start(level, target);
                List<ChunkPos> positions = new ArrayList<>();
                if (mode.equals("patch")) {
                    for (int z = -1; z <= 1; z++)
                        for (int x = -1; x <= 1; x++)
                            positions.add(new ChunkPos(target.x + x, target.z + z));
                } else {
                    positions.add(target);
                    if (mode.equals("pair")) positions.add(new ChunkPos(target.x + 1, target.z));
                }
                started = System.nanoTime();
                nextProgress = started;
                System.out.println("MOSAIC_PHYSICAL_PLAN " + PhysicalPlanEvidence.describe(target));
                for (var position : positions) {
                    TICKETS.add(position);
                    level.getChunkSource()
                            .addRegionTicket(TicketType.PLAYER, position, 0, position);
                    int repeats =
                            mode.equals("concurrent")
                                    ? Integer.getInteger(PREFIX + "repeats", 16)
                                    : 1;
                    for (int i = 0; i < repeats; i++)
                        REQUESTS.add(
                                ((AsyncChunkRequestAccess) level.getChunkSource())
                                        .mosaic$request(
                                                position.x, position.z, ChunkStatus.FULL, true));
                }
            }
            if (System.nanoTime() >= nextProgress) {
                var runtime = MosaicWorldIdentity.runtimeContext(level);
                var state =
                        runtime.isPresent()
                                ? ((MosaicPhysicalLevelOwner) level).mosaic$physicalState()
                                : null;
                System.out.println(
                        "MOSAIC_PHYSICAL_PROGRESS elapsedMillis="
                                + (System.nanoTime() - started) / 1_000_000L
                                + " requests="
                                + REQUESTS.size()
                                + " completed="
                                + REQUESTS.stream().filter(CompletableFuture::isDone).count()
                                + " partialSupported="
                                + (state != null && state.supportsPartialBiomes())
                                + " metrics="
                                + (state == null ? "Vanilla" : state.metrics()));
                nextProgress = System.nanoTime() + 30_000_000_000L;
            }
            if (System.nanoTime() - started > 180_000_000_000L)
                throw new IllegalStateException(
                        "Ordinary FULL request/publication did not complete within the smoke bound");
            if (REQUESTS.stream().anyMatch(future -> !future.isDone())) {
                return;
            }
            var chunks = new ArrayList<LevelChunk>();
            for (var future : REQUESTS) {
                var chunk =
                        future.join()
                                .orElseThrow(
                                        () -> new IllegalStateException("FULL request unloaded"));
                if (!(chunk instanceof LevelChunk full)
                        || full.getPersistedStatus() != ChunkStatus.FULL
                        || !full.isLightCorrect())
                    throw new IllegalStateException(
                            "Not a lit physical FULL chunk: " + chunk.getPos());
                chunks.add(full);
            }
            boolean vanilla = Boolean.getBoolean(PREFIX + "vanillaControl");
            var state = vanilla ? null : ((MosaicPhysicalLevelOwner) level).mosaic$physicalState();
            var metrics = state == null ? null : state.metrics();
            if (metrics != null && metrics.inFlight() != 0) return;
            if (FeatureExecutionScope.bindingCount() != 0
                    || SeedLocalRegionScope.isBound()
                    || SpawnConstructorScope.current() != null)
                throw new IllegalStateException("Generation scope leaked into physical runtime");
            boolean reload = mode.equals("reload");
            if (metrics != null
                    && (reload
                            ? metrics.artifacts() != 0 || metrics.spawn() != 0
                            : metrics.artifacts() < 1 || metrics.spawn() < 1))
                throw new IllegalStateException(
                        "Wrong canonical generation/reload counters " + metrics);
            var chunk = chunks.getFirst();
            int physicalBiomeChecks = 0;
            if (!vanilla) {
                var map = (ChunkMapQueues) level.getChunkSource().chunkMap;
                for (var position : TICKETS) {
                    var dependencies =
                            (reload
                                            ? ChunkPyramid.LOADING_PYRAMID
                                            : ChunkPyramid.GENERATION_PYRAMID)
                                    .getStepTo(ChunkStatus.FULL)
                                    .accumulatedDependencies();
                    for (int z = -dependencies.getRadius(); z <= dependencies.getRadius(); z++) {
                        for (int x = -dependencies.getRadius();
                                x <= dependencies.getRadius();
                                x++) {
                            var required = dependencies.get(Math.max(Math.abs(x), Math.abs(z)));
                            if (required.isBefore(ChunkStatus.BIOMES)) continue;
                            var requiredPos = new ChunkPos(position.x + x, position.z + z);
                            var holder = map.mosaic$holder(requiredPos.toLong());
                            var physical =
                                    holder == null
                                            ? null
                                            : holder.getChunkIfPresentUnchecked(ChunkStatus.BIOMES);
                            if (physical == null
                                    || physical.getPersistedStatus().isBefore(ChunkStatus.BIOMES))
                                throw new IllegalStateException(
                                        "Physical biome dependency has no generated palette: "
                                                + requiredPos
                                                + " required="
                                                + required);
                            // The planning assertion requires an actual generated result, not only
                            // a completed holder future whose persisted chunk is still EMPTY.
                            physicalBiomeChecks++;
                        }
                    }
                }
            }
            var marker =
                    new BlockPos(
                            target().getMinBlockX() + 8,
                            level.getMinBuildHeight() + 5,
                            target().getMinBlockZ() + 8);
            boolean expected = Boolean.getBoolean(PREFIX + "expectMarker");
            boolean present = chunk.getBlockState(marker).is(Blocks.DIAMOND_BLOCK);
            if (expected && !present)
                throw new IllegalStateException("Saved player/test mutation was overwritten");
            boolean written = Boolean.getBoolean(PREFIX + "mutate");
            if (written)
                chunk.setBlockState(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
            var result = new JsonObject();
            result.addProperty("status", "PASS");
            result.addProperty("normalVanilla", vanilla);
            result.addProperty("mode", mode);
            result.addProperty("dimension", level.dimension().location().toString());
            result.addProperty("target", target().toString());
            result.addProperty("pid", ProcessHandle.current().pid());
            result.addProperty("worldSeed", server.getWorldData().worldGenOptions().seed());
            var runtime = MosaicWorldIdentity.runtimeContext(level);
            result.addProperty(
                    "localWorldSeed",
                    runtime.map(
                                    context ->
                                            context.resolveLocalWorldSeed(
                                                    level.dimension(), target()))
                            .orElse(level.getSeed()));
            result.addProperty(
                    "featureOrderingVersion",
                    runtime.map(context -> context.profile().featureOrderingAlgorithmVersion())
                            .orElse(1));
            result.addProperty("fullChunkClass", chunk.getClass().getName());
            result.addProperty("fullStatus", chunk.getPersistedStatus().toString());
            result.addProperty("lightCorrect", chunk.isLightCorrect());
            result.addProperty("nativeFullChunkStatus", chunk.getFullStatus().name());
            result.addProperty("artifactGenerations", metrics == null ? 0 : metrics.artifacts());
            result.addProperty("spawnCalls", metrics == null ? 0 : metrics.spawn());
            result.addProperty("markerWritten", written);
            result.addProperty("markerPresent", present || written);
            result.addProperty("ordinaryRequests", REQUESTS.size());
            result.addProperty("physicalBiomeChecks", physicalBiomeChecks);
            result.add("physicalPlan", PhysicalPlanEvidence.describe(target()));
            result.addProperty("dependencyPyramid", reload ? "loading" : "generation");
            result.add("completionHandoff", CompletionHandoffEvidence.verify());
            if (mode.equals("concurrent")) {
                if (chunks.stream().anyMatch(other -> other != chunk))
                    throw new IllegalStateException(
                            "Concurrent native requests returned different physical chunks");
                result.addProperty("concurrentSamePhysicalChunk", true);
            }
            result.addProperty("requestReadyMillis", (System.nanoTime() - started) / 1_000_000.0);
            var development = new JsonObject();
            development.addProperty("generations", metrics == null ? 0 : metrics.artifacts());
            result.add("developmentB", development);
            if (metrics != null)
                result.add("physicalMetrics", new GsonBuilder().create().toJsonTree(metrics));
            RuntimeLifecycleEvidence.capture(result);
            Path output = Path.of(System.getProperty(PREFIX + "output"));
            Files.createDirectories(output.getParent());
            NbtIo.writeCompressed(
                    ChunkSerializer.write(level, chunk), Path.of(output + ".physical.nbt.gz"));
            Files.writeString(
                    output, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
            finished = true;
            for (var position : TICKETS)
                level.getChunkSource().removeRegionTicket(TicketType.PLAYER, position, 0, position);
            if (!RuntimeLifecycleEvidence.enabled()) server.halt(false);
        } catch (Throwable failure) {
            finished = true;
            var evidence = new JsonObject();
            evidence.addProperty("status", "FAIL");
            evidence.addProperty("failure", failure.toString());
            evidence.add("completionHandoff", CompletionHandoffEvidence.snapshot());
            if (level != null && MosaicWorldIdentity.runtimeContext(level).isPresent()) {
                var state = ((MosaicPhysicalLevelOwner) level).mosaic$physicalState();
                evidence.add(
                        "physicalMetrics", new GsonBuilder().create().toJsonTree(state.metrics()));
                evidence.addProperty("partialBiomesSupported", state.supportsPartialBiomes());
                evidence.add("physicalPlan", PhysicalPlanEvidence.describe(target()));
            }
            try {
                var output = Path.of(System.getProperty(PREFIX + "output"));
                Files.createDirectories(output.getParent());
                Files.writeString(output, evidence.toString() + "\n");
            } catch (Exception writing) {
                failure.addSuppressed(writing);
            }
            throw new IllegalStateException("Production lifecycle probe failed", failure);
        }
    }

    private static ChunkPos target() {
        return new ChunkPos(
                Integer.getInteger(PREFIX + "chunkX", 125),
                Integer.getInteger(PREFIX + "chunkZ", -37));
    }
}
