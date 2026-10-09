package raven.gradle;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class RepositoryConfigurationPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        if (project != project.getRootProject()) throw new GradleException("Apply raven.repository-configuration only to the root project");
        Map<String, String> facts = read(project, new File(project.getRootDir(), "gradle.properties"), true);
        Map<String, String> defaults = read(project, new File(project.getRootDir(), "instance.properties"), true);
        Map<String, String> local = read(project, new File(project.getRootDir(), "local.properties"), false);
        Map<String, Map<String, String>> targets = new LinkedHashMap<>();
        for (Project child : project.getAllprojects()) {
            if (!child.getPath().startsWith(":version:")) continue;
            String id = child.getPath().substring(":version:".length());
            RepositoryConfiguration.validateTargetId(id);
            File expected = new File(project.getRootDir(), "versions/" + id);
            if (!child.getProjectDir().toPath().normalize().equals(expected.toPath().normalize())) {
                throw new GradleException("Target directory must match versions/" + id);
            }
            targets.put(id, read(project, new File(expected, "target.properties"), true));
        }
        RepositoryConfiguration config = new RepositoryConfiguration(project.getRootDir(), facts, targets, defaults, local);
        project.getExtensions().add("repositoryConfig", config);
        project.setGroup(facts.get("mod_group"));
        project.setVersion(facts.get("mod_version"));
        for (String id : targets.keySet()) {
            project.project(":version:" + id).setGroup(facts.get("mod_group"));
            project.project(":version:" + id).setVersion(facts.get("mod_version"));
        }
        var verify = project.getTasks().register("verifyConfiguration", VerifyConfigurationTask.class, task -> {
            task.setGroup("verification");
            task.setDescription("Validate Project/Target facts, instance cascades and optional local tools.");
            task.getConfiguration().set(config);
        });
        project.getTasks().register("printInstanceConfiguration", PrintInstanceConfigurationTask.class, task -> {
            task.setGroup("help");
            task.setDescription("Show a registered Target's resolved instance settings, without printing extra argument values.");
            task.getConfiguration().set(config);
            task.getTargetId().convention("");
            task.getVariant().convention("client");
        });
        project.getPluginManager().withPlugin("base", ignored -> project.getTasks().named("check").configure(task -> task.dependsOn(verify)));
    }

    private static Map<String, String> read(Project project, File file, boolean required) {
        // Provider-backed reads make content AND absence changes visible to configuration cache.
        var regularFile = project.getLayout().file(project.provider(() -> file));
        String contents = project.getProviders().fileContents(regularFile).getAsText().getOrNull();
        if (contents == null && required) throw new GradleException("Missing configuration file: " + file.getName());
        return RepositoryConfiguration.parse(contents == null ? "" : contents,
                project.getRootDir().toPath().relativize(file.toPath()).toString());
    }
}
