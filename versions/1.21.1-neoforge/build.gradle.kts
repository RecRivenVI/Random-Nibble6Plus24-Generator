plugins {
    id("net.neoforged.moddev")
}

// Development uses the same component binaries inside the mod module as the release JAR.
val embeddedProducts = sourceSets.create("embeddedProducts")
val prepareEmbeddedProducts =
    tasks.register<Sync>("prepareEmbeddedProducts") {
        from(configurations.named("productComponents").map { files -> files.map { zipTree(it) } })
        exclude("META-INF/MANIFEST.MF", "**/module-info.class")
        into(layout.buildDirectory.dir("embeddedProducts"))
    }

embeddedProducts.output.dir(
    mapOf("builtBy" to prepareEmbeddedProducts),
    prepareEmbeddedProducts.map { it.destinationDir },
)

neoForge.mods.configureEach {
    if (name == target.fact("mod_id")) sourceSet(embeddedProducts)
}

// FML 4 must not load the bundled component again as a separate automatic library module.
// ModDev resolves these run configurations independently of SourceSet.runtimeClasspath.
configurations
    .matching { it.name.endsWith("LegacyClasspath") || it.name.endsWith("ModulesOnly") }
    .configureEach {
        exclude(group = "components")
    }

sourceSets.configureEach {
    if (name == "main" || name == "probe") {
        runtimeClasspath =
            runtimeClasspath.minus(configurations.named("productComponents").get()) +
                embeddedProducts.output
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// These native primitive/Codec tests do not use the loader's game-test bootstrap.
sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    jvmArgs("-XX:ActiveProcessorCount=4")
}
