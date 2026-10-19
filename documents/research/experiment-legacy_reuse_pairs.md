# 1.21.1 状态安全来源复用三组配对

调查日期：2026-10-17。H121-U 第二轮实现及 SPAWN 碰撞修复完成同指纹三组配对；吞吐改善重复出现，但尾延迟和内存门禁仍未闭合，候选不提升为默认启用或 Target 收口。

## 问题

来源复用的模块和结构 B 对照已通过代表范围，但一次性能筛选不足以决定保留。需要区分真实端到端收益、窗口截尾及不同完成工作量造成的内存差异；不能用旧源码或修复前崩溃组拼成三组。

## 方法

输入指纹为 `CADAD92C1A83EF1829063E547D95E9CA0085751A4305EBD7A59CBB2FBD17BB75`。主世界、同一 master seed/目标和原生玩家票据轨迹，8/8、四逻辑 CPU、低优先级，单个重负载 JVM；各组独立进程和 fresh save，观察上限固定 180 秒。原定第 15/30 秒移动不变，不延长窗口以取得通过。

三组分别交错运行未复用 B、状态安全复用 B 和普通 Vanilla，九个 PID/run identity 不重复。每次运行另行 CheckOnly，聚合工具复核原始条件、结果与 JFR 分析的哈希。来源缓存算法没有第三轮实现。

证据为[第一组](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_pair_a-duration180/comparison.json)、[第二组](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_pair_b-duration180/comparison.json)、[第三组](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_pair_c-duration180/comparison.json)及[聚合结果](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/reuse-paired-summary.json)。CLI send-ready 是服务器可发送条件，不代表实际客户端收包或画面。

## 发现

### 吞吐

| 指标 | 未复用 B 中位数 | 状态安全复用 B 中位数 | Vanilla 中位数 | 复用 B / Vanilla |
| --- | --- | --- | --- | --- |
| canonical/s | 0.217 | 1.066 | 不适用 | 不适用 |
| FULL/s | 0 | 0.511 | 2.683 | 19.05% |
| send-ready/s | 0 | 0.311 | 2.216 | 14.04% |

复用组 canonical 为 1.066、1.289、1.044/s；FULL 完成 92、124、92，send-ready 完成 56、82、56。Vanilla 每组完成 483 FULL、399 send-ready，最后视野 313/313 就绪。未复用组 canonical 为 0.217、0.089、0.239/s，三组均未在窗口内完成 FULL/send-ready。canonical 中位数约为未复用的 4.92 倍；FULL/send 的基线为零，只报告绝对增量，不制造无限提升百分比。这仍不是稳定最大容量测量。

### 尾延迟与内存

复用组最后视野仅 56、82、56/313 就绪，p50/p95 均截尾。相对 Vanilla 的 p95 下界约为 951%～1008%，不是完整延迟点值。三组区间相交，均不能证明 p95 不退化。

JFR 加权分配量：未复用为 219.57、243.12、242.72 GiB，复用为 196.90、237.87、195.09 GiB。它是采样加权估计，不是精确分配计数。after-GC heap 中位数分别约为 380/349/343 MiB 与 532/396/490 MiB；复用组实际完成更多 physical 数据，不能把这组不等工作量的比较当作内存不退化证明。内存门禁保持未通过，不以总分配较低代替驻留和峰值验收。

### 剩余热点与覆盖边界

复用组互斥 CPU 样本中，其它结构约占 48.7%～50.8%，Noise/Surface/Biomes 约占 44.0%～46.2%，Stronghold 约占 0.23%～0.36%。GC pause 总和约 2.74～3.55 秒，不能解释整个 180 秒缺口；不把线程累计锁等待当作墙钟占比。

九次游戏均正常完成、保存退出，没有新增崩溃。单独的[碰撞筛选](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_screen-duration180/comparison.json)已有实际正向读取覆盖；配对组没有读取命中时只记未覆盖，不判新崩溃，也不冒充碰撞正例。第一组最初误将“必须有命中”用于每个配对，被工具拒绝；原始正常游戏和 coverage assertion 保留在该组 first_failure，修正为只在专门筛选强制正例后，独立复核同一原始结果，没有重新挑选有利的一轮。

## 结论

H121-U 的重复计算削减能改善有限窗口的端到端完成量，但不满足完整保留门禁：p95 仍截尾，内存无法用不等工作量比较证明，冷载与完整生命周期也尚缺验收。候选继续默认关闭，第二轮算法预算到此用完，不增加第三轮来源缓存实现或继续延长观察上限。

这些结果不改变已有 B、种子、存档或第三方兼容合同。后续正确性门禁独立处理，不用吞吐收益豁免原始失败，也不将本 Target 的气候树适配自动移植到其它版本。
