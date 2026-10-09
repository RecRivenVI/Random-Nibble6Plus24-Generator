package raven.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Explicit diagnostic task without output files")
public abstract class PrintInstanceConfigurationTask extends DefaultTask {
    @Internal public abstract Property<RepositoryConfiguration> getConfiguration();
    @Input public abstract Property<String> getTargetId();
    @Input public abstract Property<String> getVariant();
    @Option(option = "target", description = "Registered Minecraft Target ID")
    public void target(String value) { getTargetId().set(value); }
    @Option(option = "variant", description = "client, client-multiplayer or server")
    public void variant(String value) { getVariant().set(value); }

    @TaskAction public void print() {
        RepositoryConfiguration config = getConfiguration().get();
        var run = config.resolveInstance(getTargetId().get(), getVariant().get());
        getLogger().lifecycle("target={} variant={} deployment={}", run.target(), run.variant(), run.deployment());
        getLogger().lifecycle("directory={} memory={} width={} height={} playerName={}",
                run.directory(), run.memory(), run.width(), run.height(), run.playerName());
        getLogger().lifecycle("extraJvmArgs={} entries; extraGameArgs={} entries (values not printed)",
                run.extraJvmArgs().size(), run.extraGameArgs().size());
        getLogger().lifecycle("effectiveMemory={} (extraJvmArgs -Xmx takes priority over memory)", run.effectiveMemory());
        for (String name : new String[]{"memory", "width", "height", "playerName", "extraJvmArgs", "extraGameArgs"}) {
            if (run.variant().equals("server") && (name.equals("width") || name.equals("height") || name.equals("playerName"))) continue;
            var selected = config.value(run.target(), run.variant(), name);
            if (selected != null) getLogger().lifecycle("{} <- {} [{}]", name, selected.file(), selected.key());
        }
    }
}
