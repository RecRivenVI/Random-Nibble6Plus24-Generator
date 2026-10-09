package raven.gradle;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.tasks.Input;
import org.gradle.process.CommandLineArgumentProvider;

/** Project-specific tail provider; does not replace Loom's native argument providers. */
public final class LoomRunArguments implements CommandLineArgumentProvider, Serializable {
    /** Explicit CLI --args must not compete with properties or fixture-managed identity. */
    public static final class ManagedOptionsGuard implements org.gradle.api.Action<org.gradle.api.Task>, Serializable {
        @Override public void execute(org.gradle.api.Task task) {
            var run = (org.gradle.api.tasks.JavaExec) task;
            rejectManagedOptions(run.getArgs() == null ? List.of() : run.getArgs());
        }
    }
    public static void rejectManagedOptions(List<String> args) {
        for (String arg : args) {
            if (List.of("--width", "--height", "--username", "--gameDir").contains(arg.split("=", 2)[0])) {
                throw new IllegalArgumentException("Use instance settings or the fixture identity, not managed options in --args");
            }
        }
    }
    public static List<String> clientArguments(RepositoryConfiguration.ResolvedInstance settings, String fixturePlayer) {
        if (fixturePlayer == null || settings.variant().equals("server")) return settings.clientArguments();
        if (!fixturePlayer.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Invalid spawn fixture player name");
        if (settings.playerName() != null && !settings.playerName().equals(fixturePlayer)) {
            throw new IllegalArgumentException("Configured playerName conflicts with spawn fixture identity");
        }
        var args = new ArrayList<>(settings.clientArguments());
        if (settings.playerName() == null) args.addAll(List.of("--username", fixturePlayer));
        return List.copyOf(args);
    }
    private final List<String> required;
    private final List<String> extras;
    public LoomRunArguments(List<String> required, List<String> extras) {
        if (extras.stream().anyMatch(arg -> arg.equals("-Dmosaic.repositoryRoot") || arg.startsWith("-Dmosaic.repositoryRoot="))) {
            throw new IllegalArgumentException("extraJvmArgs cannot replace Mosaic's required repository coordinate");
        }
        this.required = List.copyOf(required);
        this.extras = List.copyOf(extras);
    }
    @Input public List<String> getRequired() { return required; }
    @Input public List<String> getExtras() { return extras; }
    @Override public Iterable<String> asArguments() {
        var args = new ArrayList<>(required);
        args.addAll(extras);
        return List.copyOf(args);
    }
}
