plugins {
    base
    id("raven.repository-configuration")
}

tasks.named("check") {
    dependsOn(":version:26.2-fabric:verifyFabricRunConfiguration")
    dependsOn(gradle.includedBuild("raven-configuration").task(":check"))
    dependsOn(":version:26.2-fabric:check")
}

tasks.named("build") {
    dependsOn(":version:26.2-fabric:build")
}

tasks.register("test") {
    group = "verification"
    description = "Runs tests for every implemented target."
    dependsOn(":version:26.2-fabric:test")
}
