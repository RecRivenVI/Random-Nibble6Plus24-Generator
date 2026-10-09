package raven.gradle;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepositoryConfigurationTest {
    static final String TARGET = "26.2-neoforge";
    static final String PROJECT = "mod_id=examplemod\nmod_name=Example Mod\nmod_group=io.github.example\nmod_version=0.1.0\nmod_environment=both\n";
    static final String FACTS = "minecraft_version=26.2\nloader=neoforge\nloader_version=1\njava_version=" + Runtime.version().feature() + "\n";

    static Map<String, String> parse(String source) { return RepositoryConfiguration.parse(source, "test.properties"); }
    static RepositoryConfiguration config(String defaults, String local) {
        return new RepositoryConfiguration(new File("."), parse(PROJECT), Map.of(TARGET, parse(FACTS)), parse(defaults), parse(local));
    }

    @ParameterizedTest
    @ValueSource(strings = {"memory", "width", "height", "playerName", "extraJvmArgs", "extraGameArgs", "customFlag"})
    void sameCascadeForEveryProperty(String property) {
        String[] keys = {"instance." + property, "instance.client." + property,
                "instance.[26.2-neoforge]." + property, "instance.[26.2-neoforge].client." + property};
        for (int repositoryLevel = 0; repositoryLevel < 4; repositoryLevel++) {
            Map<String, String> repository = new LinkedHashMap<>();
            for (int i = 0; i <= repositoryLevel; i++) repository.put(keys[i], "repository-" + i);
            var noLocal = new RepositoryConfiguration(new File("."), parse(PROJECT), Map.of(TARGET, parse(FACTS)), repository, Map.of());
            assertEquals("repository-" + repositoryLevel, noLocal.value(TARGET, "client", property).raw());
            for (int localLevel = 0; localLevel < 4; localLevel++) {
                Map<String, String> local = new LinkedHashMap<>();
                for (int i = 0; i <= localLevel; i++) local.put(keys[i], "local-" + i);
                var c = new RepositoryConfiguration(new File("."), parse(PROJECT), Map.of(TARGET, parse(FACTS)), repository, local);
                var value = c.value(TARGET, "client", property);
                assertEquals("local-" + localLevel, value.raw());
                assertEquals("local.properties", value.file());
                assertEquals(keys[localLevel], value.key());
            }
        }
    }

    @Test void nativeGradleSettingsAreNotProductFactsOrInstanceOverrides() {
        var input = parse(PROJECT + "org.gradle.jvmargs=-Xmx1G\norg.gradle.parallel=true\norg.gradle.configuration-cache=false\n");
        var c = new RepositoryConfiguration(new File("."), input, Map.of(TARGET, parse(FACTS)), Map.of(), Map.of());
        c.verify();
        assertEquals(parse(PROJECT), c.getProjectFacts());
        assertEquals("-Xmx1G", input.get("org.gradle.jvmargs"));
        assertNull(c.resolveInstance(TARGET, "client").memory());
        assertThrows(IllegalArgumentException.class, () -> config("", "org.gradle.parallel=true"));
        assertThrows(IllegalArgumentException.class, () -> config("org.gradle.parallel=true", ""));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryConfiguration(new File("."),
                parse(PROJECT + "minecraft_version=26.2"), Map.of(), Map.of(), Map.of()));
    }

    @Test void localVariantOverridesRepositoryTargetVariant() {
        var c = config("instance.[26.2-neoforge].client.memory=12G", "instance.client.memory=8G");
        assertEquals("8G", c.resolveInstance(TARGET, "client").memory());
    }

    @Test void multilineBlocksPreserveLiteralArgumentsAndPropertiesAroundThem() {
        var c = config("""
                instance.memory=4G
                instance.[26.2-neoforge].client.extraJvmArgs=[
                    -Xms4G
                    -Xmx8G
                    -Dmessage=hello world

                    -Dpath=C:\\tools\\jdk
                    -Dliteral=$(literal)
                    -Dunicode=雪
                ]
                instance.width=800
                instance.extraGameArgs=[
                    --message
                    two words
                ]
                """, "");
        var run = c.resolveInstance(TARGET, "client");
        assertEquals("4G", run.memory());
        assertEquals("8G", run.effectiveMemory());
        assertEquals(800, run.width());
        assertEquals(List.of("-Xms4G", "-Dmessage=hello world", "-Dpath=C:\\tools\\jdk", "-Dliteral=$(literal)", "-Dunicode=雪"), run.jvmArgumentsWithoutMaxHeap());
        assertEquals(List.of("--message", "two words"), run.extraGameArgs());
        assertEquals("4G", c.resolveInstance(TARGET, "server").effectiveMemory());
    }

    @Test void heapPriorityIsAppliedAfterBothIndependentCascades() {
        String repo = "instance.[26.2-neoforge].client.memory=4G\ninstance.extraJvmArgs=[\n-Xmx8G\n]\n";
        assertEquals("8G", config(repo, "instance.memory=2G").resolveInstance(TARGET, "client").effectiveMemory());
        assertEquals("12G", config(repo, "instance.extraJvmArgs=[\n-Xmx12G\n]").resolveInstance(TARGET, "client").effectiveMemory());
        assertEquals("2G", config(repo, "instance.memory=2G\ninstance.extraJvmArgs=[]").resolveInstance(TARGET, "client").effectiveMemory());
        assertEquals("4G", config(repo, "instance.extraJvmArgs=[\n\n]").resolveInstance(TARGET, "client").effectiveMemory());
    }

    @ParameterizedTest
    @ValueSource(strings = {"instance.extraJvmArgs=[\n-ea", "instance.extraJvmArgs=[\n[\n]", "instance.extraJvmArgs=[\n] trailing", "instance.extraJvmArgs=[\n-ea\n]\ninstance.extraJvmArgs=[]"})
    void malformedOrDuplicateBlocksFail(String text) {
        assertThrows(IllegalArgumentException.class, () -> config(text, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-Xmx", "-Xmx0G", "-Xmx8G\n-Xmx4G", "-Xmx8 G"})
    void invalidOrDuplicateHeapArgumentsFail(String arg) {
        assertThrows(IllegalArgumentException.class, () -> config("instance.extraJvmArgs=[\n" + arg + "\n]", "").resolveInstance(TARGET, "client"));
    }

    @Test void unrelatedScopesDoNotMatchAndMissingIsNotEmpty() {
        var c = config("instance.memory=4G\ninstance.[26.2-fabric].memory=12G\ninstance.server.memory=6G", "instance.client-multiplayer.memory=8G");
        assertEquals("4G", c.resolveInstance(TARGET, "client").memory());
        assertEquals("6G", c.resolveInstance(TARGET, "server").memory());
        assertEquals("8G", c.resolveInstance(TARGET, "client-multiplayer").memory());
        assertNull(c.value(TARGET, "client", "customFlag"));
    }

    @Test void emptyExtrasExplicitlyClearRepositoryArgsAndAreNotConcatenated() {
        var c = config("instance.extraJvmArgs=[\"-ea\"]\ninstance.client.extraJvmArgs=[\"-Dlevel=variant\"]", "instance.extraJvmArgs=");
        assertEquals(List.of(), c.resolveInstance(TARGET, "client").extraJvmArgs());
        assertEquals("local.properties", c.value(TARGET, "client", "extraJvmArgs").file());
        assertEquals(List.of("-Dlevel=variant"), config("instance.extraJvmArgs=[\"-ea\"]\ninstance.client.extraJvmArgs=[\"-Dlevel=variant\"]", "").resolveInstance(TARGET, "client").extraJvmArgs());
    }

    @Test void jsonArgumentsKeepSpacesEmptyStringsUnicodeAndLiteralShellText() {
        var run = config("instance.extraJvmArgs=[\"-Dmessage=hello world\",\"-ea\"]\ninstance.extraGameArgs=[\"--message\",\"two words\",\"\",\"雪\",\"$(literal)\"]", "").resolveInstance(TARGET, "client");
        assertEquals(List.of("-Dmessage=hello world", "-ea"), run.extraJvmArgs());
        assertEquals(List.of("--message", "two words", "", "雪", "$(literal)"), run.extraGameArgs());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-ea", "{}", "[1]", "[true]", "[null]", "[\"unterminated]"})
    void invalidArgsFailWithoutLeakingTheirValues(String args) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> config("instance.extraGameArgs=" + args, "").resolveInstance(TARGET, "client"));
        assertTrue(error.getMessage().contains("instance.extraGameArgs"));
        assertNull(error.getCause());
    }

    @Test void selectedEmptyTypedValueDoesNotFallBack() {
        var c = config("instance.memory=4G", "instance.memory=");
        assertThrows(IllegalArgumentException.class, () -> c.resolveInstance(TARGET, "client"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"instance.memory=0G", "instance.memory=-1G", "instance.width=0", "instance.height=2147483648", "instance.client.playerName=bad name"})
    void invalidRuntimeValuesFailOnConsumption(String value) {
        assertThrows(IllegalArgumentException.class, () -> config(value, "").resolveInstance(TARGET, "client"));
    }

    @Test void serverDoesNotConsumeClientOnlyProperties() {
        var c = config("instance.width=not-a-number\ninstance.playerName=bad name\ninstance.memory=4G", "");
        assertEquals(List.of(), c.resolveInstance(TARGET, "server").clientArguments());
        assertEquals("4G", c.resolveInstance(TARGET, "server").memory());
        assertThrows(IllegalArgumentException.class, () -> c.resolveInstance(TARGET, "client"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"instance.extraGameArgs=[\"--width=800\"]", "instance.extraGameArgs=[\"--username\",\"Player\"]", "instance.extraGameArgs=[\"--gameDir\",\"elsewhere\"]"})
    void extrasCannotIntroduceCompetingAuthorities(String value) {
        assertThrows(IllegalArgumentException.class, () -> config(value, "").resolveInstance(TARGET, "client"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"instance.26.2-neoforge.client.memory=4G", "instance.[../x].memory=4G", "instance.multiplayer.memory=4G", "instance.[26.2-forge].client.window.width=4", "instance.mod_id=x", "instance.minecraft_version=26.2"})
    void malformedOrMisplacedInstanceKeysFail(String value) {
        assertThrows(IllegalArgumentException.class, () -> config(value, ""));
    }

    @Test void projectAndTargetFactsCannotBeOverriddenLocally() {
        assertThrows(IllegalArgumentException.class, () -> config("", "mod_version=2"));
        assertThrows(IllegalArgumentException.class, () -> config("", "minecraft_version=1"));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryConfiguration(new File("."), parse(PROJECT + "minecraft_version=26.2"), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryConfiguration(new File("."), parse(PROJECT), Map.of(TARGET, parse(FACTS + "mod_id=x")), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryConfiguration(new File("."), parse(PROJECT), Map.of(TARGET, parse(FACTS + "memory=4G")), Map.of(), Map.of()));
    }

    @Test void missingAndMismatchedFactsFail() {
        assertThrows(IllegalArgumentException.class, () -> new RepositoryConfiguration(new File("."), Map.of(), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryConfiguration(new File("."), parse(PROJECT), Map.of("26.2-fabric", parse(FACTS)), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RepositoryConfiguration(new File("."), parse(PROJECT.replace("both", "distribution")), Map.of(), Map.of(), Map.of()));
    }

    @Test void profileMatrixKeepsServerClientAsProjectDefined() {
        Map<String, List<String>> expected = Map.of("client", List.of("INSTALL", "INSTALL", "ABSENT"),
                "server", List.of("PROJECT_DEFINED", "ABSENT", "INSTALL"), "both", List.of("INSTALL", "INSTALL", "INSTALL"));
        expected.forEach((profile, values) -> {
            var c = new RepositoryConfiguration(new File("."), parse(PROJECT.replace("both", profile)), Map.of(), Map.of(), Map.of());
            assertEquals(values, List.of(c.deployment("client"), c.deployment("client-multiplayer"), c.deployment("server")));
        });
    }

    @Test void parserSupportsNativePropertiesEscapesAndRejectsDuplicateKeys() {
        assertEquals("雪", parse("name=雪").get("name"));
        assertEquals("ab", parse("name=a\\\n  b").get("name"));
        assertThrows(IllegalArgumentException.class, () -> parse("name=first\nname=second"));
        assertThrows(IllegalArgumentException.class, () -> parse("name=first\n\\u006eame=second"));
    }

    @Test void pathsAreOptionalAndJdkVersionIsChecked() {
        assertNull(config("", "").javaHome(21));
        assertNull(config("", "").prismLauncher());
        int major = Runtime.version().feature();
        String home = System.getProperty("java.home").replace('\\', '/');
        var c = config("", "java." + major + ".home=" + home);
        assertTrue(c.javaExecutable(major).isFile());
        assertThrows(IllegalArgumentException.class, () -> config("", "java.8.home=" + home).javaExecutable(8));
        assertThrows(IllegalArgumentException.class, () -> config("", "java.21.home=").javaHome(21));
        assertThrows(IllegalArgumentException.class, () -> config("", "launcher.prism.path=missing-prism").verify());
    }

    @Test void noTargetsStillValidatesDeclaredRuntimeDefaults() {
        var c = new RepositoryConfiguration(new File("."), parse(PROJECT), Map.of(), parse("instance.width=bad"), Map.of());
        assertThrows(IllegalArgumentException.class, c::verify);
    }

    @Test void returnedFactsAndArgumentsAreImmutable() {
        var c = config("instance.extraGameArgs=[\"one\"]", "");
        assertThrows(UnsupportedOperationException.class, () -> c.getProjectFacts().put("mod_id", "other"));
        assertThrows(UnsupportedOperationException.class, () -> c.targetFacts(TARGET).put("java_version", "8"));
        assertThrows(UnsupportedOperationException.class, () -> c.resolveInstance(TARGET, "client").extraGameArgs().add("two"));
    }
}
