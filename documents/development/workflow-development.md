# 开发、构建与验证

项目登记的 Target、Java 与加载器以[项目规范](../AGENTS.md)为准，参考端为 26.3 Fabric。项目事实由 `gradle.properties` 提供，加载器与 API 版本由 `target.properties` 提供，通用工具与 JUnit 版本由版本目录提供。

开发按[当前里程碑](../project/milestone-productization.md)组织，已完成能力与限制见[生成实现状态](../project/status-generation.md)。只执行当前里程碑中明确授权的工作，不从历史安排推导后续任务；未通过验收的版本或能力不写入当前支持表。

## 构建与运行

从仓库根目录执行：

```powershell
.\gradlew.bat --max-workers=2 spotlessApply check verifyRelease
.\gradlew.bat :version:26.3-fabric:runClient
.\gradlew.bat :version:26.3-fabric:runServer
```

日常实例按 Target 分别位于 `instances/26.2-fabric/` 与 `instances/26.3-fabric/`，不套额外的 `.minecraft`。旧版本实例与存档不移动、不原地升级。个人运行设置写入被忽略的 `local.toml`；共享预设可写入 `instances.toml`，具体格式见[仓库配置](../../gradle/conventions/specification/reference/configuration-repository.md)。

服务端必须由使用者明确接受 EULA。新建普通世界不会自动变成 Mosaic，当前没有公开的 Mosaic WorldPreset。

## 验证

原生 JUnit 仍在 Target 的 `src/test`。游戏内独立测试 Mod 合并为验证运行专用的 probe，并保留每个场景的激活边界。产品 Mixin 链接的 gated 验证 hooks 不从产品移除。

验证入口见各 `validations/*/validation.md` 和[验证工具](testing-worldgen.md)。编排工具不再配置 Target 的 source set；模板自动加载对应验证实例与 probe。脚本中的 Phase 名称仍是稳定内部场景标识，不表示要检出历史代码。

## 验收边界

分别报告构建、运行和人工观察。单元测试不证明玩家进入、飞行、保存退出或视觉效果；不使用 GUI 自动化替代用户操作。

冻结世界生成合同不变。历史结果保持原始字节，不能代替当前重新运行；里程碑完成后停止，由使用者决定新的开发范围。

升级 Minecraft 时，既有 golden 仍只证明其原 Minecraft 版本。新版本需要以同版本的普通 Vanilla 世界重新建立对照，单独记录结果；不能直接替换旧 hash，也不能为了通过移植扩大忽略字段或修改冻结合同。新 Target 使用独立实例，不直接升级用户已有存档。

`check` 聚合构建、合规、格式、原生测试、独立探针编译和工具检查；`verifyRelease` 核对发行 JAR 的元数据及探针隔离。
