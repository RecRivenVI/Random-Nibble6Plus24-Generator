# 1.20.1 同目标冷区块重复请求

调查日期：2026-10-17。默认 B 的下界与末地各完成独立单请求/32 重叠请求控制，生成、保存与新 JVM 重载共十六个游戏进程全部通过。只证明同目标 pending 请求去重，不代表多目标并行、顺序独立、出生或性能收口。

## 问题

一次 FULL 成功与已加载后重复读取不能证明冷生成时多次请求只生成、物化和刷怪一次。需要确认重复请求实际重叠，并与独立 fresh 单请求控制比较真实数据和阶段次数。

## 方法

审计本版 ServerChunkCache.getChunkFuture 字节码：服务端线程分支 managed-block，非服务端线程分支将原生任务提交给 mainThreadProcessor，再返回组合 future。探针在一个普通调用线程连续提交未等待完成的公开请求，不创建生成 executor、不改调度器、不手工调用内部阶段。

记录请求数、issued/completed/pending、peak pending、全部发出与首次完成的先后关系，以及所有成功结果是否为同一个 Chunk 实例。fresh 要求 32 个请求在首个完成前全部发出且 peak pending 为 32；reload 只验证结果与次数，不提升为冷生成并发。

使用[物理交接验证](../../validations/integration-legacy_physical/validation.md)的 requests_1 与 requests_32，普通 Vanilla/B 各自独立 fresh world，随后新 JVM reload；每个维度八个独立游戏进程。与另一正确性队列并行时各限四逻辑 CPU、低优先级，实例与端口独立；不测量性能，运行中 Target 和合同源码冻结。

当前输入为 C1632C226DD0EED1BC4E6870E3907A45698AF18D92348574BAE3D14A72160F61。每组先执行当前源码 CheckOnly，再由 Check-ForgeRequests 比较阶段次数、Artifact payload、结构 NBT 与 B 的 stored-biome 前沿。普通 Vanilla 不生成 B 专用 biome-frontier 文件，校验不假定两种控制有相同文件布局。

## 发现

### 请求与阶段次数

证据在 requests_1（本机证据：`../../validations/integration-legacy_physical/result/requests_1/1.20.1-forge`） 与 requests_32（本机证据：`../../validations/integration-legacy_physical/result/requests_32/1.20.1-forge`），两维度均完成最终对照和当前源码 CheckOnly。

| B 维度 | 请求数 | fresh peak pending | 目标 canonical 次数 | 全部依赖 canonical / 物化 / POI | LIGHT / SPAWN / FULL |
| --- | --- | --- | --- | --- | --- |
| 下界 | 1 | 1 | 1 | 49 / 49 / 49 | 1 / 1 / 1 |
| 下界 | 32 | 32 | 1 | 49 / 49 / 49 | 1 / 1 / 1 |
| 末地 | 1 | 1 | 1 | 49 / 49 / 49 | 1 / 1 / 1 |
| 末地 | 32 | 32 | 1 | 49 / 49 / 49 | 1 / 1 / 1 |

每个 32 请求 fresh 都在首次完成前发完全部请求，全部返回同一个真实且光照正确的 LevelChunk，最后 pending 为零。没有用同一个 future 复制三十二次来伪造请求，也没有在已完成后才制造重叠。

单请求与重复请求的全部依赖生成次数、publication、POI、LIGHT、SPAWN、FULL、collisionReads 与 coldCollisionSources 完全相同。各目标的 Artifact payload、完整 structure NBT 与 B stored-biome 前沿字节相同，local seed 相同且不同于 master seed。

### 保存、重载与范围

所有 reload 的目标 canonical、全部依赖 canonical、publication、POI 与 SPAWN 为零，修改标记和结构保持，所有重复请求仍返回同一实际对象。LIGHT/FULL 的原生加载路径观察各一次，不把加载回调误记成 canonical 重生成。关闭后 in-flight、worker 与 generation binding 为零；普通 Vanilla 的 Mosaic 生成/物化计数为零，无新增 crash report。

两个代表目标的原始 Artifact payload 中 entity 与 block_entities 均为零。本项没有实体/BlockEntity 正例，也没有将 payload 比较提升为独立 pending/实例化 sidecar 的并发证明；相关正例门禁仍需分别覆盖。原始 payload 不删除字段或扩大 canonical 例外。

当前输入的四项六阶段独立原生/轻量回归（本机证据：`../../validations/conformance-legacy_terrain/result/legacy_terrain_smoke_request_burst_probe/1.20.1-forge`）通过及 CheckOnly，覆盖 origin/non-origin、下界、末地；不提升为完整 72 项。成功世界由验证工具移入回收站，证据保留。

## 结论

默认 B 在这两个维度的同目标 32 pending 请求与单请求具有相同数据和工作量，不重复生成、物化或 SPAWN。本项没有改动生产生成算法、线程数、调度优先级、Seed V1、Ordering1/2 或 format V2，也不是性能优化结果。

主世界原 300 秒未完成 FULL 的失败保持未通过；不同 physical target 的并发、前后顺序、正例副作用、出生及维度往返仍待验证。没有使用有限环/缩锁候选或延长预算替代默认路径，既有历史结果保持各自指纹。
