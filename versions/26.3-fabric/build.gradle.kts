plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
    implementation(
        target.module(
            "net.fabricmc.fabric-api:fabric-registry-sync-v0",
            "fabric_registry_sync_version",
        ),
    )
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

loom.runs.configureEach {
    systemProperties.put("mosaic.repositoryRoot", rootProject.projectDir.absolutePath)
}

val probe = sourceSets.named("probe")

sourceSets.test {
    compileClasspath += probe.get().output
    runtimeClasspath += probe.get().output
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    jvmArgs("-XX:ActiveProcessorCount=4")
}
