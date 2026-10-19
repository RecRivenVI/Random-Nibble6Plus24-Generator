# B 方案严格切断验证

## 目标

验证 26.3 的独立 B 基线：仅目标执行原版 FEATURES，越界方块、BlockEntity、实体、tick 与后处理没有邻区写权限，目标内完整数据保留。使用真实同种子 TERRAIN 输入，不接入生产分流、不改变已有 Ordering 1/2 存档。此验证不声称旧九 writer 装饰 parity。

## 运行

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -Lightweight
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -Preparation
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -StructureReuse
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-StructureB.ps1 -EvidenceLabel climate_state_order
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-StructureB.ps1 -EvidenceLabel climate_state_order -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -NativeCat
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-MonsterRooms.ps1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.3-fabric -EvidenceLabel climate_state_launch_ports -CpuAffinity 61440
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.3-fabric -EvidenceLabel climate_state_launch_ports -CpuAffinity 61440 -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.3-fabric -FixtureId nether-chests -EvidenceLabel nether_natural -CpuAffinity 3840
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.3-fabric -FixtureId nether-chests -EvidenceLabel piglin_order_iterator_a -AttributeCaptureOnly -CpuAffinity 3840
$causalInput = (Get-Content validations/conformance-feature_b/result/piglin_order_iterator-effective-causality.json -Raw | ConvertFrom-Json).inputHash
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-PiglinOrderCausality.ps1 -EvidenceLabel piglin_order_iterator -ExpectedInputHash $causalInput -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.3-neoforge -Smoke
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.3-neoforge -Smoke -EvidenceLabel climate_state_retry -CpuAffinity 61440 -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.1.2-neoforge -Smoke
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.1.2-neoforge
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.1.2-neoforge -Handoff -Smoke
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1 -Target 26.1.2-neoforge -Handoff
```

两个独立宿主进程串行运行，宿主种子分别为 17、23；四 logical CPU、低优先级、单个高负载 JVM。运行只使用自己的 instance/result，不读取日常实例；服务端需要已明确接受 EULA。固定结果在 `result/current/26.3-fabric/host_0/` 与 `host_1/`，正常生成的验证世界结束后移入回收站。

AttributeCaptureOnly 是仅限 nether-chests 的原版诊断，不运行完整四角色门禁，不声明独立 B 通过。探针记录实际 Holder 身份哈希、原版 map iterator、pack 与最终原始 NBT 列表的对应；不使用顺序不同的 stream 代替 iterator。显式 AttributeReplaySource 只能引用同一结果根下已捕获的原生 attribute-order.json，重放只排列同一组 Packed 对象，不更改属性、modifier、随机源或生产数据；完整 UUID-only 快照和所有其它字段必须严格匹配被重放的独立原版结果。它不扩大 NativeEntityAcceptance，也不把原始堡垒失败改写为通过；自然两种顺序的见证和双向证明另记研究。

Run-PiglinOrderCausality 固定运行四份原版自然捕获和四份重放，不按最接近的结果挑轮次。每个方向至少一份必须真的改变八个目标实体的 pack 顺序；写回同一顺序的 no-op 不作为该方向因果证明。schema-2 effective-causality 还核对全部生成输入、随机 trace、引用结构、完整 UUID-only hash、独立 JVM/save、实际资源限制及当前源码；诊断通过只标 PASS_CAUSAL_ONLY，不提升完整 B 门禁。无 CheckOnly 使用新 EvidenceLabel；已有捕获只能显式 ReuseCaptures、ReuseInitialReplays 并先只读复核，结果不覆盖。

使用者已批准的猪灵有限规则只在 nether-chests 普通四角色门禁显式开启：两个已证明属性按唯一 ID 对齐，全部值和修饰符严格，未知成员失败，其它实体不豁免。运行前核验原生 schema-2 因果证据；探针或验收器更新后，旧因果结果必须用显式 ExpectedInputHash 只读复核，并标为历史输入，不能重写为当前实验。完整快照的 strictParity 与有限 accepted 分开保留，原 FAIL 不覆盖。当前独立四角色重验使用 nether_natural_accepted 标签。

参考 Fabric 的独立 B 可用 EvidenceLabel 隔离新条件，例如 result/climate_state_launch_ports/independent/26.3-fabric；世界名也加后缀，已有目录拒绝覆盖。引导只读取模板任务先前导出的、属于本验证实例的原生服务端启动配置；仍在新的 JVM/save 中重新导出 fixture 和当前启动配置，不复用旧世界或生成结果。实际游戏通过最后一个 validation.result JVM 参数写入新根，现有 Minecraft 源码与旧证据不修改。

26.3 NeoForge 同样支持独立 EvidenceLabel，使用其真实 DevLaunch 参数文件，不套用 Fabric 的 DLI 配置。工具保留模板参数，只在验证实例创建端口为 0 的私有副本；每个角色核对参数 SHA、PID、四 CPU affinity、优先级、具体世界与真实端口日志。未知 launcher、外部／链接路径或不唯一端口拒绝运行。当前气候状态修正的预检证据使用 climate_state_retry；完整十九项另用 climate_state_full，不能覆盖原有中断目录。重新执行选择新的标签并去掉 CheckOnly，不把旧启动记录或世界当作新原生证据。

该模式须为本实例、参考 Target、服务端环境与原生 port 0 参数。Loom 共享配置可能追加覆盖命令行端口，导出的游戏参数也可能已经包含这些追加项。因此任务生成本实例独占的配置快照，只把服务端端口改为 0；没有服务端端口时追加该参数，存在多个时拒绝启动。导出参数中的每个明确端口也统一为 0，其它原生参数保留，不修改共享配置。每个游戏绑定快照哈希、参数，并在实际启动日志中断言 port 0，临时监听端口由系统分配。原 climate_state 与 climate_state_ports 批次的端口碰撞保留为未完成，不计完整 B 通过。

与装饰前队列并行时，两个验证使用不同实例、世界、结果与端口，分别限制四逻辑 CPU/低优先级。每个 B 游戏记录实际 PID、affinity、priority 和世界名，与游戏证据绑定；不在此期间测性能。Target 及 worldgen_contract 源码冻结到两个队列结束。CheckOnly 默认绑定当前源码，显式历史指纹只允许只读，不用旧指纹重新生成。

## 通过标准

`Run-IndependentB` 为 19 项实际 B 交接 fixture 分别创建两个 fresh 物理原版控制和两个无关宿主（17、23），共 76 个独立 JVM/save。原版控制只由本版 physical scheduler 生成九份装饰前输入，严格切断后只运行目标 writer；消费者调用真实 BFeaturePreparation，不用新旧模块同进程对照代替独立原版。严格比较目标装饰前、完整 FEATURES、全部 owner/referenced start/pieces、writer/feature-seed 序列、stored-biome 可见集合；保留 raw NBT、UUID-only 原始 hash 和逐字段 diff。仅沿用已批准的有限原生实体验收，不新增忽略字段。普通原版宿主禁止额外地形模组，隔离消费者不得改变 physical host，所有 binding 清零。`-Smoke` 的四项只验证入口，不代替完整套件；失败证据和世界保留，不以新结果回填历史失败。

`-Target` 选择实际加载器的独立实现；NeoForge 按模块隔离 probe 包，fresh JVM 原样复用本版 DevLaunch 参数文件，不借用 Fabric 结果。`-FixtureId` 只用于单案例复现，清单和复核明确只含一项，不提升完整套件状态。原生猫变种谓词保留原版 StructureCheck 方法，只在 native control 的短作用域允许它读取自己的真实 physical S(C) 结构；隔离消费者继续使用生产路由，未知 escape 仍失败。所有临时绑定在正常或异常返回时清除。

26.3 Fabric 的 nether-chests 是从真实生产清点取得的显式附加案例，默认十九项选择不变。它以主种子 123456789、下界 (-10,-10) 经既有 resolver 得到 local seed，要求真实结构 piece 相交与 BE 正例；四角色仍严格比较完整目标及实体字段。不因容器换序猜测而自动排序或套用其它实体的例外，失败保留，生命周期通过不能替代此内容门禁。

26.1.2 的独立入口为 18 项、72 个 fresh JVM/save，不包含 26.3 特有的含硫植被；本版装饰前状态为 CARVERS，而非 TERRAIN。消费者直接调用尚未接入生产的 StrictFeatureBaseline，保留九份完整精确输入；原生与消费者均在装饰前按本版 FEATURES 入口准备四种高度图。需要实体随机例外时，必须先有本版两份 fresh native 的同输入证据；不得借用 26.3 的不同版本结果放宽对照。此入口不改变已有存档身份或 Ordering。

本版 `-Handoff` 另存 `result/independent_handoff/26.1.2-neoforge/`；调用接线中的 BFeaturePreparation，比较原始生成态与 fresh native，并在关闭会话前提取八邻区真实 stored SPAWN palette。Artifact 还原另严格比较原生加载控制，保留 before/after/native-load-control 原始 NBT，不把生成与加载边界混为一谈。本版原生 loader 会重算模板 piece 包围盒，并以显式 local seed 重建 Monument pieces；控制直接加载自身结构 NBT，用 S(C)，其余完整目标数据和原始实体 transport 不忽略。此项通过只证明模块与交接，不代替普通 physical FULL、保存重启、运行期或性能验收。`-Smoke`、`-FixtureId` 和 `-CheckOnly` 分别用于入口、单案例与已有证据复核，不覆盖未选范围。

本版模板 RuleProcessor 会在裁剪放置区域前读取外圈。外围输入按 FEATURES 的 direct/accumulated dependency metadata 派生：九份 CARVERS 之外仅在实际读取时生成对应的原生 STRUCTURE_STARTS/REFERENCES/BIOMES 阶段，不用完整地形或假空气代替。Native 控制直接保留自身 physical prerequisite Chunk，消费者使用同 S(C) 的精确阶段；逐个外圈输入的完整快照和状态必须严格一致，所有外圈写入仍拒绝。完整快照计算仅在 probe 中，不能进入未来生产热路径。

15 个真实目标覆盖 Overworld、Nether、End 与七类结构；每次实际观察到的目标 writer 为 1，全部八个邻区的完整前后快照相同且停在 TERRAIN，目标严格停在 FEATURES。十个代表目标正序、逆序、固定打乱与五目标并发、不同 JVM/host seed 得到同样完整 canonical 数据、reference start NBT 投影、feature seed 序列和可见 biome 集合；同坐标不同种子同时运行不得污染。实体 canonical 只沿用 UUID 例外，不扩大忽略字段。

目标内真实 BE、矿车、EndSpike 水晶、block/fluid ticks 与后处理必须有正例；pending NBT 的自然正例未命中时明确报告，另由单元测试验证其完整保存与越界拒绝。八方向及边角的 chunk/section/NBT/BE/实体/乘客/tick/后处理防护通过，远于读取前沿的 region tick 被丢弃而不是查询外部 chunk。

非原点目标在物理前沿 absent/generated/mutated 时完整结果一致，宿主已有数据不变，无新增 physical chunk/entity/tick，虚拟方块写入的 POI 更新被抑制。正常、并发及注入异常后的 binding 为零，真实异常继续传播；已有生产分流和普通世界不调用 B 模块。

此基线仍保留八份精确只读 TERRAIN，而不是仅一个完整 ProtoChunk 的性能宣称；不运行邻区 FEATURES，不进入 LIGHT/SPAWN/FULL。End City 的 Shulker Rotation 保持原版行为，该目标只覆盖实体/BE/结构与越界隔离，不冒充全部实体字段的跨进程确定性。树木/装饰完整视觉、物理交接与性能对照不属于本项验证。

`-Lightweight` 单独写入 `result/lightweight/26.3-fabric/host_0/` 与 `host_1/`，不覆盖原 B。18 个目标包含原 15 项、白桦及两个已知局部算法回退场景；每次先运行原 B，再运行按需 B。逐次比较所有实际邻区方块、流体、四种高度及 stored biome 读取，包括原版矿脉的 `BulkSectionAccess` 和装饰前 biome section 扫描；比较最终完整目标状态、引用 start NBT、feature seed 序列、写入拒绝与已审计 physical escape 计数。当前探针沿用有限原生实体验收，逐字段比较 End City 实体并保留 raw NBT、UUID-only hash 与完整差异；旧证据中仅非实体状态和实体数量的范围不自动升级。

十二个代表目标逆序、五目标并发和两个无关宿主的结果一致。非原点前沿 absent/generated/mutated、真实异常、关闭后查询、跨坐标查询和直接 chunk/section/palette 写入的防护不退化。完整地形回退必须有原因、与原 B 的邻区完整状态一致、装饰结束后仍为 TERRAIN 且未修改；未知高度图、bulk 状态容器、BE/NBT、ticks 和元数据读取不得用空数据回答。额外未证明的表面规则和洞穴表面修复继续使用真实同种子 TERRAIN。

只执行一个 FEATURES writer。记录完整邻区数量、实际局部列、噪声批次、biome-only halo 与新旧模块时间，不把减掉完整 ProtoChunk 等同于所有邻区计算消失。验证计时含逐次读取校验及回退快照，且原 B 先执行，不作为生产探索吞吐或无偏 benchmark；两条路径均没有生产分流、存档修改或 LIGHT/SPAWN/FULL。

局部查询的高度快速回答只有在规则保持该高度、对应列洞穴掩码没有触及最高有效格或其上方时才允许。每次实际高度读取仍与原 B 完整地形比较；未知规则和能力、冰山/荒地反馈与未证明洞穴表面修复保留精确回退。额外记录状态采样、实际 material 批次、高度快速回答及完成列高度复用，不改变 RNG 或快照比较规则。

位置查询使用原版小批次与会话内插值角点复用，不允许普通标量采样替代批次。继续逐次对照真实邻区读取与完整 B，并记录输出密度格、批次数及角点输入求值量；不能把输出格减少直接称为 CPU 或探索吞吐提升。未知节点/网格须精确回退，正常、并发和注入失败后的局部采样绑定不得残留。额外的独立原版完整列及密度 raw bits 对照由 A 白桦的 `-Trees -Lightweight` 套件执行，覆盖既有 72 项矩阵与 18 项结构输入。

`-Preparation` 单独写入 `result/preparation/26.3-fabric/host_0/` 与 `host_1/`。19 项覆盖三维度、七类结构、出生区域村庄猫变种查询、模板式含硫植被与两种精确回退；比较原 B 与真实 detached B 交接的完整目标、所有引用 start/piece NBT、八份 stored SPAWN 生物群系 palette、实际单 writer trace，以及 Artifact 还原的原始实体 NBT。先核验独立原版随机字段证据，有限实体验收与 strict hash 分开保留，不修改快照规则。

真实交接只携带轻量读取计数，证明列计数必须为零；生成完整证明的验证查询与轻量计数逐字段一致。证明开关只控制字符串与密度 hash 证据采集，不改变地形、回退和装饰结果，也不跳过 Artifact 校验。

26.3 原版 `PoolElementStructurePiece` 加载时按模板重算包围盒。因此分别严格验证生成边界和原版加载边界：原 B 生成态与按需 B 生成态判等；Artifact 还原态与直接调用原版 structure loader 的控制判等，包括完整 pieces NBT。保留生成态与加载态差异，不把原版转换当作新增忽略字段。这项对照不是玩家进入、物理生命周期或探索性能验收。

`-NativeCat` 保存三个独立普通原版世界的 FEATURES 稳定捕获，结果在 `result/native_cat/26.3-fabric/`。猫属性按唯一 `id` 对齐时值与完整项目必须一致；原始列表和 hash 不修改。`-Preparation` 必须先核验这些证据，再对两个宿主逐项作完整快照与有限实体验收，不能把包含实体的 status-independent hash 称为非实体哈希，也不能直接删除整个实体列表。

`-StructureReuse` 将按需读取套件运行在 B 的会话内结构来源复用上，结果单独写入 `result/structure_reuse/26.3-fabric/`。沿用完整输入、目标、随机序列、逆序、五目标并发、两个宿主和异常门槛。源输入最多覆盖半径九的 361 个零 section 载体；目标拥有或引用的可变结构在装饰前移交出去，之后的邻区读取重新求值为只读输入，不共享正在放置的 pieces。复用仅属于一个目标、一个宿主实例、一个 local seed；结束或异常时清空所有来源，关闭后 resident 必须为零。原 `-Lightweight` 路径仍不复用，保留作 Oracle。

`Run-StructureB` 为当前参考 Target 的来源复用顺序门禁，使用独立 EvidenceLabel，拒绝覆盖已有结果、配置和世界；CheckOnly 默认绑定当前 main/probe、合同组件与 Target 事实，历史指纹只允许只读声明。新 JVM 从本验证原生启动配置引导，在自己的 fresh save 重新导出，私有 DLI 配置与全部端口参数统一使用临时端口，并绑定实际 PID、四逻辑 CPU、低优先级和世界。每个游戏限时 300 秒，失败保存原始证据和世界，不增加预算冒充通过；成功世界移入回收站。

该入口每个无关宿主运行 18 项完整原 B/按需 B 对照、十二代表的逆序和五线程并发，以及非原点物理前沿生成/修改；第二个宿主逐项比较 44 份完整结果。新探针逐项保存原始实体 NBT、UUID-only 快照和完整比较；沿用已批准的有限原生实体验收，包括末地城潜影贝朝向，不再以实体数量代替其它字段。canonical 快照、原生行为和历史有限诊断证据不变。它证明模块层顺序/宿主/并发，不代替 production holder 去重、玩家出生、网络发送或自然实体激活。

该套件同时核对 B 的 stored-biome 输入复用：每个位置只调用一次原版 `createBiomes`，复制独立 palette 到完整回退目标，有限 halo 不超过 25 份，正常、异常和五目标并发结束后 resident 为零；不同 local seed、宿主维度、越界和关闭后的查询必须拒绝。目标完整数据、实际邻区读取、RNG 与回退输出继续严格比较，不因为复用而放宽门槛。

刷怪笼专项使用展示存档主种子 `246813580`，复现日志坐标所在的三个目标；保留原版错误日志，记录 writer、local seed、实际方块和返回的方块实体。B 新旧完整路径共同对照；`Run-MonsterRooms.ps1` 另起三个全新普通 Vanilla 物理世界，以各自 local seed 执行合法的目标 FEATURES 请求，写入 `result/native_monster/26.3-fabric/`。这项诊断不承诺 B 等于原版跨界装饰，也不静默吞掉目标内缺失的方块实体。

该套件另在模板尚未被串行 fixture 预热时执行七类结构、五线程、21 项结构来源对照，写入 `structure-locks.json`。缩小锁的 B 路径必须与保守锁控制的完整 start/pieces NBT 和 references 相同；要塞、下界要塞和末地城的静态生成状态仍受各自独立锁保护。完整 B 数据和逐次邻区读取对照继续执行，不把结构来源相同替代最终生成结果。
