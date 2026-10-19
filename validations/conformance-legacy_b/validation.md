# 旧版 B 独立原生对照

## 目标

分别验证 1.20.1 Forge 与 1.21.1 NeoForge 的目标单 writer、严格越界切断 B 入口。初始 smoke 覆盖主世界原点/非原点、下界及真实 EndSpike，不能代替后续完整结构、副作用与生产生命周期验证。每项有两个原生重复和两个不同宿主的消费者，不共享 Minecraft 源码或原生证据。

## 运行

1.21.1 环搜索候选必须显式传入 `-RingScope`，当前默认关闭；独立结果加 ring-scope 后缀，不覆盖既有结构/随机因果证据。因果控制先在自己的固定目录复验，再执行完整 B/脱离式交接。`EvidenceLabel` 为固定验证目的隔离目录，不按运行次数建目录；B 对照只有 ring/handoff 模式接受标签，因果工具始终把相同 variant 显式传给 JVM。它不改变原版实体随机字段的有限验收规则。

```powershell
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -RingScope
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Preparation shared -Handoff -RingScope -ValidationName integration-world_identity
# 能力守卫后的固定证据域，不覆盖首次候选；普通模式不接受标签
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -RingScope -EvidenceLabel guarded
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Suite structures -Preparation shared -Handoff -RingScope -EvidenceLabel guarded -ValidationName integration-world_identity
# 1.20.1 原生 Codec/结构加载、脱离式 Artifact 与 SPAWN palettes；尚不接物理 FULL
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.20.1-forge -EvidenceLabel artifact
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.20.1-forge -Preparation independent -Handoff -EvidenceLabel artifact
# 1.20.1 独立审计的窄锁候选：因果控制不安装候选 Mixin，消费者必须实际激活能力守卫
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.20.1-forge -RingScope -EvidenceLabel rotation_safe
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.20.1-forge -Suite structures -Handoff -RingScope -NarrowLocks -EvidenceLabel rotation_safe -Offline
```

```powershell
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.20.1-forge -Suite structures
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.20.1-forge -Suite structures -ArchiveInterrupted -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Suite structures
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.20.1-forge -Suite cold -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Suite cold -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Preparation shared -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Suite structures -Preparation shared -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Suite ocean -Preparation shared -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Preparation shared -Handoff -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Suite structures -Preparation shared -Handoff -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Compare-LegacyBPreparation.ps1 -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Compare-LegacyBPreparation.ps1 -CheckOnly
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -CheckOnly
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.20.1-forge
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.20.1-forge -CheckOnly
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.20.1-forge -Suite structures -FixtureId end_city-start -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -Suite structures -Preparation shared -FixtureId end_city-start -Offline
.\gradlew.bat :worldgen_validation:verifyLegacyB
```

串行启动四核、低优先级 fresh JVM/save，结果固定覆盖各 Target 的 result/smoke 目录，首次失败与失败世界保留，成功临时世界移入回收站。不运行 LIGHT、SPAWN、FULL，不加载日常实例。环境比较严格核对完整落盘 JSON，避免 Gson 内存 Float 与读回十进制的数值类型差异；不修改列表顺序或区块内容比较规则。

只有使用者明确确认外部中断后才使用 ArchiveInterrupted：工具要求实例已无游戏 JVM、结果不足完整套件且没有记录 FAIL，先将原始结果及未完成世界保全，再整套重跑。result/interrupted 中以 INTERRUPTED 标记，既不计通过也不计失败，不重写各原始 summary；已有归档拒绝覆盖。正常真实异常仍保留失败，不自动归类为中断。此队列可与另一独立 Target/验证的四核低优先级正确性队列并行，实例、世界和端口不得共享；性能测量期间不并行。

首次运行先执行本 Target 的 Run-ShulkerYawCausality；尚无结构 fixture 时它会串行调用原生 ExportOnly 发现入口，避免“必须先有完整 B PASS 才能取得因果样本”的循环。ExportOnly 明确输出 EXPORTED_ONLY_NOT_PARITY，只生成 manifest/launcher，不运行或宣称 B 判等；已有结构证据时不会覆盖它来取样。

structures 与 cold 的结果分别在对应套件目录。冷地形套件使用种子 Long.MIN_VALUE 的主世界原点，要求实际雪层与亮度读取为正；原生控制继续查询自己的 physical engine，同时逐次核对未初始化原生 light storage 候选，不能以两侧同一默认值判等。POI 只读取已在内存中的完整 section codec 与 dirty keys，不发起加载。依赖已缓存时可用 Offline；不关闭 TLS 或修改上游版本。

1.21.1 的 shared 候选另写带 shared 后缀的固定套件目录，不覆盖独立准备证据。它仅在单个目标准备期借用同一原生 generator、RandomState、StructureState 与 StructureCheck，保留九份精确地形和各自 stored palette；原独立入口不变。范围由 FEATURES 地形依赖与 SURFACE 群系依赖相加推导，外圈部分阶段仍独立求值。记录实际 root/借用构造、ring preparation/biome search 和关闭计数；完整九份输入、实际外圈输入和最终 NBT 都必须与独立原生控制相同，不以耗时下降替代正确性。

准备期测量仅在上述正确性完成后执行：固定原点样本，三组交错 independent/shared，均为四逻辑 CPU、低优先级、单 JVM；两个宿主中的 host_17 均启用相同 JFR profile。结果覆盖固定 preparation_comparison/1.21.1-neoforge 的三个 pair 位置，原始 NBT、上下文/搜索次数、JFR 与各组时长保留。这是模块筛选，不是 physical FULL/send-ready 性能验收，不以该结果直接决定生产保留。screen 套件只运行原生 manifest 中的原点，不声称完成其它三项。

显式 Handoff 使用独立 handoff 目录，不覆盖严格 B 生成证据；1.21.1 使用已审计的 shared 准备，1.20.1 保留 independent 准备。原生角色直接走本版 ChunkSerializer.read 形成独立结构加载控制；消费者捕获不读取宿主光照的原生 section/tick/structure payload、分别保留 instantiated 与 pending BE 原始数据及八邻区 stored SPAWN palettes。关闭所有 context 后按明确 S(C) 重建，与生成原始 NBT 加独立原生结构加载状态严格比较；携带的每个群系格及纵向 clamp 与原生 stored palette 对照，跨 seed/坐标/维度/合同误用必须拒绝。原始实体 UUID/朝向在同进程交接比较中不豁免，既有跨进程 B 比较规则不扩大。各 Target 已执行的范围与证据入口只在[当前里程碑](../../documents/project/milestone-features.md)维护；模块交接不等于生产 codec、FULL 或 SPAWN 激活通过。

## 通过标准

NarrowLocks 仅适用于已分别审计的 1.20.1 与 1.21.1 原生入口，必须显式启用 RingScope/Handoff 和独立证据标签；不据另一个 Target 的通过推定安全。1.20.1 消费者记录实际 context 能力，1.21.1 记录实际 context 构造计数；原生控制不得进入候选 context。只读复核绑定当前 Target 与合同组件的源码指纹，源码改变后不把历史输出当作当前验证。

1.20.1 的 RingScope 候选保留 independent 准备并要求 Handoff，单独写入 ring-scope 目录；不替换其它结构算法。先按同一 EvidenceLabel 运行 Run-ShulkerYawCausality 的 RingScope 原生控制，再用 Run-LegacyB 的 RingScope 验证结构或 smoke。实际游戏必须记录 opt-in，原生 state 未绑定有限域，消费者 scope 完成且无 supplier 残留；原始完整 NBT、父 feature 随机流与交接判等规则不变。

End City 因果专项只验证本版原生构造器的不确定输入，不改变 B 判等规则。先运行两个完整 fresh 原生单 writer，对 Sentry marker 的三次实际 Math.random 返回值、坐标与最终 Shulker 朝向建立关联；再用另两个 fresh JVM/save 双向重放构造器输入。只在读取、结构、feature RNG 和全部其它 NBT 严格一致、两个方向的完整 UUID-only NBT 精确复现时输出 NATIVE_CAUSAL_PROOF_NOT_B_PARITY。诊断只改构造器输入，不改最终实体数据，仍消费原生 Math.random，关闭时完全委托原生。原始严格失败不改写；专项通过本身不提升完整结构 B 的验收状态。

- 四项 fixture、十六份独立 JVM/save 证据完整；原生 seed 为 S(C)，宿主分别为 17 与 23，均不等于 S(C)。
- 结构套件另取各版本原生实际发现的 start、跨区 piece、地形 halo 和派生种子，分别为 15/18 项；非 halo 的结构必须实际进入 placeInChunk，不以 metadata 存在代替结构放块。1.21.1 还记录该调用范围内原生 setBlockState 的实际变化，每个结构类型至少一个目标内方块变化，且 Native/消费者的逐结构计数相同。
- 每项恰好一个 FEATURES writer，装饰 seed、feature seed 调用序列、stored-biome 可见集合相同；所有越界写入按 B 拒绝。
- 完整原生 Chunk NBT 与 referenced structure starts/pieces 严格一致。两版分别用自身源码、原生字节码和四个独立 JVM 证明有限 Sentry Shulker Rotation[0] 规则；每个值须与本次观测的原生构造器 raw bits 相同，位置/类型/其它字段不豁免。原始 NBT、UUID-only NBT/hash 和构造器随机输入均完整保留；没有对应原生创建观察的 Shulker 不接受此例外。完整 B 前须先通过本版因果专项，证据输入指纹必须匹配当前源码，不借用另一 Target 的结论判 PASS。
- 末地水晶实体数为正；目标内 BE、ticks、后处理均保留在完整快照，未命中项不宣称正例。
- 消费者不得请求 physical host Chunk，包括无生成的读取；未知世界逃逸即使被原版捕获也不能通过。实体不注册到 physical manager，构造/装饰绑定清理为零。
- 冷地形的每次真实 Native 亮度读取必须与独立原生 fresh-storage 候选相同，随后消费者的实际读取序列、雪层和完整目标必须相同；物理 POI storage 与 dirty keys 前后严格不变。
- 海洋掩码专项固定本版真实 seed-0 Monument 起点 (86,-12)，实际 CarvingMaskPlacement 读取次数必须为正。Native 控制直接查询其物理 ProtoChunk 的原生掩码，消费者查询精确装饰前输入；掩码读取序列、最终海草等方块与完整 NBT 严格相同，不能以跳过装饰或放开邻区写入通过。
- shared 模式须通过不同种子资源分离、借用关闭不影响 owner、owner 关闭后借用不可再用、超出推导范围拒绝的真实游戏内断言。消费者九份输入共用一套状态、80 个短期借用；实际外圈另算。全部角色 liveContexts 为零，禁止跨目标或存档共享。
- 任意新崩溃、缺失结果、错误 seed/进程复用、内容差异或新原生随机字段均失败，不自动增加忽略规则。
