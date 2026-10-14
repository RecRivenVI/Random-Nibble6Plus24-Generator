# Random Nibble6Plus24 Generator

Minecraft Java Fabric 的 Random Chunk Mosaic 实验项目：每个物理 Chunk 使用独立派生的 local world seed，通过隔离生成、数据提取及物化进入真实 Minecraft 生命周期。

已登记的 Target 如下；参考 Target 为 26.3，构建、原生对照与人工进入、探索、保存退出和重进验收通过。26.2 Target、实例与历史证据继续保留。开发按里程碑组织，当前里程碑为完成 B 模式、人工体验与性能评估。

## Target

| Target | 加载器版本 | Java |
| --- | --- | --- |
| `26.2-fabric` | 0.19.5 | 25 |
| `26.3-fabric` | 0.19.5 | 25 |

## 快速开始

在仓库根目录使用 Java 25：

```powershell
.\gradlew.bat check verifyRelease
.\gradlew.bat :version:26.3-fabric:runClient
```

日常客户端保留本项目已有实例。隐藏 Mosaic 存档可继续加载和探索；尚未公开 Create World 入口。不要把研究验证世界当作正式存档。

## 文档

- [当前里程碑](documents/project/milestone-features.md)
- [生成实现状态](documents/project/status-generation.md)
- [玩家能力与限制](documents/usage/guide-mosaic.md)
- [开发与验证](documents/development/workflow-development.md)
- [构建配置](gradle/conventions/specification/reference/configuration-repository.md)
- [验证工具](documents/development/testing-worldgen.md)
- [生产生命周期验证](validations/integration-production_lifecycle/validation.md)
- [世界生成合同](validations/conformance-worldgen/validation.md)
- [性能验证](validations/performance-worldgen/validation.md)
- [项目规范](documents/AGENTS.md)

## 许可

本项目保留所有权利，不授予使用或再分发许可；第三方文件适用各自许可，详见 [NOTICE](NOTICE)。
