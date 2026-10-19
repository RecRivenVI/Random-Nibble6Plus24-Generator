package io.github.recrivenvi.randomnibble6plus24generator.probe.structure;

import com.google.gson.JsonObject;
import com.mojang.math.OctahedralGroup;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.RotationCacheAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.core.*;

/** Native cache component only: no terrain, worldgen fixture, or ignored output fields. */
public final class NativeRotationVerifier {
    private static final String FLAG = ModConstants.MOD_ID + ".structure.narrowLocks";
    private static final AtomicBoolean INJECT = new AtomicBoolean();
    private static final AtomicBoolean OBSERVED = new AtomicBoolean();

    private NativeRotationVerifier() {}

    public static final class InjectedFailure extends IllegalStateException {}

    public static void maybeFail(OctahedralGroup group) {
        if (INJECT.get()
                && StructureGenerationLocks.rotationOwner() == group
                && OBSERVED.compareAndSet(false, true)) throw new InjectedFailure();
    }

    static JsonObject run() throws Exception {
        var groups = OctahedralGroup.values();
        var saved = new IdentityHashMap<OctahedralGroup, Map<Direction, Direction>>();
        for (var group : groups)
            saved.put(group, ((RotationCacheAccess) (Object) group).mosaic$directions());
        String original = System.getProperty(FLAG);
        var pool = Executors.newFixedThreadPool(2);
        try {
            System.clearProperty(FLAG);
            int[][] expected = new int[groups.length][];
            for (var group : groups) {
                ((RotationCacheAccess) (Object) group).mosaic$directions(null);
                expected[group.ordinal()] = values(group);
            }
            System.setProperty(FLAG, "true");
            for (var group : groups) ((RotationCacheAccess) (Object) group).mosaic$directions(null);
            var gate = new CountDownLatch(1);
            var jobs = new ArrayList<CompletableFuture<Void>>();
            for (int actor = 0; actor < 2; actor++)
                jobs.add(
                        CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        gate.await();
                                    } catch (InterruptedException error) {
                                        Thread.currentThread().interrupt();
                                        throw new CompletionException(error);
                                    }
                                    for (int round = 0; round < 32; round++)
                                        for (var group : groups)
                                            if (!Arrays.equals(
                                                    expected[group.ordinal()], values(group)))
                                                throw new IllegalStateException(
                                                        "Native rotation mapping changed: "
                                                                + group);
                                },
                                pool));
            gate.countDown();
            CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new))
                    .get(20, TimeUnit.SECONDS);
            if (StructureGenerationLocks.activeRotationInitializers() != 0)
                throw new IllegalStateException("Native cache initializer leaked");

            var group = groups[0];
            ((RotationCacheAccess) (Object) group).mosaic$directions(null);
            INJECT.set(true);
            try {
                group.rotate(Direction.UP);
                throw new IllegalStateException("Injected native failure was swallowed");
            } catch (InjectedFailure expectedFailure) {
                if (!OBSERVED.get())
                    throw new IllegalStateException("No nested native initializer");
            } finally {
                INJECT.set(false);
            }
            if (StructureGenerationLocks.activeRotationInitializers() != 0)
                throw new IllegalStateException("Exception retained a rotation binding");
            var recovered =
                    CompletableFuture.supplyAsync(() -> values(group), pool)
                            .get(3, TimeUnit.SECONDS);
            if (!Arrays.equals(expected[group.ordinal()], recovered))
                throw new IllegalStateException("Native rotation did not recover");
            System.clearProperty(FLAG);
            for (var current : groups)
                if (!Arrays.equals(expected[current.ordinal()], values(current)))
                    throw new IllegalStateException("Native continuation changed");
            var result = new JsonObject();
            result.addProperty("status", "PASS");
            result.addProperty("groups", groups.length);
            result.addProperty("directions", Direction.values().length);
            result.addProperty("frontAndTop", FrontAndTop.values().length);
            result.addProperty("parallelRounds", 64);
            result.addProperty("nativeFailureReported", OBSERVED.get());
            result.addProperty("recovered", true);
            result.addProperty(
                    "activeInitializers", StructureGenerationLocks.activeRotationInitializers());
            return result;
        } finally {
            INJECT.set(false);
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS))
                throw new IllegalStateException("Rotation workers did not terminate");
            saved.forEach(
                    (group, map) -> ((RotationCacheAccess) (Object) group).mosaic$directions(map));
            if (original == null) System.clearProperty(FLAG);
            else System.setProperty(FLAG, original);
        }
    }

    private static int[] values(OctahedralGroup group) {
        int[] result = new int[Direction.values().length + FrontAndTop.values().length];
        int index = 0;
        for (var direction : Direction.values())
            result[index++] = Objects.requireNonNull(group.rotate(direction)).ordinal();
        for (var orientation : FrontAndTop.values()) {
            var value = group.rotate(orientation);
            result[index++] = value == null ? -1 : value.ordinal();
        }
        return result;
    }
}
