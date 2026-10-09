plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(localGroovy())
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")
    testImplementation(gradleTestKit())
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    // Functional-test repositories are temporary, never template modules.
    systemProperty("gradle.installation", gradle.gradleHomeDir!!.absolutePath)
    systemProperty("junit.jupiter.tempdir.cleanup.mode.default", "NEVER")
    providers.gradleProperty("testFixturesDirectory").orNull?.let {
        systemProperty("java.io.tmpdir", it)
    }
    providers.gradleProperty("alternateJdkHome").orNull?.let {
        systemProperty("alternateJdkHome", it)
    }
}

gradlePlugin {
    plugins {
        create("repositoryConfiguration") {
            id = "raven.repository-configuration"
            implementationClass = "raven.gradle.RepositoryConfigurationPlugin"
        }
    }
}
