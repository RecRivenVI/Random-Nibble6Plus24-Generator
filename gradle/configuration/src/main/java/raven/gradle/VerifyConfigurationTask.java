package raven.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Validation has no output; optional tool paths are checked on every invocation")
public abstract class VerifyConfigurationTask extends DefaultTask {
    @Internal public abstract Property<RepositoryConfiguration> getConfiguration();

    @TaskAction public void verify() {
        RepositoryConfiguration config = getConfiguration().get();
        config.verify();
        getLogger().lifecycle("Configuration verified: profile={}, registered Targets={}; no Minecraft launched.",
                config.getEnvironment(), config.getTargets().size());
    }
}
