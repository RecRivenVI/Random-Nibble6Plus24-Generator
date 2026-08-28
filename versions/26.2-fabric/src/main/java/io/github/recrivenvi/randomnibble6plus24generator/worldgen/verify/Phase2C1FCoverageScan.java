package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;

import net.minecraft.core.QuartPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;

/** Deterministic positive-fixture discovery for Phase 2C1F only. */
public final class Phase2C1FCoverageScan {

    private static final String PREFIX = "randomnibble6plus24generator.phase2c1f.scan.";

    private Phase2C1FCoverageScan() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(PREFIX + "mode");
        if (mode == null || mode.isBlank()) return;
        Path output = Path.of(require("output"));
        JsonObject result = switch (mode) {
            case "end-spike" -> scanEndSpike(server);
            case "pale-moss" -> scanPaleMoss(server);
            case "capped-processor" -> scanCappedProcessor(server);
            default -> throw new IllegalArgumentException("Unknown Phase 2C1F scan mode " + mode);
        };
        write(output, result.toString());
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Generation context leak after Phase 2C1F scan");
        }
        server.execute(() -> server.halt(false));
    }

    private static JsonObject scanEndSpike(MinecraftServer server) {
        ServerLevel level = requireLevel(server, Level.END);
        long[] masters = {0L, 1L, -1L, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE};
        int tested = 0;
        for (long master : masters) {
            for (int z = -5; z <= 5; z++) {
                for (int x = -5; x <= 5; x++) {
                    tested++;
                    JsonObject match = runAndMatch(
                            level, master, new ChunkPos(x, z), "minecraft:end_crystal", 0L, 0L);
                    if (match != null) {
                        match.addProperty("status", "FOUND");
                        match.addProperty("mode", "end-spike");
                        match.addProperty("tested", tested);
                        return match;
                    }
                }
            }
        }
        throw new IllegalStateException("No EndSpike entity fixture found after " + tested + " deterministic targets");
    }

    private static JsonObject scanPaleMoss(MinecraftServer server) {
        ServerLevel level = requireLevel(server, Level.OVERWORLD);
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        MosaicSeedResolver resolver = new MosaicSeedResolver(profile);
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("Pale Moss scan requires NoiseBasedChunkGenerator");
        }
        var noiseRegistry = level.registryAccess().lookupOrThrow(Registries.NOISE);
        long state = 0x6a09e667f3bcc909L;
        int biomeCandidates = 0;
        int generated = 0;
        int limit = Integer.getInteger(PREFIX + "limit", 1200);
        for (int index = 0; index < limit; index++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((state >>> 16) % 20001L) - 10000;
            state = state * 6364136223846793005L + 1442695040888963407L;
            int z = (int) ((state >>> 16) % 20001L) - 10000;
            long master = switch (index & 3) {
                case 0 -> 0L;
                case 1 -> 1L;
                case 2 -> -1L;
                default -> 123456789L;
            };
            ChunkPos target = new ChunkPos(x, z);
            long localSeed = resolver.resolveLocalWorldSeed(master, Level.OVERWORLD, target);
            RandomState randomState = RandomState.create(
                    generator.generatorSettings().value(), noiseRegistry, localSeed);
            var biome = generator.getBiomeSource().getNoiseBiome(
                    QuartPos.fromBlock(target.getMiddleBlockX()),
                    QuartPos.fromBlock(80),
                    QuartPos.fromBlock(target.getMiddleBlockZ()),
                    randomState.sampler());
            boolean paleGarden = biome.unwrapKey()
                    .map(key -> key.identifier().toString().equals("minecraft:pale_garden"))
                    .orElse(false);
            if (!paleGarden) continue;
            biomeCandidates++;
            generated++;
            JsonObject match = runAndMatch(level, master, target, null, 1L, 0L);
            if (match != null) {
                match.addProperty("status", "FOUND");
                match.addProperty("mode", "pale-moss");
                match.addProperty("tested", index + 1);
                match.addProperty("biomeCandidates", biomeCandidates);
                match.addProperty("generatedCandidates", generated);
                return match;
            }
        }
        throw new IllegalStateException(
                "No Pale Moss route hit after targets=" + limit
                        + ", paleGardenCandidates=" + biomeCandidates
                        + ", generatedCandidates=" + generated);
    }

    private static JsonObject scanCappedProcessor(MinecraftServer server) {
        ServerLevel level = requireLevel(server, Level.OVERWORLD);
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        MosaicSeedResolver resolver = new MosaicSeedResolver(profile);
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            throw new IllegalStateException("CappedProcessor scan requires NoiseBasedChunkGenerator");
        }
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> trailRuinsKey = ResourceKey.create(
                Registries.STRUCTURE, Identifier.withDefaultNamespace("trail_ruins"));
        Holder<Structure> trailRuins = structures.get(trailRuinsKey)
                .orElseThrow(() -> new IllegalStateException("Missing minecraft:trail_ruins structure"));
        var structureSets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var noiseRegistry = level.registryAccess().lookupOrThrow(Registries.NOISE);
        long state = 0xbb67ae8584caa73bL;
        int placementCandidates = 0;
        int generatedCandidates = 0;
        int limit = Integer.getInteger(PREFIX + "limit", 12000);
        for (int index = 0; index < limit; index++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((state >>> 16) % 20001L) - 10000;
            state = state * 6364136223846793005L + 1442695040888963407L;
            int z = (int) ((state >>> 16) % 20001L) - 10000;
            long master = switch (index & 3) {
                case 0 -> 0L;
                case 1 -> 1L;
                case 2 -> -1L;
                default -> 123456789L;
            };
            ChunkPos target = new ChunkPos(x, z);
            long localSeed = resolver.resolveLocalWorldSeed(master, Level.OVERWORLD, target);
            RandomState randomState = RandomState.create(
                    generator.generatorSettings().value(), noiseRegistry, localSeed);
            var structureState = generator.createState(structureSets, randomState, localSeed);
            structureState.ensureStructuresGenerated();
            boolean candidate = false;
            for (var placement : structureState.getPlacementsForStructure(trailRuins)) {
                for (int dz = -8; dz <= 8 && !candidate; dz++) {
                    for (int dx = -8; dx <= 8; dx++) {
                        if (placement.isStructureChunk(structureState, x + dx, z + dz)) {
                            candidate = true;
                            break;
                        }
                    }
                }
            }
            if (!candidate) continue;
            placementCandidates++;
            generatedCandidates++;
            JsonObject match = runAndMatch(level, master, target, null, 0L, 1L);
            if (match != null) {
                match.addProperty("status", "FOUND");
                match.addProperty("mode", "capped-processor");
                match.addProperty("tested", index + 1);
                match.addProperty("placementCandidates", placementCandidates);
                match.addProperty("generatedCandidates", generatedCandidates);
                return match;
            }
        }
        throw new IllegalStateException(
                "No CappedProcessor route hit after targets=" + limit
                        + ", placementCandidates=" + placementCandidates
                        + ", generatedCandidates=" + generatedCandidates);
    }

    private static JsonObject runAndMatch(
            ServerLevel level,
            long masterSeed,
            ChunkPos target,
            String requiredEntity,
            long minimumPaleMossHits,
            long minimumCappedProcessorHits) {
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        long localSeed = new MosaicSeedResolver(profile)
                .resolveLocalWorldSeed(masterSeed, level.dimension(), target);
        var run = new IsolatedGenerationSession(profile).generateFeaturesStable(level, masterSeed, target);
        FeatureStableSnapshot snapshot = FeatureStableSnapshot.capture(
                level.dimension().identifier().toString(), run.targetChunk(), level.registryAccess());
        boolean entityMatch = requiredEntity == null
                || snapshot.rawEntityNbt().stream().anyMatch(nbt -> nbt.contains(requiredEntity));
        boolean paleMatch = run.featureTrace().paleMossGeneratorRedirects() >= minimumPaleMossHits;
        boolean cappedMatch = run.featureTrace().cappedProcessorSeedRedirects() >= minimumCappedProcessorHits;
        if (!entityMatch || !paleMatch || !cappedMatch) return null;
        JsonObject match = new JsonObject();
        match.addProperty("masterSeed", Long.toString(masterSeed));
        match.addProperty("localSeed", Long.toString(localSeed));
        match.addProperty("dimension", level.dimension().identifier().toString());
        match.addProperty("chunkX", target.x());
        match.addProperty("chunkZ", target.z());
        match.addProperty("hash", snapshot.hash());
        match.addProperty("entities", snapshot.entityCount());
        match.addProperty("rawEntityNbt", snapshot.rawEntityNbt().toString());
        match.addProperty("canonicalEntityNbt", snapshot.canonicalEntityNbt().toString());
        match.addProperty("paleMossRouteHits", run.featureTrace().paleMossGeneratorRedirects());
        match.addProperty("cappedProcessorRouteHits", run.featureTrace().cappedProcessorSeedRedirects());
        match.addProperty("physicalLevelEscapes", run.featureTrace().physicalLevelEscapeSummary().toString());
        match.addProperty("featureWrites", run.featureTrace().featureWriteSummary().toString());
        return match;
    }

    private static ServerLevel requireLevel(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing scan dimension " + dimension.identifier());
        return level;
    }

    private static String require(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + PREFIX + suffix);
        return value;
    }

    private static void write(Path output, String value) {
        try {
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Files.writeString(output, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 2C1F scan " + output, exception);
        }
    }
}
