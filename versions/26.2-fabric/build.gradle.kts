import raven.gradle.RepositoryConfiguration
import raven.gradle.LoomRunArguments

plugins {
    id("net.fabricmc.fabric-loom")
}

val configuration = rootProject.extensions.getByType<RepositoryConfiguration>()
val projectFacts = configuration.projectFacts
val targetFacts = configuration.targetFacts(project.name)
fun targetFact(name: String): String = requireNotNull(targetFacts[name]) { "Missing target fact: $name" }

base {
    archivesName = projectFacts.getValue("mod_archive_base_name")
}

val validationProjects = setOf(
    "conformance-structure-candidates",
    "conformance-feature-order",
    "conformance-worldgen",
    "integration-artifact",
    "regression-generation-shutdown",
    "conformance-world-identity",
    "conformance-carvers",
    "regression-vanilla",
    "performance-worldgen",
    "integration-derived-state",
    "integration-materialization",
    "integration-spawn-full",
    "regression-poi-ownership",
    "integration-production-lifecycle",
    "regression-spawn-reuse",
    "conformance-stronghold",
    "feature-structure-index",
    "feature-structure-overlay"
)
val selectedValidation = providers.gradleProperty("validationProject").orNull
require(selectedValidation == null || selectedValidation in validationProjects) { "Unknown validationProject: $selectedValidation" }

loom {
    runs {
        named("client") {
            runDirectory.set(rootProject.layout.projectDirectory.dir(selectedValidation?.let { "validations/$it/instance/client" } ?: "instances/26.2-fabric/client"))
        }
        named("server") {
            runDirectory.set(rootProject.layout.projectDirectory.dir(selectedValidation?.let { "validations/$it/instance/server" } ?: "instances/26.2-fabric/server"))
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${targetFact("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${targetFact("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-registry-sync-v0:${targetFact("fabric_registry_sync_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${targetFact("junit_version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(targetFact("java_version").toInt())
    }

    sourceCompatibility = JavaVersion.toVersion(targetFact("java_version"))
    targetCompatibility = JavaVersion.toVersion(targetFact("java_version"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = targetFact("java_version").toInt()
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val metadata = mapOf(
        "version" to projectFacts.getValue("mod_version"),
        "mod_id" to projectFacts.getValue("mod_id"),
        "mod_name" to projectFacts.getValue("mod_name"),
        "mod_author" to projectFacts.getValue("mod_author"),
        "mod_environment" to if (configuration.environment == "both") "*" else configuration.environment,
        "loader_version" to targetFact("loader_version"),
        "minecraft_version" to targetFact("minecraft_version"),
        "java_version" to targetFact("java_version")
    )
    inputs.properties(metadata)

    filesMatching("fabric.mod.json") {
        expand(metadata)
    }
}

sourceSets.test {
    java.srcDirs(
        rootProject.layout.projectDirectory.dir("validations/conformance-structure-candidates/task/src/java"),
        rootProject.layout.projectDirectory.dir("validations/conformance-world-identity/task/src/java"),
        rootProject.layout.projectDirectory.dir("validations/regression-poi-ownership/task/src/java"),
        rootProject.layout.projectDirectory.dir("validations/regression-generation-shutdown/task/src/java"),
        rootProject.layout.projectDirectory.dir("validations/regression-spawn-reuse/task/src/java"),
        rootProject.layout.projectDirectory.dir("validations/conformance-stronghold/task/src/java"),
        rootProject.layout.projectDirectory.dir("validations/performance-worldgen/task/src/java")
    )
}

// Loom owns the source set, directory, DLI/classpath and native argument providers.
// Only its two existing variants are wired: no new multiplayer run is invented.
listOf("client", "server").forEach { variant ->
    val instance = providers.provider { configuration.resolveInstance(project.name, variant) }
    val fixturePlayer = if (selectedValidation == "integration-spawn-full")
        providers.gradleProperty("spawnFixturePlayer").getOrElse("SpawnVerifier") else null
    loom.runs.named(variant) {
        programArguments.addAll(instance.map { LoomRunArguments.clientArguments(it, fixturePlayer) })
        programArguments.addAll(instance.map { it.extraGameArgs() })
    }
    tasks.named<JavaExec>("run" + variant.replaceFirstChar { it.uppercase() }) {
        val resolved = instance.get()
        val localJava = configuration.javaExecutable(configuration.javaVersion(project.name))
        if (localJava != null) setExecutable(localJava.absolutePath)
        else javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(configuration.javaVersion(project.name)))
        })
        resolved.effectiveMemory()?.let { maxHeapSize = it }
        // Appended AFTER Loom's native JVM provider, not before its finalisation.
        jvmArgumentProviders.add(LoomRunArguments(
            listOf("-Dmosaic.repositoryRoot=" + rootProject.projectDir.absolutePath),
            resolved.jvmArgumentsWithoutMaxHeap()
        ))
        doFirst(LoomRunArguments.ManagedOptionsGuard())
    }
}

tasks.test {
    useJUnitPlatform()
}

// Real Fabric lifecycle regression, isolated from ordinary client/server runs.
if (providers.gradleProperty("identityLifecycleTest").isPresent) {
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/conformance-world-identity/task/resources"))
    loom.mods.create("mosaic_identity_lifecycle_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/conformance-world-identity/instance/server"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Opt-in production SPAWN equivalence and persistence regression.
if (providers.gradleProperty("spawnReuseTest").isPresent) {
    require(!providers.gradleProperty("identityLifecycleTest").isPresent)
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/regression-spawn-reuse/task/resources"))
    loom.mods.create("mosaic_spawn_reuse_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/regression-spawn-reuse/instance/server"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Native concentric-ring Oracle regression. Test sources never enter the production JAR.
if (providers.gradleProperty("strongholdTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/conformance-stronghold/task/resources"))
    loom.mods.create("mosaic_stronghold_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/conformance-stronghold/instance/server"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Explicit lifecycle cancellation / real-error propagation regression, excluded from production JARs.
if (providers.gradleProperty("generationShutdownTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/regression-generation-shutdown/task/resources"))
    loom.mods.create("mosaic_generation_shutdown_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/regression-generation-shutdown/instance/server"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Physical POI ownership and restart regression; no test hooks enter the product JAR.
if (providers.gradleProperty("poiOwnershipTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/regression-poi-ownership/task/resources"))
    loom.mods.create("mosaic_poi_ownership_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/regression-poi-ownership/instance/server"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().runtimeClasspath
    }
}

// Native placement-contract and false-negative regression, not part of product artifacts.
if (providers.gradleProperty("candidatePruningTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/conformance-structure-candidates/task/resources"))
    loom.mods.create("mosaic_candidate_pruning_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/conformance-structure-candidates/instance/server"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Opt-in research mod. It is not included in the production artifact or normal runs.
if (providers.gradleProperty("lightweightProbe").isPresent) {
    require(!providers.gradleProperty("identityLifecycleTest").isPresent)
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/performance-worldgen/task/resources/lightweight"))
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/performance-worldgen/instance/lightweight-research"))
    }
    loom.mods.create("mosaic_lightweight_probe") {
        sourceSet(sourceSets.test.get())
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Observers only; independent of the lightweight algorithm experiment.
if (providers.gradleProperty("performanceProbe").isPresent) {
    require(!providers.gradleProperty("identityLifecycleTest").isPresent)
    require(!providers.gradleProperty("lightweightProbe").isPresent)
    sourceSets.test.get().resources.srcDir(rootProject.layout.projectDirectory.dir("validations/performance-worldgen/task/resources/performance"))
    loom.mods.create("mosaic_performance_probe") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/performance-worldgen/instance/server"))
        if (providers.gradleProperty("structureIsolationProbe").isPresent) {
            runDirectory.set(rootProject.layout.projectDirectory.dir("validations/performance-worldgen/instance/structure-isolation"))
        }
        if (providers.gradleProperty("lazyStructureStudy").isPresent) {
            runDirectory.set(rootProject.layout.projectDirectory.dir("validations/performance-worldgen/instance/lazy-structure"))
        }
    }
    loom.runs.named("client") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("validations/performance-worldgen/instance/client"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
    tasks.named<JavaExec>("runClient") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Reads actual Loom task providers without executing a Minecraft task or creating a world.
tasks.register("verifyFabricRunConfiguration") {
    group = "verification"
    description = "Verify the real Loom run model, arguments and directory without launching Minecraft."
    notCompatibleWithConfigurationCache("Explicit inspection of live Loader task model")
    val selectedModelVariant = providers.gradleProperty("runModelVariant").orNull
    require(selectedModelVariant == null || selectedModelVariant in listOf("client", "server"))
    doLast {
        val report = (selectedModelVariant?.let { listOf(it) } ?: listOf("client", "server")).map { variant ->
            val run = tasks.named<JavaExec>("run" + variant.replaceFirstChar { it.uppercase() }).get()
            val model = loom.runs.getByName(variant)
            val resolved = configuration.resolveInstance(project.name, variant)
            val main = run.mainClass.get() // Finalises native Loom defaults.
            LoomRunArguments.rejectManagedOptions(run.args ?: emptyList())
            val game = (run.args ?: emptyList()) + run.argumentProviders.flatMap { it.asArguments().toList() }
            val jvm = run.jvmArgumentProviders.flatMap { it.asArguments().toList() }
            val tail = listOf("-Dmosaic.repositoryRoot=" + rootProject.projectDir.absolutePath) + resolved.jvmArgumentsWithoutMaxHeap()
            check(jvm.takeLast(tail.size) == tail) { "Required/extra JVM argument order changed" }
            check(game.takeLast(resolved.extraGameArgs().size) == resolved.extraGameArgs()) { "Extra game arguments changed" }
            check(main == "net.fabricmc.devlaunchinjector.Main") { "Lost Loom launcher" }
            check(jvm.any { it.startsWith("-Dfabric.dli.config=") }) { "Lost Loom DLI configuration" }
            if (variant == "server") check(game.none { it in listOf("--width", "--height", "--username") })
            else listOf("--width", "--height", "--username").forEach { option ->
                check(game.count { it == option } <= 1) { "Duplicate managed client option: $option" }
            }
            if (resolved.effectiveMemory() != null) check(run.maxHeapSize.equals(resolved.effectiveMemory(), ignoreCase = true))
            check(jvm.none { it.startsWith("-Xmx") }) { "Max heap must be applied only by the native heap API" }
            if (resolved.effectiveMemory() != null) check(run.allJvmArgs.count { it.startsWith("-Xmx") } == 1)
            check(run.javaLauncher.get().metadata.languageVersion.asInt() == configuration.javaVersion(project.name))
            mapOf("variant" to variant, "main" to main, "directory" to model.runDirectory.get().asFile.absolutePath,
                "deployment" to resolved.deployment(), "heap" to run.maxHeapSize,
                "java" to run.javaLauncher.get().executablePath.asFile.absolutePath,
                "gameArgumentCount" to game.size, "jvmArgumentCount" to jvm.size,
                "sourceSet" to model.sourceSet.get(), "nativeProvidersPreserved" to true)
        }
        val output = layout.buildDirectory.file("configuration/loom-model.json").get().asFile
        output.parentFile.mkdirs()
        output.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(report)))
        logger.lifecycle("Loom client/server model verified; no Minecraft launched. Report: {}", output)
    }
}
