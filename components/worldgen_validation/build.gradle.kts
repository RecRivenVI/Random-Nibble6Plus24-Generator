plugins {
    base
}

val validateScripts =
    tasks.register<Exec>("validateScripts") {
        group = "verification"
        commandLine("pwsh", "-NoProfile", "-File", "scripts/Check-Scripts.ps1")
        inputs.files(fileTree("scripts") { include("**/*.ps1") })
    }

tasks.check {
    dependsOn(validateScripts)
}
