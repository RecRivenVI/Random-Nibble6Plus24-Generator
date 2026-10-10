# 构建与测试依赖

构建工具与各 Target 共用的测试依赖由 `gradle/libs.versions.toml` 定义；本文件不另行定义版本号。

| 依赖 | 用途 | 版本来源 |
| --- | --- | --- |
| Fabric Loader | 模组加载与运行 | Target 的 `loader_version`；最低要求由 `loader_minimum` 定义 |
| Fabric Registry Sync（`net.fabricmc.fabric-api:fabric-registry-sync-v0`） | 当前产品使用的注册表同步模块 | Target 的 `fabric_registry_sync_version` |
| Loom | Fabric 构建与开发运行 | 版本目录的 `loom` |
| ModDevGradle | 模板构建插件类路径；当前没有 NeoForge 或 Forge Target | 版本目录的 `moddev` |
| Foojay resolver | 下载 Java 工具链 | 版本目录的 `foojay` |
| Spotless | 格式任务 | 版本目录的 `spotless` |
| google-java-format | Java 格式化 | 版本目录的 `google-java-format` |
| ktfmt | Kotlin DSL 格式化 | 版本目录的 `ktfmt` |
| rumdl | Markdown 格式与检查 | 版本目录的 `rumdl` |
| tomlj | 模板配置解析 | 版本目录的 `tomlj` |
| JUnit BOM（`org.junit:junit-bom`） | 对齐原生单元测试的 JUnit 版本 | 版本目录的 `junit` |
| JUnit Jupiter（`org.junit.jupiter:junit-jupiter`） | 单元测试 API 与引擎 | JUnit BOM |
| JUnit Platform Launcher（`org.junit.platform:junit-platform-launcher`） | Gradle 测试运行 | JUnit BOM |

JUnit 只参与测试，不进入产品发行 JAR；其余构建工具不作为产品依赖发布。产品目前只声明 Fabric Registry Sync 模块及其原生依赖，不为升级添加整个 Fabric API。

Gradle 发行版与校验和由 Wrapper 配置定义；守护进程 Java 要求由 `gradle-daemon-jvm.properties` 定义，Target 的 Java 要求由 `java_version` 定义。Minecraft 自带的库随游戏版本取得，不独立覆盖其版本。

升级选择与核实来源见[升级计划](../project/plan-target_upgrade.md)。主线采用 Minecraft 最新正式版及支持它的最新 Loader，只有 Loader 可采用测试版；产品依赖选择支持该游戏正式版的最新发布，构建工具采用最新稳定版。模板受保护文件只随完整模板标签升级，不单独修改。

Java 格式化器选择当前 Spotless 支持的最新版本。1.37.0 移除了 Spotless 调用的 `Style.valueOf`，因此当前固定最新兼容版 1.36.0；保留格式检查，不修改模板来绕过工具接口不兼容。
