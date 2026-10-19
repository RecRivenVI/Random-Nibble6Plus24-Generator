package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.mojang.serialization.JsonOps;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Audits rule capabilities; actual per-voxel decisions still call the original RuleTest. */
public final class OreRuleSupport {
    private OreRuleSupport() {}

    private static final class AirStates {
        // Block state definitions are process-wide immutable registry data, not world/seed data.
        static final List<BlockState> VALUES =
                java.util.stream.StreamSupport.stream(
                                Block.BLOCK_STATE_REGISTRY.spliterator(), false)
                        .filter(BlockState::isAir)
                        .toList();
    }

    public static void requireSafe(RuleTest rule) {
        Node node = audit(rule, 0);
        if (AirStates.VALUES.stream().anyMatch(s -> node.onAir(s).mayTrue()))
            throw new IllegalArgumentException("replacement can create blocks in empty space");
    }

    private record Truth(boolean mayTrue, boolean mayFalse) {}

    private record Node(RuleTest rule, List<Node> children) {
        Truth onAir(BlockState state) {
            Class<?> type = rule.getClass();
            if (type == HeightMatchTest.class) return new Truth(true, true);
            if (type == BlockMatchTest.class || type == TagMatchTest.class) {
                boolean match = rule.test(state, BlockPos.ZERO, RandomSource.create(0));
                return new Truth(match, !match);
            }
            if (type == NotRuleTest.class) {
                var t = children.getFirst().onAir(state);
                return new Truth(t.mayFalse(), t.mayTrue());
            }
            boolean yes = type == AllOfRuleTest.class, no = type == AnyOfRuleTest.class;
            for (var child : children) {
                Truth t = child.onAir(state);
                yes = type == AllOfRuleTest.class ? yes && t.mayTrue() : yes || t.mayTrue();
                no = type == AllOfRuleTest.class ? no || t.mayFalse() : no && t.mayFalse();
            }
            return new Truth(yes, no);
        }
    }

    private static Node audit(RuleTest rule, int depth) {
        if (depth > 64) throw new IllegalArgumentException("unaudited rule nesting depth");
        Class<?> type = rule.getClass();
        if (type == BlockMatchTest.class
                || type == TagMatchTest.class
                || type == HeightMatchTest.class) return new Node(rule, List.of());
        if (type != AllOfRuleTest.class && type != AnyOfRuleTest.class && type != NotRuleTest.class)
            throw new IllegalArgumentException(
                    "random or unaudited replacement rule " + type.getSimpleName());
        // Native codecs expose private children without reflection. Decode through the same native
        // contract, but never replace predicate evaluation with this conservative capability test.
        var encoded =
                RuleTest.CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow().getAsJsonObject();
        List<Node> children = new ArrayList<>();
        if (type == NotRuleTest.class)
            children.add(
                    audit(
                            RuleTest.CODEC
                                    .parse(JsonOps.INSTANCE, encoded.get("rule"))
                                    .getOrThrow(),
                            depth + 1));
        else
            for (var child : encoded.getAsJsonArray("rules"))
                children.add(
                        audit(
                                RuleTest.CODEC.parse(JsonOps.INSTANCE, child).getOrThrow(),
                                depth + 1));
        return new Node(rule, List.copyOf(children));
    }
}
