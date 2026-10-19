package io.github.recrivenvi.randomnibble6plus24generator.validation;

import io.github.recrivenvi.randomnibble6plus24generator.performance.StructureReuseHistory;
import java.util.List;

public final class StructureReuseHistoryTest {
    public static void main(String[] args) {
        var history = new StructureReuseHistory();
        Object first = new String("same"), second = new String("same");
        history.observe(1, first, 10);
        history.observe(1, second, 20);
        history.observe(1, first, 30);
        require(history.counters().get("evaluations") == 3, "evaluations");
        require(history.counters().get("repeat2") == 1, "identity, not equals");
        require(history.counters().get("nanos2") == 30, "only repeated native cost");
        history.invalidate(List.of(1L));
        history.observe(1, first, 40);
        require(history.counters().get("repeat2") == 1, "handoff invalidates history");
        history.observe(2, null, 50);
        history.observe(2, second, 60);
        history.observe(2, null, 70);
        require(history.counters().get("repeat2") == 2, "null native state");
        var bounded = new StructureReuseHistory();
        Object oldest = new Object();
        bounded.observe(1, oldest, 1);
        for (int i = 0; i < 16; i++) bounded.observe(1, new Object(), 1);
        bounded.observe(1, oldest, 1);
        require(bounded.counters().get("maximumStates") == 16, "bounded token retention");
        require(bounded.counters().get("repeat16") == 0, "evicted token is not known");
        history.clear();
        require(history.retainedPositions() == 0, "close releases tokens");
        System.out.println("PASS: native token identity, cost, handoff, null, bound and cleanup");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
