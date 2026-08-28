plugins {
    base
}

tasks.named("check") {
    dependsOn(":versions:26.2-fabric:check")
}

tasks.named("build") {
    dependsOn(":versions:26.2-fabric:build")
}

tasks.register("test") {
    group = "verification"
    description = "Runs tests for every implemented target."
    dependsOn(":versions:26.2-fabric:test")
}

