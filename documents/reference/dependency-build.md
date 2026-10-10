# 构建与测试依赖

构建工具与各 Target 共用的测试依赖由 `gradle/libs.versions.toml` 定义；本文件不另行定义版本号。

| 依赖 | 用途 | 版本来源 |
| --- | --- | --- |
| JUnit BOM（`org.junit:junit-bom`） | 对齐原生单元测试的 JUnit 版本 | 版本目录的 `junit` |
| JUnit Jupiter（`org.junit.jupiter:junit-jupiter`） | 单元测试 API 与引擎 | JUnit BOM |
| JUnit Platform Launcher（`org.junit.platform:junit-platform-launcher`） | Gradle 测试运行 | JUnit BOM |

这些依赖只参与测试，不进入产品发行 JAR。
