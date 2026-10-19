# 1.21.1 实际物理准备前沿与日志通道

调查日期：2026-10-16。完整控制台与新增准备计数表明：晚注册 focus 实际已经执行，主世界普通 FULL 在不使用强制时序时仍触及 180 秒。前者更正此前日志通道不完整的判断，后者继续保持未通过，不改变任何原始证据。

## 问题

只检查 latest.log 是否足以判断探针的 System.out 标记？部分 BIOMES 接入后，主世界 FULL 的长等待是否只是强制等待 focus 导致，而非正常生产路径？

## 方法

读取 [发布越权记录](incident-legacy_publication_race.md)对应的完整 console.log、原始失败归档及原生 server 日志，不改写该记录或输出。新增默认关闭的 probe 计数：原生 FULL 依赖分布、实际数据义务、每三十秒准备/发布进度及失败时的计数快照。

在同样四逻辑 CPU、低优先级和原有 180 秒预算下，另跑未开启 CompletionOrder 的普通生产 FULL/save/reload 请求；不改变依赖、生产并发或 worldgen 输出。该轮在 fresh 阶段失败，未执行到 reload。

## 发现

- 原始与修正候选两次 completion-order 的完整控制台均含 `MOSAIC_COMPLETION_ORDER input-complete-before-handle target=[125, -37] registeringThread=worldgen`。因此此前记录中“未输出 focus 标记”的句子不成立：当时只检查了不包含该 System.out 通道的 latest.log。原始记录不改写，在此明确纠正。
- 该标记证明 focus 输入已完成后才返回注册线程，不等于全部 native holder 完成归属或整个 FULL 通过。两次原有 FAIL 仍为 FAIL，首次 Worker-Main-1 发布越权的真实异常栈仍成立；修正候选缺少当时的逐次完成计数，不能补造。
- 实际 native FULL 前沿为 529 个 holder：STRUCTURE_STARTS 480、BIOMES 24、CARVERS 16、INITIALIZE_LIGHT 8、FULL 1。实际数据义务共 49：24 个 BIOMES 候选与 25 个完整 Artifact 回退。它们是不同物理 S(N) 的任务，不是一个 target 的 529 份同 S(C) 虚拟地形；两种数量不可混用。
- 不使用强制时序的主世界 fresh 请求在 18:23:44 达到 180 秒。此时 partialBiomesSupported=true，Artifact 已准备/发布各 8、部分 BIOMES 各 16、in-flight 25；尚无 POI、LIGHT、SPAWN 或 FULL 完成。主要等待发生在完整物理交接之前，而非已有 FULL 被客户端阻塞。
- 30/60 秒均只有九个在途、没有准备完成；90 秒为四个 Artifact 与两个 BIOMES 完成、43 个在途。由此可确认工作逐步推进，不能把所有等待直接解释为死锁；也不能仅凭 future 等待栈归因于锁或 shared-pool starvation。
- 该轮原始 JSON、console.log 与 crash report 保留于 integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b，结果为 FAIL。Gradle 输出 BUILD SUCCESSFUL，但验证工具正确依据新 crash report 拒绝通过；退出码不能取代内容检查。服务器随后正常保存所有维度并退出。
- 源码中每个完整 B 需九份 CARVERS 地形，各自生成周边 palette；CompactStructureInputs 在同种子 owner 派生查询中仍逐次评估 source，而不是查询一次全程复用。此为可见重复工作，实际占比与安全复用收益尚未由本轮计数证明，不据此直接启用新的优化。

## 结论

日志结论必须覆盖实际证据通道。指定 focus 的晚注册条件已执行，但线程修正的完整验收与主世界 FULL 仍未闭合；原始失败不能降级为工具问题或被本次更正改成 PASS。

下一步把其它维度和完成边界分别验证，同时明确记录真实准备成本。若进一步复用同 S(C) 输入，仍须独立 Oracle、作用域/关闭/并发验证和有界性能筛选；不得以空数据、近似结果、延长超时或减少前沿解决。
