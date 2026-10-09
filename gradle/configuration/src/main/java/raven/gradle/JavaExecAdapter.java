package raven.gradle;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

/** Explicit opt-in adapter for ordinary JavaExec. Loader run DSLs may consume the model directly. */
public final class JavaExecAdapter {
    private JavaExecAdapter() {}

    public static void configure(JavaExec task, RepositoryConfiguration configuration, String target, String variant,
            List<String> requiredJvmArgs, List<String> requiredGameArgs) {
        configuration.targetFacts(target);
        if (task.getExtensions().getExtraProperties().has("raven.instanceConfigured")) {
            throw new GradleException("Instance adapter already installed on " + task.getPath());
        }
        task.getExtensions().getExtraProperties().set("raven.instanceConfigured", true);
        File executable = configuration.javaExecutable(configuration.javaVersion(target));
        if (executable != null) {
            // Set before task input calculation: Gradle derives javaLauncher from this
            // executable. Changing it in doFirst is too late for toolchain consistency.
            task.setExecutable(executable);
        } else {
            var toolchains = task.getProject().getExtensions().getByType(JavaToolchainService.class);
            task.getJavaLauncher().convention(toolchains.launcherFor(spec ->
                    spec.getLanguageVersion().set(JavaLanguageVersion.of(configuration.javaVersion(target)))));
        }
        task.doFirst(new PrepareRun(configuration, target, variant, List.copyOf(requiredJvmArgs), List.copyOf(requiredGameArgs)));
    }

    // Gradle 9.5 configuration-cache decoding requires a public record constructor.
    public record PrepareRun(RepositoryConfiguration configuration, String target, String variant,
            List<String> requiredJvmArgs, List<String> requiredGameArgs) implements Action<Task>, Serializable {
        @Override public void execute(Task task) {
            JavaExec run = (JavaExec) task;
            var settings = configuration.resolveInstance(target, variant);
            // This adapter owns these ordinary Minecraft client options. Loader DSL adapters
            // should set their native properties instead of adding a competing copy here.
            Set<String> owned = Set.of("--width", "--height", "--username", "--gameDir");
            if (!variant.equals("server")) {
                for (String arg : run.getArgs() == null ? List.<String>of() : run.getArgs()) {
                    if (owned.contains(arg.split("=", 2)[0])) throw new GradleException("JavaExec already defines an instance-managed option; use the Loader's native run adapter");
                }
                for (String arg : requiredGameArgs) {
                    if (owned.contains(arg.split("=", 2)[0])) throw new GradleException("Required arguments must not duplicate instance-managed options");
                }
            }
            for (String arg : requiredJvmArgs) {
                if (arg.startsWith("-Xmx")) throw new GradleException("Use instance.memory instead of required -Xmx");
            }
            configuration.javaExecutable(configuration.javaVersion(target));
            try { Files.createDirectories(settings.directory().toPath()); }
            catch (IOException e) { throw new GradleException("Cannot create instance working directory", e); }
            run.setWorkingDir(settings.directory());
            if (settings.effectiveMemory() != null) run.setMaxHeapSize(settings.effectiveMemory());
            run.jvmArgs(requiredJvmArgs);
            run.jvmArgs(settings.jvmArgumentsWithoutMaxHeap());
            run.args(requiredGameArgs);
            run.args(settings.clientArguments());
            run.args(settings.extraGameArgs());
        }
    }
}
