# Fabric 普通生产请求的去重与区块布局

调查日期：2026-10-17。结论：参考 Target 三维度的重复请求、相邻不同种子和九区块区域通过；这里只证明真实 FULL 请求与逐目标 Artifact 交接，不提升为 ticking、玩家出生或性能验收。

## 问题

[模块层顺序对照](experiment-fabric_structure_order.md)没有覆盖 production holder 的请求去重和多目标布局。已有生产输出也没有逐一确认返回的 Chunk 对象及所有请求 owner 的 Artifact 计数，不能仅凭一份 FULL 结果推定区域通过。

## 方法

只增强默认关闭的 Phase3C2ProductionVerification 观察器和验证工具，不调整生成算法、调度器、线程模型或世界格式。新增 RequestProof 不参与生产判断，只在既有验证入口发起普通 ServerChunkCache.getChunkFuture(FULL) 后检查结果。当前全 src 输入为 05FD3403954C23C5BF3824CAA476BA0A99138773396B13712F6FD11F568ADBB0；其它门禁保留自己的原始指纹，不用本结果回填。

每个维度分别创建三个 fresh save，主种子 123456789，目标 (125,-37)：三十二次同目标请求、与东侧的相邻 pair、按 Z/X 排列的 3×3 patch。共九个独立 JVM/save，单实例，四个可见处理器、affinity 3840、BelowNormal；Gradle 使用两个可见处理器、一个 worker。各请求批次观察上限 300 秒，超出即失败，不把该限额写入生产调度。

所有返回位置必须准确、状态为已照亮的 LevelChunk/FULL；重复请求必须是同一对象。每个请求 owner 的 Artifact 捕获与物化发布分别恰好一次，记录其 local seed，核对不同请求布局中的同坐标种子不变。依赖通过本版 physical plan 派生并实际读取 BIOMES，不使用空区块。世界、输出、源码和实际游戏 PID 绑定，当前源码只读复核覆盖九组全部条件；成功世界由任务回收，已有条件与失败世界拒绝覆盖。

## 发现

[原始结果](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/request-proof)位于各维度的 climate_state_requests；当前源码 Run-FabricRequests -CheckOnly 通过。

| 维度 | 32 次同目标 PID | 相邻 pair PID | 3×3 patch PID |
| --- | --- | --- | --- |
| 主世界 | 104504 | 100132 | 50636 |
| 下界 | 75792 | 86868 | 50956 |
| 末地 | 85400 | 115428 | 4160 |

九组共 129 次普通请求、36 份请求 owner 记录，全部通过。每维度分别返回一个、两个和九个唯一 LevelChunk 对象；每个请求 owner 的捕获/发布各一次，pair 的 local seed 不同，跨布局同坐标种子一致。context 与 SPAWN 绑定为零，lifecycleDuplicates 为空，本批次观测到的全局 in-flight 均为零。九组都正常保存停服，没有新的 worldgen failure 或崩溃报告。

每维度的全请求窗口 Artifact 捕获分别为 25、30、49，包含原生依赖区块，不是重复计算次数。BIOMES 状态检查分别为 49、98、441，也不是去重后的 owner/依赖数量。developmentB.generations 是整个世界累计计数，含开服阶段；artifactGenerations 是观察窗口差值，两者不能相除推定重复计算或生成吞吐。需要性能结论时必须使用同窗口时间线及同条件原版控制。

日志包含 Windows OSHI 的 Perflib 009 缺失与 SystemReport.ignoreErrors 诊断；既有普通 Vanilla all_quart_biomes 控制也有相同告警。它们发生在系统信息收集，未阻止游戏进入请求或正常保存，不是 Mosaic 生成失败。本调查不修改系统计数器，不把这类失效计数当成后续 CPU 利用率证据。

## 结论

补齐参考版三维度普通 FULL 请求的 32 去重、相邻 seed 和 3×3 布局门禁，没有重新生成旧存档或改变 B 结果。验证观察器默认关闭；每个命中的生产阶段仍由原生调度及现有 Mosaic 交接驱动。

本项没有逐区块内容 Oracle、自然实体/BE/tick 激活、保存重载、出生/重生、维度往返或客户端发送证明；这些仍分别使用自己的门禁。环搜索异步续流、自然正例持久化与当前输入的性能收口继续待验。原版字节码已确认环搜索和 Mosaic 准备均使用 Util.backgroundExecutor，但共享执行器这一静态事实不证明差异，也不直接授权修改或通过续流验收。
