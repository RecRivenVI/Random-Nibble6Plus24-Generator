package io.github.recrivenvi.randomnibble6plus24generator.probe.seed;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.NativePlanDependencyAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalGenerationPlan;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Native metadata proof only; it does not create physical Mosaic chunks or claim FULL support. */
public final class NativePhysicalPlanVerifier {
    public static final String PREFIX = ModConstants.MOD_ID + ".physicalPlan.";

    private NativePhysicalPlanVerifier() {}

    public static void run(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Expected server lifecycle");
        JsonObject proof = new JsonObject();
        proof.addProperty("schema", 1);
        proof.addProperty("target", System.getProperty("validation.target"));
        proof.addProperty("inputHash", System.getProperty(PREFIX + "inputHash"));
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("nativeLifecycle", "ServerStartedEvent");
        proof.addProperty("scope", "NATIVE_DEPENDENCY_METADATA_NOT_PHYSICAL_FULL");
        var variant = System.getProperty(PREFIX + "variant", "native-plan");
        if (!variant.matches("[a-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid metadata evidence variant");
        proof.add("mods", NativeStructureVerifier.environment(server.overworld()).get("mods"));
        var checks = new JsonArray();
        try {
            var nativeMap =
                    (NativePlanDependencyAccess) server.overworld().getChunkSource().chunkMap;
            for (var owner :
                    new ChunkPos[] {
                        new ChunkPos(0, 0), new ChunkPos(125, -37), new ChunkPos(-125, 37)
                    }) {
                for (var desired : BuiltInRegistries.CHUNK_STATUS) {
                    var plan = MosaicPhysicalGenerationPlan.derive(owner, desired);
                    var expected = nativeRequirements(nativeMap, owner, desired);
                    if (!expected.equals(plan.requirements()))
                        throw new IllegalStateException(
                                "Native dependency closure differs " + desired + " " + owner);
                    var artifacts = new HashMap<ChunkPos, ChunkStatus>();
                    expected.forEach(
                            (pos, status) -> {
                                if (status.isOrAfter(ChunkStatus.BIOMES))
                                    artifacts.put(pos, status);
                            });
                    if (desired.isOrAfter(ChunkStatus.STRUCTURE_STARTS)
                            && ChunkStatus.FEATURES.isOrAfter(desired))
                        artifacts.put(owner, ChunkStatus.FEATURES);
                    if (!artifacts.equals(plan.artifacts()))
                        throw new IllegalStateException(
                                "Missing physical data obligation " + desired);
                    try {
                        plan.requirements().put(owner, ChunkStatus.FULL);
                        throw new IllegalStateException("Mutable dependency plan");
                    } catch (UnsupportedOperationException expectedReadOnly) {
                        // Plans are values, not mutable scheduler authority.
                    }
                    var check = new JsonObject();
                    check.addProperty("x", owner.x);
                    check.addProperty("z", owner.z);
                    check.addProperty(
                            "status", BuiltInRegistries.CHUNK_STATUS.getKey(desired).toString());
                    check.addProperty("requirements", expected.size());
                    check.addProperty("artifacts", artifacts.size());
                    check.addProperty("nativeEqual", true);
                    checks.add(check);
                }
            }
            if (checks.size() != 36)
                throw new IllegalStateException("Changed native status registry");
            proof.addProperty("status", "PASS");
        } catch (RuntimeException | Error failure) {
            proof.addProperty("status", "FAIL");
            proof.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            proof.add("checks", checks);
            NativeStructureVerifier.write(
                    Path.of(System.getProperty("validation.result"))
                            .resolve(variant)
                            .resolve(System.getProperty("validation.target"))
                            .resolve("native-plan.json"),
                    proof);
            server.halt(false);
        }
    }

    private record Request(ChunkPos owner, ChunkStatus status) {}

    private static Map<ChunkPos, ChunkStatus> nativeRequirements(
            NativePlanDependencyAccess nativeMap, ChunkPos owner, ChunkStatus status) {
        Map<ChunkPos, ChunkStatus> expected = new HashMap<>();
        if (status == ChunkStatus.EMPTY) return expected;
        var pending = new ArrayDeque<Request>();
        var visited = new HashSet<Request>();
        pending.add(new Request(owner, status));
        while (!pending.isEmpty()) {
            var request = pending.removeFirst();
            if (!visited.add(request)) continue;
            expected.merge(
                    request.owner(),
                    request.status(),
                    (left, right) -> left.isOrAfter(right) ? left : right);
            if (request.status() == ChunkStatus.EMPTY) continue;
            int range = request.status().getRange();
            for (int z = -range; z <= range; z++)
                for (int x = -range; x <= range; x++)
                    pending.addLast(
                            new Request(
                                    new ChunkPos(request.owner().x + x, request.owner().z + z),
                                    nativeMap.mosaic$nativeDependency(
                                            request.status(), Math.max(Math.abs(x), Math.abs(z)))));
        }
        return expected;
    }
}
