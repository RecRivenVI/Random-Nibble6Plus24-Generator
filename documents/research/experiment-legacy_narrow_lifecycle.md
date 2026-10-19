# 1.21.1 缩锁候选的下界与末地自然运行期

调查日期：2026-10-16。默认关闭的 H121-T 候选在下界、末地的真实运行期和保存重载通过；主世界仍在原预算内失败。不能把已通过维度扩大为完整产品或正式性能验收。

## 问题

缩锁后是否仍能正确激活物理实体、BlockEntity、ticks、真实跨 seed 邻居，并在重载时不重新生成 canonical 数据？普通 Vanilla 是否保持原路径？

## 方法

使用输入指纹 0D35D887DBC10F0F80F8E90ADAE30CBD033782971F87983E98B0BB0D94423050 的原生 RuntimeProof：候选显式开启有限环与缩锁，普通 Vanilla 不开。两队列正确性负载各四逻辑 CPU、BelowNormal、独立实例与端口；主世界控制两 JVM，下界/末地各四 JVM。固定 49 个真实 FULL 目标、原生 ENTITY_TICKING futures 和 220 个自然 tick；每个场景 fresh/save/stop/restart/reload。

## 发现

- [下界候选](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/runtime-proof-frontier/minecraft-the_nether/template_lock)与[末地候选](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/runtime-proof-frontier/minecraft-the_end/template_lock)各有两个独立 JVM/save；与各自普通 Vanilla 控制合计八 JVM，Check-LegacyRuntime 的独立只读复核通过。
- 两维度 fresh 均真实生成 121 份 Artifact，完成 49 FULL 和 SPAWN、48 次部分 BIOMES 准备/发布，实际缩锁 context 分别为 9852、9849；in-flight 和 generation binding 清空。重载 Artifact、BIOMES 准备和 SPAWN 为零，标记方块保留，实际缩锁 context 为零。
- 跨 physical seam 的实体只注册一次，正常移动；水或岩浆跨界传播，block/fluid callback 非零，熔炉经原生 ticker 完成烧炼。检查设施是测试安装，不代替自然 worldgen 的实体/BE/tick 正例。
- 普通 Vanilla 三维度 fresh/reload 六 JVM 全通过，实际 Mosaic state/context 创建为零；下界/末地的四个 JVM 已包含在上面八 JVM 中，不重复计数。全部成功运行共十个 JVM，主世界候选另有一个超时 FAIL。
- 主世界候选的失败、冷并发范围与线程观察保存在[前沿初筛实验](experiment-legacy_narrow_runtime.md)，没有通过延长 180 秒或削减前沿消除失败。
- 候选下界/末地 fresh 探针总耗时分别约 40.78、71.18 秒，普通 Vanilla 对应约 13.78、12.18 秒；含票据准备、静止等待和测试设施运行。这些是双队列正确性记录，不是探索吞吐、send-ready p50/p95 或正式 A/B，不能按它们决定保留优化。

## 结论

缩锁候选在已执行两维度的真实激活与保存重载没有发现退化，普通 Vanilla 控制保持零 Mosaic 生成。主世界与端到端性能仍未通过，候选不得默认启用。当前完整结构 B 队列尚未结束，不提前宣称本 Target 收口；随后只重跑受后续改动影响的门禁，Target 收口时再跑完整矩阵。
