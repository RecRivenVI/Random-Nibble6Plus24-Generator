# Fabric 全 owner 物理群系输入与重载

调查日期：2026-10-17。结论：当前参考版三维度 Vanilla/B 的 49 owner 群系输入、完整 quart 读取和保存重载通过；未证明每个依赖 Chunk 的独立内容 Oracle、玩家出生、发包或性能。

## 问题

[schema-1 运行期证据](experiment-fabric_state_runtime.md)没有逐一检查所有 FULL owner 的群系前沿；[首轮增强探针](incident-fabric_biome_quart_probe.md)又漏读最顶层 quart，原数据不具备全量覆盖资格。需要原生 generation/loading 依赖分别取证，并独立核验读取数量。

## 方法

直接核对 26.3 common JAR 的 LevelHeightAccessor、LevelReader、ChunkAccess 与 holder 接口。只修改本 Target 的运行期探针，产品摘要 EFF0A7E9087FDEBDB9E1FA5E9A17DFEBA1771FAA7FD074EBB77DA8CC79EFBBD2、其余 src 摘要 61106BF7881D4289E321A13C51AEF856D135E8920535A1DD1C245D055B14AED0 均保持不变；不重跑未启用本探针的装饰前／B 入口，不改写其原输入。

新条件 all_quart_biomes 使用全 src 指纹 CF39EA0E23251629AE8BFA8C0A8F985640158B9DB7606B3504EB6518FBCBE379，main/probe 指纹为 B072BA8B3820F46D95DCFBD71E1DC89D6CD2A564FACAAE0E93E8B5007C71BF84。六个 fresh 世界分别做 Vanilla/B × 三维度，每个另用新 JVM 重载，共十二个游戏进程；单实例、四逻辑 CPU、BelowNormal，不与性能测量重叠。

原生票据先完成 49 个 FULL 与两侧实体 ticking。每个 owner 按本版 ChunkPyramid 的实际 generation/loading 累积依赖枚举需要 BIOMES 的位置，检查 holder 和无创建的物理读取。遍历真实 palette 的所有 quart，比较 LevelReader 的实际服务器结果；不使用 BiomeSource 或 uncached sampler 代替 stored authority。按唯一 Chunk 去重，独立记录真实 section 数与每格读取数，要求后者等于前者 ×64。

## 发现

| 维度 | B fresh / reload PID | Vanilla fresh / reload PID | 每组 fresh / reload cells | 每组 fresh / reload sections |
| --- | --- | --- | --- | --- |
| 主世界 | 92616 / 56856 | 72468 / 46596 | 259584 / 124416 | 4056 / 1944 |
| 下界 | 107016 / 55652 | 77596 / 77220 | 173056 / 82944 | 2704 / 1296 |
| 末地 | 60008 / 70328 | 96532 / 77496 | 173056 / 82944 | 2704 / 1296 |

十二份结果的 biomeQuartCoverageVersion 均为 1，全部读取数恰等于实际 section 数 ×64，包含最高有效方块所在的 quart。每组均保存 49 个 owner 的完整群系 SHA-256，fresh/reload 逐项一致。当前原生元数据导出 fresh 169、loading 81 个唯一物理输入 Chunk；没有把 generation halo 强加给重载，也没有硬编码这个范围到生产规划器。

physicalBiomeChecks 是状态检查次数，不是唯一 Chunk 数。原生组为 fresh 2401、reload 441；B 的观察器在基线异步完成前可重复检查，记录 4802、882。唯一输入与 palette cells 已去重，不把重复状态观测当成新增 worldgen、更多 owner 或性能收益。

读取前后 loaded count、B generation 与 Artifact 计数不变。两侧 holder 当前均为 ENTITY_TICKING，实际服务器 biome 查询与物理 palette 一致；不是通过统一 physical master-seed sampler 得到这些值。49 个 owner 的哈希只证明实际保存数据与读取路径，不单独证明每个 S(N) 的原版内容。

每个进程仍通过 220 个自然 tick、唯一实体注册、实体／流体跨物理边界、block/fluid callback 和熔炉烧炼；探针移除票据并恢复设施后 binding 与 in-flight 为零。重载保留测试修改，B generation、Artifact、POI、LIGHT、SPAWN 与 FULL 生成计数为零；普通 Vanilla 不进入 Mosaic 生产路径。两类数据的[验证入口](../../validations/integration-production_lifecycle/validation.md)均支持只读复核，新结果默认绑定当前源码；原缺顶层批次继续被特定检查拒绝。

证据位于 B runtime-proof（本机证据：`../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof`）与 Vanilla runtime-proof（本机证据：`../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/vanilla-control/runtime-proof`） 各维度的 all_quart_biomes。当前源码 Check-FabricRuntime 复核十二个独立 PID、六份条件指纹、全部 owner 哈希和清理条件通过。

## 结论

补齐全 owner 的物理群系就绪、真实读取和持久化门禁，没有改变 B、Seed V1、Ordering1/2、format V2、旧存档或 Overlay。首轮探针缺陷留作原始未通过证据，不用新数据回填。

仍欠宿主预热／线程／顺序、环搜索续流、自然生成正例的完整激活／持久化，以及出生／重生和维度往返。这里没有客户端发包、画面或性能对照；四 CPU 正确性测试的耗时不换算为相对 Vanilla 的吞吐或 send-ready 分位数。
