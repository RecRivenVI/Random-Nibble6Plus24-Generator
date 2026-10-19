package io.github.recrivenvi.randomnibble6plus24generator.probe.features;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Real native inputs plus a labelled copy-isolation component check, never canonical mutation. */
final class SpawnCollisionEvidence {
    private SpawnCollisionEvidence() {}

    static JsonObject verify(
            ServerLevel level,
            SpawnBiomeSnapshot biomes,
            SpawnCollisionSnapshot snapshot,
            Map<Long, ProtoChunk> terrain) {
        snapshot.requireProvenance(biomes);
        long voxels = 0;
        for (var position : biomes.positions()) {
            var source = terrain.get(position.toLong());
            var view = snapshot.view(position);
            for (int y = source.getMinBuildHeight() - 1; y <= source.getMaxBuildHeight(); y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        var pos =
                                new BlockPos(
                                        position.getMinBlockX() + x,
                                        y,
                                        position.getMinBlockZ() + z);
                        if (view.getBlockState(pos) != source.getBlockState(pos)
                                || !view.getFluidState(pos).equals(source.getFluidState(pos)))
                            throw new IllegalStateException(
                                    "Detached population collision differs from native CARVERS at "
                                            + pos);
                        voxels++;
                    }
        }
        var first = biomes.positions().iterator().next();
        var clone =
                new ProtoChunk(
                        first,
                        UpgradeData.EMPTY,
                        level,
                        level.registryAccess().registryOrThrow(Registries.BIOME),
                        null);
        // A fresh component input, not a mutable copy of canonical data: native copy() palettes
        // retain their original resize callback. The actual native comparison above is untouched.
        clone.setBlockState(
                new BlockPos(
                        first.getMinBlockX() + 8,
                        clone.getMinBuildHeight() + clone.getHeight() / 2,
                        first.getMinBlockZ() + 8),
                Blocks.STONE.defaultBlockState(),
                false);
        clone.setPersistedStatus(ChunkStatus.CARVERS);
        var detached =
                SpawnCollisionSnapshot.capture(
                        biomes,
                        position ->
                                position.equals(first) ? clone : terrain.get(position.toLong()));
        var mutation =
                new BlockPos(
                        first.getMinBlockX() + 8,
                        clone.getMinBuildHeight() + clone.getHeight() / 2,
                        first.getMinBlockZ() + 8);
        var before = detached.view(first).getBlockState(mutation);
        var replacement =
                before.is(Blocks.GLASS)
                        ? Blocks.STONE.defaultBlockState()
                        : Blocks.GLASS.defaultBlockState();
        clone.setBlockState(mutation, replacement, false);
        if (clone.getBlockState(mutation) != replacement
                || detached.view(first).getBlockState(mutation) != before)
            throw new IllegalStateException("Population snapshot aliases its source containers");
        var proof = new JsonObject();
        proof.addProperty("nativeInputsIdentical", true);
        proof.addProperty("chunks", snapshot.chunks());
        proof.addProperty("voxels", voxels);
        proof.addProperty("copyMutationComponentOnly", true);
        proof.addProperty("hash", hash(biomes, snapshot));
        return proof;
    }

    static String hash(SpawnBiomeSnapshot biomes, SpawnCollisionSnapshot snapshot) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var position :
                    biomes.positions().stream()
                            .sorted(
                                    java.util.Comparator.comparingInt(
                                                    (net.minecraft.world.level.ChunkPos p) -> p.z)
                                            .thenComparingInt(p -> p.x))
                            .toList()) {
                var view = snapshot.view(position);
                for (int y = view.getMinBuildHeight(); y < view.getMaxBuildHeight(); y++)
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) {
                            var state =
                                    view.getBlockState(
                                            new BlockPos(
                                                    position.getMinBlockX() + x,
                                                    y,
                                                    position.getMinBlockZ() + z));
                            digest.update(state.toString().getBytes(StandardCharsets.UTF_8));
                            digest.update((byte) 0);
                        }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
