# 26.3 NeoForge 状态守卫后三维度性能

调查日期：2026-10-17。状态守卫后的三维度 8/8、12/12、16/16 独立 Vanilla／B 对照全部完成，共十八个 fresh JVM／world identity；只建立基线，不作优化保留、玩家登录、发包或渲染验收。

## 问题

[原生气候状态修正](incident-neoforge_source_climate_state.md)后，守卫之前的性能比不能继续代表当前实现。必须重新测量整片真实物理前沿，包括排队、独立 canonical 工作、materialization、LIGHT、FULL 及发送就绪，而不是只测一个函数或引用 Fabric 的结果。

## 方法

本次输入为 7172EA749A0F0F9FFA4F57A2BE4B3C9C6424480EB22CD7A16C487820B83A5073。每组四逻辑 CPU、掩码 3840、BelowNormal、单游戏实例，Java 25.0.4+9-LTS，NeoForge 26.3.0.83；不与正确性队列重叠。master seed 123456789，目标 (125,-37)，第 15／30 秒各沿 X 移动两 Chunk。8/8 测 60 秒，12/12 与 16/16 测 45 秒；未改变生产调度或生成合同。

使用真实原生玩家距离票据而非 forceload，不登录假玩家。各组新建世界，按 Vanilla→B 串行；十八个 PID、world identity 各不相同，源码指纹相同，实际 availableProcessors 均为四。模组集合不含 MCP，不借未知模组回退的客户端结果代替当前快路径性能。

结果固定于 [climate_state_frontier](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/climate_state_frontier)。归并器分别核对相同请求轨迹、原生视野需求集合、正常测量终态及原始文件 hash；保留未完成和已撤销请求，截尾延迟使用区间，不只报告已完成子集。send-ready 仅为服务端首次发送就绪，不等于 packet 已到客户端。

## 发现

| 视距／窗口 | 维度 | B canonical/s | FULL 吞吐 / Vanilla | send-ready 吞吐 / Vanilla | 最终视野已就绪／需求 |
| --- | --- | --- | --- | --- | --- |
| 8／60 s | 主世界 | 10.35 | 67.96% | 64.73% | 283／289 |
| 8／60 s | 下界 | 13.05 | 91.95% | 91.25% | 289／289 |
| 8／60 s | 末地 | 13.95 | 99.97% | 99.97% | 289／289 |
| 12／45 s | 主世界 | 9.33 | 23.12% | 19.40% | 152／625 |
| 12／45 s | 下界 | 27.96 | 90.65% | 90.04% | 625／625 |
| 12／45 s | 末地 | 30.33 | 99.91% | 99.91% | 625／625 |
| 16／45 s | 主世界 | 9.62 | 15.04% | 12.43% | 161／1089 |
| 16／45 s | 下界 | 35.63 | 79.32% | 77.50% | 1004／1089 |
| 16／45 s | 末地 | 44.17 | 97.43% | 97.29% | 1089／1089 |

| 视距 | 维度 | 当前视野 send-ready p50 / Vanilla | 当前视野 send-ready p95 / Vanilla |
| --- | --- | --- | --- |
| 8 | 主世界 | 525.12–544.52% | 546.77–546.90% |
| 8 | 下界 | 261.44% | 250.95% |
| 8 | 末地 | 259.25% | 238.96% |
| 12 | 主世界 | ≥427.45%，无有限上界 | ≥251.08%，无有限上界 |
| 12 | 下界 | 257.77% | 223.73% |
| 12 | 末地 | 215.22% | 235.14% |
| 16 | 主世界 | ≥326.76%，无有限上界 | ≥193.19%，无有限上界 |
| 16 | 下界 | 288.93–302.49% | ≥260.02%，无有限上界 |
| 16 | 末地 | 239.87% | 249.53% |

完整比值分别见 [8/8](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/climate_state_frontier/vanilla-comparison.json)、[12/12](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/climate_state_frontier/vanilla-comparison-v12-duration45.json)、[16/16](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/climate_state_frontier/vanilla-comparison-v16-duration45.json)。高视距主世界的百分比只是截尾下界，不能据其数值变小认定加载更快。后两维度部分组已清空有限前沿，canonical/s 和约 100% 完成吞吐不是持续满载上限，不能据此认定追平 Vanilla。

### 主世界 8/8 关键路径

中心 request 从 0 秒开始；0.592 秒进入 canonical，5.071 秒结束，约 4.479 秒执行。捕获和 rehydrate 后，5.074 秒 preparation 已完成；等待其它物理依赖至 18.962 秒开始初始化光照，LIGHT 为 18.998–19.096 秒，SPAWN 为 19.110–19.111 秒，FULL 为 19.128 秒，send-ready 为 19.159 秒，ENTITY_TICKING 为 19.265 秒。

因此约 19.16 秒等待不是全部来自目标自己的计算，更不是 LIGHT 花了十几秒；准备完成后约 13.89 秒主要仍在物理前沿依赖。整组 621 个 canonical、357 FULL、283 send-ready，队列峰值为 admission 289／executor 92，峰值 active sessions 为 43。累计线程 span 不等于单请求墙钟。

本组平均使用约 3.45 核，占四核预算 86.23%；主线程约占单核 5.64%，未记录落后告警，不以缺日志断言所有 tick 零延迟。结构 CPU 为 31.86%（含环 2.66%），Noise/Surface/Biomes 为 27.94%，FEATURES 为 23.36%；装饰分类中的邻区读取还会调用地形，不把它全解释为装饰放块本身。

主要叶方法为 NoiseStack.Perlin.get、GradientNoise.permute、Climate.RTree.SubTree.search、VoxelShape 合并与 Aquifer。密度支持审查只有 32／7787 个 CPU 样本，结构锁能力 supports／supportsPool 分别为 30／22 个；这些检查各不到 1%，没有证据支持为它们另建缓存。Batch 构造为 1687／7787 个包容样本，但其中有真实结构和原生采样准备，不能把它全算成可删除的对象构造。

GC 暂停累计 1.462 秒、最大 28.7 ms，峰值堆约 1.31 GB；采样分配权重最大的站点包含 DensityBuffer、JigsawBlockInfo.withInfo、VoxelShape 合并及 Climate.TargetPoint。分配权重是估计，不当作精确 allocation。模板缓存监视器累计约 67 ms，Registry.find 约 135 ms；这些多线程累计值不能当作主线程停顿或当前第一锁瓶颈。

身份文件检查只有生命周期两次，主线程一次约 0.292 ms，未回到 Chunk 热路径。SPAWN 357 次全部使用携带数据；biome preparation 累计约 10.3 ms，保留 Repair A／B 的边界。

### 下一项有界筛选

当前 8/8 checkpoint 记录约 177 万次 structure source 查询、46.6 万次实际求值、130.6 万次命中及约 64 秒累计 supplier 墙钟。不能从总量推定多入口缓存能省掉多少；也不能直接引用 Fabric 低收益结论。

下一项仅增加默认关闭的本版 probe，记录同位置原生入口 token 的有限历史、真实求值耗时及交接失效，不返回缓存对象、不恢复 token、不改变原生工作。最多一次三维度诊断筛选，再结合一组关闭探针的控制和正确性／内存门槛决定是否有实施候选；不把诊断吞吐当作优化收益，不继续无限扩展候选。

## 结论

当前主世界仍受累计计算与前沿就绪限制，高视距明显不足；下界较轻，末地在有限负载下能补齐，但单请求延迟仍高于 Vanilla。不能判定正常中高视距探索已追平原版。

本次没有生产优化或新生成合同，也不拿临时诊断路径代替生产。下一步按有界候选审查推进，同时保留原版正确性、保存重载、其余 Target 和 BOP 的独立门禁；没有宣布 NeoForge 或整个里程碑收口。
