package raven.gradle;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoomRunArgumentsTest {
    @Test void multilineMaxHeapIsProjectedOutOfLoomTail() {
        var run = RepositoryConfigurationTest.config("instance.memory=64M\ninstance.extraJvmArgs=[\n-Xmx128M\n-Dmessage=雪 a b\n]\n", "instance.memory=96M").resolveInstance(RepositoryConfigurationTest.TARGET, "client");
        assertEquals("128M", run.effectiveMemory());
        assertEquals(List.of("-Drequired=kept", "-Dmessage=雪 a b"), new LoomRunArguments(List.of("-Drequired=kept"), run.jvmArgumentsWithoutMaxHeap()).asArguments());
    }
    @Test void cliCannotBypassManagedOptions() {
        assertThrows(IllegalArgumentException.class, () -> LoomRunArguments.rejectManagedOptions(List.of("--username=Other")));
        assertThrows(IllegalArgumentException.class, () -> LoomRunArguments.rejectManagedOptions(List.of("--gameDir", "other")));
        assertDoesNotThrow(() -> LoomRunArguments.rejectManagedOptions(List.of("--offlineDeveloperMode", "--quickPlayMultiplayer", "localhost:25565")));
    }
    @Test void fixtureIdentityAbsentSameOrConflictingIsExplicit() {
        var none = RepositoryConfigurationTest.config("", "").resolveInstance(RepositoryConfigurationTest.TARGET, "client");
        assertEquals(List.of("--username", "SpawnVerifier"), LoomRunArguments.clientArguments(none, "SpawnVerifier"));
        var same = RepositoryConfigurationTest.config("instance.playerName=SpawnVerifier", "").resolveInstance(RepositoryConfigurationTest.TARGET, "client");
        assertEquals(List.of("--username", "SpawnVerifier"), LoomRunArguments.clientArguments(same, "SpawnVerifier"));
        assertThrows(IllegalArgumentException.class, () -> LoomRunArguments.clientArguments(same, "OtherPlayer"));
        assertEquals(List.of(), LoomRunArguments.clientArguments(none, null));
    }
    @Test void tailKeepsRequiredBeforeWholeJsonArguments() {
        var tail = new LoomRunArguments(List.of("-Dmosaic.repositoryRoot=C:/a b"), List.of("-Dmessage=雪 a", "", "$(literal)"));
        assertEquals(List.of("-Dmosaic.repositoryRoot=C:/a b", "-Dmessage=雪 a", "", "$(literal)"), tail.asArguments());
    }
    @Test void emptyExtrasDoNotEraseRequiredCoordinate() {
        assertEquals(List.of("-Dmosaic.repositoryRoot=root"), new LoomRunArguments(List.of("-Dmosaic.repositoryRoot=root"), List.of()).asArguments());
    }
    @Test void extrasCannotOverrideRequiredCoordinate() {
        assertThrows(IllegalArgumentException.class, () -> new LoomRunArguments(List.of(), List.of("-Dmosaic.repositoryRoot=other")));
    }
}
