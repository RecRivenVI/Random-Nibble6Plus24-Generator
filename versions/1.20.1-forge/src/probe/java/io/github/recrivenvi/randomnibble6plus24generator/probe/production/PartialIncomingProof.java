package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkSerializerAccess;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Observe actual native writes; never reorder execution, substitute state or relax Mosaic parity.
 */
final class PartialIncomingProof {
    private record Write(
            BlockPos pos, BlockState before, BlockState after, ChunkPos writer, String thread) {}

    private static final ConcurrentLinkedQueue<Write> WRITES = new ConcurrentLinkedQueue<>();

    private PartialIncomingProof() {}

    static int count() {
        return WRITES.size();
    }

    static void record(BlockPos pos, BlockState before, BlockState after, ChunkPos writer) {
        WRITES.add(
                new Write(
                        pos.immutable(), before, after, writer, Thread.currentThread().getName()));
    }

    static void compare(
            ServerLevel level,
            ChunkPos target,
            CompoundTag expected,
            CompoundTag actual,
            boolean nativeResume,
            Path folder)
            throws IOException {
        require(
                expected.getCompound("structures").equals(actual.getCompound("structures")),
                "Structure metadata changed");
        var before = sections(expected);
        var after = sections(actual);
        require(before.keySet().equals(after.keySet()), "Native section range changed");
        Map<BlockPos, List<Write>> grouped = new HashMap<>();
        var journal = new ListTag();
        for (var event : WRITES) {
            grouped.computeIfAbsent(event.pos(), key -> new ArrayList<>()).add(event);
            var tag = new CompoundTag();
            tag.putLong("position", event.pos().asLong());
            tag.putLong("writer", event.writer().toLong());
            tag.put("before", NbtUtils.writeBlockState(event.before()));
            tag.put("after", NbtUtils.writeBlockState(event.after()));
            tag.putString("stage", "minecraft:features");
            tag.putString("thread", event.thread());
            journal.add(tag);
        }
        var saved = new CompoundTag();
        saved.put("writes", journal);
        NbtIo.writeCompressed(saved, folder.resolve("native-incoming-writes.nbt").toFile());
        require(
                nativeResume || grouped.isEmpty(),
                "Mosaic or FULL reload received new worldgen writes");
        var stateCodec = ChunkSerializerAccess.blockStates();
        var biomeCodec =
                ChunkSerializerAccess.biomes(
                        level.registryAccess().registryOrThrow(Registries.BIOME));
        for (int sectionY : before.keySet()) {
            var a = before.get(sectionY);
            var b = after.get(sectionY);
            var aStates =
                    stateCodec
                            .parse(NbtOps.INSTANCE, a.getCompound("block_states"))
                            .getOrThrow(false, PartialIncomingProof::invalid);
            var bStates =
                    stateCodec
                            .parse(NbtOps.INSTANCE, b.getCompound("block_states"))
                            .getOrThrow(false, PartialIncomingProof::invalid);
            var aBiomes =
                    biomeCodec
                            .parse(NbtOps.INSTANCE, a.getCompound("biomes"))
                            .getOrThrow(false, PartialIncomingProof::invalid);
            var bBiomes =
                    biomeCodec
                            .parse(NbtOps.INSTANCE, b.getCompound("biomes"))
                            .getOrThrow(false, PartialIncomingProof::invalid);
            for (int y = 0; y < 4; y++)
                for (int z = 0; z < 4; z++)
                    for (int x = 0; x < 4; x++)
                        require(
                                aBiomes.get(x, y, z).equals(bBiomes.get(x, y, z)),
                                "Stored biome changed during native FULL");
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        var pos =
                                new BlockPos(
                                        target.getMinBlockX() + x,
                                        sectionY * 16 + y,
                                        target.getMinBlockZ() + z);
                        var events = grouped.remove(pos);
                        var initial = aStates.get(x, y, z);
                        var last = bStates.get(x, y, z);
                        if (events == null)
                            require(initial == last, "Unexplained target block change at " + pos);
                        else transitions(pos, initial, last, events);
                    }
        }
        require(grouped.isEmpty(), "Observed a write outside saved sections");
    }

    private static void transitions(
            BlockPos pos, BlockState initial, BlockState last, List<Write> events) {
        Map<BlockState, ArrayDeque<BlockState>> edges = new IdentityHashMap<>();
        for (var event : events)
            edges.computeIfAbsent(event.before(), key -> new ArrayDeque<>()).add(event.after());
        // All observed transitions must form a complete trail; callback arrival is not execution
        // order.
        var stack = new ArrayDeque<BlockState>();
        var trail = new ArrayList<BlockState>();
        stack.push(initial);
        int consumed = 0;
        while (!stack.isEmpty()) {
            var next = edges.get(stack.peek());
            if (next == null || next.isEmpty()) trail.add(stack.pop());
            else {
                stack.push(next.remove());
                consumed++;
            }
        }
        Collections.reverse(trail);
        require(
                consumed == events.size()
                        && trail.size() == consumed + 1
                        && trail.get(0) == initial
                        && trail.get(trail.size() - 1) == last,
                "Native writes do not explain target state at " + pos);
        var remaining = new ArrayList<>(events);
        for (int i = 1; i < trail.size(); i++) {
            int edge = -1;
            for (int j = 0; j < remaining.size(); j++)
                if (remaining.get(j).before() == trail.get(i - 1)
                        && remaining.get(j).after() == trail.get(i)) {
                    edge = j;
                    break;
                }
            require(edge >= 0, "Native transition trail invented an edge");
            remaining.remove(edge);
        }
        require(remaining.isEmpty(), "Native transition trail lost a write");
    }

    private static Map<Integer, CompoundTag> sections(CompoundTag raw) {
        require(raw.contains("structures", Tag.TAG_COMPOUND), "Missing native structure metadata");
        Map<Integer, CompoundTag> result = new TreeMap<>();
        for (var cell : raw.getList("sections", Tag.TAG_COMPOUND)) {
            var section = (CompoundTag) cell;
            require(
                    section.contains("block_states", Tag.TAG_COMPOUND)
                            && section.contains("biomes", Tag.TAG_COMPOUND),
                    "Incomplete native palette data");
            require(
                    result.put((int) section.getByte("Y"), section) == null,
                    "Duplicate native section");
        }
        require(!result.isEmpty(), "No native sections");
        return result;
    }

    private static void invalid(String error) {
        throw new IllegalStateException(error);
    }

    private static void require(boolean condition, String error) {
        if (!condition) invalid(error);
    }
}
