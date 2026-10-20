# 物理 BIOMES 结构缩锁筛选

调查日期：2026-10-20。参考端三组同源配对的发送就绪收益中位数为 8.97%、canonical 为 6.21%，独立群系／结构 smoke 和部分阶段保存重载通过；保留该候选继续集成，不代表整个 Goal 或完整结构矩阵通过。

## 问题

[逐 FULL 基线](investigation-b_full_work_amplification.md)发现整段 StructureTemplateManager 锁仍覆盖物理 BIOMES 的结构准备，而 B canonical 已使用同一版本审计过的窄锁。需要验证只是补齐入口，而不是修改结构算法，并测出真实收益。

## 方法

PhysicalBiomePreparation 在原有 context 内创建 ScopedStructureSources，完成后先关闭 source scope、再关闭 isolated context。CompactStructureInputs 已有能力审计继续决定是否缩锁；未知结构／池仍保守回退。命中后的原生结构生成、共享静态 pieces 锁、原生缓存气候采样、真实 starts／references 与存档合同不变，不跨 local seed 共享对象。

probe 的 referenceBiomeLocks 仅将这一调用的 sources 置空，恢复原有整段锁；日常产品不读取属性。每份实际准备产生窄锁真假标记，避免只切开关而没有切到昂贵工作。该标记与结果计数只属于 profiling probe。

原版独立 smoke 结果在本机路径 `validations/conformance-prefeatures/result/biome_only_smoke_biome_lock_scope/26.3-fabric`，部分生命周期在 `validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/partial-biomes/biome_lock_scope`。性能标签为 `biome_lock_screen`、`biome_lock_pair_one`、`biome_lock_pair_two`，位于性能验证的 `result/b_milestone/26.3-fabric` 下。

共同 inputHash 为 `05E8A0648239F129F7AEE3C2CB5DD7A982C1A45E4A25C842EB85DA79BAB56EE8`。六个性能 PID 分别为 reference/candidate 127816/100228、64016/69012、61044/46328，世界身份独立；三组顺序为 reference→candidate、candidate→reference、reference→candidate。资源、世界种子、坐标、8/8 与六十秒固定轨迹相同，四逻辑 CPU、BelowNormal，本线程单实例。

## 发现

### 正确性范围

四个 native／biome-only 独立 JVM 对照覆盖主世界原点、非原点、下界与末地；前三阶段数据严格一致，四个候选均实际 narrowTemplateLocks=true、virtualChunks=0。该 smoke 不是七类结构正例的完整重验。

四个生产生命周期 JVM 依次执行十六请求 BIOMES 去重并同时升级、真实保存的 BIOMES 重载、普通 FULL 请求并写测试标记、FULL 再重载。均通过；部分阶段不捕获 Artifact，已有 FULL 重载 Artifact=0、SPAWN=0，修改保留。原生成合同、普通世界身份判定及关闭取消没有被替换。

参考端单测、probe 编译和受影响 JFR／归并组件测试通过。归并脚本首次将公共 read_run 的 conditions/result 返回顺序解包反了，已修正并以实际结果及三项边界单测验证；没有重跑游戏或改写原始条件以通过检查。

### 性能

| 配对 | canonical/s reference→candidate | FULL | 发送就绪 | 发送就绪改善 | p95 相对 reference |
| --- | --- | --- | --- | --- | --- |
| 筛选 | 9.184→9.416 | 361→377 | 289→297 | 2.76% | 101.07% |
| 反向 | 9.086→9.651 | 361→393 | 289→315 | 8.97% | 94.75% |
| 正向 | 8.814→9.782 | 358→402 | 286→322 | 12.61% | [89.38%,91.39%] |

canonical 改善中位数为 6.21%，FULL 为 8.84%，发送就绪为 8.97%。第三组 reference 有截尾请求，p95 不制造点值；中位配对的 p95 为 reference 的 94.75%。第一组单独没有达到保留门槛，未据它提前判定成功或失败。

第一组实际窄锁标记为 reference 525 次、candidate 551 次。reference 的整段模板锁累计阻塞为 10.792 worker-seconds，candidate 没有采到该调用点的阻塞；这不是十秒单目标提速，也不表示所有细粒度共享锁为零。结构 CPU 份额仍约三分之一，不能把窄锁收益包装成结构成本消失。

整机 CPU 均值 reference/candidate 为 33.47%/45.99%、34.69%/30.05%、37.57%/33.88%，外部负载有波动；三组配对不能声称严格消除了所有机器噪声。相同 CPU 限制、反向顺序、实际锁消失及三个正向产出差是保留候选的依据，不承诺每次均提高 9%。

GC 后堆中位数三组的中位值为 reference 348389440、candidate 346851664 字节。第三组 candidate 的单次最大值较 reference 高，且完成的 FULL 更多，不据这一点证明严格内存不变；没有观察到持续驻留膨胀，后续完整生命周期仍需保持清理。按整窗抽样分配量／FULL 摊销的中位值基本相同，但其分子含未完成工作，不是每个 owner 的精确分配成本。

这里没有同源的新 Vanilla 对照，不能直接将与旧 Vanilla 数字的相除称为完成了 80% 门槛。最终三个视距和两个 Target 的相对 Vanilla 比值仍待统一冻结候选测量。

## 结论

此候选通过当前保留筛选，代码仅补齐现成的线程安全入口，没有修改 structure placement、stored palette 或 B 内容。继续保留到集成验证，最终仍须完整受影响门禁及 Vanilla 配对。

它不能解释或解决全部吞吐差距。下一调查是实际物理状态义务：修正 profiling 中遗漏的三参数 derive 观察，只记录阶段分布和每次准备的真实请求／义务，确认仅 TERRAIN 的依赖是否提前做了 FEATURES。尚未改动调度器或增加新的部分 TERRAIN 生成路径。
