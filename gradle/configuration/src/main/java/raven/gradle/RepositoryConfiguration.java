package raven.gradle;

import groovy.json.JsonSlurper;
import groovy.json.JsonOutput;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable configuration snapshot. No Gradle/Loader objects or filesystem writes. */
public final class RepositoryConfiguration implements Serializable {
    private static final Set<String> VARIANTS = Set.of("client", "client-multiplayer", "server");
    private static final Pattern TARGET = Pattern.compile("([A-Za-z0-9][A-Za-z0-9._-]*)-(neoforge|forge|fabric|quilt)");
    private static final Pattern INSTANCE = Pattern.compile(
            "instance\\.(?:\\[([^]]+)\\]\\.)?(?:(client|client-multiplayer|server)\\.)?([A-Za-z][A-Za-z0-9_-]*)");
    private static final Pattern JAVA_HOME = Pattern.compile("java\\.([1-9][0-9]*)\\.home");
    private static final Set<String> TARGET_KEYS = Set.of("minecraft_version", "loader", "loader_version", "java_version");
    private static final Set<String> RUNTIME_KEYS = Set.of("memory", "width", "height", "playerName", "extraJvmArgs", "extraGameArgs");
    private static final Set<String> MANAGED_GAME_ARGS = Set.of("--width", "--height", "--username", "--gameDir");

    private final File root;
    private final Map<String, String> project;
    private final Map<String, Map<String, String>> targets;
    private final Map<String, String> defaults;
    private final Map<String, String> local;

    public RepositoryConfiguration(File root, Map<String, String> project,
            Map<String, Map<String, String>> targets, Map<String, String> defaults, Map<String, String> local) {
        this.root = root.getAbsoluteFile();
        Map<String, String> productFacts = new LinkedHashMap<>();
        project.forEach((key, value) -> {
            // Gradle consumes its own settings; they are not product facts or instance overrides.
            if (key.startsWith("org.gradle.") && key.length() > "org.gradle.".length()) return;
            productFacts.put(key, value);
        });
        this.project = immutable(productFacts);
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        targets.forEach((id, facts) -> copy.put(id, immutable(facts)));
        this.targets = Collections.unmodifiableMap(copy);
        this.defaults = immutable(defaults);
        this.local = immutable(local);
        validateFacts();
        validateInstanceKeys(this.defaults, "instance.properties");
        Map<String, String> localInstances = new LinkedHashMap<>();
        this.local.forEach((key, value) -> {
            if (key.startsWith("instance.")) localInstances.put(key, value);
            else if (!JAVA_HOME.matcher(key).matches() && !key.equals("launcher.prism.path")) {
                throw error("local.properties", key, "only instance overrides and supported machine paths belong here");
            }
        });
        validateInstanceKeys(localInstances, "local.properties");
    }

    private static Map<String, String> immutable(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public static Map<String, String> parse(String text, String source) {
        String[] lines = text.split("\\R", -1);
        StringBuilder normalized = new StringBuilder();
        boolean continued = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher opening = Pattern.compile("^\\s*(instance\\.[^\\s=]+)\\s*=\\s*\\[\\s*$").matcher(line);
            if (!continued && opening.matches()
                    && (opening.group(1).endsWith(".extraJvmArgs") || opening.group(1).endsWith(".extraGameArgs"))) {
                String key = opening.group(1);
                List<String> args = new ArrayList<>();
                boolean closed = false;
                while (++i < lines.length) {
                    String arg = lines[i].trim();
                    if (arg.equals("]")) { closed = true; break; }
                    if (arg.equals("[") || arg.startsWith("]")) throw error(source, key, "invalid argument block delimiter");
                    if (!arg.isEmpty()) args.add(arg);
                }
                if (!closed) throw error(source, key, "argument block requires a closing ] line");
                // Encode once for JSON, then escape for the ordinary properties reader.
                normalized.append(key).append('=').append(JsonOutput.toJson(args).replace("\\", "\\\\")).append('\n');
            } else {
                normalized.append(line).append('\n');
                int slashes = 0;
                for (int j = line.length() - 1; j >= 0 && line.charAt(j) == '\\'; j--) slashes++;
                continued = (continued || !(line.stripLeading().startsWith("#") || line.stripLeading().startsWith("!"))) && slashes % 2 == 1;
            }
        }
        Properties values = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw error(source, key.toString(), "duplicate key");
                return super.put(key, value);
            }
        };
        try {
            values.load(new StringReader(normalized.toString()));
        } catch (IOException e) {
            throw new IllegalArgumentException(source + ": cannot read properties", e);
        }
        Map<String, String> result = new LinkedHashMap<>();
        values.stringPropertyNames().stream().sorted().forEach(key -> result.put(key, values.getProperty(key)));
        return result;
    }

    public Map<String, String> getProjectFacts() { return project; }
    public Map<String, Map<String, String>> getTargets() { return targets; }
    public String getEnvironment() { return project.get("mod_environment").trim(); }
    public File getRootDirectory() { return root; }

    public Map<String, String> targetFacts(String target) {
        validateTargetId(target);
        Map<String, String> facts = targets.get(target);
        if (facts == null) throw new IllegalArgumentException("Unregistered Target: " + target);
        return facts;
    }

    public int javaVersion(String target) {
        return positive(targetFacts(target).get("java_version"), targetFile(target), "java_version");
    }

    /** Typical deployment policy, not a network/distribution contract or deployment action. */
    public String deployment(String variant) {
        validateVariant(variant);
        return switch (getEnvironment()) {
            case "client" -> variant.equals("server") ? "ABSENT" : "INSTALL";
            case "server" -> switch (variant) {
                case "server" -> "INSTALL";
                case "client-multiplayer" -> "ABSENT";
                default -> "PROJECT_DEFINED";
            };
            default -> "INSTALL";
        };
    }

    /** Presence, including an empty string, wins. Files are never merged before selection. */
    public Value value(String target, String variant, String property) {
        targetFacts(target);
        validateVariant(variant);
        if (!property.matches("[A-Za-z][A-Za-z0-9_-]*")) throw new IllegalArgumentException("Invalid runtime property name");
        List<String> keys = List.of("instance.[" + target + "]." + variant + "." + property,
                "instance.[" + target + "]." + property, "instance." + variant + "." + property, "instance." + property);
        Value repository = select(defaults, keys, "instance.properties");
        Value override = select(local, keys, "local.properties");
        return override != null ? override : repository;
    }

    private static Value select(Map<String, String> values, List<String> keys, String file) {
        for (String key : keys) if (values.containsKey(key)) return new Value(file, key, values.get(key));
        return null;
    }

    public ResolvedInstance resolveInstance(String target, String variant) {
        targetFacts(target);
        validateVariant(variant);
        Value heap = value(target, variant, "memory");
        String memory = heap == null ? null : heap.raw().trim();
        if (memory != null && !memory.matches("[1-9][0-9]*[kKmMgG]")) {
            throw error(heap.file(), heap.key(), "expected positive heap size with K, M or G suffix");
        }
        Integer width = null;
        Integer height = null;
        String player = null;
        if (!variant.equals("server")) {
            Value w = value(target, variant, "width");
            Value h = value(target, variant, "height");
            width = w == null ? null : positive(w.raw(), w.file(), w.key());
            height = h == null ? null : positive(h.raw(), h.file(), h.key());
            Value name = value(target, variant, "playerName");
            if (name != null) {
                player = name.raw().trim();
                if (!player.matches("[A-Za-z0-9_]{3,16}")) throw error(name.file(), name.key(), "expected 3-16 ASCII letters, digits or underscores");
            }
        }
        return new ResolvedInstance(target, variant, new File(root, "instances/" + target + "/" + variant),
                memory, width, height, player, arguments(value(target, variant, "extraJvmArgs"), true),
                arguments(value(target, variant, "extraGameArgs"), false), deployment(variant));
    }

    public File javaHome(int major) { return machinePath("java." + major + ".home"); }
    public File prismLauncher() { return machinePath("launcher.prism.path"); }

    private File machinePath(String key) {
        if (!local.containsKey(key)) return null;
        String value = local.get(key).trim();
        if (value.isEmpty()) throw error("local.properties", key, "machine path must not be empty");
        File file = new File(value);
        return (file.isAbsolute() ? file : new File(root, value)).toPath().normalize().toFile();
    }

    public File javaExecutable(int major) {
        File home = javaHome(major);
        if (home == null) return null;
        String key = "java." + major + ".home";
        File java = new File(home, "bin/" + (System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"));
        File javac = new File(home, "bin/" + (System.getProperty("os.name").startsWith("Windows") ? "javac.exe" : "javac"));
        if (!java.isFile() || !javac.isFile()) throw error("local.properties", key, "expected a JDK containing bin/java and bin/javac");
        try {
            String version = parse(Files.readString(new File(home, "release").toPath(), StandardCharsets.UTF_8), key).get("JAVA_VERSION");
            if (version == null) throw error("local.properties", key, "missing JAVA_VERSION in JDK release file");
            version = version.replace("\"", "");
            Matcher match = Pattern.compile("^(?:1\\.)?([0-9]+)").matcher(version);
            if (!match.find() || Integer.parseInt(match.group(1)) != major) throw error("local.properties", key, "JDK version does not match the key");
        } catch (IOException e) {
            throw error("local.properties", key, "cannot read JDK release file");
        }
        return java;
    }

    public void verify() {
        verifyDeclaredValues(defaults, "instance.properties");
        verifyDeclaredValues(local, "local.properties");
        for (String target : targets.keySet()) for (String variant : VARIANTS) resolveInstance(target, variant);
        for (String key : local.keySet()) {
            Matcher matcher = JAVA_HOME.matcher(key);
            if (matcher.matches()) javaExecutable(positive(matcher.group(1), "local.properties", key));
        }
        File prism = prismLauncher();
        if (prism != null && !prism.isFile()) throw error("local.properties", "launcher.prism.path", "launcher executable does not exist");
    }

    private void validateFacts() {
        for (String key : project.keySet()) if (!key.startsWith("mod_")) {
            throw error("gradle.properties", key, "expected Project Facts (mod_*) or native Gradle settings (org.gradle.*)");
        }
        for (String key : List.of("mod_id", "mod_name", "mod_group", "mod_version", "mod_environment")) required(project, "gradle.properties", key);
        if (!project.get("mod_id").trim().matches("[a-z][a-z0-9_]*")) throw error("gradle.properties", "mod_id", "expected lowercase Mod ID");
        if (!Set.of("client", "server", "both").contains(getEnvironment())) throw error("gradle.properties", "mod_environment", "expected client, server or both");
        targets.forEach((target, facts) -> {
            validateTargetId(target);
            String source = targetFile(target);
            for (String key : facts.keySet()) if (key.startsWith("mod_") || RUNTIME_KEYS.contains(key) || !key.matches("[a-z][a-z0-9_]*")) {
                throw error(source, key, "only Target Facts belong in this file");
            }
            for (String key : TARGET_KEYS) required(facts, source, key);
            if (!(facts.get("minecraft_version").trim() + "-" + facts.get("loader").trim()).equals(target)) {
                throw error(source, "minecraft_version/loader", "facts do not match Target directory ID");
            }
            positive(facts.get("java_version"), source, "java_version");
        });
    }

    private static void validateInstanceKeys(Map<String, String> values, String source) {
        for (String key : values.keySet()) {
            Matcher matcher = INSTANCE.matcher(key);
            if (!matcher.matches()) throw error(source, key, "invalid instance key; use brackets around a Target ID");
            if (matcher.group(1) != null) validateTargetId(matcher.group(1));
            String property = matcher.group(3);
            if (property.startsWith("mod_") || TARGET_KEYS.contains(property)) throw error(source, key, "Project/Target facts are not instance runtime properties");
        }
    }

    private static void verifyDeclaredValues(Map<String, String> values, String source) {
        values.forEach((key, raw) -> {
            Matcher matcher = INSTANCE.matcher(key);
            if (!matcher.matches()) return; // Machine paths are verified separately.
            String property = matcher.group(3);
            if ("server".equals(matcher.group(2)) && Set.of("width", "height", "playerName").contains(property)) return;
            switch (property) {
                case "memory" -> {
                    if (!raw.trim().matches("[1-9][0-9]*[kKmMgG]")) throw error(source, key, "expected positive heap size with K, M or G suffix");
                }
                case "width", "height" -> positive(raw, source, key);
                case "playerName" -> {
                    if (!raw.trim().matches("[A-Za-z0-9_]{3,16}")) throw error(source, key, "expected 3-16 ASCII letters, digits or underscores");
                }
                case "extraJvmArgs" -> arguments(new Value(source, key, raw), true);
                case "extraGameArgs" -> arguments(new Value(source, key, raw), false);
                default -> { } // Custom flat runtime properties retain the same cascade.
            }
        });
    }

    public static void validateTargetId(String target) {
        if (!TARGET.matcher(target).matches() || target.contains("..")) throw new IllegalArgumentException("Invalid Target ID: " + target);
    }

    private static void validateVariant(String variant) {
        if (!VARIANTS.contains(variant)) throw new IllegalArgumentException("Unknown instance variant: " + variant);
    }

    private static String targetFile(String target) { return "versions/" + target + "/target.properties"; }
    private static void required(Map<String, String> values, String source, String key) {
        if (!values.containsKey(key) || values.get(key).isBlank()) throw error(source, key, "required fact is missing or empty");
    }
    private static int positive(String value, String source, String key) {
        try {
            if (value == null || !value.trim().matches("[1-9][0-9]*")) throw new NumberFormatException();
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) { throw error(source, key, "expected a positive integer"); }
    }

    private static List<String> arguments(Value selected, boolean jvm) {
        if (selected == null || selected.raw().isBlank()) return List.of();
        Object parsed;
        try { parsed = new JsonSlurper().parseText(selected.raw()); }
        catch (RuntimeException e) { throw error(selected.file(), selected.key(), "expected a JSON array of strings"); }
        if (!(parsed instanceof List<?> list)) throw error(selected.file(), selected.key(), "expected a JSON array of strings");
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String arg) || arg.indexOf('\0') >= 0) throw error(selected.file(), selected.key(), "every argument must be a string without NUL");
            String option = arg.split("=", 2)[0];
            if (!jvm && MANAGED_GAME_ARGS.contains(option)) {
                throw error(selected.file(), selected.key(), "managed window/player/directory options must use their dedicated settings");
            }
            if (jvm && arg.startsWith("-Xmx")) {
                if (!arg.matches("-Xmx[1-9][0-9]*[kKmMgG]?")) throw error(selected.file(), selected.key(), "invalid -Xmx size");
                if (result.stream().anyMatch(a -> a.startsWith("-Xmx"))) throw error(selected.file(), selected.key(), "duplicate -Xmx in selected arguments");
            }
            result.add(arg);
        }
        return List.copyOf(result);
    }

    private static IllegalArgumentException error(String source, String key, String problem) {
        return new IllegalArgumentException(source + " [" + key + "]: " + problem);
    }

    public record Value(String file, String key, String raw) implements Serializable {}

    public record ResolvedInstance(String target, String variant, File directory, String memory,
            Integer width, Integer height, String playerName, List<String> extraJvmArgs,
            List<String> extraGameArgs, String deployment) implements Serializable {
        /** The selected extra JVM array has priority over the selected memory property. */
        public String effectiveMemory() {
            return extraJvmArgs.stream().filter(arg -> arg.startsWith("-Xmx"))
                    .map(arg -> arg.substring(4)).findFirst().orElse(memory);
        }

        /** Apply effectiveMemory through the native heap API, never emit a second -Xmx. */
        public List<String> jvmArgumentsWithoutMaxHeap() {
            return extraJvmArgs.stream().filter(arg -> !arg.startsWith("-Xmx")).toList();
        }

        public List<String> clientArguments() {
            List<String> result = new ArrayList<>();
            if (width != null) result.addAll(List.of("--width", width.toString()));
            if (height != null) result.addAll(List.of("--height", height.toString()));
            if (playerName != null) result.addAll(List.of("--username", playerName));
            return List.copyOf(result);
        }
    }
}
