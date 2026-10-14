pluginManagement {
    includeBuild("gradle/conventions")
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("io.github.recrivenvi.conventions")
}

rootProject.name = "RandomNibble6Plus24Generator"

targets {
    register("26.2-fabric")
    register("26.3-fabric")
}

components {
    product("worldgen_contract")
    tool("worldgen_validation")
}
