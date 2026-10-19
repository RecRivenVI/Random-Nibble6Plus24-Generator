plugins {
    id("net.neoforged.moddev.legacyforge")
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

legacyForge.mods.configureEach {
    if (name == target.fact("mod_id")) sourceSet(embeddedProducts)
}

// FML must not load the bundled component again as a separate automatic library module.
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

// The probe needs native SRG remapping; it is never part of the product artifact.
val nativeMixins = extensions.getByType<net.neoforged.moddevgradle.legacyforge.dsl.MixinExtension>()

nativeMixins.config("mosaic-worldgen.mixins.json")

val mainRefmap = nativeMixins.add(sourceSets["main"], "mosaic-worldgen.refmap.json")

tasks.named<ProcessResources>("processResources") {
    dependsOn(tasks.named("compileJava"))
    from(mainRefmap)
}

// LegacyForge also adds the refmap to Jar directly; development needs the resource copy.
val runtimeRefmap =
    tasks.named<ProcessResources>("processResources").map {
        it.destinationDir.resolve("mosaic-worldgen.refmap.json")
    }

tasks.named<Jar>("jar") {
    filesMatching("mosaic-worldgen.refmap.json") { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    doFirst {
        check(mainRefmap.get().asFile.readBytes().contentEquals(runtimeRefmap.get().readBytes())) {
            "Development and packaged Mixin refmaps differ"
        }
    }
}

val probeRefmap =
    extensions
        .getByType<net.neoforged.moddevgradle.legacyforge.dsl.MixinExtension>()
        .add(sourceSets["probe"], "mosaic-structure-input-test.refmap.json")

tasks.named<ProcessResources>("processProbeResources") {
    dependsOn(tasks.named("compileProbeJava"))
    from(probeRefmap)
}

// LegacyForge aggregates AP mappings from every configured source set for reobfuscation.
tasks
    .matching { it.name == "reobfJar" }
    .configureEach {
        dependsOn(tasks.named("compileProbeJava"))
    }

dependencies {
    add(
        "annotationProcessor",
        target.module("org.spongepowered:mixin", "mixin_processor_version") + ":processor",
    )
    add(
        "probeAnnotationProcessor",
        target.module("org.spongepowered:mixin", "mixin_processor_version") + ":processor",
    )
}
