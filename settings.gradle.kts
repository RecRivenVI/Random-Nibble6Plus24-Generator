pluginManagement {
    includeBuild("gradle/configuration") { name = "raven-configuration" }
    val facts = java.util.Properties().apply {
        file("versions/26.2-fabric/target.properties").inputStream().use { load(it) }
    }
    plugins {
        id("net.fabricmc.fabric-loom") version facts.getProperty("loom_version")
    }
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "Random-Nibble6Plus24-Generator"

include(":version:26.2-fabric")
project(":version").projectDir = file("versions")
project(":version:26.2-fabric").projectDir = file("versions/26.2-fabric")
