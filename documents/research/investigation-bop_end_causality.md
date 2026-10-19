# TerraBlender 末地装饰差异的条件因果验证

调查日期：2026-10-15。Minecraft 26.3 Fabric、BOP 26.3.0.0.15、TerraBlender 26.3.0.0.9、GlitchCore 26.3.0.0.5 与 Fabric API 0.163.0+26.3；只研究 seed 0、End Chunk `(112,-512)` 的 B 单 writer，不代表其它输入、完整自然世界或任意第三方模组。

## 问题

此前独立原生与无关宿主的 B 对照出现 514 和 532 格差异；原生重复本身也出现三个不同结果。需要区分上游合法输入顺序波动与 Mosaic 缺陷，不能因为原生存在波动就豁免这些差异。

## 方法

使用 15 个独立 JVM/save，串行、四逻辑 CPU、低优先级。三个全新物理原生控制和宿主 17、23 的自然 Mosaic 查询记录各自顺序；另外进行原生到 Mosaic、Mosaic 到原生的条件重现、原生同顺序重复，以及第三种已记录自然顺序的独立重现。入口和结果目录见 [BOP 验证](../../validations/compatibility-bop/validation.md)。

原生控制的装饰前数据来自普通 physical Chunk scheduler；Mosaic 消费者使用自己的 seed-local 输入。双方执行相同的目标一次原版装饰与 B 越界防护，不执行八个邻区的 FEATURES。所有重现都只在 probe 中替换本次生成器的 FeatureSorter 输入，不修改生产排序、BiomeSource 集合、随机种子或原版装饰内核。

合法性检查先调用未经本探针改写的 TerraBlender `collectPossibleBiomes`，要求结果逐项等于本来源的自然枚举；记录实际 Holder hash 与上游 HashSet 顺序，不伪造对象 hash。再用原版 FeatureSorter 和原生群系配置重建顺序，要求逐项匹配原生成器的全局 feature index。受控条件只来自这些实际观察，不把任意排列宣称为上游合法顺序。

每个有效新进程记录完整有序事件：装饰 seed、step/index、实际 feature、随机 bit/API 返回值、Xoroshiro 状态、抽取计数、Gaussian 缓存、区域方块/流体/群系/高度读取、写入尝试及返回值、目标 ProtoChunk 实际写入与 flags。每个 feature 出口用独立 RNG 副本检查下一次 long，不消耗真实续流。本样本没有 Gaussian、double 或随机 fork 调用，不外推这些 API 的覆盖。

校验器另外检查 bounded 抽取的有符号溢出拒绝规则、float 原始位、long 返回值及完整消费；相同最终 hash 不能豁免中途读取或随机差异。每个最终方块必须等于最后一次实际写入或装饰前状态；装饰出口与最终 FEATURES 方块再次完全比较。完整快照仍含 biomes、结构、heightmaps、实体、BE、ticks 与后处理，不改 UUID 以外的既有规则。本目标的 entity、BE、block/fluid tick 和 post-processing 实际均为零，因此不宣称正例覆盖。

早期探针把流体 owner 的 JVM 地址写进字符串，随后改用注册名与完整属性重新采集。旧采样仅保留其独立有效的自然顺序见证，SHA-256 为 `9b1b48ffaf2529839912cb0d0a7379fd4f2953a8fa71482bd93eb2e5b46162d4`；旧读取日志不参与严格对照。该见证用于诊断，不是生产固定顺序。原 `feature_b/` 的 90 份文件逐个 SHA-256 校验不变。

## 发现

9 组独立同条件对照通过，涉及 31,073～38,321 条事件的整序列判等；不仅检查汇总计数或末尾 hash。两个无关宿主的物理加载数、实体数、block/fluid scheduler 不变，隔离 binding 清理为零。

三种输入的目标装饰前 hash 均为 `3ad1a2999125ccc8a2c844c4f79eb08d768797425d4736ee6242911e643bb5c1`，最终完整快照分别为：

| 条件 | jagged_sandstone 全局 index | 最终 SHA-256 | 相对 A 的方块差异 |
| --- | --- | --- | --- |
| A | 0 | `9184bac16e36ab4b27cca7e52f6c0cb1f3b0779a2a5758af6c4f604c16dc352f` | 0 |
| B | 1 | `fca2250b40031f578e0334c02ca168715ec9aaf445af2fb83851b8fd733a48c2` | 514 |
| C | 2 | `2d7218a075d7766550303b778c049daa86462ed14b366d68b9674e70e63c4a50` | 532 |

最早装饰事件分叉均在事件 120：相同 decoration seed `7775661404555493632`、相同 step 7，index 却为 0、1 或 2。原版实际 feature seed 因而分别为 `7775661404555563632`、`7775661404555563633`、`7775661404555563634`。

第一个原版 `nextFloat` 的 raw bits 分别为 1051281708、1057292574、1064925474。BOP 配置该 feature 使用 `RarityFilter.onAverageOnceEvery(3)`；26.3 原版比较 `nextFloat() < 1.0f / chance`，A 继续求值，B/C 直接返回 false。随后 tidepool 等装饰也因各自全局 index 改变而取得不同候选与随机续流。

代表最终差异为 `(1799,58,-8189)`：A 保留 white_sand，B 在 tidepool 的事件 5359 写入 white_sandstone；`(1807,58,-8191)` 则是 A 保留 white_sand，C 在 tidepool 的事件 5003 写入 dead_bubble_coral_block。所有差异成员及最后写入事件保存在 `result/causal_end/differences.json`，原始读取、分支输入、随机状态与续流在各角色的 `events.jsonl.gz`，不是按数量容差判等。

上游 HashSet 枚举先影响整个 BiomeSource 的 FeatureSorter 编号。即使其它群系的某个 feature 不在目标执行，它也可能改变目标所用的全局索引。因此本例首先是装饰算法的输入顺序变化，不需要用线程竞争或邻区 FEATURES 就绪顺序解释。

## 结论

本轮新见证的同条件严格等价与双向因果重现通过，514/532 格差异均有完整的新读取/随机/写入见证，未发现这些条件下额外的 Mosaic 偏差。三个完整产出与原始三个结果逐字节一致，不只是落在某个数量范围。

历史原始运行没有完整读取/RNG 链，不能据新见证倒填历史实际执行条件；它们的机器状态和原失败保持不变，历史执行链继续标记为 `unexplained`。新的 `conditionalParity = PASS` 不等于无条件 B 跨 JVM 唯一结果通过，也不自动将原 16/18 严格门槛改为 18/18。

生产排序、B 合同、Seed V1、旧存档和快照忽略规则均未改变。尚未证明全部输入、上游顺序出现频率、其它模组、其它 Target 或 C2ME 的等价性；当前完整 BOP FEATURES 兼容结论仍为 PARTIAL。
