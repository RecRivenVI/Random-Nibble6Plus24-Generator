# 1.20.1 原生区域票据与自然运行期

调查日期：2026-10-17。默认 B 的下界和末地通过真实 49 FULL、自然 tick 与新 JVM 重载；主世界 B 未在原 300 秒内完成 FULL，保持未通过，不延长预算或启用未保留优化。

## 问题

本版 physical FULL 能否经原生区域票据进入 BLOCK_TICKING/ENTITY_TICKING，正确运行实体、流体和方块实体，并在重载后不重新生成？

## 方法

检查 Forge 47.4.28 的实际 DistanceManager、ChunkHolder、ServerLevel、AbstractFurnaceBlockEntity 字节码。addRegionTicket 同时更新 loading 与 TickingTracker，票据等级由原生 FullChunkStatus.FULL 等级减半径得到；探针使用 PLAYER radius 3，使两个相邻 owner 的等级分别为 30、31。完整 FULL、block-ticking、entity-ticking 三类 Either future 分别观察，不手工执行生成或 tick 方法。

输入为 4578AF1C6CDB6EED65652D8B2CFAAECE8F34B05A1F37C74A051F21C02801F04E，只增加 probe 与观测，不修改生产算法、B 或存档合同。每次单个四逻辑 CPU、低优先级游戏；独立地形 smoke 队列使用另外四个逻辑 CPU、不同实例和端口，性能测量已结束，二者不与性能重叠。

每维度运行普通 Vanilla fresh/reload 与隐藏 B fresh/reload 四个独立 JVM，同侧 reload 使用同一 save。静止区域 49 FULL 和原生依赖真正就绪、in-flight 排空后，安装明确的运行期设施，再运行 220 个真实服务器 tick；设施不算 canonical generation 正例。冷区跳过首次出生准备，所以不证明玩家出生、网络或客户端。

## 发现

| 维度 | Vanilla fresh/reload | B fresh | B reload |
| --- | --- | --- | --- |
| 下界 | PASS / PASS | PASS，101.579 s | PASS，12.235 s |
| 末地 | PASS / PASS | PASS，79.441 s | PASS，12.087 s |
| 主世界 | PASS / PASS | 原 300 s FULL 预算未完成，FAIL | 未运行，不计通过 |

上表正确性运行与另一验证队列并行，不作为性能 A/B；相对原版的正式探索比值仍见[单实例性能记录](../research/experiment-forge_dimension_frontiers.md)。原始运行在[固定 runtime_current 目录](../../validations/integration-legacy_physical/result/runtime_current/1.20.1-forge)，下界和末地的 CheckOnly 绑定当前源码通过。

两个通过的 B fresh 均实际 generation/publication/POI 169 次、LIGHT/SPAWN/FULL 49 次；reload 均 generation/publication/POI/SPAWN 为零，49 个 FULL 正常加载。49 个 stored biome owner、标记方块和完整结构 NBT 保持，fresh 的 169 个原生 biome 依赖与 reload 的 49 个 FULL 依赖均检查。

四个 B 运行均观察到 220 次 item 实体自然 tick、一次实体注册、真实跨物理 seam、一次 scheduled block tick、正向 fluid tick/跨界与 220 次熔炉回调并完成烧炼。owner 与相邻 Chunk local seed 不同，运行期间 generation scope 为零、物理生成指标不再增长；全部受检回调在原生 server thread，退出时票据、worker 和 in-flight 清除。普通 Vanilla 四个运行使用相同设施，Mosaic generation/publication/SPAWN 为零。

主世界 B 进程 53504 的最初异常为 NativePhysicalProbe.tick 报告 Native physical FULL observation exceeded 300 seconds；它没有到达自然模拟检查。原始 console.log 保留完整调用链，失败存档和日志同时保全在[首次失败](../../validations/integration-legacy_physical/result/first_failure/runtime_current/1.20.1-forge/b/minecraft-overworld)。本次不是新的生成结果不等价证据，也不证明主世界生命周期通过；它与该 Target 已测的严重准备成本一致。

探针的 stopped 分支随后将摘要 failure 覆盖为 Native stop followed an incomplete/failed request。这个次要诊断不能代替主异常：原始摘要不改写，判断以完整原始日志为准；报告链需要单独保留首错并记录停服次错。进程已经终止，不将一次观察等待误当成重启理由，也不重跑本次失败以挑选通过样本。

[当前四项默认 eager 六阶段 smoke](../../validations/conformance-legacy_terrain/result/legacy_terrain_smoke_runtime_probe/1.20.1-forge)通过当前源码只读复核。构建、发行和严格合规检查通过；这些不提升主世界门禁，也不把旧完整 72/15 的其它指纹倒填为当前全量。

## 结论

本版原生区域票据与自然运行期 API 可以沿用；下界、末地的独立普通世界/B 与保存重载门禁成立，不再只是 FULL 直接请求证据。主世界默认路径在原预算未完成，保持明确未通过；出生、维度往返、完整 B 并发、半成品恢复及世界命令仍欠验收。

继续性能止损，不追加第三轮环/缩锁优化，不通过改变生成合同、降低前沿或提高超时来通过。下一步先修探针首错保留及补剩余功能门禁；已通过结果只适用对应输入与范围。
