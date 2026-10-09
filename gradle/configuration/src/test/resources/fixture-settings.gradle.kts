pluginManagement {
    includeBuild("gradle/configuration") {
        name = "raven-configuration"
    }
}

rootProject.name = "Ravens-mod-template"

// Explicit Gradle project paths mirror the versions/ and components/ directories.
fun includeTarget(target: String) {
    require(target.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*-(neoforge|forge|fabric|quilt)")) && ".." !in target)
    require(findProject(":version:$target") == null) { "Duplicate Target: $target" }
    val directory = file("versions/$target")
    require(directory.resolve("build.gradle.kts").isFile && directory.resolve("target.properties").isFile) {
        "Target needs build.gradle.kts and target.properties: $target"
    }
    include(":version:$target")
    project(":version").projectDir = file("versions")
    project(":version:$target").projectDir = directory
}

// Only Gradle components belong here. IDs must be unique within their category.
fun includeComponent(component: String) {
    require(component.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")))
    require(findProject(":component:$component") == null) { "Duplicate component: $component" }
    val directory = file("components/$component")
    require(directory.resolve("build.gradle.kts").isFile) { "Missing Gradle component: $component" }
    include(":component:$component")
    project(":component").projectDir = file("components")
    project(":component:$component").projectDir = directory
}

// Register real modules explicitly after creating their build files.
// includeTarget("1.20.1-forge")
// includeComponent("core")
