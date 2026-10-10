# 生产 Chunk 请求

## 目标

通过普通服务器 Chunk 请求验证 Mosaic 从缺失 Chunk 到 FULL 的生产流程。

这是 Minecraft 26.2 Fabric / 26.3 Fabric 分 Target 验证。工具属于 `worldgen_validation`，独立游戏内入口属于 `src/probe`，产品 Mixin 直接链接的默认关闭 hooks 留在产品中。

## 运行

从仓库根目录执行。先由使用者在 `local.toml` 顶层写入 `eula = true`，不要代替使用者接受 EULA。只启动单个高负载 JVM；所有运行限制为 4 个可见处理器。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric' # 旧版回归改为 26.2-fabric
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1
```

其他矩阵与子场景的脚本在同一工具目录。脚本负责隐藏 preset、输入与系统属性，不直接使用日常实例。人工客户端操作仍由使用者完成。

## 通过标准

不靠手动逐阶段调用；去重、保存、重启、已修改 Chunk 和各维度生命周期保持正确。

26.3 冷区请求额外检查所有 BIOMES 物理依赖的实际状态和 palette，并执行运行期刷怪使用的环境属性查询；`physicalBiomeChecks` 必须大于零，不允许用未物化 ProtoChunk 完成 biome future。FULL 重载继续要求已有 target 不重新生成。

按输出 JSON、异常链、资源与副作用断言判断，真实错误不能被吞掉。构建通过不代替 Runtime、Functional 或 Visual 通过。

## 结果

新结果写入 `result/current/<target>/` 固定位置，同一场景覆盖。实例目录为 `instance/<target>/<角色>/`；旧研究运行目录原地保留并忽略。`result/legacy/` 原样保留历史证据，不能据此宣称当前版本通过。
