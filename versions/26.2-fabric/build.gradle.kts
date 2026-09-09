plugins {
    id("net.fabricmc.fabric-loom") version "1.17.20"
}

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName = property("artifact_base_name") as String
}

loom {
    runs {
        named("server") {
            runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/server"))
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-registry-sync-v0:7.1.0+c7bd5b8e9e")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

// Real Fabric lifecycle regression, isolated from ordinary client/server runs.
if (providers.gradleProperty("identityLifecycleTest").isPresent) {
    sourceSets.test.get().resources.srcDir("src/test/identity-lifecycle/resources")
    loom.mods.create("mosaic_identity_lifecycle_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/identity-lifecycle"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Opt-in production SPAWN equivalence and persistence regression.
if (providers.gradleProperty("spawnReuseTest").isPresent) {
    require(!providers.gradleProperty("identityLifecycleTest").isPresent)
    sourceSets.test.get().resources.srcDir("src/test/spawn-reuse/resources")
    loom.mods.create("mosaic_spawn_reuse_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/spawn-reuse"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Native concentric-ring Oracle regression. Test sources never enter the production JAR.
if (providers.gradleProperty("strongholdTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir("src/test/stronghold/resources")
    loom.mods.create("mosaic_stronghold_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/stronghold-test"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Explicit lifecycle cancellation / real-error propagation regression, excluded from production JARs.
if (providers.gradleProperty("generationShutdownTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir("src/test/generation-shutdown/resources")
    loom.mods.create("mosaic_generation_shutdown_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/generation-shutdown"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Physical POI ownership and restart regression; no test hooks enter the product JAR.
if (providers.gradleProperty("poiOwnershipTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir("src/test/poi-ownership/resources")
    loom.mods.create("mosaic_poi_ownership_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/poi-ownership"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().runtimeClasspath
    }
}

// Native placement-contract and false-negative regression, not part of product artifacts.
if (providers.gradleProperty("candidatePruningTest").isPresent) {
    require(!providers.gradleProperty("performanceProbe").isPresent)
    sourceSets.test.get().resources.srcDir("src/test/candidate-pruning/resources")
    loom.mods.create("mosaic_candidate_pruning_test") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/candidate-pruning"))
    }
    tasks.named<JavaExec>("runServer") {
        dependsOn(tasks.testClasses)
        classpath += sourceSets.test.get().output
    }
}

// Opt-in research mod. It is not included in the production artifact or normal runs.
if (providers.gradleProperty("lightweightProbe").isPresent) {
    require(!providers.gradleProperty("identityLifecycleTest").isPresent)
    sourceSets.test.get().resources.srcDir("src/test/lightweight/resources")
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/lightweight-research"))
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
    sourceSets.test.get().resources.srcDir("src/test/performance/resources")
    loom.mods.create("mosaic_performance_probe") { sourceSet(sourceSets.test.get()) }
    loom.runs.named("server") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/performance-server"))
        if (providers.gradleProperty("structureIsolationProbe").isPresent) {
            runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/structure-isolation"))
        }
        if (providers.gradleProperty("lazyStructureStudy").isPresent) {
            runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/lazy-structure"))
        }
    }
    loom.runs.named("client") {
        runDirectory.set(rootProject.layout.projectDirectory.dir("runs/26.2-fabric/performance-client"))
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
