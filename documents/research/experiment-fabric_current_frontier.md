# Fabric 当前状态修正后的性能前沿

调查日期：2026-10-17。26.3 Fabric 当前 B 的三维度、三档视距共十八次独立 JVM 测量已完成；主世界仍明显受生成吞吐和依赖前沿限制，下界／末地完成量更接近原版，但延迟仍高。本实验不是优化保留或整个 Target 收口。

## 问题

当前气候状态修正后的实现缺少可比性能数据，不能沿用历史输入的吞吐或完成子集 p95。MCP 客户端生命周期验证也不能替代相同模组集合的生产快路径性能。

## 方法

使用已有 Run-BPerformance、Analyze-BPerformance 与 compare_native_frontier，固定标签 climate_state_frontier。十八组条件的输入指纹均为 `20B23DEC114CBF30EBB0B6162DDF25E2AF477DFBA1802409DA1B0A7CDDFB7E43`，按运行脚本的 main／probe／产品组件与 target.properties 算法计算；不与真实客户端验证的全 src 指纹混用。

四逻辑 CPU、affinity 3840、BelowNormal、单个游戏 JVM、相同 master seed 123456789 和起点 (125,-37)。每组新进程、新世界；第 15／30 秒沿 X 各移动两 Chunk。8/8 为 60 秒，12/12 与 16/16 为 45 秒短测。逐维度交错 Vanilla/B 顺序，不与其它游戏或正确性队列重叠，不改生产调度或线程数。

使用普通原生 PLAYER 票据的离线 source token，不登录玩家、不发包、不请求客户端渲染；26.3 ServerPlayer 构造器经字节码复核不执行出生搜索。CLI 跳过初始出生准备，真实出生／重生由[独立客户端证据](experiment-fabric_client_lifecycle.md)承担。没有加载 MCP 或重型生命周期观察器；结构和部分 BIOMES 快路径受其原有能力检查约束，未放宽已验环境。

汇总保留原生 VIEW_DEMAND／VIEW_WITHDRAWN，而非理论正方形；未就绪请求保留截尾区间，不只取完成子集。三份归并分别核对六个独立进程／world identity、相同条件／输入与实际四 CPU，另外复核十八个 PID 和 world identity 不重复、输入一致，最终 physical BIOMES 的 pending／in-flight 为零。

## 发现

原始与严格归并入口：[8/8](../../validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier/vanilla-comparison.json)、[12/12](../../validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier/vanilla-comparison-v12-duration45.json)、[16/16](../../validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier/vanilla-comparison-v16-duration45.json)。吞吐比例越高越好，延迟比例越低越好；≥ 表示只有下界、没有有限上界。

| 视距／模拟 | 维度 | B canonical/s | FULL / Vanilla | send-ready / Vanilla | 最终原生视野就绪 | p50 / Vanilla | p95 / Vanilla |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 8/8 | 主世界 | 7.30 | 52.35% | 47.11% | 206/289 | 515.34–604.96% | ≥469.57% |
| 8/8 | 下界 | 11.25 | 91.99% | 91.29% | 289/289 | 244.89% | 241.95% |
| 8/8 | 末地 | 12.08 | 99.93% | 99.93% | 289/289 | 212.32% | 206.69% |
| 12/12 | 主世界 | 9.38 | 29.44% | 25.26% | 198/625 | ≥459.30% | ≥264.01% |
| 12/12 | 下界 | 25.67 | 93.89% | 93.50% | 625/625 | 233.19% | 202.42% |
| 12/12 | 末地 | 27.14 | 99.90% | 99.90% | 625/625 | 245.26% | 265.95% |
| 16/16 | 主世界 | 7.20 | 12.74% | 10.03% | 130/1089 | ≥299.08% | ≥179.90% |
| 16/16 | 下界 | 34.57 | 86.37% | 85.20% | 1089/1089 | 280.13% | 277.54% |
| 16/16 | 末地 | 41.00 | 100.02% | 100.02% | 1089/1089 | 196.05% | 219.28% |

每组只作一次基线。有限前沿清空后的空闲时间仍在窗口内，canonical/s 不是最大持续产能；末地接近 100% 是有限任务完成数相同，不等于延迟或最大吞吐追平。不同视距改变完成目标的种子分布和就绪顺序，不能把 12/12 比 8/8 的 canonical/s 高解释为优化收益。主世界高视距的分位数下界下降同样不表示更快：观察窗口不同，大量请求尚未完成。

### 主世界关键路径

8/8 固定初始中心请求至 send-ready 为 21.281 秒，ENTITY_TICKING 为 21.475 秒；它不是实际 player-ready 或客户端画面。

| 片段 | 墙钟 |
| --- | --- |
| 请求到 canonical 开始，含计划和排队 | 10.283 s |
| canonical | 0.502 s |
| Artifact 捕获 | 0.0015 s |
| 物化 | 0.0013 s |
| 本目标准备完成到 INITIALIZE_LIGHT 开始 | 10.218 s |
| INITIALIZE_LIGHT 开始到 FULL | 0.228 s |
| FULL 到 send-ready | 0.048 s |

LIGHT 自身约 0.146 秒，SPAWN 约 0.027 秒，POI 约 0.00018 秒。第二个十秒区间是本目标准备完成后的依赖／阶段就绪等待，不应全部归因于 LIGHT，也不能仅凭此断言调度器错误。主世界 438 个 canonical、275 FULL、206 send-ready，原版为 525 FULL／437 send-ready；采样排队峰值 82、活跃准备峰值 8，JFR 连续 span 峰值 10，与离散采样口径不同。

### CPU、锁与回归边界

主世界 B 平均使用 2.83 核当量，约四核预算的 70.63%；server thread 为单核的 4.53%，主要在等待工作。GC pause 累计 1.242 秒，堆采样峰值约 640 MiB，没有日志报告的长 tick lag。下界／末地 8/8 分别约 1.38／0.41 核当量；由于有限前沿提前完成，不将整窗平均 CPU 当作持续满负载效率。

主世界调用链分类：其它结构 29.69%、要塞环 2.34%、noise/surface/biomes 25.22%、FEATURES 32.07%、CARVERS 1.16%。这是按栈归类，局部高度查询也会出现在装饰栈中，不能把 32.07% 全解释为放方块。实际叶热点包括 Perlin／GradientNoise 和 Climate RTree；结构构造及邻区高度／密度查询仍占明显成本。ForkJoin join 可以帮助其它任务，包含关系不等于独立耗时，不能相加。

共享 StructureTemplateManager monitor 累计阻塞约 6.405 worker-seconds，不是 6.405 秒主线程停顿，也不能直接当作端到端占比。部分 physical BIOMES 的结构准备仍保守使用整段锁；尚未证明缩锁或其它候选的本轮端到端收益，不据此直接进入优化。

十八组 profile 文件检查均不超过生命周期的两次，主世界 B 两次共约 0.321 毫秒；没有热路径轮询回归。SPAWN 的 carried snapshot fast path 命中，没有 cold neighborhood rebuild。所有组正常保存停止，未出现新 crash、Unloaded chunk、POI 并发异常、意外 cancellation 或刷怪笼告警。Windows OSHI 系统信息的 Perflib 告警发生在计时前；CPU 统计使用 JVM process CPU time 差与 JFR，不依赖失败的 OSHI 系统信息项。

源码审计还确认：26.3 ChunkGenerator.createBiomes／doCreateBiomes 不读取 StructureManager 参数，但 physical BIOMES 宣告的前置阶段和存档仍需完整结构 starts／references。因此不能以“方法没用参数”为理由删掉这些元数据。当前同 source 位置的复用只保留一份输入／出口叶状态；多次不同状态可能覆盖缓存，是值得先计量的候选，而非已证明可保留的优化。

## 结论

当前主世界第一问题是累计计算与前沿等待，不能继续用旧输入比值或已完成子集制造乐观结论；本轮没有修改生产生成算法。后续先对当前前三热点做有界筛选，候选仍须 Oracle、至少三组交错对照与 ≥5% 保留门槛，最多两轮失败即止损。没有因此重启已止损的旧版假设。

MCP 模组集合的[保守回退边界](../development/compatibility-mcp_alpha.md)另计，真实客户端耗时不能冒充本表无 MCP 的快路径性能。其它 Target／BOP 不继承本结果。专用临时世界由运行任务移入回收站，原始 JFR、日志、条件、失败和统计证据保留；没有提交或推送。
