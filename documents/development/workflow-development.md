# 开发、构建与验证

项目只有 Minecraft 26.2 Fabric Target，使用 Java 25。项目事实由 `gradle.properties` 提供，加载器与 API 版本由 `target.properties` 提供，通用工具与 JUnit 版本由版本目录提供。

## 构建与运行

从仓库根目录执行：

```powershell
.\gradlew.bat --max-workers=2 spotlessApply check verifyRelease
.\gradlew.bat :version:26.2-fabric:runClient
.\gradlew.bat :version:26.2-fabric:runServer
```

日常实例在 `instances/26.2-fabric/`，不套额外的 `.minecraft`。个人运行设置写入被忽略的 `local.toml`；共享预设可写入 `instances.toml`，具体格式见[仓库配置](../reference/configuration-repository.md)。

服务端必须由使用者明确接受 EULA。新建普通世界不会自动变成 Mosaic，当前没有公开的 Mosaic WorldPreset。

## 验证

原生 JUnit 仍在 Target 的 `src/test`。游戏内独立测试 Mod 合并为验证运行专用的 probe，并保留每个场景的激活边界。产品 Mixin 链接的 gated 验证 hooks 不从产品移除。

验证入口见各 `validations/*/validation.md` 和[验证工具](testing-worldgen.md)。编排工具不再配置 Target 的 source set；模板自动加载对应验证实例与 probe。脚本中的 Phase 名称仍是稳定内部场景标识，不表示要检出历史代码。

## 验收边界

分别报告构建、运行和人工观察。单元测试不证明玩家进入、飞行、保存退出或视觉效果；不使用 GUI 自动化替代用户操作。

冻结世界生成合同不变。D1 的历史性能结论仍是 PARTIAL，不自动进入 D2、V3 或 Preview。历史结果保持原始字节，不能代替当前重新运行。

`check` 聚合构建、合规、格式、原生测试、独立探针编译和工具检查；`verifyRelease` 核对发行 JAR 的元数据及探针隔离。
