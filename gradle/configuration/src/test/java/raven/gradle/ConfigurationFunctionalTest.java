package raven.gradle;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationFunctionalTest {
    @TempDir(cleanup = CleanupMode.NEVER) Path root;
    private static final String TARGET = RepositoryConfigurationTest.TARGET;

    private void write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private void fixture(boolean target) throws IOException {
        String realSettings;
        try (var input = getClass().getResourceAsStream("/fixture-settings.gradle.kts")) {
            realSettings = new String(java.util.Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        // Isolated plugin fixture; never registers fake modules in the product repository.
        String helpers = realSettings.substring(realSettings.indexOf("rootProject.name"));
        write("settings.gradle.kts", helpers + (target ? "\nincludeTarget(\"" + TARGET + "\")\n" : ""));
        write("build.gradle.kts", "plugins { base; id(\"raven.repository-configuration\") }\n");
        write("gradle.properties", RepositoryConfigurationTest.PROJECT);
        write("instance.properties", "instance.memory=96M\ninstance.width=1280\ninstance.height=720\ninstance.client.playerName=DevPlayer\ninstance.client-multiplayer.playerName=DevGuest\ninstance.extraJvmArgs=\ninstance.extraGameArgs=\n");
        if (target) {
            write("versions/" + TARGET + "/target.properties", RepositoryConfigurationTest.FACTS);
            write("versions/" + TARGET + "/build.gradle.kts", "plugins { base }\n");
        }
    }

    private GradleRunner runner(String... arguments) {
        List<String> args = new ArrayList<>(List.of(arguments));
        args.addAll(List.of("--console=plain", "--stacktrace", "--max-workers=1", "--configuration-cache-problems=fail"));
        return GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
                .withGradleInstallation(new File(System.getProperty("gradle.installation")))
                .withArguments(args);
    }

    private BuildResult build(String... args) { return runner(args).build(); }

    @Test void rootWithoutTargetsOrLocalFileWorksAndCaches() throws IOException {
        fixture(false);
        BuildResult first = build("verifyConfiguration", "projects", "--configuration-cache");
        assertTrue(first.getOutput().contains("registered Targets=0"));
        assertTrue(first.getOutput().contains("No sub-projects"));
        assertFalse(Files.exists(root.resolve("instances")));
        assertFalse(Files.exists(root.resolve("local.properties")));
        assertTrue(build("verifyConfiguration", "projects", "--configuration-cache").getOutput().contains("Reusing configuration cache"));
    }

    @Test void gradleStillConsumesNativeSettingsAlongsideProductFacts() throws IOException {
        fixture(false);
        write("gradle.properties", RepositoryConfigurationTest.PROJECT + "org.gradle.parallel=true\norg.gradle.configuration-cache=false\n");
        write("build.gradle.kts", """
                plugins { base; id("raven.repository-configuration") }
                val config = extensions.getByType<raven.gradle.RepositoryConfiguration>()
                check(config.projectFacts.keys.none { it.startsWith("org.gradle.") })
                check(gradle.startParameter.isParallelProjectExecutionEnabled)
                """);
        assertTrue(build("verifyConfiguration").getOutput().contains("registered Targets=0"));
    }

    @Test void fixtureAllowsSameLeafNameInSeparateNamespaces() throws IOException {
        fixture(false);
        String id = "26w01a-fabric"; // Valid for both categories; component IDs cannot contain dots.
        Files.writeString(root.resolve("settings.gradle.kts"), "\nincludeTarget(\"" + id + "\")\nincludeComponent(\"" + id + "\")\n", java.nio.file.StandardOpenOption.APPEND);
        write("versions/" + id + "/target.properties", RepositoryConfigurationTest.FACTS.replace("minecraft_version=26.2", "minecraft_version=26w01a").replace("loader=neoforge", "loader=fabric"));
        write("versions/" + id + "/build.gradle.kts", "plugins { base }\n");
        write("components/" + id + "/build.gradle.kts", "plugins { base }\n");
        BuildResult result = build("projects", "verifyConfiguration");
        assertTrue(result.getOutput().contains(":version:" + id));
        assertTrue(result.getOutput().contains(":component:" + id));
        assertFalse(Files.exists(root.resolve("version")));
        assertFalse(Files.exists(root.resolve("component")));
        assertFalse(Files.exists(root.resolve("versions/build.gradle.kts")));
    }

    @Test void missingTargetDoesNotSilentlyDisappear() throws IOException {
        fixture(false);
        Files.writeString(root.resolve("settings.gradle.kts"), "\nincludeTarget(\"26.2-fabric\")\n", java.nio.file.StandardOpenOption.APPEND);
        assertTrue(runner("projects").buildAndFail().getOutput().contains("Target needs build.gradle.kts and target.properties"));
        assertFalse(Files.exists(root.resolve("versions")));
    }

    @Test void addingEditingAndRemovingLocalFileInvalidatesCachedSnapshot() throws IOException {
        fixture(true);
        String[] args = {"printInstanceConfiguration", "--target=" + TARGET, "--variant=client", "--configuration-cache"};
        assertTrue(build(args).getOutput().contains("memory=96M"));
        assertTrue(build(args).getOutput().contains("Reusing configuration cache"));
        write("local.properties", "instance.memory=128M\n");
        assertTrue(build(args).getOutput().contains("memory=128M"));
        write("local.properties", "instance.memory=160M\n");
        assertTrue(build(args).getOutput().contains("memory=160M"));
        // Preserve the test input; no permanent-delete cleanup or local-file requirement.
        Files.move(root.resolve("local.properties"), root.resolve("saved-local.properties"));
        assertTrue(build(args).getOutput().contains("memory=96M"));
    }

    @Test void repositoryAndTargetEditsInvalidateConfigurationCache() throws IOException {
        fixture(true);
        build("verifyConfiguration", "--configuration-cache");
        write("instance.properties", "instance.memory=not-a-size\n");
        assertTrue(runner("verifyConfiguration", "--configuration-cache").buildAndFail().getOutput().contains("instance.memory"));
        write("instance.properties", "instance.memory=64M\n");
        build("verifyConfiguration", "--configuration-cache");
        write("versions/" + TARGET + "/target.properties", RepositoryConfigurationTest.FACTS.replace("minecraft_version=26.2", "minecraft_version=26.1.2"));
        assertTrue(runner("verifyConfiguration", "--configuration-cache").buildAndFail().getOutput().contains("facts do not match Target directory ID"));
    }

    @Test void verifyChecksDefaultsEvenWithNoTargets() throws IOException {
        fixture(false);
        write("instance.properties", "instance.extraJvmArgs=[123]\n");
        assertTrue(runner("verifyConfiguration").buildAndFail().getOutput().contains("every argument must be a string"));
    }

    @Test void actualJavaProcessReceivesHeapArgsDirectoryAndLocalJdk() throws IOException {
        fixture(true);
        int major = Runtime.version().feature();
        String home = System.getProperty("java.home").replace('\\', '/');
        write("local.properties", "java." + major + ".home=" + home + "\n"
                + "instance.memory=64M\n"
                + "instance.extraJvmArgs=[\n-Xms32M\n-Xmx128M\n-Dmessage=hello world\n-ea\n]\n"
                + "instance.extraGameArgs=[\n--extra\ntwo words\n雪\n$(literal)\n]\n");
        write("versions/" + TARGET + "/build.gradle.kts", """
                import raven.gradle.JavaExecAdapter
                import raven.gradle.RepositoryConfiguration
                plugins { java }
                val config = rootProject.extensions.getByType<RepositoryConfiguration>()
                listOf("client", "client-multiplayer", "server").forEach { variant ->
                    tasks.register<JavaExec>("probe-" + variant) {
                        classpath = sourceSets.main.get().runtimeClasspath
                        mainClass.set("ArgumentProbe")
                        maxHeapSize = "48m"
                        jvmArgs("-Dloader=kept")
                        args("--loader", "kept")
                        JavaExecAdapter.configure(this, config, "26.2-neoforge", variant,
                            listOf("-Dproject=required"), listOf("--project", "required"))
                    }
                }
                """);
        write("versions/" + TARGET + "/src/main/java/ArgumentProbe.java", """
                public class ArgumentProbe {
                    public static void main(String[] args) {
                        boolean enabled = false;
                        assert enabled = true;
                        System.out.println("probe.assertions=" + enabled);
                        System.out.println("probe.loader=" + System.getProperty("loader"));
                        System.out.println("probe.project=" + System.getProperty("project"));
                        System.out.println("probe.message=" + System.getProperty("message"));
                        System.out.println("probe.heap=" + Runtime.getRuntime().maxMemory());
                        System.out.println("probe.xmxCount=" + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream().filter(a -> a.startsWith("-Xmx")).count());
                        System.out.println("probe.cwd=" + System.getProperty("user.dir"));
                        System.out.println("probe.home=" + System.getProperty("java.home"));
                        System.out.println("probe.args=" + String.join("|", args));
                        System.out.println("probe.args64=" + java.util.Base64.getEncoder().encodeToString(String.join("|", args).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    }
                }
                """);
        String task = ":version:" + TARGET + ":probe-client";
        for (int attempt = 0; attempt < 2; attempt++) {
            String out = build(task, "--configuration-cache").getOutput();
            if (attempt == 1) assertTrue(out.contains("Reusing configuration cache"));
            assertTrue(out.contains("probe.assertions=true"));
            assertTrue(out.contains("probe.loader=kept"));
            assertTrue(out.contains("probe.project=required"));
            assertTrue(out.contains("probe.message=hello world"));
            assertTrue(out.contains("probe.heap=134217728"));
            assertTrue(out.contains("probe.xmxCount=1"));
            assertTrue(out.contains("probe.cwd=" + root.resolve("instances/" + TARGET + "/client")));
            assertTrue(out.contains("probe.home=" + System.getProperty("java.home")));
            assertTrue(out.contains("probe.args64=" + encoded("--loader|kept|--project|required|--width|1280|--height|720|--username|DevPlayer|--extra|two words|雪|$(literal)")), out);
        }
        String multi = build(":version:" + TARGET + ":probe-client-multiplayer").getOutput();
        assertTrue(multi.contains("--username|DevGuest"));
        assertTrue(multi.contains("probe.cwd=" + root.resolve("instances/" + TARGET + "/client-multiplayer")));
        // A server does not parse or pass client-only values, even when they are invalid.
        Files.writeString(root.resolve("local.properties"), "instance.width=invalid\ninstance.playerName=invalid name\n", java.nio.file.StandardOpenOption.APPEND);
        String server = build(":version:" + TARGET + ":probe-server").getOutput();
        assertTrue(server.contains("probe.args64=" + encoded("--loader|kept|--project|required|--extra|two words|雪|$(literal)")), server);
        assertTrue(server.contains("probe.cwd=" + root.resolve("instances/" + TARGET + "/server")));
        assertFalse(server.contains("--username"));
        // Edit the multiline value, then clear it: cache must not retain the old heap.
        write("local.properties", "instance.memory=64M\ninstance.extraJvmArgs=[\n-Xmx160M\n]\n");
        String changed = build(task, "--configuration-cache").getOutput();
        assertTrue(changed.contains("probe.heap=167772160"), changed);
        assertTrue(changed.contains("probe.xmxCount=1"), changed);
        write("local.properties", "instance.memory=64M\ninstance.extraJvmArgs=[]\n");
        String cleared = build(task, "--configuration-cache").getOutput();
        assertTrue(cleared.contains("probe.heap=67108864"), cleared);
        assertTrue(cleared.contains("probe.xmxCount=1"), cleared);
    }

    private static String encoded(String text) {
        return java.util.Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test void localJdkCanDifferFromTheGradleJvm() throws IOException {
        String home = System.getProperty("alternateJdkHome");
        org.junit.jupiter.api.Assumptions.assumeTrue(home != null, "Supply -PalternateJdkHome for the additional JDK integration test");
        int alternate = readJavaVersion(home);
        org.junit.jupiter.api.Assumptions.assumeTrue(alternate != Runtime.version().feature(), "Alternate JDK must differ from the Gradle JVM");
        fixture(true);
        write("versions/" + TARGET + "/target.properties", RepositoryConfigurationTest.FACTS.replace("java_version=" + Runtime.version().feature(), "java_version=" + alternate));
        write("local.properties", "java." + alternate + ".home=" + home.replace('\\', '/') + "\n");
        write("versions/" + TARGET + "/build.gradle.kts", """
                import raven.gradle.JavaExecAdapter
                import raven.gradle.RepositoryConfiguration
                plugins { java }
                tasks.withType<JavaCompile>().configureEach { options.release = 17 }
                val config = rootProject.extensions.getByType<RepositoryConfiguration>()
                tasks.register<JavaExec>("probe-jdk") {
                    classpath = sourceSets.main.get().runtimeClasspath
                    mainClass.set("JdkProbe")
                    JavaExecAdapter.configure(this, config, "26.2-neoforge", "client", emptyList(), emptyList())
                }
                """);
        write("versions/" + TARGET + "/src/main/java/JdkProbe.java", """
                public class JdkProbe {
                    public static void main(String[] args) {
                        System.out.println("probe.major=" + Runtime.version().feature());
                        System.out.println("probe.home=" + System.getProperty("java.home"));
                    }
                }
                """);
        for (int attempt = 0; attempt < 2; attempt++) {
            String out = build(":version:" + TARGET + ":probe-jdk", "--configuration-cache").getOutput();
            assertTrue(out.contains("probe.major=" + alternate), out);
            assertTrue(out.contains("probe.home=" + new File(home).getAbsolutePath()), out);
            if (attempt == 1) assertTrue(out.contains("Reusing configuration cache"), out);
        }
    }

    private static int readJavaVersion(String home) throws IOException {
        String release = Files.readString(Path.of(home, "release"));
        String version = RepositoryConfiguration.parse(release, "release").get("JAVA_VERSION").replace("\"", "");
        return Integer.parseInt(version.split("[.+-]", 2)[0]);
    }

}
