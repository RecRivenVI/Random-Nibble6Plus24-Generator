package io.github.recrivenvi.randomnibble6plus24generator.probe.features;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Independent B with raw UUID-only evidence and separately proven native constructor entropy. */
public final class NativeFeatureVerifier {
    private static final String PREFIX = ModConstants.MOD_ID + ".featureBInputs.";
    private static State active;
    private static volatile boolean isolatedGeneration;

    private NativeFeatureVerifier() {}

    public static boolean enabled() {
        return "conformance-legacy_b".equals(System.getProperty("validation.name"));
    }

    public static void run(MinecraftServer server) {
        Path root =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                System.getProperty(
                                        PREFIX + "variant",
                                        System.getProperty(PREFIX + "suite", "smoke")))
                        .resolve(System.getProperty("validation.target"));
        try {
            var mods =
                    NativeStructureVerifier.environment(server.overworld()).getAsJsonObject("mods");
            for (String mod : mods.keySet())
                if (!Set.of(
                                "minecraft",
                                "forge",
                                "javafml",
                                "mclanguage",
                                "lowcodefml",
                                ModConstants.MOD_ID,
                                ModConstants.MOD_ID + "_probe")
                        .contains(mod))
                    throw new IllegalStateException("Unexpected B Oracle mod " + mod);
            if (Boolean.getBoolean(PREFIX + "export")) {
                if (System.getProperty(PREFIX + "suite", "smoke").equals("structures")) {
                    io.github.recrivenvi.randomnibble6plus24generator.probe.structure
                            .NativeStructureFixtures.export(server, root);
                    NativeStructureVerifier.exportLauncher(root);
                    return;
                }
                var fixtures = new JsonArray();
                if (System.getProperty(PREFIX + "suite", "smoke").equals("cold")) {
                    fixture(
                            fixtures,
                            "cold-origin",
                            "minecraft:overworld",
                            "-9223372036854775808",
                            0,
                            0);
                    NativeStructureVerifier.write(root.resolve("fixtures.json"), fixtures);
                    NativeStructureVerifier.exportLauncher(root);
                    return;
                }
                if (System.getProperty(PREFIX + "suite", "smoke").equals("ocean")) {
                    // Independently discovered for this version, not inferred from a newer port.
                    fixture(fixtures, "ocean-carving-mask", "minecraft:overworld", "0", 86, -12);
                    NativeStructureVerifier.write(root.resolve("fixtures.json"), fixtures);
                    NativeStructureVerifier.exportLauncher(root);
                    return;
                }
                fixture(fixtures, "origin", "minecraft:overworld", "123456789", 0, 0);
                fixture(
                        fixtures,
                        "nonorigin",
                        "minecraft:overworld",
                        "-5161763991829980711",
                        125,
                        -37);
                fixture(fixtures, "nether", "minecraft:the_nether", "123456789", -11, -6);
                fixture(fixtures, "end-spike", "minecraft:the_end", "123456789", 2, 0);
                NativeStructureVerifier.write(root.resolve("fixtures.json"), fixtures);
                NativeStructureVerifier.exportLauncher(root);
                return;
            }
            String role = required("role"), fixture = required("fixture");
            if (!(ShulkerYawCausality.enabled()
                                    ? Set.of(
                                            "native_a",
                                            "native_b",
                                            "native_a_with_b",
                                            "native_b_with_a")
                                    : Set.of("native_0", "native_1", "host_17", "host_23"))
                            .contains(role)
                    || !fixture.matches("[a-z0-9_-]+"))
                throw new IllegalArgumentException("Invalid B role/fixture");
            var key =
                    ResourceKey.create(
                            Registries.DIMENSION, new ResourceLocation(required("dimension")));
            var level = Objects.requireNonNull(server.getLevel(key));
            var pos =
                    new ChunkPos(Integer.parseInt(required("x")), Integer.parseInt(required("z")));
            long seed = Long.parseLong(required("local"));
            boolean nativeRole = role.startsWith("native_");
            if (nativeRole != (level.getSeed() == seed) || level.getGameTime() != 0)
                throw new IllegalStateException("Invalid independent B host boundary");
            Path output = root.resolve(fixture).resolve(role);
            var proof = new JsonObject();
            proof.addProperty("fixture", fixture);
            proof.addProperty("role", role);
            proof.addProperty("dimension", key.location().toString());
            proof.addProperty("x", pos.x);
            proof.addProperty("z", pos.z);
            proof.addProperty("localSeed", Long.toString(seed));
            proof.addProperty("hostSeed", Long.toString(level.getSeed()));
            proof.addProperty("pid", ProcessHandle.current().pid());
            proof.addProperty(
                    "save",
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .toAbsolutePath()
                            .toString());
            proof.addProperty("inputHash", required("inputHash"));
            proof.addProperty(
                    "ringCandidateOptIn",
                    Boolean.getBoolean(ModConstants.MOD_ID + ".structure.lazyRings"));
            proof.add("environment", NativeStructureVerifier.environment(level));
            int loaded = level.getChunkSource().getLoadedChunksCount();
            long physicalEntities = level.getAllEntities().spliterator().getExactSizeIfKnown();
            if (physicalEntities < 0)
                physicalEntities =
                        java.util.stream.StreamSupport.stream(
                                        level.getAllEntities().spliterator(), false)
                                .count();
            long blockScheduler = level.getBlockTicks().count(),
                    fluidScheduler = level.getFluidTicks().count();
            JsonObject initialPoi = poiSnapshot(level);
            long began = System.nanoTime();
            active = new State(pos, seed, output);
            ShulkerYawCausality.begin();
            try {
                ProtoChunk target;
                JsonObject structures;
                Map<String, Long> rejected;
                BFeaturePreparation.Result handoff = null;
                if (nativeRole) {
                    boolean scoped =
                            ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                                            .ConcentricRingStateAccess)
                                                    level.getChunkSource().getGeneratorState())
                                            .mosaic$ringScope()
                                    != null;
                    proof.addProperty("physicalRingScopeBound", scoped);
                    if (scoped)
                        throw new IllegalStateException(
                                "Native B Oracle acquired a finite ring scope");
                    var result = nativeGenerate(level, pos);
                    target = result.target();
                    rejected = result.rejected();
                    structures = NativeStructureVerifier.structures(level, target, null);
                } else {
                    isolatedGeneration = true;
                    StructureGenerationContext completedOwner;
                    try (var context = new StructureGenerationContext(level, seed, pos)) {
                        proof.addProperty("narrowTemplateLocks", context.narrowTemplateLocks());
                        completedOwner = context;
                        StrictFeatureBaseline.Result result;
                        if (BArtifactHandoffVerifier.enabled()) {
                            var observed =
                                    new java.util.concurrent.atomic.AtomicReference<
                                            StrictFeatureBaseline.Result>();
                            handoff =
                                    BFeaturePreparation.generate(
                                            context,
                                            io.github.recrivenvi.randomnibble6plus24generator
                                                    .worldgen.profile.MosaicWorldProfile
                                                    .targetOnly(),
                                            (r, t) -> recordTerrain(level, t),
                                            observed::set);
                            result = Objects.requireNonNull(observed.get());
                            BArtifactHandoffVerifier.verifyPalettes(
                                    level, pos, seed, handoff.spawnBiomes(), result.terrain());
                        } else {
                            result =
                                    StrictFeatureBaseline.generate(
                                            context, (r, t) -> recordTerrain(level, t));
                        }
                        target = result.target();
                        rejected = result.rejected();
                        structures =
                                NativeStructureVerifier.structures(
                                        level, target, result.structures());
                        var scope =
                                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                                        .structure.ConcentricRingStateAccess)
                                                context.state())
                                        .mosaic$ringScope();
                        if (scope != null)
                            proof.add("ringScope", new Gson().toJsonTree(scope.metrics()));
                    } finally {
                        isolatedGeneration = false;
                    }
                    if (handoff != null) BArtifactHandoffVerifier.verifyOwnerClosed(completedOwner);
                    long afterEntities =
                            java.util.stream.StreamSupport.stream(
                                            level.getAllEntities().spliterator(), false)
                                    .count();
                    if (loaded != level.getChunkSource().getLoadedChunksCount()
                            || physicalEntities != afterEntities)
                        throw new IllegalStateException("B changed physical host chunks/entities");
                }
                if (target.getStatus() != ChunkStatus.FEATURES
                        || active.writers != 1
                        || active.openWriters != 0
                        || active.maxWriters != 1
                        || active.decorationSeedReads != 1
                        || active.featureSeedCalls <= 0
                        || SeedLocalRegionScope.isBound()
                        || FeatureExecutionScope.bindingCount() != 0)
                    throw new IllegalStateException("Incomplete writer/RNG/cleanup proof");
                Files.createDirectories(output);
                var raw = ChunkSerializer.write(level, target);
                if (handoff != null) {
                    isolatedGeneration = true;
                    try {
                        proof.add(
                                "handoff",
                                BArtifactHandoffVerifier.roundtrip(
                                        level,
                                        target,
                                        handoff,
                                        raw,
                                        output,
                                        root.resolve(fixture)
                                                .resolve(
                                                        "native_0/handoff-native-load-control.nbt.gz")));
                    } finally {
                        isolatedGeneration = false;
                    }
                }
                long entityDelta =
                        java.util.stream.StreamSupport.stream(
                                                level.getAllEntities().spliterator(), false)
                                        .count()
                                - physicalEntities;
                long blockDelta = level.getBlockTicks().count() - blockScheduler,
                        fluidDelta = level.getFluidTicks().count() - fluidScheduler;
                proof.addProperty("physicalEntitiesDelta", entityDelta);
                proof.addProperty("blockSchedulerDelta", blockDelta);
                proof.addProperty("fluidSchedulerDelta", fluidDelta);
                var finalPoi = poiSnapshot(level);
                var beforePoi =
                        nativeRole ? Objects.requireNonNull(active.nativePoiBefore) : initialPoi;
                NativeStructureVerifier.write(
                        output.resolve("physical-poi-before.json"), beforePoi);
                NativeStructureVerifier.write(output.resolve("physical-poi-after.json"), finalPoi);
                boolean poiUnchanged = beforePoi.equals(finalPoi);
                proof.addProperty("physicalPoiUnchanged", poiUnchanged);
                if (!poiUnchanged)
                    throw new IllegalStateException(
                            "B changed physical POI storage/dirty sections");
                if (entityDelta != 0 || blockDelta != 0 || fluidDelta != 0)
                    throw new IllegalStateException("B escaped to physical entity/tick lifecycle");
                Files.createDirectories(output);
                NbtIo.writeCompressed(raw, output.resolve("raw.nbt.gz").toFile());
                if (nativeRole && BArtifactHandoffVerifier.enabled())
                    proof.add(
                            "handoff",
                            BArtifactHandoffVerifier.nativeControl(level, target, raw, output));
                ShulkerYawCausality.finish(raw, output);
                var canonical = raw.copy();
                var entities = canonical.getList("entities", Tag.TAG_COMPOUND);
                if (entities.size() != target.getEntities().size())
                    throw new IllegalStateException("Serialized entity evidence missing");
                for (var item : entities) {
                    var entity = (CompoundTag) item;
                    if (entity.contains("UUID")) {
                        if (!(entity.get("UUID") instanceof IntArrayTag uuid)
                                || uuid.getAsIntArray().length != 4)
                            throw new IllegalArgumentException("Malformed entity UUID");
                        entity.putIntArray("UUID", new int[4]);
                    }
                }
                String uuidOnly = NativeStructureVerifier.canonicalNbt(canonical);
                NbtIo.writeCompressed(canonical, output.resolve("uuid-only.nbt.gz").toFile());
                Files.writeString(
                        output.resolve("uuid-only.txt"), uuidOnly, StandardCharsets.UTF_8);
                proof.addProperty("uuidOnlySnapshot", hash(uuidOnly));
                int acceptedYaw =
                        ShulkerYawCausality.enabled()
                                ? 0
                                : ShulkerYawCausality.acceptObservedYaw(canonical);
                proof.addProperty("shulkerYawAcceptanceCount", acceptedYaw);
                proof.addProperty(
                        "entityAcceptanceRule",
                        acceptedYaw == 0 ? "root-uuid-only" : ShulkerYawCausality.ACCEPTANCE_RULE);
                String encoded = NativeStructureVerifier.canonicalNbt(canonical);
                NbtIo.writeCompressed(canonical, output.resolve("canonical.nbt.gz").toFile());
                Files.writeString(output.resolve("canonical.txt"), encoded, StandardCharsets.UTF_8);
                NativeStructureVerifier.write(output.resolve("structures.json"), structures);
                proof.addProperty("snapshot", hash(encoded));
                proof.addProperty(
                        "structures",
                        hash(NativeStructureVerifier.canonical(structures).toString()));
                proof.addProperty("entities", entities.size());
                proof.addProperty("blockEntities", target.getBlockEntitiesPos().size());
                proof.addProperty("instantiatedBlockEntities", target.getBlockEntities().size());
                proof.addProperty("pendingBlockEntityNbt", target.getBlockEntityNbts().size());
                proof.addProperty("blockTicks", target.getBlockTicks().count());
                proof.addProperty("fluidTicks", target.getFluidTicks().count());
                long post = 0;
                for (var positions : target.getPostProcessing())
                    if (positions != null) post += positions.size();
                proof.addProperty("postProcessing", post);
                proof.addProperty("nativeLightReadCrossChecks", active.nativeLightReadCrossChecks);
                if (fixture.equals("cold-origin")) {
                    long snow = 0;
                    for (var block :
                            net.minecraft.core.BlockPos.betweenClosed(
                                    pos.getMinBlockX(),
                                    target.getMinBuildHeight(),
                                    pos.getMinBlockZ(),
                                    pos.getMaxBlockX(),
                                    target.getMaxBuildHeight() - 1,
                                    pos.getMaxBlockZ()))
                        if (target.getBlockState(block)
                                .is(net.minecraft.world.level.block.Blocks.SNOW)) snow++;
                    proof.addProperty("snowLayers", snow);
                }
                proof.add("rejected", new Gson().toJsonTree(rejected));
                proof.add("trace", active.json());
                proof.add("preInputs", active.preInputs);
                proof.addProperty("bindings", FeatureExecutionScope.bindingCount());
                proof.addProperty("physicalHostUnchanged", !nativeRole);
                if (fixture.equals("end-spike") && entities.isEmpty())
                    throw new IllegalStateException("EndSpike entity positive missing");
                if (ShulkerYawCausality.enabled()) {
                    ShulkerYawCausality.verifyOutput(canonical, proof, output);
                } else if (!role.equals("native_0")) {
                    var nativeProof =
                            JsonParser.parseString(
                                            Files.readString(
                                                    root.resolve(fixture)
                                                            .resolve("native_0/summary.json")))
                                    .getAsJsonObject();
                    for (String field :
                            List.of(
                                    "fixture",
                                    "dimension",
                                    "x",
                                    "z",
                                    "localSeed",
                                    "environment",
                                    "inputHash",
                                    "snapshot",
                                    "shulkerYawAcceptanceCount",
                                    "entityAcceptanceRule",
                                    "structures",
                                    "trace",
                                    "preInputs",
                                    "rejected"))
                        // Compare the persisted JSON representation on both sides. Gson's
                        // Float(0.9) != parsed decimal(0.9) even though both encode as "0.9".
                        if (!NativeStructureVerifier.canonical(nativeProof.get(field))
                                .toString()
                                .equals(
                                        NativeStructureVerifier.canonical(proof.get(field))
                                                .toString())) {
                            var control =
                                    NbtIo.readCompressed(
                                            root.resolve(fixture)
                                                    .resolve("native_0/canonical.nbt.gz")
                                                    .toFile());
                            NativeStructureVerifier.write(
                                    output.resolve("difference.json"),
                                    new Gson()
                                            .toJsonTree(
                                                    NativeFeatureNbtDiff.compare(
                                                            control, canonical)));
                            throw new IllegalStateException(
                                    "Independent B differs "
                                            + field
                                            + ": "
                                            + jsonDifference(
                                                    nativeProof.get(field),
                                                    proof.get(field),
                                                    field));
                        }
                }
                proof.addProperty(
                        "status",
                        ShulkerYawCausality.enabled()
                                ? (ShulkerYawCausality.mode().equals("capture")
                                        ? "CAPTURED"
                                        : "REPLAYED")
                                : (role.equals("native_0") ? "PRODUCED" : "PASS"));
            } catch (Exception | Error failure) {
                proof.addProperty("status", "FAIL");
                proof.addProperty("failure", failure.toString());
                throw failure;
            } finally {
                proof.add("preInputs", active.preInputs);
                proof.add("trace", active.json());
                proof.addProperty("elapsedMs", (System.nanoTime() - began) / 1_000_000);
                NativeStructureVerifier.write(output.resolve("summary.json"), proof);
                active = null;
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } finally {
            server.halt(false);
        }
    }

    private static String jsonDifference(
            com.google.gson.JsonElement expected, com.google.gson.JsonElement actual, String path) {
        if (Objects.equals(expected, actual)) return "equal";
        if (expected != null && actual != null) {
            if (expected.isJsonObject() && actual.isJsonObject()) {
                var keys = new TreeSet<>(expected.getAsJsonObject().keySet());
                keys.addAll(actual.getAsJsonObject().keySet());
                for (String key : keys) {
                    var a = expected.getAsJsonObject().get(key);
                    var b = actual.getAsJsonObject().get(key);
                    if (!Objects.equals(a, b)) return jsonDifference(a, b, path + "." + key);
                }
            }
            if (expected.isJsonArray() && actual.isJsonArray()) {
                var a = expected.getAsJsonArray();
                var b = actual.getAsJsonArray();
                if (a.size() != b.size()) return path + ".length: " + a.size() + " != " + b.size();
                for (int i = 0; i < a.size(); i++)
                    if (!a.get(i).equals(b.get(i)))
                        return jsonDifference(a.get(i), b.get(i), path + "[" + i + "]");
            }
        }
        return path + ": " + jsonValueType(expected) + " != " + jsonValueType(actual);
    }

    private static String jsonValueType(com.google.gson.JsonElement value) {
        if (value == null) return "missing";
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
            return value + " (" + value.getAsNumber().getClass().getName() + ")";
        return value.toString();
    }

    private record NativeResult(ProtoChunk target, Map<String, Long> rejected) {}

    private static NativeResult nativeGenerate(ServerLevel level, ChunkPos pos) {
        Map<Long, ProtoChunk> terrain = new HashMap<>();
        for (int z = pos.z - 1; z <= pos.z + 1; z++)
            for (int x = pos.x - 1; x <= pos.x + 1; x++) {
                var chunk = (ProtoChunk) level.getChunk(x, z, ChunkStatus.CARVERS, true);
                if (chunk.getStatus() != ChunkStatus.CARVERS)
                    throw new IllegalStateException("Native B prerequisite escaped CARVERS");
                terrain.put(ChunkPos.asLong(x, z), chunk);
            }
        var target = terrain.get(pos.toLong());
        recordTerrain(level, terrain);
        active.nativePoiBefore = poiSnapshot(level);
        Map<String, Long> rejected = new HashMap<>();
        java.util.function.Consumer<String> drop = api -> rejected.merge(api, 1L, Long::sum);
        Map<Long, StrictFeatureChunk> guarded = new HashMap<>();
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        terrain.forEach(
                (key, chunk) ->
                        guarded.put(key, new StrictFeatureChunk(chunk, pos, biomeRegistry, drop)));
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var region =
                new StrictFeatureRegion(
                        level,
                        target,
                        guarded,
                        drop,
                        generator,
                        level.getChunkSource().randomState()) {
                    @Override
                    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
                        return level.getLightEngine();
                    }

                    @Override
                    protected int readPreFeatureBrightness(
                            LightLayer layer, net.minecraft.core.BlockPos pos) {
                        return level.getLightEngine().getLayerListener(layer).getLightValue(pos);
                    }

                    @Override
                    protected int readPreFeatureRawBrightness(
                            net.minecraft.core.BlockPos pos, int skyDarken) {
                        return level.getLightEngine().getRawBrightness(pos, skyDarken);
                    }

                    @Override
                    public CarvingMask carvingMask(ChunkPos at, GenerationStep.Carving stage) {
                        getChunk(at.x, at.z, ChunkStatus.EMPTY, true);
                        var original = terrain.get(at.toLong());
                        if (original == null) original = outerReadInputs().get(at.toLong());
                        if (original == null)
                            throw new IllegalStateException("Missing native mask input");
                        var mask =
                                at.equals(pos)
                                        ? original.getOrCreateCarvingMask(stage)
                                        : original.getCarvingMask(stage);
                        if (mask == null)
                            mask =
                                    new CarvingMask(
                                            original.getHeight(), original.getMinBuildHeight());
                        carvingMaskRead(at, stage, mask);
                        return mask;
                    }
                };
        region.bindOuterReadSource(
                (x, z) ->
                        (ProtoChunk)
                                level.getChunk(
                                        x,
                                        z,
                                        ChunkStatus.getStatusAroundFullChunk(
                                                ChunkStatus.getDistance(ChunkStatus.FEATURES)
                                                        + Math.max(
                                                                Math.abs(x - pos.x),
                                                                Math.abs(z - pos.z))),
                                        true));
        active.nativeRegion = region;
        try {
            Heightmap.primeHeightmaps(
                    target,
                    EnumSet.of(
                            Heightmap.Types.MOTION_BLOCKING,
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Heightmap.Types.OCEAN_FLOOR,
                            Heightmap.Types.WORLD_SURFACE));
            generator.applyBiomeDecoration(region, target, level.structureManager());
            Blender.generateBorderTicks(region, target);
            region.assertNoForbiddenEscapes();
            target.setStatus(ChunkStatus.FEATURES);
            return new NativeResult(target, Map.copyOf(rejected));
        } finally {
            region.close();
        }
    }

    public static void beginWriter(WorldGenLevel region, ChunkAccess target) {
        var state =
                Objects.requireNonNull(
                        active, "Unexpected native FEATURES before explicit B writer");
        if (!target.getPos().equals(state.pos)
                || region.getSeed() != state.seed
                || ++state.writers != 1) throw new IllegalStateException("Wrong B writer/seed");
        state.openWriters++;
        state.maxWriters = Math.max(state.maxWriters, state.openWriters);
        var registry = region.registryAccess().registryOrThrow(Registries.BIOME);
        for (int z = state.pos.z - 1; z <= state.pos.z + 1; z++)
            for (int x = state.pos.x - 1; x <= state.pos.x + 1; x++)
                for (var section : region.getChunk(x, z, ChunkStatus.BIOMES, true).getSections())
                    section.getBiomes()
                            .getAll(b -> state.biomes.add(registry.getKey(b.value()).toString()));
    }

    private static JsonObject poiSnapshot(ServerLevel level) {
        var access =
                (io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin
                                .FeaturePoiStorageAccess)
                        level.getPoiManager();
        var storage = new JsonObject();
        var ops =
                net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        for (var entry : access.mosaic$storage().long2ObjectEntrySet()) {
            var section = entry.getValue();
            storage.addProperty(
                    Long.toString(entry.getLongKey()),
                    section.isEmpty()
                            ? "<absent>"
                            : NativeStructureVerifier.canonicalNbt(
                                    net.minecraft.world.entity.ai.village.poi.PoiSection.codec(
                                                    () -> {})
                                            .encodeStart(ops, section.orElseThrow())
                                            .result()
                                            .orElseThrow()));
        }
        var result = new JsonObject();
        result.add("storage", storage);
        result.add("dirty", new Gson().toJsonTree(access.mosaic$dirty().toLongArray()));
        return NativeStructureVerifier.canonical(result).getAsJsonObject();
    }

    private static void recordTerrain(ServerLevel level, Map<Long, ProtoChunk> terrain) {
        if (active == null || !active.preInputs.keySet().isEmpty() || terrain.size() != 9)
            throw new IllegalStateException("Invalid B prerequisite capture");
        try {
            for (var entry : new TreeMap<>(terrain).entrySet()) {
                var chunk = entry.getValue();
                if (chunk.getStatus() != ChunkStatus.CARVERS)
                    throw new IllegalStateException("B prerequisite is not CARVERS");
                var tag = ChunkSerializer.write(level, chunk);
                String id = chunk.getPos().x + "_" + chunk.getPos().z;
                Path folder = active.output.resolve("pre_inputs");
                Files.createDirectories(folder);
                NbtIo.writeCompressed(tag, folder.resolve(id + ".nbt.gz").toFile());
                active.preInputs.addProperty(id, hash(NativeStructureVerifier.canonicalNbt(tag)));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static void checkPhysicalRequest(String api, int x, int z) {
        if (isolatedGeneration)
            throw new AssertionError("B attempted physical host " + api + " at " + x + "," + z);
    }

    public static void endWriter() {
        if (active == null || --active.openWriters != 0)
            throw new IllegalStateException("B writer completion imbalance");
    }

    public static void lightRead(LightLayer layer, net.minecraft.core.BlockPos pos, int value) {
        if (active == null || active.openWriters == 0) return;
        if (active.nativeRegion != null) {
            if (active.nativeLightCandidate == null)
                active.nativeLightCandidate =
                        new FreshPreFeatureLighting(
                                active.nativeRegion,
                                active.nativeRegion.dimensionType().hasSkyLight());
            int candidate = active.nativeLightCandidate.brightness(layer, pos);
            if (candidate != value)
                throw new IllegalStateException(
                        "Fresh native light storage differs at "
                                + pos
                                + ": "
                                + value
                                + " != "
                                + candidate);
            active.nativeLightReadCrossChecks++;
        }
        active.lightReads.add(layer + ":" + pos.asLong() + "=" + value);
    }

    public static void rawLightRead(net.minecraft.core.BlockPos pos, int darken, int value) {
        if (active == null || active.openWriters == 0) return;
        if (active.nativeRegion != null) {
            if (active.nativeLightCandidate == null)
                active.nativeLightCandidate =
                        new FreshPreFeatureLighting(
                                active.nativeRegion,
                                active.nativeRegion.dimensionType().hasSkyLight());
            if (active.nativeLightCandidate.rawBrightness(pos, darken) != value)
                throw new IllegalStateException("Fresh native raw light differs at " + pos);
            active.nativeLightReadCrossChecks++;
        }
        active.lightReads.add("RAW:" + darken + ":" + pos.asLong() + "=" + value);
    }

    public static void structurePlacement(
            net.minecraft.world.level.levelgen.structure.StructureStart start,
            WorldGenLevel level,
            ChunkPos owner) {
        var state = active;
        if (state == null || state.openWriters == 0) return;
        if (!owner.equals(state.pos) || !start.isValid())
            throw new IllegalStateException("Invalid B structure placement");
        String key =
                level.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE)
                        .getKey(start.getStructure())
                        .toString();
        state.structures.add(key + "@" + start.getChunkPos());
    }

    public static void decorationSeed(long seed, int x, int z) {
        var state = active;
        if (state == null || state.openWriters == 0) return;
        if (seed != state.seed || x != state.pos.getMinBlockX() || z != state.pos.getMinBlockZ())
            throw new IllegalStateException("B decoration seed/position differs");
        state.decorationSeedReads++;
    }

    public static void featureSeed(long seed, int feature, int step) {
        var state = active;
        if (state == null || state.openWriters == 0) return;
        state.featureSeedCalls++;
        state.featureSeedHash =
                ((state.featureSeedHash ^ seed) * 0x100000001b3L ^ Integer.toUnsignedLong(feature))
                        * 0x100000001b3L;
        state.featureSeedHash =
                (state.featureSeedHash ^ Integer.toUnsignedLong(step)) * 0x100000001b3L;
    }

    public static void carvingMaskRead(
            ChunkPos pos, GenerationStep.Carving stage, CarvingMask mask) {
        if (active == null || active.openWriters == 0) return;
        active.carvingMaskReads.add(
                pos + ":" + stage + ":" + hash(Arrays.toString(mask.toArray())));
    }

    private static final class State {
        final ChunkPos pos;
        final long seed;
        final Path output;
        final JsonObject preInputs = new JsonObject();
        final Set<String> biomes = new TreeSet<>();
        final List<String> structures = new ArrayList<>();
        final List<String> lightReads = new ArrayList<>();
        final List<String> carvingMaskReads = new ArrayList<>();
        JsonObject nativePoiBefore;
        StrictFeatureRegion nativeRegion;
        FreshPreFeatureLighting nativeLightCandidate;
        int nativeLightReadCrossChecks;
        int writers, openWriters, maxWriters, decorationSeedReads;
        long featureSeedCalls, featureSeedHash = 0xcbf29ce484222325L;

        State(ChunkPos pos, long seed, Path output) {
            this.pos = pos;
            this.seed = seed;
            this.output = output;
        }

        JsonObject json() {
            var result = new JsonObject();
            result.addProperty("writers", writers);
            result.addProperty("maxWriters", maxWriters);
            result.addProperty("decorationSeedReads", decorationSeedReads);
            result.addProperty("featureSeedCalls", featureSeedCalls);
            result.addProperty("featureSeedHash", Long.toString(featureSeedHash));
            result.add("biomes", new Gson().toJsonTree(biomes));
            result.add("structurePlacements", new Gson().toJsonTree(structures));
            result.add("lightReads", new Gson().toJsonTree(lightReads));
            result.add("carvingMaskReads", new Gson().toJsonTree(carvingMaskReads));
            return result;
        }
    }

    private static void fixture(
            JsonArray output, String id, String dimension, String seed, int x, int z) {
        var row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("dimension", dimension);
        row.addProperty("local", seed);
        row.addProperty("x", x);
        row.addProperty("z", z);
        output.add(row);
    }

    private static String required(String key) {
        return Objects.requireNonNull(System.getProperty(PREFIX + key), key);
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
}
