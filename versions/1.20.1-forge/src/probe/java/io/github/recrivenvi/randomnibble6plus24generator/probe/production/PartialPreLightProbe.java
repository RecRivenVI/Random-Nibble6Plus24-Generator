package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin.PhysicalStateAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/** Real native INITIALIZE_LIGHT save, then new-JVM FULL; no synthetic partial disk state. */
public final class PartialPreLightProbe {
    private static ServerLevel level;
    private static ChunkPos target;
    private static Path common;
    private static String role;
    private static String pipeline;
    private static final java.util.concurrent.ConcurrentHashMap<Thread, ChunkPos> WRITERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<
                    net.minecraft.world.level.chunk.LevelChunkSection, net.minecraft.core.BlockPos>
            SECTIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final AtomicInteger POPULATION = new AtomicInteger(), COLD = new AtomicInteger();

    private PartialPreLightProbe() {}

    public static boolean enabled() {
        return NativePhysicalProbe.enabled()
                && Boolean.getBoolean(NativePhysicalProbe.PREFIX + "partialPreLight");
    }

    public static boolean staging() {
        return enabled() && "fresh".equals(role);
    }

    public static void started(
            ServerLevel host, ChunkPos pos, Path folder, String phase, JsonObject proof)
            throws IOException {
        if (!enabled()) return;
        level = host;
        target = pos;
        common = folder;
        role = phase;
        pipeline = proof.get("pipeline").getAsString();
        proof.addProperty("scope", "NATIVE_PRE_LIGHT_SAVE_RESUME_NOT_PLAYER_ENTRY");
        if (phase.equals("fresh")) return;
        var raw = host.getChunkSource().chunkMap.read(pos).join().orElseThrow();
        var expected = phase.equals("resume") ? ChunkStatus.INITIALIZE_LIGHT : ChunkStatus.FULL;
        require(
                ChunkStatus.byName(raw.getString("Status")) == expected,
                "Wrong actual persisted native checkpoint " + raw.getString("Status"));
        proof.addProperty("loadedStatus", raw.getString("Status"));
        require(
                projection(raw)
                        .equals(
                                NbtIo.readCompressed(
                                        common.resolve(
                                                        phase.equals("resume")
                                                                ? "partial-projection.nbt"
                                                                : "full-projection.nbt")
                                                .toFile())),
                "Disk blocks/biomes/structures changed before native load");
        proof.addProperty("diskProjectionEqual", true);
    }

    public static void completed(ChunkAccess chunk, JsonObject proof) throws IOException {
        if (!enabled()) return;
        require(level.getServer().isSameThread(), "Partial snapshot off server thread");
        var current = projection(ChunkSerializer.write(level, chunk));
        if (staging()) {
            require(
                    chunk.getStatus() == ChunkStatus.INITIALIZE_LIGHT && POPULATION.get() == 0,
                    "Partial checkpoint advanced into population");
            NbtIo.writeCompressed(current, common.resolve("partial-projection.nbt").toFile());
            if (proof.get("pipeline").getAsString().equals("b")) {
                var state = ((MosaicPhysicalLevelOwner) level).mosaic$physicalState();
                var biomes =
                        Objects.requireNonNull(
                                ((PhysicalStateAccess) (Object) state)
                                        .mosaic$spawnBiomes()
                                        .get(chunk));
                NbtIo.writeCompressed(
                        palettes(biomes), common.resolve("warm-spawn-biomes.nbt").toFile());
            }
        } else {
            var expected =
                    NbtIo.readCompressed(
                            common.resolve(
                                            role.equals("resume")
                                                    ? "partial-projection.nbt"
                                                    : "full-projection.nbt")
                                    .toFile());
            boolean nativeResume = pipeline.equals("vanilla") && role.equals("resume");
            PartialIncomingProof.compare(level, target, expected, current, nativeResume, common);
            if (nativeResume) proof.addProperty("nativeNeighborReplayExact", true);
            if (role.equals("resume"))
                NbtIo.writeCompressed(current, common.resolve("full-projection.nbt").toFile());
            require(
                    POPULATION.get() == (role.equals("resume") ? 1 : 0),
                    "Native population was repeated/skipped");
            boolean mosaic = proof.get("pipeline").getAsString().equals("b");
            require(
                    COLD.get() == (mosaic && role.equals("resume") ? 1 : 0),
                    "Cold palette fallback was repeated/skipped");
            proof.addProperty("targetProjectionEqual", true);
        }
        proof.addProperty("rootPopulationCalls", POPULATION.get());
        proof.addProperty("coldBiomeComparisons", COLD.get());
        proof.addProperty("incomingNativeWrites", PartialIncomingProof.count());
    }

    public static void population(WorldGenRegion region) {
        if (enabled() && region.getLevel() == level && region.getCenter().equals(target))
            POPULATION.incrementAndGet();
    }

    public static void loaded(ServerLevel host, ChunkAccess chunk) {
        if (!enabled() || staging() || host != level || !chunk.getPos().equals(target)) return;
        var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++)
            SECTIONS.put(
                    sections[i],
                    new net.minecraft.core.BlockPos(
                            target.getMinBlockX(),
                            chunk.getSectionYFromSectionIndex(i) * 16,
                            target.getMinBlockZ()));
    }

    public static void decoration(
            net.minecraft.world.level.WorldGenLevel source, ChunkAccess chunk, boolean enter) {
        if (!enabled()
                || staging()
                || !pipeline.equals("vanilla")
                || !role.equals("resume")
                || !(source instanceof WorldGenRegion region)
                || region.getLevel() != level) return;
        require(region.getCenter().equals(chunk.getPos()), "Native decoration center changed");
        if (enter)
            require(
                    WRITERS.putIfAbsent(Thread.currentThread(), chunk.getPos()) == null,
                    "Nested native decoration observation");
        else
            require(
                    chunk.getPos().equals(WRITERS.remove(Thread.currentThread())),
                    "Native decoration scope lost");
    }

    public static void write(
            net.minecraft.world.level.chunk.LevelChunkSection section,
            int x,
            int y,
            int z,
            net.minecraft.world.level.block.state.BlockState before,
            net.minecraft.world.level.block.state.BlockState after) {
        if (!enabled() || staging() || before == null || before == after) return;
        var base = SECTIONS.get(section);
        if (base == null) return;
        var writer = WRITERS.get(Thread.currentThread());
        require(
                pipeline.equals("vanilla")
                        && role.equals("resume")
                        && writer != null
                        && !writer.equals(target)
                        && Math.max(Math.abs(writer.x - target.x), Math.abs(writer.z - target.z))
                                <= 1,
                "Unexpected physical generation write outside native FEATURES neighbor replay");
        require(
                (x | y | z) >= 0 && x < 16 && y < 16 && z < 16,
                "Native section write outside section");
        PartialIncomingProof.record(base.offset(x, y, z), before, after, writer);
    }

    public static void closed(JsonObject proof) {
        if (!enabled()) return;
        int active = WRITERS.size();
        int sections = SECTIONS.size();
        int expectedSections = level.getSectionsCount();
        WRITERS.clear();
        SECTIONS.clear();
        level = null;
        target = null;
        common = null;
        proof.addProperty("partialWriterScopes", active);
        proof.addProperty("partialLoadedSections", sections);
        require(
                active == 0 || proof.get("status").getAsString().equals("FAIL"),
                "Native decoration observations leaked");
        require(
                role.equals("fresh")
                        || sections == expectedSections
                        || proof.get("status").getAsString().equals("FAIL"),
                "Native disk section identities were not observed");
    }

    public static void coldBiomes(ServerLevel host, ChunkPos pos, SpawnBiomeSnapshot snapshot) {
        if (!enabled() || host != level || !pos.equals(target)) return;
        require("resume".equals(role), "Cold target palettes reconstructed outside resume");
        try {
            require(
                    palettes(snapshot)
                            .equals(
                                    NbtIo.readCompressed(
                                            common.resolve("warm-spawn-biomes.nbt").toFile())),
                    "Cold same-seed stored palettes differ from the carried generation inputs");
            COLD.incrementAndGet();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static CompoundTag palettes(SpawnBiomeSnapshot snapshot) {
        var result = new CompoundTag();
        for (var pos : SpawnBiomeSnapshot.neighbors(target)) {
            var cells = new ListTag();
            for (int y = snapshot.minY(pos) / 4;
                    y < (snapshot.minY(pos) + snapshot.height(pos)) / 4;
                    y++)
                for (int z = 0; z < 4; z++)
                    for (int x = 0; x < 4; x++)
                        cells.add(
                                StringTag.valueOf(
                                        snapshot.biome(pos, pos.x * 4 + x, y, pos.z * 4 + z)
                                                .unwrapKey()
                                                .orElseThrow()
                                                .location()
                                                .toString()));
            result.put(Long.toString(pos.toLong()), cells);
        }
        return result;
    }

    private static CompoundTag projection(CompoundTag raw) {
        var result = new CompoundTag();
        result.put("structures", raw.getCompound("structures").copy());
        var sections = new ListTag();
        for (var value : raw.getList("sections", Tag.TAG_COMPOUND)) {
            var section = (CompoundTag) value;
            if (!section.contains("block_states") && !section.contains("biomes")) continue;
            var cell = new CompoundTag();
            cell.putByte("Y", section.getByte("Y"));
            if (section.contains("block_states"))
                cell.put("block_states", section.getCompound("block_states").copy());
            if (section.contains("biomes"))
                cell.put("biomes", section.getCompound("biomes").copy());
            sections.add(cell);
        }
        require(!sections.isEmpty(), "No real native section data");
        result.put("sections", sections);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
