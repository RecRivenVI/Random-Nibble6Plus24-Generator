# 26.1.2 玩家冷前沿与首版暂缓

调查日期：2026-10-20。26.1.2 的独立内容对照、普通 FULL／运行期和重载具有通过证据，但完整玩家冷切换末地触发独立服看门狗。使用者决定首版暂缓本版游玩支持，保留源码、构建、原始失败与已经通过的局部结论，不继续性能研究。

## 问题

有限世界功能不能只由无玩家的 FULL 请求证明。新增本版原生玩家生命周期对照后，首先暴露全局加载数量的错误隔离断言；修正该断言后，另一条真实的冷维度等待仍未通过。

## 方法

四逻辑 CPU、低优先级、独立世界与端口，以 `PrepareSpawnTask`、`PlayerList`、原生玩家、票据和自然 tick 执行首次加入、返回、死亡重生及三维度命令式传送。使用 EmbeddedChannel 作为探针传输，并调用 NeoForge 原生 mock-connection 配置；这不是客户端握手、渲染或真实传送门验证。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.1.2-neoforge'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -PlayerLifecycle -VerifyReload -Offline -EvidenceLabel productization_player_current -CpuAffinity 3840
```

同一工具的普通原版对照使用 `-VanillaControl`，场景标签为 `productization_player_memory_transport`。它的 fresh／独立 JVM reload 均完成；B 的完整场景没有完成，不把局部阶段改为整套通过。

## 发现

原隔离断言比较准备前后的 `ServerChunkCache.getLoadedChunksCount()`，将所有物理 holder 的全局数量当作当前 worker 的因果证据。实际目标 (124,-37) 的准备期间，数量从 1104 降到 529，诊断未观察到准备线程调用物理取块；其它 holder 卸载就足以触发异常。这是断言错误，不是必须放宽 B 读取边界。生产仅删除这项全局数量判等，保留数量诊断、原有隔离 facade、原版生成和严格切断。

临时归因探针随后因假定 ForkJoin worker 不会嵌套执行其它准备而失败；原生 join 的帮助执行是合法路径，不能为迁就观察器修改调度。该探针已移除，原始失败保留。最终无此诊断的运行完成主世界首次加入、返回、重生、下界与返回，相关 Chunk 均为 ENTITY_TICKING、light correct、单次 Artifact；进入末地后没有完成。

13:16:27 原生独立服看门狗报告一个 tick 超过 60 秒。主线程停在 `ServerPlayer.doTick → EntityFluidInteraction.hasFluidAndLoaded → ServerChunkCache.getChunk → managedBlock`，等待真实物理 Chunk。工作线程仍有密度和 ring biome search 计算，也有等待原生 BIOMES／NOISE future 的 worker；不是仅凭 WAITING 线程数量就证明死锁。这里没有足够证据把各计算的 CPU 比例或确切等待 Chunk 坐标归因完成，不新增性能调查。

证据位于 `validations/integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production/`。B 的 `features-2/development-b/player-lifecycle/productization_player_call_attribution` 保留原断言与线程归因；`productization_player_population_fix` 保留诊断嵌套失败；`productization_player_current` 保留玩家阶段、原始控制台及看门狗失败。原版对照位于 `vanilla-control/player-lifecycle/productization_player_memory_transport`。完整 crash report 位于该验证自己的 server 实例，日期与时间为 2026-10-20 13:16:28。

## 结论

全局数量误判已根因修正，不能因此宣布完整玩家生命周期通过。B 内容门禁与原版一致；冷末地玩家生命周期有差异，触发服务端看门狗。实际客户端画面无法由此探针比较。使用者选择首版暂缓 26.1.2，首版只交付两个 26.3 端；不延长看门狗、预热前沿、开启未验候选或改变生成合同来制造通过。
