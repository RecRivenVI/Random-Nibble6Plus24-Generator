# 物理预光照物化

## 目标

当前 V2 Artifact 到物理预光照 FEATURES 的单目标、矩阵、顺序与故障边界验证。

这是 Minecraft 26.2 Fabric / 26.3 Fabric 分 Target 验证。旧 golden 矩阵只适用于 26.2；26.3 先完成[独立原生矩阵](../conformance-worldgen/validation.md)，再用该版本的原生证据验收物化，不复用旧 hash。工具属于 `worldgen_validation`，独立游戏内入口属于 `src/probe`，产品 Mixin 直接链接的默认关闭 hooks 留在产品中。

## 运行

从仓库根目录执行。先由使用者在 `local.toml` 顶层写入 `eula = true`，不要代替使用者接受 EULA。只启动单个高负载 JVM；所有运行限制为 4 个可见处理器。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
$native = Get-Content .\validations\conformance-worldgen\result\current\26.3-fabric\phase2c1f-matrix\ow-origin-03\native.json -Raw | ConvertFrom-Json
& .\components\worldgen_validation\scripts\integration-materialization\Run-Phase3APhysicalMaterialization.ps1 -ExpectedHash $native.featureHash -DuplicateRequests 16 -VerifyReload
```

26.2 的完整 golden 矩阵使用 `MOSAIC_VALIDATION_TARGET=26.2-fabric` 和 `Run-Phase3AMatrix.ps1`。其他子场景的脚本在同一工具目录。脚本负责隐藏 preset、输入与系统属性，不直接使用日常实例。人工客户端操作仍由使用者完成。

## 通过标准

完整原子交接 canonical 数据，保存/重载、并发去重和故障边界正确，不伪造 terrain。

按输出 JSON、异常链、资源与副作用断言判断，真实错误不能被吞掉。构建通过不代替 Runtime、Functional 或 Visual 通过。

## 结果

新结果写入 `result/current/<target>/` 固定位置，同一场景覆盖。实例目录为 `instance/<target>/<角色>/`；旧研究运行目录原地保留并忽略。`result/legacy/` 原样保留历史证据，不能据此宣称当前版本通过。
