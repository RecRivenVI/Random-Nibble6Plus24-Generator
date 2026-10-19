package io.github.recrivenvi.randomnibble6plus24generator.probe.terrain;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureFixtures;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/** Same-version independent physical Oracle; no entity or NBT field normalization. */
public final class NativeTerrainVerifier {
    private static final String PREFIX = ModConstants.MOD_ID + ".terrainInputs.";
    private static final List<ChunkStatus> STAGES =
            List.of(
                    ChunkStatus.STRUCTURE_STARTS,
                    ChunkStatus.STRUCTURE_REFERENCES,
                    ChunkStatus.BIOMES,
                    ChunkStatus.NOISE,
                    ChunkStatus.SURFACE,
                    ChunkStatus.CARVERS);

    private NativeTerrainVerifier() {}

    public static boolean enabled() {
        return "conformance-legacy_terrain".equals(System.getProperty("validation.name"));
    }

    public static void run(MinecraftServer server) {
        Path root =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                System.getProperty(
                                        PREFIX + "variant", "legacy_terrain_" + required("suite")))
                        .resolve(System.getProperty("validation.target"));
        try {
            // Includes complete registry values, tags and iteration order, with no tolerance.
            var hostEnvironment = NativeStructureVerifier.environment(server.overworld());
            var mods = hostEnvironment.getAsJsonObject("mods");
            for (String mod : mods.keySet())
                if (!Set.of(
                                "minecraft",
                                "forge",
                                "mclanguage",
                                "lowcodefml",
                                "javafml",
                                "mixinextras",
                                ModConstants.MOD_ID,
                                ModConstants.MOD_ID + "_probe")
                        .contains(mod))
                    throw new IllegalStateException("Unexpected Oracle mod " + mod);
            if (Boolean.getBoolean(PREFIX + "export")) {
                Files.createDirectories(root);
                if (required("suite").equals("structures"))
                    NativeStructureFixtures.export(server, root);
                else
                    NativeStructureVerifier.write(
                            root.resolve("fixtures.json"),
                            matrix(required("suite").equals("smoke")));
                NativeStructureVerifier.exportLauncher(root);
                if (Boolean.getBoolean(PREFIX + "ringProof"))
                    io.github.recrivenvi.randomnibble6plus24generator.probe.structure
                            .ConcentricRingProof.verify(server.overworld(), root);
                return;
            }
            String role = required("role"), fixture = required("fixture");
            if (!Set.of("native", "lightweight").contains(role) || !fixture.matches("[a-z0-9_-]+"))
                throw new IllegalArgumentException("Invalid native terrain fixture");
            var key =
                    ResourceKey.create(
                            Registries.DIMENSION, new ResourceLocation(required("dimension")));
            var level = Objects.requireNonNull(server.getLevel(key));
            var pos =
                    new ChunkPos(Integer.parseInt(required("x")), Integer.parseInt(required("z")));
            long local = Long.parseLong(required("local"));
            if (role.equals("native") != (local == level.getSeed()))
                throw new IllegalStateException("Independent host/local seed boundary failed");
            if (level.getGameTime() != 0)
                throw new IllegalStateException("Snapshot lifecycle must be before ticking");
            Path output = root.resolve(fixture).resolve(role);
            var result = new JsonObject();
            if (!Boolean.getBoolean(ModConstants.MOD_ID + ".structure.narrowLocks")
                    && (Object) level.getChunkSource().getGenerator()
                            instanceof
                            io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                    .NativeStructureSynchronization)
                throw new IllegalStateException(
                        "Disabled synchronization mixins were installed in the native control");
            result.addProperty("fixture", fixture);
            result.addProperty("role", role);
            result.addProperty("dimension", key.location().toString());
            result.addProperty("x", pos.x);
            result.addProperty("z", pos.z);
            result.addProperty("localSeed", Long.toString(local));
            result.addProperty("hostSeed", Long.toString(level.getSeed()));
            result.addProperty("pid", ProcessHandle.current().pid());
            result.addProperty(
                    "save",
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .toAbsolutePath()
                            .toString());
            result.addProperty("inputHash", required("inputHash"));
            result.add("environment", NativeStructureVerifier.environment(level));
            int loaded = level.getChunkSource().getLoadedChunksCount();
            var checkpoints = new JsonObject();
            long began = System.nanoTime();
            NoiseDensityEvidence.configure(output, pos);
            try {
                if (role.equals("native")) {
                    if (((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                                    .ConcentricRingStateAccess)
                                            level.getChunkSource().getGeneratorState())
                                    .mosaic$ringScope()
                            != null)
                        throw new IllegalStateException(
                                "Physical Native Oracle acquired a finite scope");
                    for (ChunkStatus status : STAGES) {
                        var chunk = level.getChunk(pos.x, pos.z, status, true);
                        if (chunk.getStatus() != status)
                            throw new IllegalStateException("Native stage escaped " + status);
                        checkpoint(output, level, chunk, null, status, checkpoints);
                    }
                    result.add(
                            "beardifier",
                            NativeStructureVerifier.beardifier(
                                    output, level, pos, level.structureManager()));
                    writeBiomes(output, level, pos, null);
                } else {
                    try (var context = new StructureGenerationContext(level, local, pos)) {
                        result.addProperty("narrowTemplateLocks", context.narrowTemplateLocks());
                        var inputs = CompactStructureInputs.resolve(context);
                        var generated = new LightweightTerrainGenerator(context, inputs);
                        var terrain =
                                generated.generate(
                                        (status, chunk) ->
                                                checkpoint(
                                                        output,
                                                        level,
                                                        chunk,
                                                        inputs,
                                                        status,
                                                        checkpoints));
                        result.add("terrainMetrics", new Gson().toJsonTree(terrain.metrics()));
                        var scope =
                                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                                        .structure.ConcentricRingStateAccess)
                                                context.state())
                                        .mosaic$ringScope();
                        if (scope != null)
                            result.add("ringScope", new Gson().toJsonTree(scope.metrics()));
                        result.add(
                                "beardifier",
                                NativeStructureVerifier.beardifier(
                                        output, level, pos, inputs.manager()));
                        writeBiomes(output, level, pos, generated);
                        // A real scoped failure must release the constructor-only routing binding.
                        var injected =
                                new IllegalStateException("probe region construction failure");
                        try {
                            SeedLocalRegionScope.construct(
                                    context,
                                    inputs.manager(),
                                    () -> {
                                        throw injected;
                                    });
                            throw new AssertionError("Injected failure was swallowed");
                        } catch (IllegalStateException failure) {
                            if (failure != injected) throw failure;
                        }
                    }
                    if (loaded != level.getChunkSource().getLoadedChunksCount())
                        throw new IllegalStateException(
                                "Lightweight terrain changed physical host chunks");
                }
                if (SeedLocalRegionScope.isBound())
                    throw new IllegalStateException("Region seed binding leaked");
                result.add("checkpoints", checkpoints);
                result.add(
                        "density",
                        JsonParser.parseString(
                                Files.readString(output.resolve("density-slices.json"))));
                result.addProperty("featuresExecuted", 0);
                result.addProperty("regionBindings", 0);
                if (role.equals("native")) result.addProperty("status", "PRODUCED");
                else {
                    Path control = root.resolve(fixture).resolve("native");
                    var previous =
                            JsonParser.parseString(
                                            Files.readString(control.resolve("summary.json")))
                                    .getAsJsonObject();
                    for (String field :
                            List.of(
                                    "fixture",
                                    "dimension",
                                    "x",
                                    "z",
                                    "localSeed",
                                    "inputHash",
                                    "environment",
                                    "checkpoints",
                                    "beardifier",
                                    "density"))
                        if (!NativeStructureVerifier.canonical(previous.get(field))
                                .toString()
                                .equals(
                                        NativeStructureVerifier.canonical(result.get(field))
                                                .toString()))
                            throw new IllegalStateException(
                                    "Independent native terrain differs " + field);
                    for (String file :
                            List.of(
                                    "biome-frontier.json.gz",
                                    "beardifier.bin",
                                    "density-slices.bin"))
                        if (Files.mismatch(control.resolve(file), output.resolve(file)) != -1)
                            throw new IllegalStateException("Native terrain input differs " + file);
                    result.addProperty("status", "PASS");
                }
            } catch (RuntimeException | Error | IOException failure) {
                result.add("checkpoints", checkpoints);
                result.addProperty("status", "FAIL");
                result.addProperty("failure", failure.toString());
                throw failure;
            } finally {
                result.addProperty("elapsedMs", (System.nanoTime() - began) / 1_000_000);
                NativeStructureVerifier.write(output.resolve("summary.json"), result);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } finally {
            NoiseDensityEvidence.clear();
            server.halt(false);
        }
    }

    private static void checkpoint(
            Path output,
            ServerLevel level,
            ChunkAccess chunk,
            CompactStructureInputs inputs,
            ChunkStatus status,
            JsonObject checkpoints) {
        try {
            String stage =
                    net.minecraft.core.registries.BuiltInRegistries.CHUNK_STATUS
                            .getKey(status)
                            .getPath();
            var tag = ChunkSerializer.write(level, chunk);
            Files.createDirectories(output);
            NbtIo.writeCompressed(tag, output.resolve(stage + ".raw.nbt.gz").toFile());
            String state = NativeStructureVerifier.canonicalNbt(tag);
            compressed(output.resolve(stage + ".canonical.gz"), state);
            var projection = NativeStructureVerifier.structures(level, chunk, inputs);
            NativeStructureVerifier.write(output.resolve(stage + "-structures.json"), projection);
            var row = new JsonObject();
            row.addProperty("chunkNbt", hash(state));
            row.addProperty(
                    "structures", hash(NativeStructureVerifier.canonical(projection).toString()));
            checkpoints.add(stage, row);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void writeBiomes(
            Path output, ServerLevel level, ChunkPos pos, LightweightTerrainGenerator generated)
            throws IOException {
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var result = new JsonObject();
        for (int z = pos.z - 1; z <= pos.z + 1; z++)
            for (int x = pos.x - 1; x <= pos.x + 1; x++) {
                var chunk =
                        generated == null
                                ? level.getChunk(x, z, ChunkStatus.BIOMES, true)
                                : generated.storedBiome(x, z);
                var samples = new JsonArray();
                var visible = new TreeSet<String>();
                for (int qy = QuartPos.fromBlock(level.getMinBuildHeight());
                        qy < QuartPos.fromBlock(level.getMaxBuildHeight());
                        qy++)
                    for (int qz = 0; qz < 4; qz++)
                        for (int qx = 0; qx < 4; qx++)
                            samples.add(
                                    registry.getKey(
                                                    chunk.getNoiseBiome(x * 4 + qx, qy, z * 4 + qz)
                                                            .value())
                                            .toString());
                java.util.function.Consumer<
                                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>
                        add = b -> visible.add(registry.getKey(b.value()).toString());
                if (chunk instanceof BiomePaletteChunk palette) palette.getAllBiomes(add);
                else for (var section : chunk.getSections()) section.getBiomes().getAll(add);
                var row = new JsonObject();
                row.add("samples", samples);
                row.add("visible", new Gson().toJsonTree(visible));
                result.add(x + "," + z, row);
            }
        compressed(
                output.resolve("biome-frontier.json.gz"),
                NativeStructureVerifier.canonical(result).toString());
    }

    private static JsonArray matrix(boolean smoke) {
        var rows = new JsonArray();
        var resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        if (resolver.resolveLocalWorldSeed(123456789L, Level.OVERWORLD, new ChunkPos(125, -37))
                        != -5161763991829980711L
                || resolver.resolveLocalWorldSeed(-987654321L, Level.END, new ChunkPos(-125, 37))
                        != -7286762380808216340L
                || resolver.resolveLocalWorldSeed(-1L, Level.NETHER, new ChunkPos(-1, 1))
                        != -4702794264821873409L)
            throw new IllegalStateException("Frozen Seed V1 vectors differ");
        long[] seeds = {
            0, 1, -1, 123456789, -987654321, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789ABCDEFL
        };
        for (long seed : seeds) add(rows, resolver, seed, Level.OVERWORLD, ChunkPos.ZERO);
        for (long seed : seeds)
            for (var pos :
                    List.of(
                            new ChunkPos(1, 0),
                            new ChunkPos(-1, 1),
                            new ChunkPos(125, -37),
                            new ChunkPos(-20000, 30000)))
                add(rows, resolver, seed, Level.OVERWORLD, pos);
        for (var dimension : List.of(Level.NETHER, Level.END))
            for (long seed : new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE})
                for (var pos :
                        List.of(
                                ChunkPos.ZERO,
                                new ChunkPos(1, -1),
                                new ChunkPos(-125, 37),
                                new ChunkPos(10000, -20000)))
                    add(rows, resolver, seed, dimension, pos);
        if (!smoke) return rows;
        var selected = new JsonArray();
        for (int index : new int[] {3, 22, 41, 57}) selected.add(rows.get(index));
        return selected;
    }

    private static void add(
            JsonArray rows,
            MosaicSeedResolver resolver,
            long master,
            ResourceKey<Level> dimension,
            ChunkPos pos) {
        var row = new JsonObject();
        row.addProperty("id", String.format(Locale.ROOT, "fixture-%02d", rows.size()));
        row.addProperty("master", Long.toString(master));
        row.addProperty(
                "local", Long.toString(resolver.resolveLocalWorldSeed(master, dimension, pos)));
        row.addProperty("dimension", dimension.location().toString());
        row.addProperty("x", pos.x);
        row.addProperty("z", pos.z);
        rows.add(row);
    }

    private static void compressed(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        try (var out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String required(String name) {
        return Objects.requireNonNull(System.getProperty(PREFIX + name), name);
    }
}
