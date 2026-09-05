package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MosaicGenerationLifecycleTest {
    @Test void cancellationCannotBeCreatedWithoutAnExplicitClose() {
        var life = new MosaicGenerationLifecycle();
        assertThrows(IllegalStateException.class, life::cancellation);
        assertFalse(life.isExpected(new CancellationException()));
    }

    @Test void recognizesItsOwnSignalThroughCompletionStageWrappers() {
        var life = new MosaicGenerationLifecycle();
        life.activate(); life.beginClosing();
        var cancelled = life.cancellation();
        assertTrue(life.isExpected(cancelled));
        assertTrue(life.isExpected(new CompletionException(new ExecutionException(new CompletionException(cancelled)))));
    }

    @Test void plainUnexpectedCancellationStillFailsEvenDuringClose() {
        var life = new MosaicGenerationLifecycle();
        life.activate(); life.beginClosing();
        assertFalse(life.isExpected(new CancellationException("Mosaic generation terminated by its world shutdown lifecycle")));
        assertFalse(life.isExpected(new CompletionException(new CancellationException())));
    }

    @Test void oneWorldCannotSuppressAnotherWorldsSignal() {
        var first = new MosaicGenerationLifecycle(); var second = new MosaicGenerationLifecycle();
        first.activate(); second.activate(); first.beginClosing(); second.beginClosing();
        assertFalse(first.isExpected(second.cancellation()));
    }

    @Test void realErrorsWithCancellationCausesAreNotSilenced() {
        var life = new MosaicGenerationLifecycle(); life.activate(); life.beginClosing();
        var error = new IllegalStateException("Corrupt generation data", life.cancellation());
        assertFalse(life.isExpected(error));
        assertFalse(life.isExpected(new CompletionException(error)));
        assertFalse(life.isExpected(null));
    }

    @Test void closeRejectsNewWorkAndDrainsExistingWorkers() {
        var life = new MosaicGenerationLifecycle();
        assertTrue(life.activate()); life.workerAccepted();
        assertFalse(life.quiescent()); life.beginClosing();
        assertFalse(life.activate());
        var failure = assertThrows(CancellationException.class, life::workerAccepted);
        assertTrue(life.isExpected(failure));
        life.workerFinished(); assertTrue(life.quiescent());
        assertEquals(0, life.snapshot().workers());
    }
}
