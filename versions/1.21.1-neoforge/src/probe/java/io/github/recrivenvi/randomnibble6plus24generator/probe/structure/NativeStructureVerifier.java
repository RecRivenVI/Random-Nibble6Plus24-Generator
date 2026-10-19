package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.fml.ModList;

/** Independent worlds compare exact native structure metadata, not approximate bounding volumes. */
public final class NativeStructureVerifier {
    private static final String PREFIX = ModConstants.MOD_ID + ".structureInputs.";

    private NativeStructureVerifier() {}

    public static boolean enabled() {
        return "conformance-structure_inputs".equals(System.getProperty("validation.name"));
    }

    public static void run(MinecraftServer server) {
        if (Boolean.getBoolean(PREFIX + "lockProof")) {
            StructureLockVerifier.run(server);
            return;
        }
        Path root =
                Path.of(System.getProperty("validation.result"))
                        .resolve("current")
                        .resolve(System.getProperty("validation.target"));
        try {
            for (var mod : ModList.get().getMods())
                if (!Set.of(
                                "minecraft",
                                "neoforge",
                                "javafml",
                                "mixinextras",
                                ModConstants.MOD_ID,
                                ModConstants.MOD_ID + "_probe")
                        .contains(mod.getModId()))
                    throw new IllegalStateException("Unexpected Oracle mod " + mod.getModId());
            if (Boolean.getBoolean(PREFIX + "export")) {
                NativeStructureFixtures.export(server, root);
                exportLauncher(root);
                return;
            }
            String role = required("role"), fixture = required("fixture");
            if (!Set.of("native", "compact").contains(role) || !fixture.matches("[a-z0-9_-]+"))
                throw new IllegalArgumentException("Invalid fixture role/id");
            Path output = root.resolve(fixture).resolve(role);
            var dimension =
                    ResourceKey.create(
                            Registries.DIMENSION, ResourceLocation.parse(required("dimension")));
            var level = Objects.requireNonNull(server.getLevel(dimension));
            var target =
                    new ChunkPos(Integer.parseInt(required("x")), Integer.parseInt(required("z")));
            long local = Long.parseLong(required("local")), host = level.getSeed();
            if (role.equals("native") != (local == host))
                throw new IllegalStateException("Independent local/host seed boundary failed");
            JsonObject result = new JsonObject();
            result.addProperty("fixture", fixture);
            result.addProperty("role", role);
            result.addProperty("dimension", dimension.location().toString());
            result.addProperty("x", target.x);
            result.addProperty("z", target.z);
            result.addProperty("localSeed", Long.toString(local));
            result.addProperty("hostSeed", Long.toString(host));
            result.addProperty("pid", ProcessHandle.current().pid());
            result.addProperty(
                    "save",
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .toAbsolutePath()
                            .toString());
            result.addProperty("inputHash", required("inputHash"));
            result.add("environment", environment(level));
            long began = System.nanoTime();
            try {
                CompactStructureInputs compact = null;
                ChunkAccess owner;
                StructureManager manager;
                if (role.equals("native")) {
                    owner =
                            level.getChunk(
                                    target.x, target.z, ChunkStatus.STRUCTURE_REFERENCES, true);
                    if (owner.getPersistedStatus() != ChunkStatus.STRUCTURE_REFERENCES)
                        throw new IllegalStateException("Native stage escaped references");
                    manager = level.structureManager();
                } else {
                    try (var context = new StructureGenerationContext(level, local, target)) {
                        compact = CompactStructureInputs.resolve(context);
                    }
                    result.add("compactInputs", new Gson().toJsonTree(compact.metrics()));
                    owner =
                            new ProtoChunk(
                                    target,
                                    UpgradeData.EMPTY,
                                    level,
                                    level.registryAccess().registryOrThrow(Registries.BIOME),
                                    null);
                    compact.copyStartsTo(owner);
                    compact.copyReferencesTo(owner);
                    ((ProtoChunk) owner).setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
                    manager = compact.manager();
                    var serialization = StructurePieceSerializationContext.fromLevel(level);
                    var first = compact.captureStartTags(serialization);
                    var before = first.stream().map(NativeStructureVerifier::canonicalNbt).toList();
                    if (!first.isEmpty())
                        first.getFirst().putString("probeMutation", "not-authority");
                    if (!before.equals(
                            compact.captureStartTags(serialization).stream()
                                    .map(NativeStructureVerifier::canonicalNbt)
                                    .toList()))
                        throw new IllegalStateException("Mutable structure NBT alias");
                }
                JsonObject structures = structures(level, owner, compact);
                write(output.resolve("structures.json"), structures);
                JsonObject beard = beardifier(output, level, target, manager);
                result.add("beardifier", beard);
                result.addProperty("featuresExecuted", 0);
                if (role.equals("native")) result.addProperty("status", "PRODUCED");
                else {
                    Path control = root.resolve(fixture).resolve("native");
                    var nativeSummary =
                            JsonParser.parseString(
                                            Files.readString(control.resolve("summary.json")))
                                    .getAsJsonObject();
                    for (String field :
                            List.of("environment", "inputHash", "dimension", "x", "z", "localSeed"))
                        if (!canonical(result.get(field))
                                .toString()
                                .equals(canonical(nativeSummary.get(field)).toString()))
                            throw new IllegalStateException("Oracle input differs " + field);
                    for (String file : List.of("structures.json", "beardifier.bin"))
                        if (Files.mismatch(control.resolve(file), output.resolve(file)) != -1)
                            throw new IllegalStateException(
                                    "Native structure input differs " + file);
                    result.addProperty("status", "PASS");
                }
            } catch (RuntimeException | Error | IOException failure) {
                result.addProperty("status", "FAIL");
                result.addProperty("failure", failure.toString());
                throw failure;
            } finally {
                result.addProperty("elapsedMs", (System.nanoTime() - began) / 1_000_000);
                write(output.resolve("summary.json"), result);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } finally {
            server.halt(false);
        }
    }

    public static JsonObject structures(
            net.minecraft.server.level.ServerLevel level,
            ChunkAccess owner,
            CompactStructureInputs compact) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        JsonObject starts = new JsonObject(),
                references = new JsonObject(),
                resolved = new JsonObject(),
                pieces = new JsonObject();
        owner.getAllStarts()
                .forEach(
                        (structure, start) -> {
                            starts.addProperty(
                                    registry.getKey(structure).toString(),
                                    canonicalNbt(start.createTag(serialization, owner.getPos())));
                            pieces.addProperty(
                                    registry.getKey(structure) + "@" + owner.getPos(),
                                    start.getPieces().size());
                        });
        owner.getAllReferences()
                .forEach(
                        (structure, positions) -> {
                            long[] keys = positions.toLongArray();
                            Arrays.sort(keys);
                            JsonArray list = new JsonArray();
                            for (long position : keys) {
                                list.add(position);
                                var pos = new ChunkPos(position);
                                var start =
                                        compact == null
                                                ? level.getChunk(
                                                                pos.x,
                                                                pos.z,
                                                                ChunkStatus.STRUCTURE_STARTS,
                                                                true)
                                                        .getStartForStructure(structure)
                                                : compact.referencedStart(structure, pos);
                                if (start == null || !start.isValid())
                                    throw new IllegalStateException(
                                            "Missing referenced native start");
                                resolved.addProperty(
                                        registry.getKey(structure) + "@" + pos,
                                        canonicalNbt(start.createTag(serialization, pos)));
                                pieces.addProperty(
                                        registry.getKey(structure) + "@" + pos,
                                        start.getPieces().size());
                            }
                            references.add(registry.getKey(structure).toString(), list);
                        });
        JsonObject result = new JsonObject();
        result.add("owner", starts);
        result.add("references", references);
        result.add("resolved", resolved);
        result.add("pieces", pieces);
        return result;
    }

    public static JsonObject beardifier(
            Path output,
            net.minecraft.server.level.ServerLevel level,
            ChunkPos target,
            StructureManager manager)
            throws IOException {
        var beard = Beardifier.forStructuresInChunk(manager, target);
        int nonzero = 0;
        MessageDigest digest = digest();
        Files.createDirectories(output);
        try (var stream =
                new DataOutputStream(
                        new DigestOutputStream(
                                Files.newOutputStream(output.resolve("beardifier.bin")), digest))) {
            for (int z = target.getMinBlockZ(); z <= target.getMaxBlockZ(); z++)
                for (int x = target.getMinBlockX(); x <= target.getMaxBlockX(); x++)
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        double value =
                                beard.compute(new DensityFunction.SinglePointContext(x, y, z));
                        stream.writeLong(Double.doubleToRawLongBits(value));
                        if (value != 0) nonzero++;
                    }
        }
        JsonObject result = new JsonObject();
        result.addProperty("sha256", HexFormat.of().formatHex(digest.digest()));
        result.addProperty("nonzero", nonzero);
        result.addProperty("samples", 256 * level.getHeight());
        return result;
    }

    public static JsonObject environment(net.minecraft.server.level.ServerLevel level) {
        JsonObject result = new JsonObject(), mods = new JsonObject();
        ModList.get()
                .getMods()
                .forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
        result.add("mods", mods);
        result.addProperty("java", Runtime.version().toString());
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        result.add(
                "generator",
                ChunkGenerator.CODEC
                        .encodeStart(ops, level.getChunkSource().getGenerator())
                        .result()
                        .orElseThrow());
        result.add(
                "dimensionType",
                net.minecraft.world.level.dimension.DimensionType.DIRECT_CODEC
                        .encodeStart(ops, level.dimensionType())
                        .result()
                        .orElseThrow());
        result.add(
                "noiseSettings",
                NoiseGeneratorSettings.DIRECT_CODEC
                        .encodeStart(
                                ops,
                                ((NoiseBasedChunkGenerator) level.getChunkSource().getGenerator())
                                        .generatorSettings()
                                        .value())
                        .result()
                        .orElseThrow());
        result.add(
                "structures",
                registry(
                        level.registryAccess().registryOrThrow(Registries.STRUCTURE),
                        Structure.DIRECT_CODEC,
                        ops));
        result.add(
                "sets",
                registry(
                        level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET),
                        StructureSet.DIRECT_CODEC,
                        ops));
        result.add(
                "biomes",
                registry(
                        level.registryAccess().registryOrThrow(Registries.BIOME),
                        net.minecraft.world.level.biome.Biome.DIRECT_CODEC,
                        ops));
        return canonical(result).getAsJsonObject();
    }

    private static <T> JsonObject registry(
            Registry<T> registry, Codec<T> codec, RegistryOps<JsonElement> ops) {
        JsonArray iteration = new JsonArray();
        JsonObject values = new JsonObject(), tags = new JsonObject();
        for (T value : registry) {
            var key = registry.getKey(value);
            iteration.add(key.toString());
            values.add(key.toString(), codec.encodeStart(ops, value).result().orElseThrow());
        }
        registry.getTags()
                .forEach(
                        pair -> {
                            JsonArray holders = new JsonArray();
                            pair.getSecond()
                                    .forEach(
                                            holder ->
                                                    holders.add(
                                                            holder.unwrapKey()
                                                                    .orElseThrow()
                                                                    .location()
                                                                    .toString()));
                            tags.add(pair.getFirst().location().toString(), holders);
                        });
        JsonObject result = new JsonObject();
        result.add("iteration", iteration);
        result.add("values", values);
        result.add("tags", tags);
        return result;
    }

    public static String canonicalNbt(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var entries = new ArrayList<String>();
            for (String key : new TreeSet<>(compound.getAllKeys()))
                entries.add(
                        new Gson().toJson(key)
                                + ":"
                                + canonicalNbt(Objects.requireNonNull(compound.get(key))));
            return "{" + String.join(",", entries) + "}";
        }
        if (tag instanceof ListTag list) {
            var entries = new ArrayList<String>();
            list.forEach(value -> entries.add(canonicalNbt(value)));
            return "[" + String.join(",", entries) + "]";
        }
        return tag.toString();
    }

    public static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (String key : new TreeSet<>(value.getAsJsonObject().keySet()))
                result.add(key, canonical(value.getAsJsonObject().get(key)));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value.deepCopy();
    }

    public static void write(Path path, JsonElement value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    new GsonBuilder().setPrettyPrinting().create().toJson(canonical(value)) + "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String required(String name) {
        return Objects.requireNonNull(System.getProperty(PREFIX + name), name);
    }

    public static void exportLauncher(Path root) {
        JsonObject launcher = new JsonObject();
        launcher.addProperty("java", ProcessHandle.current().info().command().orElseThrow());
        launcher.addProperty("directory", System.getProperty("user.dir"));
        launcher.addProperty("classpath", System.getProperty("java.class.path"));
        JsonArray jvm = new JsonArray();
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(
                        argument ->
                                !argument.startsWith("-D" + PREFIX)
                                        && !argument.startsWith(
                                                "-D" + ModConstants.MOD_ID + ".terrainInputs.")
                                        && !argument.startsWith(
                                                "-D" + ModConstants.MOD_ID + ".featureBInputs."))
                .forEach(jvm::add);
        launcher.add("jvmArguments", jvm);
        String[] command = System.getProperty("sun.java.command").split(" ");
        launcher.addProperty("main", command[0]);
        JsonArray game = new JsonArray();
        for (int i = 1; i < command.length; i++) game.add(command[i]);
        launcher.add("gameArguments", game);
        write(root.resolve("launcher.json"), launcher);
    }
}
