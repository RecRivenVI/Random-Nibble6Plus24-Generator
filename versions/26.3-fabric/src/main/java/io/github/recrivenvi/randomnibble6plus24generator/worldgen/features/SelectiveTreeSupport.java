package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.*;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * The C transport is narrower than the tree evaluator: audit every possible output before writing.
 */
public final class SelectiveTreeSupport {
    private SelectiveTreeSupport() {}

    public static String unsupported(PlacedFeature feature, HolderLookup.Provider registries) {
        // Reuse the bounded evaluator audit, then independently require transport-safe outputs.
        String unsupported = TreeContributionSupport.unsupportedNeighbor(feature, registries);
        if (unsupported != null) return unsupported;
        try {
            audit(feature, registries);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static void audit(PlacedFeature placed, HolderLookup.Provider registries) {
        var feature = placed.feature().value();
        if (feature instanceof net.minecraft.world.level.levelgen.feature.RandomSelectorFeature s) {
            for (var branch : s.features()) audit(branch.feature().value(), registries);
            audit(s.defaultFeature().value(), registries);
            return;
        }
        if (feature instanceof TreeFeature t) {
            provider(t.trunkProvider().value());
            provider(t.foliageProvider().value());
            provider(t.belowTrunkProvider().value());
            t.decorators().forEach(d -> decorator(d, registries));
        } else if (feature instanceof FallenTreeFeature t) {
            provider(t.trunkProvider().value());
            t.logDecorators().forEach(d -> decorator(d, registries));
        }
    }

    private static void provider(BlockStateProvider provider) {
        if (provider instanceof SimpleStateProvider p) state(p.state());
        else if (provider instanceof WeightedStateProvider p)
            p.weightedList().unwrap().forEach(s -> state(s.value()));
        else if (provider instanceof RuleBasedStateProvider p) {
            if (p.fallback() != null) provider(p.fallback().value());
            p.rules().forEach(r -> provider(r.then().value()));
        }
    }

    private static void state(net.minecraft.world.level.block.state.BlockState state) {
        if (!TreeContribution.supportedState(state))
            throw new IllegalArgumentException("unaudited C output " + state);
    }

    private static void decorator(TreeDecorator d, HolderLookup.Provider registries) {
        if (d instanceof AttachedToLogsDecorator) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            var json = TreeDecorator.CODEC.encodeStart(ops, d).getOrThrow().getAsJsonObject();
            provider(
                    BlockStateProvider.CODEC
                            .parse(ops, json.get("block_provider"))
                            .getOrThrow()
                            .value());
        }
    }
}
