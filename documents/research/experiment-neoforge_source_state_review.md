# 26.3 NeoForge 结构入口历史筛选

调查日期：2026-10-17。完成一次三维度有界历史诊断和一组默认关闭控制；未修改生产缓存、生成算法或存档。本版两项安全热点审查没有产生满足保留门槛的候选，停止本 Vanilla 组合的追加性能优化，转向剩余正确性与生命周期门禁。

## 问题

[当前基线](experiment-neoforge_state_frontier.md)显示主世界结构 CPU 31.86%，实际 source 求值数量较多。需要分辨“缓存入口不同而不得不重算”与“相同原生入口再次出现、可能进一步复用”；不能从重复坐标或缓存命中率猜测收益，也不能直接借用 Fabric 的结论。

假设 H-N263-S：有限近期入口历史可能覆盖足够昂贵的实际原生求值，值得进一步提出缓存候选。先筛选，不把出现过相同 token 当作结构对象可安全重用的证明；若潜力不足以支持约 5% 端到端保留门槛，或需要承担更多可变结构对象的生命周期风险，则不实施、不无限增加状态数。

另一项审查 H-N263-P：支持能力与查询准备是否本身足够昂贵，可以不改变采样而删除重复开销。必须区分真正的元数据检查与构造过程中执行的原生结构／地形工作；不能把包容栈上的全部 CPU 算成可删除的构造成本。

## 方法

NeoForge probe 单独绑定本版 ScopedStructureSources 的真实 supplier 与 NativeBiomeSearchState.capture；方法签名、handoff 和 close 与当前源码核对。观察器仅在显式 mosaic.profile.structureReuseAudit 时安装，默认关闭、只在 probe，发行 JAR 不依赖它。没有改变 source 返回值、原生随机源、气候 token 或异常，不写世界。

每个 owner 最多观察十六个输入对象身份；记录 2／4／16 近期位置中同一 token 再次出现的实际原生 supplier 墙钟。结构图交接时失效相应位置，close 清空历史。只存诊断 token，不存或返回历史 carrier／piece。原生求值计数与观察计数必须完全一致，open／close 相同，关闭后残留为零。

三维度顺序运行，每组 fresh world、四 CPU、BelowNormal、8/8、60 秒及相同两次移动，未与其它游戏或正确性队列并行。新 probe 输入为 5B7A0FF45FAC1F5226C0EDD56A02960157F09A2F8D2E6AEA82CA06CA9050CE8A，生产 main 未修改；不能将其重标为原基线的 7172 输入。编译、四十三项布局及四十六项性能工具测试见[编译日志](../../build/research/neoforge-source-audit-compile-retry-stdout.log)，原始任务名写错的首次启动日志保留，不计游戏或 Oracle 失败。

## 发现

| 维度 | closed scopes | 实际 supplier 求值 | 两入口重复次数／实际求值 | 两入口累计 supplier 墙钟 | 十六入口累计 supplier 墙钟 | 关闭后残留 |
| --- | --- | --- | --- | --- | --- | --- |
| 主世界 | 617 | 463748 | 107468／463748，23.17% | 9.383 s | 10.653 s | 0 |
| 下界 | 783 | 390280 | 61233／390280，15.69% | 1.395 s | 1.431 s | 0 |
| 末地 | 837 | 244780 | 0／244780 | 0 | 0 | 0 |

三组分别见 [主世界](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/source_state_screen/b-source-audit-v8-s8/source-state-audit.json)、[下界](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/source_state_screen/b-source-audit-the_nether-v8-s8/source-state-audit.json)、[末地](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/source_state_screen/b-source-audit-the_end-v8-s8/source-state-audit.json)。open 等于 close，evaluationCountMismatch 与 retainedAfterClose 均为零。scope 计量包含关闭尾部已完成的 supplier 调用，不能拿它除以六十秒伪造端到端加速。

主世界实际原生 supplier 总墙钟约 60.734 s；两入口重访占其中 15.45%，但相对完整准备与装饰累计约 1084.60 s 只占约 0.87%。下界对应 11.275 s、348.42 s，两入口占完整累计约 0.40%；末地没有机会。这些是不同线程的累计墙钟，只作筛选，既不是 CPU 百分比，也不是严格的 critical-path 加速上界。

将历史从两个扩大到十六个，主世界额外覆盖仅约 1.270 s 的 supplier 墙钟，下界约 0.036 s；不能用约四分之一的重复调用数宣称同等吞吐收益。真的缓存更多结构结果还需证明多版本 piece 可变对象、handoff 失效与内存峰值；本观察器没有持有结果，不能从 token 计数编造缓存内存数字。

[关闭控制](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/source_state_screen_control/b-v8-s8)中 reuseAudit 计数为零，实际 source 查询约 162 万，身份检查两次，SPAWN 携带命中 300 次，说明并非因所有 source 工作失效而没有诊断。该次 canonical 为 9.32/s，原基线为 10.35/s；两者不是处理／对照优化，单次波动不支持收益或退化结论，也不拿诊断 10.10/s 当作生产速度。

### 支持与查询准备审查

原基线主世界 7787 个 CPU 样本中，LocalDensityTiles.Audit.visit 为 32，StructureGenerationLocks.supports 为 30、supportsPool 为 22，各不足 1%。即使只看这些检查，也没有足够证据支持新增缓存达到保留门槛。

LocalTerrainColumns.Batch 构造有 1687 个包容样本，约 21.7%，但源码与调用栈包含 CompactStructureInputs、真实 Structure.generate、Beardifier 和原生 sampler 准备；不能全删或把它全称为重复元数据。主要叶方法仍是原生 noise、气候树、体素形状合并与 aquifer。没有实施近似查询、跳过结构或换调度顺序。

## 结论

两项有界审查未提供足够可信、值得承担额外正确性与内存风险的 ≥5% 端到端候选，不实施多入口生产缓存，不扩展到十六个 carrier，也不为了漂亮结果另开第三轮同类缓存研究。没有声称优化已达到理论极限，更没有证明所有潜在优化都无效。

按里程碑停止规则结束当前 NeoForge Vanilla 组合的追加性能工作，保留[三档实际差距](experiment-neoforge_state_frontier.md)并继续并发／顺序、自然正例、玩家出生往返及保存重载缺口。BOP 和其它 Target 必须各自适配、验证；当前原生气候状态边界不自动移植。

既有 Seed V1、Ordering1/2、format V2、B 严格切断、旧存档及原版结构算法均未改变；原始失败、基线与诊断分别保留，未创建提交或推送。
