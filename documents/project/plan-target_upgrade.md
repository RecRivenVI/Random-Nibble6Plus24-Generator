# Target 与依赖升级计划

本计划于 2026-10-10 核实公开发布信息并完成执行。`26.3-fabric` 的 API 移植、构建、原生对照、自动运行回归与人工生命周期复验通过，已切换为参考 Target；`26.2-fabric`、原有实例与历史证据保留。未提交或推送此次改动。

## 目标

主线持续使用 Minecraft Java 最新正式版与支持它的最新 Fabric Loader。测试版仅适用于 Loader；Minecraft snapshot、pre-release 与 release candidate 均不作为升级目标。新功能开发之前先完成版本与相关依赖升级。

本次确定的目标为 `26.3-fabric`。仅延续现有 Fabric 加载器路线，不新增 NeoForge 或 Forge 产品 Target。

## 范围

### 目标版本

| 项目 | 本次升级目标 | 处理 |
| --- | --- | --- |
| Minecraft | `26.3` | 最新正式版；按新增 Target 规程移植，验收后切换参考 Target |
| Fabric Loader | `0.19.5` | 当前已是支持目标游戏的最新版本，不降级到较旧测试版 |
| Java | `25` | 官方游戏元数据仍要求此主版本；不为追求新版本而独立提高 Java 要求 |
| Fabric Registry Sync | `8.0.2+1ca9f6365d` | 更新现有模块；版本取自支持目标正式版的 Fabric API 发布 POM |
| Fabric Loader 最低要求 | `0.19.3` | 保留已登记的最低要求；本次运行验证使用当前最新 `0.19.5`，不宣称测试了更低版本 |
| 构建模板 | `1.2.2` | 先按完整标签升级受保护文件，不打零散补丁 |

Fabric API `0.162.0+26.3` 是支持目标正式版的最新正式发布，仅作为模块兼容性与版本来源；产品继续只依赖当前需要的 Registry Sync 模块，不新增全量 Fabric API。不选择仅支持更新游戏快照的模块版本。

### 构建、测试与 CI

| 工具 | 本次核实的最新稳定版 | 处理 |
| --- | --- | --- |
| Gradle | `9.8.1` | 当前已对齐 |
| Loom | `1.18.3` | 当前已对齐；不使用 `1.18-SNAPSHOT` 构建插件 |
| ModDevGradle | `2.0.148` | 当前已对齐；保留模板类路径依赖，不新增加载器 Target |
| Foojay resolver | `1.0.0` | 当前已对齐 |
| Spotless | `8.10.4` | 当前已对齐 |
| google-java-format | `1.37.0` | 实测与最新 Spotless 不兼容，采用最新兼容的 `1.36.0`；不修改受保护实现 |
| ktfmt | `0.65` | 当前已对齐，以上游发布为准，不按旧 Maven group 的过时元数据降级 |
| rumdl | `0.2.79` | 随本次模板升级对齐 |
| tomlj | `2.2.0` | 当前已对齐 |
| JUnit BOM | `6.1.3` | 当前已对齐；Jupiter 与 Launcher 继续由 BOM 选取 |

CI 当前使用的 `actions/checkout@v7`、`actions/setup-java@v6`、`gradle/actions/setup-gradle@v6` 与 `actions/upload-artifact@v7` 已覆盖核实的最新稳定发布线，不为升级制造无必要的改动。模板工作流仍只按模板标签更新。

Minecraft 捆绑的库随新游戏版本取得，不独立覆盖。当前没有本地输入或新增第三方产品库的升级需求。依赖版本的正式权威位置仍是 `target.properties`、版本目录与 Wrapper；本计划记录此次升级选择与验收进度。

## 步骤

1. **完成**：核对当前分支、工作树、规范与依赖声明；保留现有世界和历史证据。
2. **完成**：核对 Mojang 游戏元数据、Fabric 支持列表、Registry Sync 模块及构建工具发布，记录本计划。
3. **完成**：按[模板升级规程](../development/procedure-upgrade_template.md)复制完整模板标签，并按[依赖升级规程](../development/procedure-update_dependencies.md)调整必要项目版本事实。
4. **完成**：按[新增 Target 规程](../development/procedure-add_target.md)移植产品、测试与独立探针。两版相同的纯 Java 合同类型移入 `worldgen_contract` 产品组件；未移植的轻量研究探针保留在旧 Target。验证工具按 Target 分离实例和结果，不重写历史结果。
5. **完成**：基于实际 26.3 字节码与反编译源码审计阶段、Mixin、结构与存档接口；适配要点见[移植记录](../development/porting-minecraft_26_3.md)。新版原版把 NOISE/SURFACE/CARVERS 合并为 TERRAIN，不伪造旧阶段 golden。
6. **完成**：两版单元测试、格式、全量构建、发行检查与严格合规通过，合规失败和警告均为零；配置缓存正常。新版普通 Vanilla 服务端和首个独立 Native 对照通过，新版证据独立记录，不替换旧 hash。
7. **完成**：普通生产 FULL 请求、保存和重载通过，重载 Artifact 与 SPAWN 计数为零，测试修改保留。结构、身份、SPAWN、POI、取消、Overlay 与已生成区域 `/locate` 回归通过；72 个独立原生世界与无关 host 隔离结果完全一致，多 host 对照通过。
8. **完成**：独立 Creative 测试存档 `Mosaic 26.3 Upgrade` 首次人工探索暴露了运行期 BIOMES 邻区交接错误；修复后通过冷区、并发、三维度、POI 和关闭重载回归。使用者重新探索、保存退出并进入同一存档后反馈目前没有问题，日志确认保存、停止与再次登录，无新增崩溃报告。使用者已明确接受 EULA 并授权本机配置；新版无探针日常独立服务端完成启动、保存和正常停止，全程未使用 GUI 自动化。
9. **完成**：参考 Target、当前支持表、使用文档与更新日志已同步到 26.3。保留旧 Target 和原存档，不自动提交、推送或继续性能工作。

## 结果

26.3 单元测试 106 项、26.2 单元测试 109 项通过。26.3 普通 Vanilla 服务端完成启动、保存和正常停止；普通 Mosaic Chunk 请求到 FULL、保存并重启重载通过。

Stronghold 的 5,120 个正例和结构候选的 47,415 个正例均无漏检，Stronghold 保存重载通过。三维度身份生命周期检查完成，45 万次热路径查询没有文件检查；SPAWN 携带的 biome palette 与重建对照一致。补齐 biome 邻区后的并发 POI 检查完成 268 次 Artifact 生成和 76 次 reconciliation，越线程和重叠存储访问均为零；重载 Artifact 计数为零，POI 数据一致。关闭时 49 个未完成任务正常取消，重启后已保存区块未重算、未完成区块可重新请求；真正生成异常和意外取消仍被正常报告。

Java 格式化器 1.37.0 将 `Style` 从 enum 改为 record，当前 Spotless 仍调用 `Style.valueOf`，实测所有 Java 格式任务失败。因此采用仍提供该接口的 1.36.0，而非修改模板或关闭格式检查。这是本次依赖升级的已知兼容限制。

独立 Native matrix 72/72 通过：Overworld origin 8、non-origin 32、Nether 16、End 16。每项原生证据来自全新世界，隔离消费者使用无关 host seed `987654321`；stable FEATURES、9-writer 顺序、生物群系观察与 feature seed 序列一致。四个代表 fixture 的完整 TERRAIN 前沿也独立一致；另选四个 local worlds，在 host seed `0`、`42`、`-42` 下重复对照通过。

移植过程中发现的高度图初始化遗漏，已按 26.3 原版 TERRAIN 调用链补齐；完整矩阵发现的 `WorldGenerationContext.of` physical generator 入口，已在绑定隔离区域时改用本地 generator 构造原版上下文。重跑对照通过，未知访问检查与 canonical 忽略规则未放宽。

人工测试暴露的运行期崩溃来自 `NaturalSpawner.createState` 的环境属性查询：physical holder 的 BIOMES future 曾指向没有 biome 数据的 EMPTY ProtoChunk。物理计划现在物化 Vanilla 依赖中全部 BIOMES-bearing 位置，并在先前 structure-only holder 后续推进到 BIOMES 时交接真实数据。冷区检查覆盖 49 个依赖位置，运行期属性读取和同目标 32 次并发请求通过；该修复不改变隔离生成结果，但增加冷区边缘生成工作，性能不能沿用 26.2 的人工验收。

六类真实结构的 Overlay 与索引查询、保存重载通过。原点及非原点 FULL 请求、同目标 32 次请求去重、EndSpike 实体和 Nether Fortress 生命周期通过。专门创建的 26.2 存档副本在 26.3 重载后，已有 FULL Chunk 的 Artifact/SPAWN 重执行计数均为零，测试修改保留；用户原存档未升级或改写。

26.2 的 SURFACE/CARVERS smoke 保持原 golden，旧版本完整矩阵未重跑。26.3 的人工客户端生命周期复验已另行完成；这不代表新版本性能验收或旧版本完整矩阵通过，跨版本的 Vanilla 地形变化不以旧 golden 验收。

遇到依赖未发布、普通 Vanilla 对照不一致或必须改变冻结合同的情况，明确记录阻塞，不用占位邻区、吞异常、扩大 canonical 忽略字段或静默回退旧游戏版本通过验收。

## 核实来源

- 游戏最新正式版与 Java 要求：[Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)、[目标版本元数据](https://piston-meta.mojang.com/v1/packages/702fe59163c6ee6578607daa85811d9bc9c7cc40/26.3.json)。
- Loader 支持：[Fabric 目标游戏 Loader 列表](https://meta.fabricmc.net/v2/versions/loader/26.3)。
- 产品模块版本：[Fabric API 发布 POM](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.162.0+26.3/fabric-api-0.162.0+26.3.pom)、[Registry Sync 发布元数据](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-registry-sync-v0/maven-metadata.xml)、[Fabric API 发布](https://modrinth.com/mod/fabric-api/version/0.162.0%2B26.3)。
- 模板：[完整发布标签](https://github.com/RecRivenVI/Ravens-Mod-Template/releases/tag/1.2.2)。
- Gradle 与 Loom：[Gradle 当前稳定版](https://services.gradle.org/versions/current)、[Loom 发布元数据](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml)。
- 其他构建与测试依赖：[ModDevGradle](https://maven.neoforged.net/releases/net/neoforged/moddev-gradle/maven-metadata.xml)、[Foojay](https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver/maven-metadata.xml)、[Spotless](https://repo.maven.apache.org/maven2/com/diffplug/spotless/spotless-plugin-gradle/maven-metadata.xml)、[google-java-format](https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/maven-metadata.xml)、[ktfmt](https://github.com/Kotlin/ktfmt/releases/tag/v0.65)、[rumdl](https://github.com/rvben/rumdl/releases/tag/v0.2.79)、[tomlj](https://repo.maven.apache.org/maven2/org/tomlj/tomlj/maven-metadata.xml)、[JUnit](https://repo.maven.apache.org/maven2/org/junit/junit-bom/maven-metadata.xml)。
- CI 发布：[checkout](https://github.com/actions/checkout/releases/tag/v7.0.1)、[setup-java](https://github.com/actions/setup-java/releases/tag/v6.0.1)、[setup-gradle](https://github.com/gradle/actions/releases/tag/v6.4.0)、[upload-artifact](https://github.com/actions/upload-artifact/releases/tag/v7.0.2)。
