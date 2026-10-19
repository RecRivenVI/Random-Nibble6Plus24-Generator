# 26.1.2 物理 BIOMES 就绪缺口

调查日期：2026-10-16。加强运行期探针在真实 Mosaic 主世界复现：原生生成图要求的 BIOMES 邻区只有完成状态，没有实际群系数据。保守补齐精确数据后，主世界 fresh/reload 已通过；本文记录该次故障与复验范围，不代表 Target 整体收口。

## 问题

原有 FULL 验证只检查目标或两侧互动区块的输入，未覆盖票据前沿边缘。加强检查在全部 49 个 FULL 请求及两个 ENTITY_TICKING future 完成、生成会话清空后发现：位置 (119,-43) 的依赖要求 minecraft:biomes，实际 persisted status 为 minecraft:empty。

原始故障由 RuntimeLifecycleEvidence.verifyPhysicalBiomes 抛出，时间为 17:30:38。现场位于 [Mosaic 原始失败](../../validations/integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-overworld/first_runtime_failure)，保留 crash report、日志与世界，不倒改为 PASS。

## 方法

先以同版本普通 Vanilla 执行三维度 fresh/reload 六个独立 JVM 控制。受控 fresh 使用 GENERATION_PYRAMID，已完全保存 FULL 前沿的 reload 使用 LOADING_PYRAMID；该观察边界的独立纠正见 [加载依赖调查](incident-loading_dependency_observer.md)。每次保留全部 49 个 FULL 目标，fresh 2401 次、reload 441 次实际群系输入检查，不通过补造 Vanilla 不需要的加载依赖满足断言。

检查 MosaicPhysicalGenerationPlan 与实际物化路径，确认生成计划只对 FEATURES 或之后的要求建立物化义务，外层原生 BIOMES future 可由 EMPTY ProtoChunk 透传完成。仅把计划的实际数据门槛改为 BIOMES；未证明本 Target 的部分 BIOMES 脱离交接前，继续使用完整精确 Artifact，不借用其它版本的快路径，不改 Seed、装饰或原生调度。

复验保持四个逻辑 CPU、低优先级、独立实例及端口，仍用原有 180 秒预算与 49 个 FULL 请求。生成图、前沿和断言均未减少。

## 发现

- 普通 Vanilla 加强控制三维度 fresh/reload 均通过。它们是实际运行期控制，不能替代 Mosaic。
- 修复后主世界 fresh JVM 95432 完成 2401 次输入检查、220 个自然 tick、唯一实体跨 physical seed 边界、一次 block callback、两次 fluid callback、220 次熔炉 callback 与真实烧炼产物。请求至探针完成为 116489 ms；该时间包含运行期观察，不是 canonical/s 或客户端延迟。
- fresh 全程生成、捕获和发布各 218 个完整 Artifact；这是包括原生前沿与启动工作在内的实际成本，不能按 49 个显式 FULL 请求推算单 target 性能。最终 in-flight、请求、物化义务、状态 allowance 与生成绑定均为零。
- 新 JVM 91676 重载同一存档，441 次输入检查与 220 个自然 tick 通过；玩家/测试标记保持，canonical、Artifact 与 SPAWN 全程为零。运行期 fixture 与探针票据已移除，两次均正常保存停止。
- 互动实体、流体、熔炉由探针明确安装，只证明正常物理生命周期，不冒充自然 worldgen 实体、BE 或 tick 正例。下界、末地的 Mosaic 加强组合在本文记录时仍须各自完成。

## 结论

ChunkStatus future 完成不能替代实际数据就绪。原生生成图中需要 BIOMES 的物理邻区必须提供自己的 S(N) 精确 palette；空数据与 physical master-seed 采样都不可接受。本次修正物理依赖义务，没有更改 B 的单 writer、越界切断或同 S(C) 装饰前读取。

保守完整 Artifact 回退已证明主世界可在既定预算内完成，但成本显著；以后若采用部分 BIOMES，须先在 26.1.2 独立证明缓存采样、结构输入、所有序列化数据及关闭后所有权，再验证生产升级、保存重载与性能，不能仅凭 1.21.1 或 26.3 的模块 PASS 启用。
