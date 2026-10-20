# 26.3 Fabric B 的 FULL 工作放大基线

调查日期：2026-10-20。三组同源、四逻辑 CPU 的主世界配对表明，B 的发送就绪产出为原版的约 66%，仍有可定位的实现开销；不能据此宣布剩余差距全部属于生成合同。

## 问题

单目标计算为数百毫秒，却不能解释玩家前沿的二十秒以上就绪等待。需要区分已完成 owner 的真实工作、尚未完成的窗口工作，以及生命周期依赖等待。

## 方法

使用 [性能验证](../../validations/performance-worldgen/validation.md) 的普通票据前沿入口，Target 为 26.3-fabric，masterSeed 为 123456789，起点 Chunk 为 (125,-37)，8/8，60 秒，第 15／30 秒各沿 X 移动两 Chunk。每个游戏固定四逻辑 CPU、affinity 3840、BelowNormal；本线程单游戏，不与正确性验证重叠。使用者允许其它线程并行，另记录整机负载。

三组交错顺序依次为 Vanilla→B、B→Vanilla、Vanilla→B。结果根为本机路径 `validations/performance-worldgen/result/b_milestone/26.3-fabric/goal_baseline_pair_one`、`goal_baseline_pair_two`、`goal_baseline_pair_three`；每组保留条件、独立 PID／世界身份、摘要和输入指纹。共同 inputHash 为 `08C14E0334B9E7B4E6F685A4E20F8C02D5C3A08421BFE3A8F979E024A5AABE20`。

仅 profiling probe 发送 schema-1 B_WORK_METRICS，按实际 FULL owner 关联计数，三组全部 FULL owner 均有对应记录。同步跨度可包含 join 等待及帮助执行别的任务，不当作纯 CPU。累计 monitor 阻塞也不当作单目标 critical path。原始失败、旧结果和生产生成合同不变。

## 发现

### 相对原版

| 组 | B canonical/s | B FULL／发送就绪 | Vanilla FULL／发送就绪 | B 发送就绪比 |
| --- | --- | --- | --- | --- |
| 一 | 8.883 | 358／286 | 525／437 | 65.39% |
| 二 | 8.966 | 361／289 | 525／437 | 66.12% |
| 三 | 8.983 | 364／290 | 525／437 | 66.27% |

这里是固定窗口产出，不是无限前沿的最大稳态吞吐；Vanilla 已提前完成该有限负载。发送就绪只证明服务端原生条件，不是发包或客户端渲染。最后保留视野均为 289 个真实原生请求，不用正方形估算代替；第一组 B 有三个截尾请求，没有删除它们。

| 组 | Vanilla send-ready p50／p95（秒） | B send-ready p50／p95（秒） |
| --- | --- | --- |
| 一 | 5.880／10.259 | [32.596,33.066]／[54.954,55.781] |
| 二 | 6.486／10.505 | 31.564／54.783 |
| 三 | 6.953／11.269 | 32.383／54.171 |

第二组 B 延迟为原版 p50 的 486.67%、p95 的 521.49%；第三组为 465.75%、480.73%。第一组只能给区间，不能补点值。整机 CPU 均值分别为 Vanilla 21.37%／30.26%／28.76%、B 40.91%／37.64%／42.29%；存在外部负载差异，不能把百分比本身当优化因果。但三组 B canonical 产出与发送数接近，不依赖某一轮最有利结果。

### 每个 FULL 多做什么

第一组 358 个 FULL owner 的实测均值：

| 工作 | 数量 |
| --- | --- |
| 完整同种子邻区 TERRAIN 精确回退 | 1.240 份；中位数 0，p95 为 7 |
| retained native biome 构建 | 25 份，逐 owner 均为 25 |
| FEATURES 可见的额外 biome palette | 8 份 |
| 局部地形列 | 299.10 列 |
| 局部 noise batch | 7.606 |
| 局部 material batch | 34.992 |
| 输出密度采样／插值源采样 | 309874.41／26767.28 |
| structure source 查询／实际求值 | 2845.60／775.84 |

25 份来自目标 TERRAIN 的 stored-biome halo 与八个邻区读取 batch 的重叠并集，并不是 25 份完整邻区地形。当前实现已复用相同 palette；不能再按 9×9 称为 81 次群系生成。已物理拼接的 S(N) 不可直接替代这些 S(C) 输入；这部分来自 B 的定义，但尚未证明所有当前准备粒度都不可再缩小。

每个窗口完成的 canonical／FULL 比为 1.489、1.490、1.481；physical BIOMES ready／FULL 比为 1.296、1.343、1.363。分子含尚未 FULL 的前沿工作，只是整窗摊销，不能称为某个 FULL 必需的 1.49 次 canonical。代码检查还发现 TERRAIN 请求仍会准备完整 FEATURES Artifact，是否有可安全拆分的冗余尚待实际最高义务取证，不能先按错误放大判定。

### 等待与锁

第一组中心目标相对请求：canonical 开始 9.472 秒、结束 9.761 秒，物化结束 9.764 秒，INITIALIZE_LIGHT 开始 19.472 秒，FULL 19.900 秒，发送就绪 19.945 秒。可见目标自身计算约 0.289 秒，不是二十秒；计算前等待和邻区 readiness 占主要延迟，物化不是主因。

第一组 CPU 样本分类为其它结构 29.82%、环搜索 2.29%、noise／surface／biome 24.98%、FEATURES 路径 30.53%；最后一项包括其发起的局部查询，不是纯装饰放块。GC 暂停总量三组约 1.11～1.24 秒，没有恢复成先前的文件系统主线程 stall。

第二组按最内层任务归属，物理 BIOMES 占 732／8632 个 CPU 样本；约 10.923 worker-seconds 的整段 StructureTemplateManager 阻塞全部来自该路径。B canonical 路径已使用通过审计的缩锁，但 PhysicalBiomePreparation 仍调用无 scoped source 的 CompactStructureInputs.resolve，从而保留整段锁。该差异是具体实现遗漏，不是 B 合同必需开销。

还发现原 PerfPlanMixin 只绑定双参数 derive，而生产现在调用三参数版本；本批没有 PLAN_SHAPE／PLAN_CANONICAL_DEPENDENCY 事件。以上逐 FULL 计数来自独立 B 结果和原生 FULL 事件，不受影响；但不能用本批宣称已经取得完整动态计划图。

## 结论

基线有效，但未达到性能目标。下一候选是只让物理 BIOMES 复用已有、已审计的 scoped source／窄锁入口，先做 native stored-biome／结构对照和部分保存重载，再筛选端到端收益；不能据累计锁等待承诺提升。

后续需要补实际三参数生产计划观察，区分必要的 same-S(C) 查询与本不需要 FEATURES 的物理依赖。只有剖析支持、对照达到保留门槛且正确性不退化的候选才能保留；本记录不批准迁移合同、近似采样、跨种子缓存或 scheduler 改写。
