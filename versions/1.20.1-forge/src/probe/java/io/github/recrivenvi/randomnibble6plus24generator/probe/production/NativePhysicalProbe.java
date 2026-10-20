package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.*;
import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkSerializerAccess;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Ordinary native Chunk futures only; no generation stage or conversion is invoked by this probe.
 */
public final class NativePhysicalProbe {
    public static final String PREFIX = ModConstants.MOD_ID + ".nativePhysical.";
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("mosaic_physical_probe", Comparator.comparingLong(ChunkPos::toLong));
    private static final AtomicInteger ROOT_GENERATIONS = new AtomicInteger();
    private static final java.util.concurrent.atomic.LongAdder
            RING_CONTEXTS = new java.util.concurrent.atomic.LongAdder(),
            RING_SLOTS = new java.util.concurrent.atomic.LongAdder(),
            RING_SEARCHES = new java.util.concurrent.atomic.LongAdder();
    private static final Map<ChunkPos, CanonicalChunkArtifact> ARTIFACTS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final JsonObject proof = new JsonObject();
    private static ServerLevel level;
    private static ChunkPos position;
    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> request;
    private static Path folder, common;
    private static boolean done, held;
    private static int ticketRadius;
    private static ChunkStatus requestedStatus;
    private static long started;

    private NativePhysicalProbe() {}

    public static boolean enabled() {
        return "integration-legacy_physical".equals(System.getProperty("validation.name"));
    }

    public static void ringContextClosed(
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                            .StructureGenerationContext
                    context) {
        if (!enabled() || !Boolean.getBoolean(ModConstants.MOD_ID + ".structure.lazyRings")) return;
        var scope =
                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                        .ConcentricRingStateAccess)
                                context.state())
                        .mosaic$ringScope();
        if (scope == null) throw new IllegalStateException("Physical candidate silently fell back");
        var metrics = scope.metrics();
        if (metrics.activeSuppliers() != 0)
            throw new IllegalStateException("Closed context retained ring suppliers");
        RING_CONTEXTS.increment();
        RING_SLOTS.add(metrics.slotsConsidered());
        RING_SEARCHES.add(metrics.biomeSearches());
    }

    public static void started(MinecraftServer server) {
        try {
            String dimension = System.getProperty(PREFIX + "dimension", "minecraft:overworld");
            level =
                    Objects.requireNonNull(
                            server.getLevel(
                                    switch (dimension) {
                                        case "minecraft:overworld" -> Level.OVERWORLD;
                                        case "minecraft:the_nether" -> Level.NETHER;
                                        case "minecraft:the_end" -> Level.END;
                                        default -> throw new IllegalArgumentException(dimension);
                                    }));
            position =
                    new ChunkPos(
                            Integer.getInteger(PREFIX + "x", 125),
                            Integer.getInteger(PREFIX + "z", -37));
            String pipeline = System.getProperty(PREFIX + "pipeline", "b");
            String role = System.getProperty(PREFIX + "role", "fresh");
            String variant = System.getProperty(PREFIX + "variant", "current");
            if (!variant.matches("[a-z0-9_]+"))
                throw new IllegalArgumentException("Unsafe physical proof variant");
            if (!Set.of("b", "vanilla").contains(pipeline)
                    || !Set.of("fresh", "resume", "reload").contains(role)
                    || (role.equals("resume") && !PartialPreLightProbe.enabled()))
                throw new IllegalArgumentException("Unknown native physical control");
            common =
                    Path.of(System.getProperty("validation.result"))
                            .resolve(variant)
                            .resolve(System.getProperty("validation.target"))
                            .resolve(pipeline)
                            .resolve(dimension.replace(':', '-'));
            folder = common.resolve(role);
            Files.createDirectories(folder);
            proof.addProperty("schema", 1);
            proof.addProperty("status", "REQUESTED");
            proof.addProperty(
                    "ringCandidate",
                    Boolean.getBoolean(ModConstants.MOD_ID + ".structure.lazyRings"));
            boolean physicalScope =
                    ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                                    .ConcentricRingStateAccess)
                                            level.getChunkSource().getGeneratorState())
                                    .mosaic$ringScope()
                            != null;
            proof.addProperty("physicalRingScopeBound", physicalScope);
            if (physicalScope)
                throw new IllegalStateException("Native physical state acquired ring scope");
            proof.addProperty(
                    "scope",
                    Boolean.getBoolean(PREFIX + "observationFailure")
                            ? "PROBE_OBSERVATION_FAILURE_NOT_GENERATION_FAILURE"
                            : "ORDINARY_NATIVE_FULL_NOT_PLAYER_ENTRY");
            proof.addProperty(
                    "inputHash", Objects.requireNonNull(System.getProperty(PREFIX + "inputHash")));
            proof.addProperty("target", System.getProperty("validation.target"));
            proof.addProperty("pipeline", pipeline);
            proof.addProperty("role", role);
            proof.addProperty("variant", variant);
            proof.addProperty("dimension", dimension);
            proof.addProperty("x", position.x);
            proof.addProperty("z", position.z);
            proof.addProperty("pid", ProcessHandle.current().pid());
            proof.addProperty(
                    "save",
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .toAbsolutePath()
                            .normalize()
                            .toString());
            proof.addProperty("processors", Runtime.getRuntime().availableProcessors());
            proof.addProperty(
                    "minecraft", net.minecraft.SharedConstants.getCurrentVersion().getName());
            var mods = new JsonObject();
            net.minecraftforge.fml.ModList.get()
                    .getMods()
                    .forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
            for (var id : mods.keySet())
                require(
                        Set.of(
                                        "minecraft",
                                        "forge",
                                        "javafml",
                                        "mclanguage",
                                        "lowcodefml",
                                        ModConstants.MOD_ID,
                                        ModConstants.MOD_ID + "_probe")
                                .contains(id),
                        "Unexpected physical-control mod " + id);
            proof.add("mods", mods);
            proof.addProperty("coldLoadedChunks", level.getChunkSource().getLoadedChunksCount());
            require(level.getChunkSource().getLoadedChunksCount() == 0, "Probe did not begin cold");
            var runtime = MosaicWorldIdentity.runtimeContext(level);
            require(runtime.isPresent() == pipeline.equals("b"), "Wrong production world identity");
            long master = server.getWorldData().worldGenOptions().seed();
            long seed =
                    runtime.isPresent()
                            ? runtime.orElseThrow()
                                    .resolveLocalWorldSeed(level.dimension(), position)
                            : master;
            proof.addProperty("masterSeed", master);
            proof.addProperty("localSeed", seed);
            require(
                    !pipeline.equals("b") || seed != master || position.equals(new ChunkPos(0, 0)),
                    "Local seed unexpectedly equals master");
            PartialPreLightProbe.started(level, position, common, role, proof);
            requestedStatus =
                    PartialPreLightProbe.staging()
                            ? ChunkStatus.INITIALIZE_LIGHT
                            : ChunkStatus.FULL;
            ticketRadius =
                    ChunkLevel.byStatus(FullChunkStatus.FULL)
                            - ChunkLevel.byStatus(requestedStatus);
            proof.addProperty("requestedStatus", requestedStatus.toString());
            proof.addProperty("ticketLevel", ChunkLevel.byStatus(requestedStatus));
            started = System.nanoTime();
            if (!NativePhysicalTargets.enabled()) {
                level.getChunkSource().addRegionTicket(TICKET, position, ticketRadius, position);
                held = true;
            }
            NativeRuntimeProbe.start(level, position);
            // This version's public API managed-blocks on the server thread. Submit from a
            // caller thread so native scheduling can continue while the probe polls its bound.
            request =
                    NativePhysicalTargets.enabled()
                            ? NativePhysicalTargets.start(level, position)
                            : NativePhysicalRequests.submit(level, position, requestedStatus);
            write();
        } catch (Throwable error) {
            failed(server, error);
        }
    }

    public static void generated(ChunkPos target, CanonicalChunkArtifact artifact) {
        if (!enabled() || position == null) return;
        if (ARTIFACTS.putIfAbsent(target, artifact) != null)
            throw new IllegalStateException("Physical owner generated twice " + target);
        if (!position.equals(target)) return;
        if (ROOT_GENERATIONS.incrementAndGet() != 1)
            throw new IllegalStateException("Root canonical generation repeated");
        try {
            Files.write(folder.resolve("root-artifact.nbt"), artifact.encodedPayloadCopy());
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    public static void tick(MinecraftServer server) {
        if (done || request == null) return;
        try {
            NativePhysicalTargets.advance();
            if (Boolean.getBoolean(PREFIX + "fullWaitEvidence"))
                NativeFullWaitEvidence.observe(level, started, folder, request);
            if (Boolean.getBoolean(PREFIX + "observationFailure"))
                throw new IllegalStateException("Injected native physical observation failure");
            if (!request.isDone()) {
                if (System.nanoTime() - started > 300_000_000_000L)
                    throw new IllegalStateException(
                            "Native physical FULL observation exceeded 300 seconds");
                return;
            }
            var access =
                    request.join()
                            .left()
                            .orElseThrow(
                                    () -> new IllegalStateException("Native FULL became unloaded"));
            boolean staging = PartialPreLightProbe.staging();
            require(
                    staging
                            ? access instanceof ProtoChunk
                                    && access.getStatus() == ChunkStatus.INITIALIZE_LIGHT
                            : access instanceof LevelChunk
                                    && access.getStatus() == ChunkStatus.FULL
                                    && access.isLightCorrect(),
                    "No real requested native Chunk");
            var chunk = access;
            if (!staging && !NativeRuntimeProbe.advance((LevelChunk) chunk, started, proof)) return;
            var again =
                    level.getChunkSource()
                            .getChunkFuture(position.x, position.z, requestedStatus, true);
            require(
                    again.isDone() && again.join().left().orElseThrow() == chunk,
                    "Repeated request did not return the same physical Chunk");
            var owner = (MosaicPhysicalLevelOwner) level;
            boolean mosaic = proof.get("pipeline").getAsString().equals("b");
            boolean reload = !proof.get("role").getAsString().equals("fresh");
            boolean resume = proof.get("role").getAsString().equals("resume");
            var counts = owner.mosaic$physicalState().metrics();
            proof.add("beforeSave", new Gson().toJsonTree(counts));
            if (mosaic && !reload) {
                require(
                        counts.generations() > 0
                                && counts.publications() == counts.generations()
                                && counts.poi() == counts.publications()
                                && (staging
                                        ? counts.spawn() == 0
                                                && counts.light() == 0
                                                && counts.full() == 0
                                        : counts.spawn() > 0
                                                && counts.light() > 0
                                                && counts.full() > 0),
                        "Missing actual production stages");
                require(ROOT_GENERATIONS.get() == 1, "Root generation was not exactly once");
            } else if (mosaic && resume)
                require(
                        ROOT_GENERATIONS.get() == 0,
                        "Saved partial target regenerated canonical data");
            else
                require(
                        counts.generations() == 0
                                && counts.publications() == 0
                                && counts.spawn() == 0,
                        "Saved/Vanilla world entered canonical generation");
            var marker =
                    new BlockPos(
                            position.getMinBlockX() + 8,
                            level.getMaxBuildHeight() - 4,
                            position.getMinBlockZ() + 8);
            if (mosaic) verifyPhysicalBiomes(chunk, reload);
            var structures =
                    ChunkSerializerAccess.packStructures(
                            StructurePieceSerializationContext.fromLevel(level),
                            position,
                            chunk.getAllStarts(),
                            chunk.getAllReferences());
            if (reload) {
                require(
                        chunk.getBlockState(marker).is(Blocks.GOLD_BLOCK),
                        "Saved marker was overwritten");
                require(
                        structures.equals(
                                NbtIo.readCompressed(common.resolve("structure.nbt").toFile())),
                        "Structure NBT changed on reload");
            } else {
                if (staging)
                    chunk.setBlockState(marker, Blocks.GOLD_BLOCK.defaultBlockState(), false);
                else
                    require(
                            level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(), 2),
                            "Could not write physical marker");
                NbtIo.writeCompressed(structures, common.resolve("structure.nbt").toFile());
            }
            PartialPreLightProbe.completed(chunk, proof);
            NativePhysicalTargets.completed(common, reload, ARTIFACTS, proof);
            proof.addProperty("markerPresent", chunk.getBlockState(marker).is(Blocks.GOLD_BLOCK));
            proof.addProperty("lightCorrect", chunk.isLightCorrect());
            proof.addProperty("fullStatus", chunk.getStatus().toString());
            proof.addProperty("rootGenerations", ROOT_GENERATIONS.get());
            if (!NativePhysicalTargets.enabled())
                proof.add("requestBurst", NativePhysicalRequests.evidence());
            proof.addProperty("elapsedNanos", System.nanoTime() - started);
            proof.addProperty("status", "PENDING_NATIVE_STOP");
            done = true;
            if (held) {
                level.getChunkSource().removeRegionTicket(TICKET, position, ticketRadius, position);
                held = false;
            }
            server.saveEverything(false, true, true);
            write();
            server.halt(false);
        } catch (Throwable error) {
            failed(server, error);
        }
    }

    public static void stopped(MinecraftServer server) {
        if (!enabled() || folder == null) return;
        try {
            NativeRuntimeProbe.close();
            NativePhysicalTargets.close();
            var owner = (MosaicPhysicalLevelOwner) level;
            require(owner.mosaic$physicalState().metrics().inFlight() == 0, "In-flight map leaked");
            require(
                    owner.randomnibble6plus24generator$generationLifecycle().quiescent(),
                    "Workers leaked");
            require(
                    FeatureExecutionScope.bindingCount() == 0
                            && !SeedLocalRegionScope.isBound()
                            && SpawnConstructorScope.current() == null,
                    "Generation scope leaked");
            proof.add("afterStop", new Gson().toJsonTree(owner.mosaic$physicalState().metrics()));
            proof.add(
                    "lifecycle",
                    new Gson()
                            .toJsonTree(
                                    owner.randomnibble6plus24generator$generationLifecycle()
                                            .snapshot()));
            proof.addProperty("bindings", FeatureExecutionScope.bindingCount());
            proof.addProperty("ringContexts", RING_CONTEXTS.sum());
            proof.addProperty("ringSlots", RING_SLOTS.sum());
            proof.addProperty("ringSearches", RING_SEARCHES.sum());
            PartialPreLightProbe.closed(proof);
            require(
                    proof.get("status").getAsString().equals("PENDING_NATIVE_STOP"),
                    "Native stop followed an incomplete/failed request");
            proof.addProperty("status", "PASS");
            write();
        } catch (Throwable error) {
            proof.addProperty("status", "FAIL");
            proof.addProperty(proof.has("failure") ? "stopFailure" : "failure", error.toString());
            write();
        } finally {
            NativeFullWaitEvidence.close();
            NativePhysicalTargets.reset();
            ARTIFACTS.clear();
            level = null;
            request = null;
        }
    }

    private static void failed(MinecraftServer server, Throwable error) {
        error.printStackTrace();
        done = true;
        proof.addProperty("status", "FAIL");
        proof.addProperty(proof.has("failure") ? "additionalFailure" : "failure", error.toString());
        if (folder != null) write();
        NativeRuntimeProbe.close();
        NativePhysicalTargets.close();
        if (held && level != null) {
            level.getChunkSource().removeRegionTicket(TICKET, position, ticketRadius, position);
            held = false;
        }
        server.halt(false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void verifyPhysicalBiomes(ChunkAccess target, boolean reload)
            throws java.io.IOException {
        var registry =
                level.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var codec = ChunkSerializerAccess.biomes(registry);
        CompoundTag saved =
                reload
                        ? NbtIo.readCompressed(common.resolve("biome-frontier.nbt").toFile())
                        : new CompoundTag();
        Map<ChunkPos, ChunkStatus> requirements;
        var heldTargets =
                NativePhysicalTargets.enabled()
                        ? NativePhysicalTargets.completedChunks()
                        : Map.<ChunkPos, ChunkAccess>of();
        if (!heldTargets.isEmpty()) {
            var fullTargets = new LinkedHashMap<ChunkPos, ChunkStatus>();
            heldTargets.keySet().forEach(pos -> fullTargets.put(pos, ChunkStatus.FULL));
            requirements = fullTargets;
        } else if (PartialPreLightProbe.enabled()) requirements = Map.of(position, requestedStatus);
        else if (reload && NativeRuntimeProbe.enabled()) {
            var fullFrontier = new LinkedHashMap<ChunkPos, ChunkStatus>();
            for (int z = position.z - 3; z <= position.z + 3; z++)
                for (int x = position.x - 3; x <= position.x + 3; x++)
                    fullFrontier.put(new ChunkPos(x, z), ChunkStatus.FULL);
            requirements = fullFrontier;
        } else
            requirements =
                    reload
                            ? Map.of(position, ChunkStatus.FULL)
                            : MosaicPhysicalGenerationPlan.derive(position, ChunkStatus.FULL)
                                    .requirements();
        long checks = 0;
        int owners = 0;
        for (var entry : requirements.entrySet()) {
            if (!entry.getValue().isOrAfter(ChunkStatus.BIOMES)) continue;
            var pos = entry.getKey();
            ChunkAccess actual;
            if (!heldTargets.isEmpty()) actual = heldTargets.get(pos);
            else if (reload)
                actual =
                        NativeRuntimeProbe.enabled()
                                ? Objects.requireNonNull(
                                        level.getChunkSource().getChunkNow(pos.x, pos.z),
                                        "Reload lacks physical FULL biome owner " + pos)
                                : target;
            else {
                var holder =
                        ((io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin
                                                .PhysicalHolderAccess)
                                        level.getChunkSource().chunkMap)
                                .mosaic$holder(pos.toLong());
                require(holder != null, "Missing native BIOMES holder " + pos);
                actual =
                        holder.getFutureIfPresentUnchecked(entry.getValue())
                                .getNow(ChunkHolder.UNLOADED_CHUNK)
                                .left()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Unready native BIOMES input " + pos));
            }
            require(actual.getStatus().isOrAfter(ChunkStatus.BIOMES), "Fake BIOMES status " + pos);
            String key = Long.toString(pos.toLong());
            ListTag sections;
            if (reload) sections = saved.getList(key, Tag.TAG_COMPOUND);
            else {
                var artifact =
                        Objects.requireNonNull(
                                ARTIFACTS.get(pos),
                                "BIOMES owner has no canonical Artifact " + pos);
                sections = artifact.payloadCopy().getList("sections", Tag.TAG_COMPOUND);
                saved.put(key, sections.copy());
            }
            require(
                    sections.size() == actual.getSectionsCount(),
                    "Incomplete physical BIOMES sections");
            for (var tag : sections) {
                var section = (CompoundTag) tag;
                int y = section.getByte("Y") * 4;
                var expected =
                        codec.parse(NbtOps.INSTANCE, section.getCompound("biomes"))
                                .getOrThrow(
                                        false,
                                        message -> {
                                            throw new IllegalStateException(message);
                                        });
                for (int qy = 0; qy < 4; qy++)
                    for (int z = 0; z < 4; z++)
                        for (int x = 0; x < 4; x++) {
                            require(
                                    actual.getNoiseBiome(pos.x * 4 + x, y + qy, pos.z * 4 + z)
                                            .equals(expected.get(x, qy, z)),
                                    "Physical palette differs from detached canonical input "
                                            + pos);
                            checks++;
                        }
            }
            owners++;
        }
        require(checks > 0, "No physical BIOMES checked");
        if (!reload) NbtIo.writeCompressed(saved, common.resolve("biome-frontier.nbt").toFile());
        proof.addProperty("physicalBiomeOwners", owners);
        proof.addProperty("physicalBiomeChecks", checks);
    }

    private static void write() {
        NativeStructureVerifier.write(folder.resolve("summary.json"), proof);
    }
}
