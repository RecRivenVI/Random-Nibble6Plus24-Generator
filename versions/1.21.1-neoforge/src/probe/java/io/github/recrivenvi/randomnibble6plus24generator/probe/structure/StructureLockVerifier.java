package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.StructureTemplateRepositoryAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureInputChunk;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Cold-cache, five-job structure comparison; no physical generation or FEATURES execution. */
final class StructureLockVerifier {
    private static final String PREFIX = ModConstants.MOD_ID + ".structureInputs.";
    private static final String FLAG = ModConstants.MOD_ID + ".structure.narrowLocks";

    private StructureLockVerifier() {}

    static void run(MinecraftServer server) {
        String order = System.getProperty(PREFIX + "lockOrder");
        if (!Set.of("candidate_first", "reference_first").contains(order))
            throw new IllegalArgumentException("Unknown cold-cache order");
        Path root =
                Path.of(System.getProperty("validation.result"))
                        .resolve("narrow_lock_concurrency")
                        .resolve(System.getProperty("validation.target"));
        Path output = root.resolve(order);
        String original = System.getProperty(FLAG);
        JsonObject proof = new JsonObject();
        proof.addProperty("status", "FAIL");
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("inputHash", System.getProperty(PREFIX + "inputHash"));
        proof.addProperty("order", order);
        proof.addProperty("processors", Runtime.getRuntime().availableProcessors());
        proof.addProperty("hostSeed", server.getWorldData().worldGenOptions().seed());
        Throwable primaryFailure = null;
        try {
            Files.createDirectories(output);
            var fixtures =
                    JsonParser.parseString(Files.readString(root.resolve("fixtures.json")))
                            .getAsJsonArray()
                            .asList()
                            .stream()
                            .map(JsonElement::getAsJsonObject)
                            .filter(row -> row.get("id").getAsString().endsWith("-start"))
                            .toList();
            if (fixtures.size() != 7)
                throw new IllegalStateException("Missing seven native positives");
            var templates =
                    ((StructureTemplateRepositoryAccess) server.getStructureManager())
                            .mosaic$repository();
            if (!templates.isEmpty())
                throw new IllegalStateException("Template cache was already warm");
            proof.addProperty("templateCacheInitiallyEmpty", true);
            int[] loaded =
                    levels(server).stream()
                            .mapToInt(l -> l.getChunkSource().getLoadedChunksCount())
                            .toArray();
            boolean candidateFirst = order.equals("candidate_first");
            setMode(candidateFirst);
            var executor = Executors.newFixedThreadPool(5);
            var gate = new CountDownLatch(1);
            var active = new AtomicInteger();
            var maximum = new AtomicInteger();
            var futures = new ArrayList<CompletableFuture<CompoundTag>>();
            try {
                for (int repeat = 0; repeat < 3; repeat++)
                    for (var fixture : fixtures)
                        futures.add(
                                CompletableFuture.supplyAsync(
                                        () -> {
                                            try {
                                                gate.await();
                                                int running = active.incrementAndGet();
                                                maximum.accumulateAndGet(running, Math::max);
                                                try {
                                                    return capture(server, fixture, candidateFirst);
                                                } finally {
                                                    active.decrementAndGet();
                                                }
                                            } catch (InterruptedException interrupted) {
                                                Thread.currentThread().interrupt();
                                                throw new CompletionException(interrupted);
                                            }
                                        },
                                        executor));
                gate.countDown();
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } finally {
                gate.countDown();
                executor.shutdown();
            }
            if (active.get() != 0 || maximum.get() < 2)
                throw new IllegalStateException("Parallel inputs were not actually concurrent");
            // The full future barrier above precedes changing this test-only property.
            setMode(!candidateFirst);
            var serial = fixtures.stream().map(f -> capture(server, f, !candidateFirst)).toList();
            for (int i = 0; i < futures.size(); i++) {
                var actual = futures.get(i).join();
                var expected = serial.get(i % fixtures.size());
                if (!actual.equals(expected)) {
                    NbtIo.writeCompressed(actual, output.resolve("first-actual.nbt.gz"));
                    NbtIo.writeCompressed(expected, output.resolve("first-expected.nbt.gz"));
                    throw new IllegalStateException(
                            "Concurrent structure projection differs: "
                                    + fixtures.get(i % fixtures.size()).get("id"));
                }
            }
            if (!Arrays.equals(
                    loaded,
                    levels(server).stream()
                            .mapToInt(l -> l.getChunkSource().getLoadedChunksCount())
                            .toArray()))
                throw new IllegalStateException("Structure-only comparison loaded physical chunks");
            JsonArray hashes = new JsonArray();
            for (var tag : serial) {
                byte[] bytes =
                        NativeStructureVerifier.canonicalNbt(tag)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                hashes.add(
                        HexFormat.of()
                                .formatHex(
                                        java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(bytes)));
            }
            proof.add("projections", hashes);
            proof.addProperty("status", "PASS");
            proof.addProperty("structures", fixtures.size());
            proof.addProperty("parallelComparisons", futures.size());
            proof.addProperty("maxConcurrentJobs", maximum.get());
            proof.addProperty("physicalChunksUnchanged", true);
            proof.addProperty("featuresExecuted", 0);
        } catch (Throwable failure) {
            primaryFailure = failure;
            proof.addProperty("failure", failure.toString());
            throw new IllegalStateException("Cold native structure comparison failed", failure);
        } finally {
            if (original == null) System.clearProperty(FLAG);
            else System.setProperty(FLAG, original);
            try {
                NativeStructureVerifier.write(output.resolve("summary.json"), proof);
            } catch (Exception failure) {
                if (primaryFailure != null) primaryFailure.addSuppressed(failure);
                else throw new IllegalStateException("Cannot save cold structure proof", failure);
            } finally {
                server.halt(false);
            }
        }
    }

    private static void setMode(boolean narrow) {
        if (narrow) System.setProperty(FLAG, "true");
        else System.clearProperty(FLAG);
    }

    private static List<ServerLevel> levels(MinecraftServer server) {
        return List.of(
                Objects.requireNonNull(server.overworld()),
                Objects.requireNonNull(server.getLevel(net.minecraft.world.level.Level.NETHER)),
                Objects.requireNonNull(server.getLevel(net.minecraft.world.level.Level.END)));
    }

    private static CompoundTag capture(MinecraftServer server, JsonObject fixture, boolean narrow) {
        ServerLevel host =
                Objects.requireNonNull(
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION,
                                        ResourceLocation.parse(
                                                fixture.get("dimension").getAsString()))));
        var position = new ChunkPos(fixture.get("x").getAsInt(), fixture.get("z").getAsInt());
        try (var context =
                new StructureGenerationContext(host, fixture.get("local").getAsLong(), position)) {
            if (context.narrowTemplateLocks() != narrow)
                throw new IllegalStateException(
                        "Structure lock guard did not select the expected mode");
            var inputs = CompactStructureInputs.resolve(context);
            if (inputs.metrics().virtualStorageScans() != 0)
                throw new IllegalStateException("Structure comparison scanned physical storage");
            var tags = inputs.captureStartTags(StructurePieceSerializationContext.fromLevel(host));
            if (tags.isEmpty()) throw new IllegalStateException("Positive start is empty");
            var carrier = new StructureInputChunk(position, host) {};
            inputs.copyReferencesTo(carrier);
            CompoundTag result = new CompoundTag(), references = new CompoundTag();
            ListTag starts = new ListTag();
            tags.forEach(starts::add);
            carrier.getAllReferences()
                    .forEach(
                            (structure, positions) ->
                                    references.putLongArray(
                                            host.registryAccess()
                                                    .registryOrThrow(Registries.STRUCTURE)
                                                    .getKey(structure)
                                                    .toString(),
                                            positions.toLongArray()));
            result.put("starts", starts);
            result.put("references", references);
            return result;
        }
    }
}
