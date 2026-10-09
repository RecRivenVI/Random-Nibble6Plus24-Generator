# Random Nibble6Plus24 Generator

Minecraft Java 26.2 Fabric 的 Random Chunk Mosaic 实验项目：每个物理 Chunk 使用独立派生的 local world seed，通过隔离生成、数据提取及物化进入真实 Minecraft 生命周期。

当前支持隐藏 Mosaic 测试世界、真实物理邻区互动、已生成区域的结构查询与 `/locate`。尚未公开 WorldPreset；不提供未生成区域全局结构搜索或 Preview。新区生成仍受吞吐限制，候选裁剪的性能结论为 PARTIAL，不等于原版探索速度。

- [开发、构建与验证](documents/development/guide-development.md)
- [共享与本机配置](documents/development/guide-configuration.md)
- [生产生命周期验证](validations/integration-production-lifecycle/validation.md)
- [世界生成合同](validations/conformance-worldgen/validation.md)
- [性能验证](validations/performance-worldgen/validation.md)
- [仓库协作规则](AGENTS.md)

唯一实现目标：`versions/26.2-fabric`，Gradle 地址 `:version:26.2-fabric`。本项目尚处开发验收阶段；不要把验证实例当作正式游戏存档。
