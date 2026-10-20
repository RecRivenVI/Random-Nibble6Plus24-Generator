# 1.21.1 状态安全结构来源复用的对照与筛选

调查日期：2026-10-17。第二版候选通过代表性独立 Oracle，并在一次有界冷前沿观察中改善实际 FULL/send-ready；完整视野尾延迟、重复性能和生产生命周期尚未收口，候选仍默认关闭。

## 问题

能否在同一目标的 B preparation 内复用完全相同输入的原生结构来源，减少重复生成，同时不省略气候搜索缓存对后续查询的影响？首版缺失的原生状态已在[故障分析](incident-legacy_source_climate_state.md)保留；不能用代表世界通过豁免该组件反例。

## 方法

只修改 `1.21.1-neoforge`。输入指纹为 `833229594633EA39E5E2E71394E8A1E8AA8EAC9E5DE0D83E2AFD0DE760A5ED4D`。`structure.reuseSources` 要求本 Target 的缩锁能力守卫先通过，未知环境、未知 biome source 或缺失原生状态适配时保持原生求值。默认关闭，不改变环、placement、Jigsaw、随机源、B 越界切断或存档格式。

同一个 preparation 限定 world、local seed、generator、structure state、线程和有限查询范围。MultiNoise 还要求原生 RTree 入口 leaf 身份相同才命中，命中重放该次原生计算的出口 leaf；入口不同时原生重算并替换该坐标的一条记录。Fixed/End 没有该 RTree 状态，不伪造状态。查询 owner 半径二、结构来源半径八，联集最多 441 个坐标；不在范围内的额外装饰读取仍走原有精确路径。

目标地形完成后、八份邻区地形生成前，将目标保留的可变结构图移出来源缓存。邻区后续查询不再取得目标拥有的可变图。返回前关闭作用域、清理来源与 world/generator/state 引用，不缓存整个 session，不让 FULL 或重载依赖这份临时状态。

独立对照使用 fresh 原生 producer 与两个不相关宿主 consumer；完整 B 还单独复核同条件原生重复。各验证完成后另行执行 CheckOnly，未以单进程 native-first 代替独立证据。正确性最多双队列、每 JVM 四逻辑 CPU、低优先级；性能只运行一个游戏 JVM，不与正确性队列重叠。

## 发现

### 正确性与生命周期边界

34 项单元测试通过，包含原生气候等距点的双向入口/出口续流反例、错误 world/seed/thread 拒绝、有限范围、失败清理及目标图移交。发行构建、严格合规和 Markdown 检查通过；下面的游戏证据各有独立复核：

| 范围 | 结果与证据 |
| --- | --- |
| 六阶段装饰前冒烟 | 四项、八 JVM（本机证据：`../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_smoke_source_state/1.21.1-neoforge`），原生阶段数据、逐 bit 密度、Beardifier、stored palettes 相同 |
| 装饰前结构 | 十八项、三十六 JVM（本机证据：`../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_structures_source_state/1.21.1-neoforge`），七类结构、邻区起点和地形适配相同 |
| 完整 B 冒烟 | 四项、十六 JVM（本机证据：`../../validations/integration-world_identity/result/smoke-shared-handoff-ring-scope-narrow-locks-source-reuse-source_state/1.21.1-neoforge`），三维度和 EndSpike 实体 |
| 完整 B 结构与 Artifact | 十八项、七十二 JVM（本机证据：`../../validations/integration-world_identity/result/structures-shared-handoff-ring-scope-narrow-locks-source-reuse-source_state/1.21.1-neoforge`），十八份 PRODUCED、五十四份 PASS |
| 原生潜影贝因果重放 | 四个 fresh JVM（本机证据：`../../validations/integration-world_identity/result/end-yaw-ring-scope-source_state/1.21.1-neoforge`），沿用已有有限验收规则，不新增忽略字段 |

有限环组件另有 1,536 个正例零漏检。完整 B 严格核对目标状态、FEATURES 随机调用、可见群系、九份装饰前输入、额外读取和拒绝副作用。36 个结构宿主 consumer 的可变目标图均独占，返回时 scope closed、resident 为零，物理宿主副作用与绑定清理检查通过。原生状态重放确实执行，不是适配失效后静默回退。

不同样本的入口状态不同，23,409 次查询对应的实际原生求值为 442～10,324 次，不能宣传所有目标都只求值 442 次。十八项结构宿主合计状态不匹配 86,858 次、实际出口重放 648,052 次。这些计数不代替输出对照，也不提升为该候选的装饰前 72 项、生产保存重载或并发通过。

### 有界性能筛选

同 seed、主世界目标 `(125,-37)`、8/8、四逻辑 CPU、低优先级、单实例，分别使用 fresh save/JVM。原生玩家 ticket 前沿在第 15/30 秒各向 X 移动两 Chunk；没有真实玩家、网络 packet 或渲染验收。原生控制未进入 Mosaic 路径，构造 ticket token 后所有维度 loaded chunk 为零。

标准 60 秒组（本机证据：`../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/source_screen/comparison.json`）的 canonical 为 0→38，但两侧 FULL/send-ready 都为零，无法据此判断端到端收益。随后只增加一次固定 180 秒观察，保持两次移动和全部请求规则不变，不修改正确性超时或生产调度。其独立结果（本机证据：`../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/source_observe-duration180/comparison.json`）为：

| 180 秒窗口 | 缩锁 B | 状态安全复用 B | Vanilla |
| --- | --- | --- | --- |
| canonical 完成 | 41 | 192 | 不适用 |
| canonical/s | 0.228 | 1.066 | 不适用 |
| FULL 完成 | 0 | 92 | 441 |
| send-ready 完成 | 0 | 56 | 361 |
| 最后视野就绪 | 0/313 | 56/313 | 313/313 |
| FULL/s 相对 Vanilla | 0% | 20.9% | 100% |
| send-ready/s 相对 Vanilla | 0% | 15.5% | 100% |
| Structure 累计 wall | 2,776 s | 580 s | 不适用 |
| GC pause 合计 | 4.50 s | 3.09 s | 另见证据 |
| JFR 加权分配估计 | 258.48 GiB | 197.02 GiB | 另见证据 |
| After-GC 堆中位 / 最大值 | 331.7 / 905.3 MiB | 394.3 / 1,073.4 MiB | 另见证据 |

这是有限需求窗口内完成量，不是稳态最大吞吐。两个 B 的完整视野 p50/p95 仍截尾：候选相对 Vanilla 只能报告至少约 556.6%/435.1%，不能把下界当点值。复用组已完成并保留更多物理区块，堆增长不能直接归因为来源缓存，也不能宣称内存门禁已通过。

候选主展示区块 canonical 从约 1.84 s 执行至 55.76 s，物化约 0.71 ms，随后等待物理邻区，约 119.85 s 到 FULL、120.18 s 到 send-ready。基线 canonical 到 141.81 s 才完成，180 秒内尚未 FULL。候选不是只在内部计数上改善，但离原版仍很远。

候选累计来源命中 3,879,940 次、原生求值 716,605 次，其中状态不匹配 628,131 次，出口重放与命中一致。结构 CPU 样本占比约 64.4%→49.7%，Noise/Surface/Biomes 约 33.3%→45.7%；Stronghold 约 0.17%→0.34%，仍非主要热点。采样比例不是累计 wall 占比，分配是 JFR 加权估计，不是精确字节账本。

## 结论

第二版在已执行代表范围保持原生输入、续流和输出，且一次 180 秒筛选已有实际 FULL/send-ready 收益，值得触发预定的三组交错配对。实现预算两次已用完，不再扩缓存或延长观察上限；三组配对固定同一个 180 秒窗口，并保留完整视野的截尾统计。

尚不满足性能保留或 Target 收口：还需重复收益、尾延迟/内存审查及新候选完整正确性和物理生命周期。其它 Target 的气候状态与对象所有权须独立审计；此实验不授权直接复制适配或默认启用，更不证明 BOP。
