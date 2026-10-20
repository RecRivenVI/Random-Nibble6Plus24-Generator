# 1.21.1 首版游玩暂缓

调查日期：2026-10-20。默认 B 的真实运行期前沿在原一百八十秒内没有完成 Artifact；使用者决定首版暂缓 1.21.1 游玩支持。已有研究组合证据、源码与构建保留，不把显式候选的通过当作默认生产能力。

## 问题

[状态安全复用运行期](experiment-legacy_reuse_runtime.md)证明显式有限环、缩锁和来源复用组合可以完成三维度前沿，但这些候选因未完成保留门禁而默认关闭。产品化需要单独判断没有研究开关的真实生产请求。

## 方法

使用四逻辑 CPU、低优先级，主世界 Chunk (125,-37)、master seed 123456789，通过原生区域票据要求 49 个 FULL，观察 ENTITY_TICKING 和自然 tick；不缩前沿、不延长原一百八十秒，也不启用研究优化。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '1.21.1-neoforge'
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -VerifyReload -Offline -EvidenceLabel productization_default -CpuAffinity 61440
```

证据为 `validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-overworld/productization_default/`；输入身份只在这里登记：`2FF8BD555365498075C79A572E8B0A3C3F53FD9B484F6B26E68D89322E92F158`。

## 发现

条件记录确认 lazyRings、narrowLocks、reuseSources 全部为 false。到原预算结束时 Artifact、物化、POI、LIGHT、SPAWN、FULL 均为零，仍有 81 个 in-flight；主异常为 `Runtime ticket/simulation evidence exceeded original bound`。这不是客户端渲染或只差发送，也不能由已通过的组件 Oracle 推定可玩。

本次只检验默认生命周期可达性，没有新增 CPU 性能研究；不从超时猜测各结构、锁或地形占比。已记录的显式候选仍保持原状态，没有为获得通过而自动启用。

## 结论

默认生产前沿未通过。使用者选择首版暂缓本版游玩支持，保留源码、构建与全部既有证据；当前交付继续收口两个 26.3 Target 与 26.1.2 NeoForge。未来若恢复本版，应先解决精确交接及默认生命周期可达性，不能把研究 JVM 属性变成玩家运行前提。
