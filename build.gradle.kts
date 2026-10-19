// Same root text scope as the template, with exclusions applied before filesystem traversal.
val repositoryText =
    fileTree(projectDir) {
        include(
            "*.md",
            "*.properties",
            "*.toml",
            "LICENSE",
            "LICENSE.txt",
            "NOTICE",
            "COPYING",
            "COPYING.LESSER",
            ".gitmodules",
            "licenses/**",
            ".gitattributes",
            ".editorconfig",
            ".gitignore",
            "gradle/*.toml",
            ".github/**/*.yml",
            ".github/**/*.yaml",
            ".github/**/*.md",
            "documents/**/*.md",
        )
        for (extension in
            io.github.recrivenvi.conventions.component.ComponentConventions.TEXT_EXTENSIONS) {
            include("validations/**/*.${extension}")
        }
        exclude(
            "local.toml",
            "validations/*/instance/**",
            "validations/*/result/**",
            ".git/**",
            ".gradle/**",
            "build/**",
            "instances/**",
            "versions/**",
            "components/**",
        )
    }

// Windows scripts have their own component formatter; all root-format inputs use LF (C-02).
extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    format("misc") { target(repositoryText) }
}
