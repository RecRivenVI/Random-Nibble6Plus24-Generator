package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;

/**
 * Explicit test-only bridge for stage-exact snapshots from the normal physical
 * ChunkMap pipeline. It is inert unless all native-control JVM properties exist.
 */
public final class NativeVanillaControlHarness {

    private static final String PREFIX = "randomnibble6plus24generator.phase2b.native.";
    private static final AtomicReference<Request> ACTIVE = new AtomicReference<>();

    private NativeVanillaControlHarness() {
    }

    public static void armIfRequested(MinecraftServer server) {
        String masterText = System.getProperty(PREFIX + "masterSeed");
        if (masterText == null) {
            return;
        }
        long masterSeed = Long.parseLong(masterText);
        Identifier dimensionId = Identifier.parse(requireProperty("dimension"));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ChunkPos target = new ChunkPos(
                Integer.parseInt(requireProperty("chunkX")),
                Integer.parseInt(requireProperty("chunkZ")));
        long localSeed = new MosaicSeedResolver(MosaicWorldProfile.version1())
                .resolveLocalWorldSeed(masterSeed, dimension, target);
        long physicalSeed = server.getWorldGenSettings().options().seed();
        if (physicalSeed != localSeed) {
            throw new IllegalStateException(
                    "Native control world seed mismatch; expected local seed "
                            + localSeed + ", physical WorldOptions.seed=" + physicalSeed);
        }
        Request request = new Request(
                masterSeed,
                localSeed,
                dimension,
                target,
                System.getProperty(PREFIX + "output"));
        if (!ACTIVE.compareAndSet(null, request)) {
            throw new IllegalStateException("Native control capture is already armed");
        }
        RandomNibble6Plus24Generator.LOGGER.info(
                "Armed native Vanilla control dimension={} masterSeed={} localSeed={} chunk={}",
                dimensionId,
                masterSeed,
                localSeed,
                target);
    }

    public static boolean shouldCapture(
            WorldGenContext worldGenContext,
            ChunkStatus status,
            ChunkAccess chunk) {
        Request request = ACTIVE.get();
        if (request == null || status != ChunkStatus.SURFACE && status != ChunkStatus.CARVERS) {
            return false;
        }
        return GenerationContextRegistry.find(worldGenContext).isEmpty()
                && worldGenContext.level().dimension().equals(request.dimension)
                && chunk.getPos().equals(request.target);
    }

    public static void capture(
            WorldGenContext worldGenContext,
            ChunkStatus status,
            ChunkAccess chunk) {
        Request request = ACTIVE.get();
        if (request == null || !shouldCapture(worldGenContext, status, chunk)) {
            return;
        }
        if (status == ChunkStatus.SURFACE) {
            request.captureSurface(SurfaceStageSnapshot.capture(
                    chunk,
                    worldGenContext.level().registryAccess()));
        } else if (status == ChunkStatus.CARVERS) {
            request.captureCarvers(CarverStageSnapshot.capture(
                    chunk,
                    worldGenContext.level().registryAccess()));
        }
    }

    public static void completeIfRequested(MinecraftServer server) {
        Request request = ACTIVE.get();
        if (request == null) {
            return;
        }
        try {
            if (MosaicWorldIdentity.isMosaicWorld(server)) {
                throw new IllegalStateException("Native Vanilla control must not use Mosaic world identity");
            }
            ServerLevel level = server.getLevel(request.dimension);
            if (level == null) {
                throw new IllegalStateException("Missing native control dimension "
                        + request.dimension.identifier());
            }

            // If global-spawn selection did not already generate this fixture,
            // force the normal physical scheduler through it. Snapshots are taken
            // at exact stage futures, not from this eventual FULL chunk.
            if (!request.hasBothSnapshots()) {
                level.getChunk(request.target.x(), request.target.z(), ChunkStatus.FULL, true);
            }
            SurfaceStageSnapshot nativeSurface = request.requireSurface();
            CarverStageSnapshot nativeCarvers = request.requireCarvers();

            MosaicWorldProfile profile = MosaicWorldProfile.version1();
            var isolatedSurfaceRun = new IsolatedGenerationSession(profile)
                    .generateSurface(level, request.masterSeed, request.target);
            var virtualSurfaceRun = new VanillaSurfaceControl()
                    .generateSurface(level, request.localSeed, request.target);
            SurfaceStageSnapshot isolatedSurface = SurfaceStageSnapshot.capture(
                    isolatedSurfaceRun.targetChunk(), level.registryAccess());
            SurfaceStageSnapshot virtualSurface = SurfaceStageSnapshot.capture(
                    virtualSurfaceRun.targetChunk(), level.registryAccess());
            isolatedSurface.assertEquivalentTo(virtualSurface);
            isolatedSurface.assertEquivalentTo(nativeSurface);

            var isolatedCarverRun = new IsolatedGenerationSession(profile)
                    .generateCarvers(level, request.masterSeed, request.target);
            var virtualCarverRun = new VanillaCarverControl()
                    .generateCarvers(level, request.localSeed, request.target);
            CarverStageSnapshot isolatedCarvers = CarverStageSnapshot.capture(
                    isolatedCarverRun.targetChunk(), level.registryAccess());
            CarverStageSnapshot virtualCarvers = CarverStageSnapshot.capture(
                    virtualCarverRun.targetChunk(), level.registryAccess());
            isolatedCarvers.assertEquivalentTo(virtualCarvers);
            isolatedCarvers.assertEquivalentTo(nativeCarvers);
            if (isolatedCarverRun.carverTrace().observedWorldSeed() != request.localSeed) {
                throw new IllegalStateException("Isolated CARVERS seed did not match native WorldOptions.seed");
            }

            String result = "{\"status\":\"PASS\",\"dimension\":\""
                    + request.dimension.identifier()
                    + "\",\"masterSeed\":" + request.masterSeed
                    + ",\"localSeed\":" + request.localSeed
                    + ",\"chunkX\":" + request.target.x()
                    + ",\"chunkZ\":" + request.target.z()
                    + ",\"surfaceHash\":\"" + nativeSurface.hash()
                    + "\",\"carverHash\":\"" + nativeCarvers.hash()
                    + "\"}";
            writeResult(request.output, result);
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Native Vanilla three-way parity PASS dimension={} masterSeed={} localSeed={} chunk={} "
                            + "surfaceHash={} carverHash={}",
                    request.dimension.identifier(),
                    request.masterSeed,
                    request.localSeed,
                    request.target,
                    nativeSurface.hash(),
                    nativeCarvers.hash());
        } finally {
            ACTIVE.compareAndSet(request, null);
        }

        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) {
            server.execute(() -> server.halt(false));
        }
    }

    private static String requireProperty(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing native control property " + PREFIX + suffix);
        }
        return value;
    }

    private static void writeResult(String output, String result) {
        if (output == null || output.isBlank()) {
            return;
        }
        Path path = Path.of(output).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, result, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write native control result " + path, exception);
        }
    }

    private static final class Request {
        private final long masterSeed;
        private final long localSeed;
        private final ResourceKey<Level> dimension;
        private final ChunkPos target;
        private final String output;
        private SurfaceStageSnapshot surface;
        private CarverStageSnapshot carvers;

        private Request(
                long masterSeed,
                long localSeed,
                ResourceKey<Level> dimension,
                ChunkPos target,
                String output) {
            this.masterSeed = masterSeed;
            this.localSeed = localSeed;
            this.dimension = dimension;
            this.target = target;
            this.output = output;
        }

        private synchronized void captureSurface(SurfaceStageSnapshot snapshot) {
            if (surface == null) {
                surface = snapshot;
            }
        }

        private synchronized void captureCarvers(CarverStageSnapshot snapshot) {
            if (carvers == null) {
                carvers = snapshot;
            }
        }

        private synchronized boolean hasBothSnapshots() {
            return surface != null && carvers != null;
        }

        private synchronized SurfaceStageSnapshot requireSurface() {
            if (surface == null) {
                throw new IllegalStateException("Native physical pipeline did not expose SURFACE for " + target);
            }
            return surface;
        }

        private synchronized CarverStageSnapshot requireCarvers() {
            if (carvers == null) {
                throw new IllegalStateException("Native physical pipeline did not expose CARVERS for " + target);
            }
            return carvers;
        }
    }
}
