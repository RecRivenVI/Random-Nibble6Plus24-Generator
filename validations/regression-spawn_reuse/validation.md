# SPAWN 数据复用

## 目标

验证当前 V2 的 carried biome snapshot、冷恢复、故障和保存重载。

这是 Minecraft 26.2 Fabric 单 Target 验证。工具属于 `worldgen_validation`，独立游戏内入口属于 `src/probe`，产品 Mixin 直接链接的默认关闭 hooks 留在产品中。

## 运行

从仓库根目录执行。先由使用者在 `local.toml` 顶层写入 `eula = true`，不要代替使用者接受 EULA。只启动单个高负载 JVM；所有运行限制为 4 个可见处理器。

```powershell
& .\components\worldgen_validation\scripts\regression-spawn_reuse\Run-SpawnReuse.ps1
```

其他矩阵与子场景的脚本在同一工具目录。脚本负责隐藏 preset、输入与系统属性，不直接使用日常实例。人工客户端操作仍由使用者完成。

## 通过标准

复用与旧 SPAWN observable 数据一致，Fortress 语义不退化，FULL 重载不再生成或 SPAWN。

按输出 JSON、异常链、资源与副作用断言判断，真实错误不能被吞掉。构建通过不代替 Runtime、Functional 或 Visual 通过。

## 结果

新结果写入 `result/current/` 固定位置，同一场景覆盖。实例目录为 `instance/26.2-fabric/<角色>/`；旧研究运行目录原地保留并忽略。`result/legacy/` 原样保留历史证据，不能据此宣称当前版本通过。
