# Random Nibble6Plus24 Generator

Minecraft Java 26.2 Fabric 的 Random Chunk Mosaic 实验项目：每个物理 Chunk 使用独立派生的 local world seed，通过隔离生成、数据提取及物化进入真实 Minecraft 生命周期。

## Target

| Target | 加载器版本 | Java |
| --- | --- | --- |
| `26.2-fabric` | 0.19.5 | 25 |

## 快速开始

在仓库根目录使用 Java 25：

```powershell
.\gradlew.bat check verifyRelease
.\gradlew.bat :version:26.2-fabric:runClient
```

日常客户端保留本项目已有实例。隐藏 Mosaic 存档可继续加载和探索；尚未公开 Create World 入口。不要把研究验证世界当作正式存档。

## 文档

- [玩家能力与限制](documents/usage/guide-mosaic.md)
- [开发与验证](documents/development/workflow-development.md)
- [构建配置](documents/reference/configuration-repository.md)
- [验证工具](documents/development/testing-worldgen.md)
- [生产生命周期验证](validations/integration-production_lifecycle/validation.md)
- [世界生成合同](validations/conformance-worldgen/validation.md)
- [性能验证](validations/performance-worldgen/validation.md)
- [项目规范](documents/AGENTS.md)
