package io.github.recrivenvi.randomnibble6plus24generator.lazy;

import java.util.*;
import net.minecraft.world.level.levelgen.RandomState;

public final class LazyScopes {
    private static final Map<RandomState, TargetStructureScope> SCOPES = new IdentityHashMap<>();
    private static final Map<String, Long> TOTALS = new TreeMap<>();

    public static synchronized void bind(RandomState random, TargetStructureScope scope) {
        SCOPES.put(random, scope);
    }

    public static synchronized TargetStructureScope get(RandomState random) {
        return SCOPES.get(random);
    }

    public static synchronized void remove(RandomState random) {
        var scope = SCOPES.remove(random);
        if (scope == null) return;
        TOTALS.merge("closedContexts", 1L, Long::sum);
        scope.metrics()
                .forEach(
                        (key, value) -> {
                            if (value instanceof Number number && !key.equals("radius"))
                                TOTALS.merge(key, number.longValue(), Long::sum);
                        });
    }

    public static synchronized Map<String, Long> totals() {
        return Map.copyOf(TOTALS);
    }

    public static synchronized int count() {
        return SCOPES.size();
    }
}
