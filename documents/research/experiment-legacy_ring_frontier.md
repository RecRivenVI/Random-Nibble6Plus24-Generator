# 1.20.1 有限环的原生冷前沿性能筛选

调查日期：2026-10-17。状态安全有限环已减少内部结构准备，但本次 60/180 秒正常票据前沿均未完成完整 B；不能保留为端到端性能优化，候选继续默认关闭。

## 问题

结构模块和单个 FULL 的正确性通过后，H120-R 能否让正常 PLAYER 票据前沿更接近 Vanilla，而不是只更快地推进大量未完成计算？

## 方法

使用独立 Forge 1.20.1 原生票据探针，三组分别为 vanilla、b_eager、b_ring；每组 fresh world、独立 JVM、四逻辑 CPU、BelowNormal、3 GiB、主世界 8/8、master seed 123456789、中心 (125,-37)，第 15/30 秒各沿 X 移动两 Chunk。先固定 60 秒筛选，再只做一次相同轨迹的 180 秒有界诊断，不与正确性队列并行。

输入指纹为 7477ACCFA2B793B380339A781E22AFF51430DBC5587C8E4EA10A9B3512CA6FC9。本轮增加独立探针与 Target 的原生 data pack format 15 事实，不修改生成算法。原生票据 token 只在构造期间跳过一次 fudgeSpawnLocation，开始前检查全部维度更新中 holder、loaded count、Mosaic 工作与在途状态为零。CLI 发送就绪仅按本版 getTickingChunk 前置条件，不是发包、画面或玩家出生证明。

## 发现

60 秒三组（本机证据：`../../validations/performance-legacy_frontier/result/legacy_frontier_cold/1.20.1-forge/overworld/v8/screen/comparison.json`）和180 秒三组（本机证据：`../../validations/performance-legacy_frontier/result/legacy_frontier_cold/1.20.1-forge/overworld/v8/screen-duration180/comparison.json`）均完成采样、正常保存退出与只读归并；六个 PID、run identity、源码与资源条件由工具核对，普通 Vanilla 的 Mosaic context/work 全为零。初次探针写错 loader 标签的原始失败保全于 result/native_api_initial，不改写、不计入正式对照。

| 固定窗口 | Pipeline | canonical 完成 | FULL / send-ready 完成 | FULL / send 吞吐相对 Vanilla | 当前视野就绪 |
| --- | --- | --- | --- | --- | --- |
| 60 s | Vanilla | 不适用 | 483 / 399 | 100% / 100% | 313 / 313 |
| 60 s | 保守 B | 0 | 0 / 0 | 0% / 0% | 0 / 313 |
| 60 s | 有限环 B | 0 | 0 / 0 | 0% / 0% | 0 / 313 |
| 180 s | Vanilla | 不适用 | 483 / 399 | 100% / 100% | 313 / 313 |
| 180 s | 保守 B | 0 | 0 / 0 | 0% / 0% | 0 / 313 |
| 180 s | 有限环 B | 0 | 0 / 0 | 0% / 0% | 0 / 313 |

这是有限前沿完成量，不是最大吞吐。180 秒 Vanilla 当前视野 send-ready p50/p95 为 10.373/16.371 秒；两个 B 仍全部截尾，p50/p95 只知不小于约 180 秒，分别大于原版约 1735%/1099%，没有精确点值或有限上界。不能把零基线制造为无限改善，也不能从统计中删除未完成请求。

180 秒保守 B 构造 59 个 context、完成 52 次结构输入解析和 15028 个 source 求值，实际完整环搜索 6722 次；有限环 B 构造 3676 个 context、完成 3586 次结构输入解析和 1036354 个 source 求值，完整搜索 1206 次。候选退出时已收集到 419712 个 slot、418620 次原生尾重放、797 次歧义原生回退；仍在执行的 context 不伪计为已关闭。这些不同进度下的总量不是同工作量加速倍数，只证明原生回退和候选均实际执行。

| 180 s CPU / 运行状态 | 保守 B | 有限环 B |
| --- | --- | --- |
| Stronghold/ring 样本 | 97.51% | 30.41% |
| 其它结构样本 | 0.75% | 39.76% |
| Noise/Surface/Biomes 样本 | 0.78% | 27.87% |
| 四 CPU 预算占用 | 29.81% | 44.75% |
| server thread CPU / 墙钟 | 0.81% | 0.88% |
| GC 累计暂停 | 129 ms | 2615 ms（约窗口 1.45%） |
| 峰值在途 worker | 18 | 150 |
| 末次已用 heap 粗观测 | 657 MB | 1371 MB |

候选的 worker 启动排队中位数约 21 微秒，峰值待启动 worker 为 2；高在途数不代表有 150 个线程在并行计算。JFR 已记录约 4250 线程秒在 CompactStructureInputs 的共享 StructureTemplateManager 监视器上等待，窗口末仍未获得锁的等待未完整计入；累计线程阻塞不等于玩家等待秒数或墙钟百分比，不能称为死锁。

中心目标约 2.398 秒开始 canonical，180 秒末仍未结束，之后 Artifact、物化、LIGHT、SPAWN、FULL、ticking 和发送前置条件均未完成；本次主路径不是“已经生成好却发不出去”。对应原版中心 FULL、BLOCK_TICKING、ENTITY_TICKING 为 3.730/5.282/6.238 秒。低 server CPU 与约 436 ms 的最大观测 tick 间隔不支持此前磁盘身份检查式几十秒主线程阻塞；GC 也不是当前主解释。

## 结论

H120-R 第二轮有严格正确性与内部工作下降证据，但单独不足以改善本版正常前沿就绪，性能保留门槛未通过；默认关闭，不启三组配对，不做第三轮同义环算法，也不再延长窗口。不能从本次四 CPU 主世界结果推定其它维度、正常资源客户端或其它 Target 的可玩性。

下一项独立热点审查应区分共享结构状态所需保护与整段 createStructures 锁，以及低阶段物理依赖触发完整 B 的工作放大；须先按 1.20.1 实际源码审计、另立有界假设，不能直接复制其它版本缩锁或缓存。当前不删除锁、不改变 scheduler、随机流、B 或旧存档语义；原版/BOP 五 Target 里程碑仍未完成。
