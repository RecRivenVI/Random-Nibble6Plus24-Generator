# 关联查询结构原点复用筛选

调查日期：2026-10-20。复用同种子关联查询的不可变结构原点，四场景独立对照通过；但首组发送就绪仅 323→325、平均等待改善约 2.63%，未达到保留阈值，已撤回产品候选和运行开关。

## 问题

当前原生 JFR 中，IsolatedGenerationContext 构造占 1,006/10,726 个 CPU 样本；直接调用 createState 占 869 个，其中 637 个来自 createRelated。需要分清不可共享的本地随机状态，与可以精确复用但未必影响关键路径的重复查询。

## 方法

26.3 实际字节码显示：ChunkGenerator.createState 先调用 NoiseBasedChunkGenerator.getOrigin，再传给 ChunkGeneratorStructureState.createForNormal。非空 spawnTarget 经 NoiseSpawnFinder 和原生缓存采样器得到坐标；它只依赖相同 seed／settings，不依赖关联查询的 ChunkPos。

候选仅在已有原生模组能力检查、隔离模式与 Ordering 2 下，把父状态的 getDimensionOrigin 坐标交给新 createForNormal；每个子上下文仍构造独立 RandomState、结构状态、缓存和生命周期。不跨种子／世界共享，不保留旧会话，未知模组和旧 Ordering 1 路径不变。

四场景分别使用两份 fresh Native 与两个无关宿主，宿主的部分地形先写盘并由原生重载；比较完整 B 快照、随机流与源状态。性能在同一构建中用仅 probe 的 reference 开关重新执行原 getOrigin，固定四逻辑 CPU、低优先级、8/8、六十秒、同一移动轨迹，两个独立进程和世界。

## 发现

原点、非原点、下界、末地水晶共四场景、十六份独立结果 PASS。输入为 `BBC8FBFBD5614808528ECA95615F9AF009EC1FB561B605DD1FF3A195A7DF76C7`，入口为 `validations/conformance-feature_b/result/related_origin_screen/independent_smoke/26.3-fabric`。端到端性能筛选如下。

| 指标 | 重新计算 | 原点复用 |
| --- | --- | --- |
| FULL | 403 | 405 |
| send-ready | 323 | 325 |
| 全需求受限平均等待 | 36.211 秒 | 35.257 秒 |
| 实际重算／复用次数 | 重算 4,364 | 复用 4,422 |
| getOrigin CPU 样本占比 | 8.428% | 2.332% |
| 整机平均 CPU | 42.63% | 37.19% |
| GC 后中位堆 | 310,231,264 字节 | 317,215,568 字节 |
| GC 暂停合计 | 1,153 毫秒 | 1,224 毫秒 |

CPU 热点确实下降，但没有转化为足够的可见区块吞吐收益，不能仅凭热点消失保留。归并入口为 `validations/performance-worldgen/result/b_milestone/26.3-fabric/related_origin_first/physical-biomes-screen.json`。第一次编排在运行游戏前因继承的两核 affinity 不含指定四核而停止；后续修正编排进程资源归属，不修改产品并发或忽略保护。

## 结论

筛选未达到约 5% 的收益阈值，不扩大到完整矩阵、不移植 NeoForge，产品及 probe 的候选执行分支已撤回。只保留研究记录、原始结果和只读分析。此结果不意味着重复计算不存在，而是它不足以解释当前主要发送就绪差距；后续判断以冻结组合的原版三档配对为准。
