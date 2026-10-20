# 构建与测试依赖

构建工具与各 Target 共用的测试依赖由 `gradle/libs.versions.toml` 定义；本文件不另行定义版本号。

| 依赖 | 用途 | 版本来源 |
| --- | --- | --- |
| Fabric Loader | 模组加载与运行 | Target 的 `loader_version`；最低要求由 `loader_minimum` 定义 |
| Fabric Registry Sync（`net.fabricmc.fabric-api:fabric-registry-sync-v0`） | 当前产品使用的注册表同步模块 | Target 的 `fabric_registry_sync_version` |
| Loom | Fabric 构建与开发运行 | 版本目录的 `loom` |
| NeoForge / Forge | 对应新 Target 的加载与运行；生成能力仍待移植验收 | Target 的 `loader_version` |
| ModDevGradle | NeoForge 与 Forge 构建、反编译和开发运行 | 版本目录的 `moddev` |
| Foojay resolver | 下载 Java 工具链 | 版本目录的 `foojay` |
| Spotless | 格式任务 | 版本目录的 `spotless` |
| google-java-format | Java 格式化 | 版本目录的 `google-java-format` |
| ktfmt | Kotlin DSL 格式化 | 版本目录的 `ktfmt` |
| rumdl | Markdown 格式与检查 | 版本目录的 `rumdl` |
| tomlj | 模板配置解析 | 版本目录的 `tomlj` |
| JUnit BOM（`org.junit:junit-bom`） | 对齐原生单元测试的 JUnit 版本 | 版本目录的 `junit` |
| JUnit Jupiter（`org.junit.jupiter:junit-jupiter`） | 单元测试 API 与引擎 | JUnit BOM |
| JUnit Platform Launcher（`org.junit.platform:junit-platform-launcher`） | Gradle 测试运行 | JUnit BOM |
| Mixin annotation processor | 1.20.1 Forge 产品与 probe 各自的 SRG refmap；与该加载器实际 Mixin 一致，只用于编译 | Target 的 `mixin_processor_version` |

JUnit 只参与测试，不进入产品发行 JAR；其余构建工具不作为产品依赖发布。Fabric 产品只声明 Fabric Registry Sync 模块及其原生依赖，不为升级添加整个 Fabric API；NeoForge/Forge 使用各自原生注册与加载机制，不带入 Fabric API。

1.21.1 NeoForge 的 FML 元数据使用本 Target 的 `javafml_minimum`，不复用新版本省略该字段的格式。开发运行将已登记 product 组件的同一二进制展开到产品模块输出，并从 ModDev 独立解析的 legacy/modules 运行配置排除重复组件模块；发行 JAR 仍由模板合并同一 product 组件，不复制源码或绕过模块安全检查。1.20.1 Forge 产品的构造器种子路由与 probe 分别生成本版 SRG refmap；产品配置由原生 MixinExtension 登记，探针启动参数只来自 validation.toml，日常运行不加载这些探针。

Gradle 发行版与校验和由 Wrapper 配置定义；守护进程 Java 要求由 `gradle-daemon-jvm.properties` 定义，Target 的 Java 要求由 `java_version` 定义。Minecraft 自带的库随游戏版本取得，不独立覆盖其版本。

当前 Target 与版本事实按上表读取，版本移植情况见[26.3 移植记录](../development/porting-minecraft_26_3.md)，本轮稳定版本与来源见[依赖更新记录](../development/porting-dependencies.md)。依赖变更按[依赖升级规程](../../gradle/conventions/specification/development/procedure-update_dependencies.md)处理；本轮已明确授权稳定依赖更新，Minecraft、模板及 Oracle 夹具保持不变，不单独修改受保护模板文件。

与 Minecraft 无关的构建、格式化和测试工具版本随模板标签统一更新；Loom 和 ModDevGradle 保留项目选定版本。具体工具版本只在版本目录中定义，不单独修改模板插件来绕过接口不兼容。

CI 无法取得本机登记的验证输入，因此按仓库规范 W-03 执行 `checkRepository`；包含本地输入的 `check` 与 `verifyRelease` 在本机执行。研究文档中的本机结果路径保留为文字，不要求云端取得测试世界或原始证据。

## BOP 验证输入

`compatibility-bop` 单独使用 Biomes O' Plenty、TerraBlender、GlitchCore 与完整 Fabric API。版本、来源和允许的 SHA-256 只在根目录 `inputs.toml` 定义，本机文件路径只写入 `local.toml` 的 `[inputs]`。验证启动前核对哈希与模组元数据；这些文件不进入产品 JAR，也不自动安装到日常客户端或已有世界。

产品对 TerraBlender 使用可选的原生初始化接口，没有添加 BOP 的强制依赖。不安装 TerraBlender 时不加载该接口、不改变原版生成器配置。验证范围与命令见 [BOP 本地种子隔离](../../validations/compatibility-bop/validation.md)。

## 人工验收客户端输入

Custom Skin Loader 与 Zume 的 Modrinth 来源、JAR 元数据版本和 SHA-256 在 `inputs.toml`，本机路径及实例选择在 `local.toml`。只供人工客户端使用，不进入产品、自动性能基线或独立原版正确性对照。Custom Skin Loader 通用版声明覆盖现有 Target；Zume 的匹配发行仅覆盖 1.20.1 Forge 与 1.21.1 NeoForge，26.x 跳过。1.20.1 Forge 的发布 JAR 不能直接假定适合开发命名空间，Zume 暂不加入该开发实例，待本版原生客户端适配验证；已下载文件保留，不能把下载或配置当作游戏加载通过。

## MCP alpha 自动化输入

固定批次、发行版本、来源与 SHA-256 在 `inputs.toml` 的 `minecraft-protocol-alpha-fabric` 与 `minecraft-protocol-alpha-neoforge`，本机复制路径在 `local.toml`。只用于[项目独占的接入验证](../../validations/integration-mcp_alpha/validation.md)，不进入产品、用户存档或其它线程的实例。各端先验证只装一个匹配 MCP JAR 的原生 Loader，再接 Mosaic；不补额外依赖、不构建或修改 MCP 仓库，端口按实例 endpoint 发现并绑定本次 Runtime 身份。NeoForge 保留模板生成的原生 loader／runtime classpath，排除开发产品目录后安装发行 JAR；该验证不声称独立官方安装器已验收。
