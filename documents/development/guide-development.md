# 开发、构建与验证

## 环境与入口

使用 Java 25 和仓库 Gradle Wrapper。项目名称/发布身份读取根 `gradle.properties`；Minecraft、Fabric、Loom 和 JUnit 版本读取 `versions/26.2-fabric/target.properties`，不要通过测试升级依赖。

共享运行设置读取 `instance.properties`，可选本机工具与覆盖读取 `local.properties`。统一解析和 Fabric 消费说明见 [配置指南](guide-configuration.md)。`verifyConfiguration` 检查配置，` :version:26.2-fabric:verifyFabricRunConfiguration` 检查真实 Loom 任务模型而不启动游戏。

从仓库根目录执行：

```powershell
.\gradlew.bat --max-workers=2 test build
.\gradlew.bat :version:26.2-fabric:runClient
.\gradlew.bat :version:26.2-fabric:runServer
```

后两条会启动真实游戏/服务器，须单独安排运行验收。EULA 必须由用户接受。测试时只运行一个高负载 JVM，限制并发并为日常电脑使用保留 CPU；不要因此修改生产线程模型。

## 实例与验证

日常客户端运行位置为 `instances/26.2-fabric/client`，服务端为 `instances/26.2-fabric/server`，由后续运行按需生成；源码树不附带存档或本机运行配置。没有额外 `.minecraft` 层。新建普通世界不会自动成为 Mosaic，当前没有公开 Mosaic WorldPreset。

产品验证入口按能力位于 `validations/*/validation.md`，程序在其 `task/`，隔离实例和临时输出在 `instance/`。首次执行前须按各项目说明准备服务器配置并由用户接受 EULA；旧实例不随源码提供，某些脚本要求已存在的 server.properties，不能假定克隆后即能直接运行。完整脚本会设置需要的 JVM property 和隐藏 fixture；不要把半条 Gradle 命令当完整验证。不要对用户存档运行带清理的测试脚本。脚本中的 Phase 名称是稳定的内部场景/属性标识，不表示需要检出旧源码或使用历史 JAR。

- 当前完整生产链：`integration-production-lifecycle`。
- 关闭取消、POI 主线程、SPAWN 复用：对应 `regression-*`。
- 已生成结构查询：`feature-structure-overlay`、`feature-structure-index`。
- 结构候选及 Stronghold Oracle：`conformance-structure-candidates`、`conformance-stronghold`。
- 原生 JUnit 在 Target 的 `src/test/java`，由 `test` 执行。测试 Mod 程序由 Target test source set 接入，只有显式运行属性才装载；生产 Mixin 链接的 gated hooks 不可仅因命名为 verify 就删除。

分阶段验证保留当前合同的精确边界：例如预光照物化须使用专用新 fixture，不能用已经 FULL 的 Chunk 替代 FEATURES 断言；它不是完整生产链门禁。所有输入与 Oracle 应由当前任务生成，不依赖旧源码快照、历史构建或归档中的旧绝对路径。

## 结果与治理边界

分别报告 Build、Runtime、Functional/Visual。构建或 JUnit 成功不证明客户端进入、飞行、保存退出或画面正常；人工 GUI 由用户操作，不使用自动化替代。现存结果按记录版本解释，未重新运行的场景标为待验证。

当前没有自动仓库布局检查器，也没有 GitHub Workflow 接替。目录职责由 `AGENTS.md` 约束，需人工审核；`test build` 不检查所有目录治理规则。

`build/`、Target `build/`、`.gradle/` 是可再生成输出，不存放唯一存档或产品权威。实例不可用 `clean` 或通用清理脚本删除。长期保留的产品证据在对应 `result/`；临时 profiler 与研究世界在 `instance/`，不得混入生产 JAR。
