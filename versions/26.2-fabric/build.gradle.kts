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
