plugins {
    id("net.neoforged.moddev")
}

// FML's module loader cannot resolve a split package from a plain classpath dependency.
// Use the same component binaries as the release JAR, inside the development mod module.
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
