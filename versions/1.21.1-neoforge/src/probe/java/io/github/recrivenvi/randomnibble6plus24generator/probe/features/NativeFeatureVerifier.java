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
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Independent B with raw NBT, UUID-only evidence and observed native-constructor yaw evidence. */
public final class NativeFeatureVerifier {
    private static final String PREFIX = ModConstants.MOD_ID + ".featureBInputs.";
    private static State active;
    private static volatile boolean isolatedGeneration;

    private NativeFeatureVerifier() {}

    public static boolean enabled() {
        return Set.of("conformance-legacy_b", "integration-world_identity")
                .contains(System.getProperty("validation.name", ""));
    }

    public static void run(MinecraftServer server) {
        String variant =
                System.getProperty(
                        PREFIX + "variant",
                        System.getProperty(PREFIX + "suite", "smoke")
                                + (preparation().equals("shared") ? "-shared" : ""));
        if (!variant.matches("[a-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid B evidence variant");
        Path root =
                Path.of(System.getProperty("validation.result"))
                        .resolve(variant)
                        .resolve(System.getProperty("validation.target"));
        try {
            var mods =
                    NativeStructureVerifier.environment(server.overworld()).getAsJsonObject("mods");
            for (String mod : mods.keySet())
                if (!Set.of(
                                "minecraft",
                                "neoforge",
                                "javafml",
                                "mclanguage",
                                "lowcodefml",
                                ModConstants.MOD_ID,
                                ModConstants.MOD_ID + "_probe")
                        .contains(mod))
                    throw new IllegalStateException("Unexpected B Oracle mod " + mod);
            if (Boolean.getBoolean(PREFIX + "export")) {
                verifyWorldBorrowing(server.overworld(), root);
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
                    // Actual seed-0 monument start from the independent structure discovery.
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
            if (!(Set.of("native_0", "native_1", "host_17", "host_23").contains(role)
                            || (ShulkerYawCausality.enabled()
                                    && Set.of(
                                                    "native_a",
                                                    "native_b",
                                                    "native_a_with_b",
                                                    "native_b_with_a")
                                            .contains(role)))
                    || !fixture.matches("[a-z0-9_-]+"))
                throw new IllegalArgumentException("Invalid B role/fixture");
            var key =
                    ResourceKey.create(
                            Registries.DIMENSION, ResourceLocation.parse(required("dimension")));
            var level = Objects.requireNonNull(server.getLevel(key));
            var pos =
                    new ChunkPos(Integer.parseInt(required("x")), Integer.parseInt(required("z")));
            long seed = Long.parseLong(required("local"));
            boolean nativeRole = role.startsWith("native_");
            if (nativeRole != (level.getSeed() == seed) || level.getGameTime() != 0)
                throw new IllegalStateException("Invalid independent B host boundary");
            Path output = root.resolve(fixture).resolve(role);
            WorldIdentityEvidence.verify(server, output);
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
            try {
                ShulkerYawCausality.begin();
                ProtoChunk target;
                JsonObject structures;
                Map<String, Long> rejected;
                BFeaturePreparation.Result handoff = null;
                if (nativeRole) {
                    var result = nativeGenerate(level, pos);
                    target = result.target();
                    rejected = result.rejected();
                    structures = NativeStructureVerifier.structures(level, target, null);
                } else {
                    isolatedGeneration = true;
                    try (var context = new StructureGenerationContext(level, seed, pos)) {
                        StrictFeatureBaseline.Result result;
                        if (BArtifactHandoffVerifier.enabled()) {
                            if (!preparation().equals("shared"))
                                throw new IllegalArgumentException(
                                        "B handoff requires shared preparation");
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
                            var collisionProof =
                                    SpawnCollisionEvidence.verify(
                                            level,
                                            handoff.spawnBiomes(),
                                            handoff.spawnCollisions(),
                                            result.terrain());
                            NativeStructureVerifier.write(
                                    output.resolve("spawn-collision-before.json"), collisionProof);
                        } else {
                            result =
                                    preparation().equals("shared")
                                            ? StrictFeatureBaseline.generateSharedWorld(
                                                    context, (r, t) -> recordTerrain(level, t))
                                            : StrictFeatureBaseline.generate(
                                                    context, (r, t) -> recordTerrain(level, t));
                        }
                        proof.addProperty("preparationNanos", result.preparationNanos());
                        proof.addProperty("decorationNanos", result.decorationNanos());
                        if (result.structureReuse() != null) {
                            requireDetachedTargetGraph(result);
                            proof.add(
                                    "structureReuse",
                                    new Gson().toJsonTree(result.structureReuse()));
                            proof.addProperty("targetStructureGraphDetached", true);
                        }
                        target = result.target();
                        rejected = result.rejected();
                        structures =
                                NativeStructureVerifier.structures(
                                        level, target, result.structures());
                        recordOuterInputs(level, result.region());
                    } finally {
                        isolatedGeneration = false;
                    }
                    long afterEntities =
                            java.util.stream.StreamSupport.stream(
                                            level.getAllEntities().spliterator(), false)
                                    .count();
                    if (loaded != level.getChunkSource().getLoadedChunksCount()
                            || physicalEntities != afterEntities)
                        throw new IllegalStateException("B changed physical host chunks/entities");
                }
                if (target.getPersistedStatus() != ChunkStatus.FEATURES
                        || active.writers != 1
                        || active.openWriters != 0
                        || active.maxWriters != 1
                        || active.decorationSeedReads != 1
                        || active.featureSeedCalls <= 0
                        || SeedLocalRegionScope.isBound()
                        || FeatureExecutionScope.bindingCount() != 0
                        || !active.openStructures.isEmpty()
                        || active.liveContexts != 0)
                    throw new IllegalStateException("Incomplete writer/RNG/cleanup proof");
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
                NbtIo.writeCompressed(raw, output.resolve("raw.nbt.gz"));
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
                NbtIo.writeCompressed(canonical, output.resolve("uuid-only.nbt.gz"));
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
                NbtIo.writeCompressed(canonical, output.resolve("canonical.nbt.gz"));
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
                proof.add("outerInputs", active.outerInputs);
                proof.addProperty("bindings", FeatureExecutionScope.bindingCount());
                proof.addProperty("worldStateBuilds", active.worldStateBuilds);
                proof.addProperty("borrowedContexts", active.borrowedContexts);
                proof.addProperty("liveContexts", active.liveContexts);
                proof.addProperty("ringPreparations", active.ringPreparations);
                proof.addProperty("ringBiomeSearches", active.ringBiomeSearches);
                proof.addProperty("narrowContexts", active.narrowContexts);
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
                                    "outerInputs",
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
                                                    .resolve("native_0/canonical.nbt.gz"),
                                            NbtAccounter.unlimitedHeap());
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
                proof.add("outerInputs", active.outerInputs);
                proof.add("trace", active.json());
                proof.addProperty("worldStateBuilds", active.worldStateBuilds);
                proof.addProperty("borrowedContexts", active.borrowedContexts);
                proof.addProperty("liveContexts", active.liveContexts);
                proof.addProperty("ringPreparations", active.ringPreparations);
                proof.addProperty("ringBiomeSearches", active.ringBiomeSearches);
                proof.addProperty("narrowContexts", active.narrowContexts);
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

    private static void verifyWorldBorrowing(ServerLevel level, Path output) {
        try (var root = new StructureGenerationContext(level, 123456789, ChunkPos.ZERO)) {
            try (var child = root.fork(new ChunkPos(1, 0));
                    var other = new StructureGenerationContext(level, -1, ChunkPos.ZERO);
                    var dimension =
                            new StructureGenerationContext(
                                    Objects.requireNonNull(
                                            level.getServer().getLevel(Level.NETHER)),
                                    123456789,
                                    ChunkPos.ZERO)) {
                if (child.generator() != root.generator()
                        || child.randomState() != root.randomState()
                        || child.state() != root.state()
                        || child.check() != root.check()
                        || child.seed() != root.seed()
                        || child.host() != root.host()
                        || !child.target().equals(new ChunkPos(1, 0))
                        || root.generator() == other.generator()
                        || root.randomState() == other.randomState()
                        || root.state() == other.state()
                        || root.check() == other.check()
                        || root.host() == dimension.host()
                        || root.generator() == dimension.generator()
                        || root.randomState() == dimension.randomState()
                        || root.state() == dimension.state()
                        || root.check() == dimension.check())
                    throw new IllegalStateException("World state borrowing crossed identity/seed");
            }
            root.seed();
            try {
                root.fork(
                        new ChunkPos(StructureGenerationContext.sharedPreparationRadius() + 1, 0));
                throw new AssertionError("Unaudited fork range accepted");
            } catch (IllegalArgumentException expected) {
            }
            var stale = root.fork(new ChunkPos(-1, 0));
            root.close();
            try {
                stale.seed();
                throw new AssertionError("Closed world state remained usable");
            } catch (IllegalStateException expected) {
            } finally {
                stale.close();
            }
        }
        var proof = new JsonObject();
        proof.addProperty("status", "PASS");
        proof.addProperty("sameWorldState", true);
        proof.addProperty("differentSeedDetached", true);
        proof.addProperty("differentDimensionDetached", true);
        proof.addProperty("childClosePreservesOwner", true);
        proof.addProperty("ownerCloseRejectsChild", true);
        proof.addProperty("outOfRangeRejected", true);
        NativeStructureVerifier.write(output.resolve("world-borrowing.json"), proof);
    }

    public static synchronized void contextOpened(boolean borrowed, boolean narrow) {
        io.github.recrivenvi.randomnibble6plus24generator.probe.production.RuntimeLifecycleEvidence
                .contextOpened(narrow);
        if (!isolatedGeneration || active == null) return;
        active.liveContexts++;
        if (narrow) active.narrowContexts++;
        if (borrowed) active.borrowedContexts++;
        else active.worldStateBuilds++;
    }

    public static synchronized void contextClosed() {
        if (!isolatedGeneration || active == null) return;
        if (--active.liveContexts < 0)
            throw new IllegalStateException("Context lifetime underflow");
    }

    public static synchronized void ringPreparation() {
        if (isolatedGeneration && active != null) active.ringPreparations++;
    }

    public static synchronized void ringBiomeSearch() {
        if (isolatedGeneration && active != null) active.ringBiomeSearches++;
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
                if (chunk.getPersistedStatus() != ChunkStatus.CARVERS)
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
                        guarded.put(
                                key,
                                new StrictFeatureChunk(
                                        chunk, pos, biomeRegistry, drop, level.registryAccess())));
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var region =
                new StrictFeatureRegion(
                        level,
                        StaticCache2D.<GenerationChunkHolder>create(
                                pos.x, pos.z, 0, (x, z) -> null),
                        ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES),
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
                            throw new IllegalStateException("Missing physical native mask input");
                        if (at.equals(pos)) return original.getOrCreateCarvingMask(stage);
                        var mask = original.getCarvingMask(stage);
                        return mask == null
                                ? new CarvingMask(
                                        original.getHeight(), original.getMinBuildHeight())
                                : mask;
                    }
                };
        region.bindOuterReadSource(
                (x, z) ->
                        (ProtoChunk)
                                level.getChunk(
                                        x,
                                        z,
                                        ChunkPyramid.GENERATION_PYRAMID
                                                .getStepTo(ChunkStatus.FEATURES)
                                                .accumulatedDependencies()
                                                .get(
                                                        Math.max(
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
            recordOuterInputs(level, region);
            target.setPersistedStatus(ChunkStatus.FEATURES);
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

    private static void requireDetachedTargetGraph(StrictFeatureBaseline.Result result) {
        var starts =
                Collections.newSetFromMap(
                        new IdentityHashMap<
                                net.minecraft.world.level.levelgen.structure.StructureStart,
                                Boolean>());
        starts.addAll(result.target().getAllStarts().values());
        result.target()
                .getAllReferences()
                .forEach(
                        (structure, positions) -> {
                            for (long key : positions)
                                starts.add(
                                        result.structures()
                                                .referencedStart(structure, new ChunkPos(key)));
                        });
        var pieces =
                Collections.newSetFromMap(
                        new IdentityHashMap<
                                net.minecraft.world.level.levelgen.structure.StructurePiece,
                                Boolean>());
        starts.stream()
                .filter(Objects::nonNull)
                .filter(start -> start.isValid())
                .forEach(start -> pieces.addAll(start.getPieces()));
        for (var entry : result.terrain().entrySet()) {
            if (entry.getKey() == result.target().getPos().toLong()) continue;
            for (var start : entry.getValue().getAllStarts().values()) {
                if (!start.isValid()) continue;
                if (starts.contains(start) || start.getPieces().stream().anyMatch(pieces::contains))
                    throw new IllegalStateException(
                            "Target mutable structure graph leaked to neighbour reads");
            }
        }
        if (!result.structureReuse().closed() || result.structureReuse().resident() != 0)
            throw new IllegalStateException("B result retained the source scope after close");
    }

    private static void recordTerrain(ServerLevel level, Map<Long, ProtoChunk> terrain) {
        if (active == null || !active.preInputs.keySet().isEmpty() || terrain.size() != 9)
            throw new IllegalStateException("Invalid B prerequisite capture");
        try {
            for (var entry : new TreeMap<>(terrain).entrySet()) {
                var chunk = entry.getValue();
                if (chunk.getPersistedStatus() != ChunkStatus.CARVERS)
                    throw new IllegalStateException("B prerequisite is not CARVERS");
                var tag = ChunkSerializer.write(level, chunk);
                String id = chunk.getPos().x + "_" + chunk.getPos().z;
                Path folder = active.output.resolve("pre_inputs");
                Files.createDirectories(folder);
                NbtIo.writeCompressed(tag, folder.resolve(id + ".nbt.gz"));
                active.preInputs.addProperty(id, hash(NativeStructureVerifier.canonicalNbt(tag)));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void recordOuterInputs(ServerLevel level, StrictFeatureRegion region) {
        try {
            for (var chunk : region.outerReadInputs().values()) {
                var tag = ChunkSerializer.write(level, chunk);
                var key = chunk.getPos().x + "_" + chunk.getPos().z;
                var file = active.output.resolve("outer_inputs/" + key + ".nbt.gz");
                Files.createDirectories(file.getParent());
                NbtIo.writeCompressed(tag, file);
                active.outerInputs.addProperty(
                        key, hash(NativeStructureVerifier.canonicalNbt(tag)));
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
        String placement = key + "@" + start.getChunkPos();
        state.structures.add(placement);
        state.openStructures.push(placement);
    }

    public static void structurePlacementCompleted() {
        if (active == null || active.openWriters == 0) return;
        if (active.openStructures.isEmpty())
            throw new IllegalStateException("Structure placement completion imbalance");
        active.openStructures.pop();
    }

    public static void carvingMaskRead(
            ChunkPos pos, GenerationStep.Carving stage, CarvingMask mask) {
        if (active == null || active.openWriters == 0) return;
        active.carvingMaskReads.add(
                pos + ":" + stage + ":" + hash(Arrays.toString(mask.toArray())));
    }

    public static void structureBlockChanged(
            ChunkAccess chunk,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState before,
            net.minecraft.world.level.block.state.BlockState value) {
        var state = active;
        if (state == null
                || state.openStructures.isEmpty()
                || before == null
                || before.equals(value)) return;
        if (!chunk.getPos().equals(state.pos) || !new ChunkPos(pos).equals(state.pos))
            throw new IllegalStateException("Structure changed a non-target B block");
        state.structureBlockWrites.merge(state.openStructures.peek(), 1L, Long::sum);
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

    private static final class State {
        final ChunkPos pos;
        final long seed;
        final Path output;
        final JsonObject preInputs = new JsonObject();
        final JsonObject outerInputs = new JsonObject();
        final Set<String> biomes = new TreeSet<>();
        final List<String> structures = new ArrayList<>();
        final Deque<String> openStructures = new ArrayDeque<>();
        final Map<String, Long> structureBlockWrites = new TreeMap<>();
        final List<String> lightReads = new ArrayList<>();
        final List<String> carvingMaskReads = new ArrayList<>();
        JsonObject nativePoiBefore;
        StrictFeatureRegion nativeRegion;
        FreshPreFeatureLighting nativeLightCandidate;
        int nativeLightReadCrossChecks;
        int worldStateBuilds, borrowedContexts, liveContexts;
        int ringPreparations, ringBiomeSearches;
        int narrowContexts;
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
            result.add("structureBlockWrites", new Gson().toJsonTree(structureBlockWrites));
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

    private static String preparation() {
        String value = System.getProperty(PREFIX + "preparation", "independent");
        if (!Set.of("independent", "shared").contains(value))
            throw new IllegalArgumentException("Unknown B preparation " + value);
        return value;
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
