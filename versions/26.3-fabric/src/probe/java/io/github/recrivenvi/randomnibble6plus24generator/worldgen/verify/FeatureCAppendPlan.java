package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureContributionJournal.Projection;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Pure classification; changing an activated cell's attached effects is also a conflict. */
final class FeatureCAppendPlan {
    record Delta(Set<BlockPos> additions, List<BlockPos> conflicts) {}

    static Delta between(Projection previous, Projection desired) {
        var conflicts = new ArrayList<BlockPos>();
        for (var entry : previous.incoming().entrySet()) {
            var p = entry.getKey();
            if (desired.protectedEdits().containsKey(p)) continue;
            if (!entry.getValue().equals(desired.incoming().get(p))
                    || !previous.blockTicks().stream()
                            .filter(t -> t.pos().equals(p))
                            .toList()
                            .equals(
                                    desired.blockTicks().stream()
                                            .filter(t -> t.pos().equals(p))
                                            .toList())
                    || !previous.fluidTicks().stream()
                            .filter(t -> t.pos().equals(p))
                            .toList()
                            .equals(
                                    desired.fluidTicks().stream()
                                            .filter(t -> t.pos().equals(p))
                                            .toList())
                    || !previous.postProcessing().stream()
                            .filter(p::equals)
                            .toList()
                            .equals(desired.postProcessing().stream().filter(p::equals).toList()))
                conflicts.add(p);
        }
        conflicts.sort(Comparator.comparingLong(BlockPos::asLong));
        var additions = new HashSet<>(desired.incoming().keySet());
        additions.removeAll(previous.incoming().keySet());
        return new Delta(Set.copyOf(additions), List.copyOf(conflicts));
    }
}
