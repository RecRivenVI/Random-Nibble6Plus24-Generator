pluginManagement {
    includeBuild("components/configuration")
    includeBuild("components/conventions")
    includeBuild("components/compliance")
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("io.github.recrivenvi.configuration")
    id("io.github.recrivenvi.conventions")
    id("io.github.recrivenvi.compliance")
}

rootProject.name = "RandomNibble6Plus24Generator"

targets {
    register("26.2-fabric")
}

components {
    tool("worldgen_validation")
}
