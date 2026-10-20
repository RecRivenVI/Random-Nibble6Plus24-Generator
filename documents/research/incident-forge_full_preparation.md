# 1.20.1 主世界 FULL 准备放大归因

调查日期：2026-10-20。默认 B 在原 300 秒预算内仍未完成 FULL；已获得实际任务图、等待栈和 CPU 剖析，归因为保守物理准备与重复要塞环求值放大，不将该 Target 的主世界标为可用。本次只改 probe／工具，没有启用优化或改变生成合同。

## 问题

已有失败只证明超时，不能据此判断死锁、原生 LIGHT 故障或单个区块本来就需要五分钟。需要定位真实等待的工作，并保留最初的失败和预算。

## 方法

沿用普通 FULL 请求与原生 PLAYER 区域票据，目标为主世界 Chunk (125,-37)，master seed 123456789，local seed -5161763991829980711。保持完整 B、默认 eager 环、四逻辑 CPU、affinity 3840、BelowNormal；不降低前沿、不延长 300 秒、不手工推进 status。

命令为 `pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension overworld -RuntimeProof -FullWaitDiagnosis -Offline`。输入指纹为 84F67AE9C767F552F341DFBC31F3A4527AB43D41FE7334E7C4FCCDE5FBB189A4。结果位于 `validations/integration-legacy_physical/result/runtime_current_full_wait_game_owned/1.20.1-forge/`，原失败另存于原有 runtime_current 路径，没有覆盖或重标。

观察器仅在显式 fullWaitEvidence 下运行，每三十秒读取现有 plan、obligation、holder、已存在 future、全线程栈、锁持有者和累计线程 CPU 时间。JFR 在游戏进程内创建，不改变任务、随机源或 future。图来自真实运行中的状态，不重新调用 planner 构造诊断图。JFR 汇总包含关闭尾部，不当作恰好三百秒的性能 benchmark。

首次诊断启动误将 JFR 启动参数放入 JAVA_TOOL_OPTIONS，导致 Gradle 工具链探测也启动录制、无法解析 JDK 探测输出；游戏没有启动。该启动失败及其原始输出保留在 runtime_current_full_wait，修正为 game-owned JFR 后才进行本次游戏复现，不将启动失败计作 worldgen 失败。

## 发现

普通 Vanilla fresh／reload 分别在 PID 100396／107432 通过相同原生区域运行期场景，总耗时 20.597／14.038 秒，包含 220 次自然 tick，不是单 Chunk 或玩家进入耗时。Mosaic 指标均为零。

B PID 40344 在 300.091 秒仍为零 canonical 完成、零物化、零 LIGHT／SPAWN／FULL，25 个 in-flight。关闭后 workers、in-flight、绑定均为零；主异常保留为 Native physical FULL observation exceeded 300 seconds，停服次错单列 stopFailure，没有覆盖最初错误。

### 实际依赖与准备放大

58 份当前 plan 合并为 841 个依赖位置，三百秒内保持不变，没有持续扩大到远方。运行期要求的 49 个 FULL 对应以下真实 obligation：

| 所需状态 | 位置数 | 当前实现是否支付完整 B |
| --- | --- | --- |
| FULL | 49 | 是 |
| INITIALIZE_LIGHT | 32 | 是 |
| CARVERS | 40 | 是 |
| BIOMES | 48 | 是 |
| STRUCTURE_STARTS | 672 | 否，仅元数据义务 |

因此 49 个 FULL 前沿需要 169 次完整 B 准备，即 3.449 倍；其中仅 BIOMES 的 48 个位置也走完整 Artifact。MosaicPhysicalLevelState.openStage 对 obligated 且低于 FEATURES 的输入先调用 prepare，这是本版保守实现，不是原版 BIOMES 算法要求执行 FEATURES。

StrictFeatureBaseline 为目标和八个邻区建立真实装饰前地形；每份 LightweightTerrainGenerator 又准备自己与八个邻区的 stored biome 输入，CompactStructureInputs 每次都等待新 structure state 的完整要塞环。仅装饰前已有 9×9＝81 次同一 local seed 的结构输入准备，尚未包括 FEATURES 按需外圈读取。该乘数来自源码控制流，不冒充已完成 target 的实测计数。

### 等待链与 CPU

实际等待链为 physical FULL future → 完整 B → CompactStructureInputs／地形或群系准备 → getRingPositionsFor.join → 原生环位置 biome search。30／60／90／120／210 秒均有工作线程执行 findBiomeHorizontal → Climate／Noise；不同 worker 的 CPU 时间持续增长，其余工作线程等待这些 future。等待环的位置在获取共享模板 monitor 前完成，观测没有发现 monitor deadlock，也没有 BLOCKED 锁链把所有 worker 卡死。

JFR 共 29,461 个 CPU 样本，其中 28,797 个属于要塞环，即 97.7462%；叶热点为 ImprovedNoise、PerlinNoise 与 Climate RTree。整份录制约 318.168 秒，包含关闭尾部；GC pause 合计 130.9997 ms，最大 16.9065 ms。整机 CPU 样本均值 50.88%，只作环境说明，不能把本次与其他线程并行时的耗时当作吞吐优化证据。

JFR 原始 SHA-256 为 FA9184429510968B0FE3F4497F6000F6A90A259B787FC69637F4227EB40DFF44；pending-0 到 pending-10、完整录制、汇总、FAIL、日志和失败世界均保留。没有强制结束游戏，原生关闭与作用域排空已完成。

## 结论

主因是本版保守准备义务把大量低级邻区请求升级为完整 B，并在每个 B 内反复准备同 seed 的完整要塞环；不是 LIGHT 已算好却不晋升，也没有证据支持 JVM 锁死、GC 或网络主导。等待中的完整 B 均尚未完成，不能用增加超时制造通过。

最小后续方案分两项，均须另行移植与 Oracle 证明，不在本次自动开启：

1. 为本版适配已研究的有限环／原生缓存状态尾部处理，或证明可安全复用 local-seed-scoped 的环输入；必须覆盖原生 RNG、气候树入口／出口状态与结构正例，不能只按 seed 共享可变状态。
2. 区分物理 BIOMES 和完整 FEATURES 准备，仿照已验证目标的精确中间状态路径，保留真实 starts／references／stored biome、原生依赖及保存重载；只有真正需要完整 B 时才支付其成本。不能返回空区块或缺失元数据。

当前 Goal 的“查明原因并给出方案”已有证据；1.20.1 主世界 FULL 本身仍为 FAIL，生产未修复，不据此宣称五端可玩或完成该版性能收口。
