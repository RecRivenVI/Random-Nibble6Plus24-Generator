# B 方案严格切断验证

## 目标

验证 26.3 的独立 B 基线：仅目标执行原版 FEATURES，越界方块、BlockEntity、实体、tick 与后处理没有邻区写权限，目标内完整数据保留。使用真实同种子 TERRAIN 输入，不接入生产分流、不改变已有 Ordering 1/2 存档。此验证不声称旧九 writer 装饰 parity。

## 运行

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -Lightweight
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -Preparation
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -StructureReuse
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -NativeCat
```

两个独立宿主进程串行运行，宿主种子分别为 17、23；四 logical CPU、低优先级、单个高负载 JVM。运行只使用自己的 instance/result，不读取日常实例；服务端需要已明确接受 EULA。固定结果在 `result/current/26.3-fabric/host_0/` 与 `host_1/`，正常生成的验证世界结束后移入回收站。

## 通过标准

15 个真实目标覆盖 Overworld、Nether、End 与七类结构；每次实际观察到的目标 writer 为 1，全部八个邻区的完整前后快照相同且停在 TERRAIN，目标严格停在 FEATURES。十个代表目标正序、逆序、固定打乱与五目标并发、不同 JVM/host seed 得到同样完整 canonical 数据、reference start NBT 投影、feature seed 序列和可见 biome 集合；同坐标不同种子同时运行不得污染。实体 canonical 只沿用 UUID 例外，不扩大忽略字段。

目标内真实 BE、矿车、EndSpike 水晶、block/fluid ticks 与后处理必须有正例；pending NBT 的自然正例未命中时明确报告，另由单元测试验证其完整保存与越界拒绝。八方向及边角的 chunk/section/NBT/BE/实体/乘客/tick/后处理防护通过，远于读取前沿的 region tick 被丢弃而不是查询外部 chunk。

非原点目标在物理前沿 absent/generated/mutated 时完整结果一致，宿主已有数据不变，无新增 physical chunk/entity/tick，虚拟方块写入的 POI 更新被抑制。正常、并发及注入异常后的 binding 为零，真实异常继续传播；已有生产分流和普通世界不调用 B 模块。

此基线仍保留八份精确只读 TERRAIN，而不是仅一个完整 ProtoChunk 的性能宣称；不运行邻区 FEATURES，不进入 LIGHT/SPAWN/FULL。End City 的 Shulker Rotation 保持原版行为，该目标只覆盖实体/BE/结构与越界隔离，不冒充全部实体字段的跨进程确定性。树木/装饰完整视觉、物理交接与性能对照不属于本项验证。

`-Lightweight` 单独写入 `result/lightweight/26.3-fabric/host_0/` 与 `host_1/`，不覆盖原 B。18 个目标包含原 15 项、白桦及两个已知局部算法回退场景；每次先运行原 B，再运行按需 B。逐次比较所有实际邻区方块、流体、四种高度及 stored biome 读取，包括原版矿脉的 `BulkSectionAccess` 和装饰前 biome section 扫描；比较最终完整目标状态、引用 start NBT、feature seed 序列、写入拒绝与已审计 physical escape 计数。End City 仍只对非实体完整状态和实体数量作严格断言，完整 canonical 实体 NBT 原样保留、不新增 ignore 字段，不宣称 Shulker Rotation 等价。

十二个代表目标逆序、五目标并发和两个无关宿主的结果一致。非原点前沿 absent/generated/mutated、真实异常、关闭后查询、跨坐标查询和直接 chunk/section/palette 写入的防护不退化。完整地形回退必须有原因、与原 B 的邻区完整状态一致、装饰结束后仍为 TERRAIN 且未修改；未知高度图、bulk 状态容器、BE/NBT、ticks 和元数据读取不得用空数据回答。额外未证明的表面规则和洞穴表面修复继续使用真实同种子 TERRAIN。

只执行一个 FEATURES writer。记录完整邻区数量、实际局部列、噪声批次、biome-only halo 与新旧模块时间，不把减掉完整 ProtoChunk 等同于所有邻区计算消失。验证计时含逐次读取校验及回退快照，且原 B 先执行，不作为生产探索吞吐或无偏 benchmark；两条路径均没有生产分流、存档修改或 LIGHT/SPAWN/FULL。

局部查询的高度快速回答只有在规则保持该高度、对应列洞穴掩码没有触及最高有效格或其上方时才允许。每次实际高度读取仍与原 B 完整地形比较；未知规则和能力、冰山/荒地反馈与未证明洞穴表面修复保留精确回退。额外记录状态采样、实际 material 批次、高度快速回答及完成列高度复用，不改变 RNG 或快照比较规则。

`-Preparation` 单独写入 `result/preparation/26.3-fabric/host_0/` 与 `host_1/`。19 项覆盖三维度、七类结构、出生区域村庄猫变种查询、模板式含硫植被与两种精确回退；比较原 B 与真实 detached B 交接的完整目标、所有引用 start/piece NBT、八份 stored SPAWN 生物群系 palette、实际单 writer trace，以及 Artifact 还原的原始实体 NBT。先核验独立原版随机字段证据，有限实体验收与 strict hash 分开保留，不修改快照规则。

真实交接只携带轻量读取计数，证明列计数必须为零；生成完整证明的验证查询与轻量计数逐字段一致。证明开关只控制字符串与密度 hash 证据采集，不改变地形、回退和装饰结果，也不跳过 Artifact 校验。

26.3 原版 `PoolElementStructurePiece` 加载时按模板重算包围盒。因此分别严格验证生成边界和原版加载边界：原 B 生成态与按需 B 生成态判等；Artifact 还原态与直接调用原版 structure loader 的控制判等，包括完整 pieces NBT。保留生成态与加载态差异，不把原版转换当作新增忽略字段。这项对照不是玩家进入、物理生命周期或探索性能验收。

`-NativeCat` 保存三个独立普通原版世界的 FEATURES 稳定捕获，结果在 `result/native_cat/26.3-fabric/`。猫属性按唯一 `id` 对齐时值与完整项目必须一致；原始列表和 hash 不修改。`-Preparation` 必须先核验这些证据，再对两个宿主逐项作完整快照与有限实体验收，不能把包含实体的 status-independent hash 称为非实体哈希，也不能直接删除整个实体列表。

`-StructureReuse` 将按需读取套件运行在 B 的会话内结构来源复用上，结果单独写入 `result/structure_reuse/26.3-fabric/`。沿用完整输入、目标、随机序列、逆序、五目标并发、两个宿主和异常门槛。源输入最多覆盖半径九的 361 个零 section 载体；目标拥有或引用的可变结构在装饰前移交出去，之后的邻区读取重新求值为只读输入，不共享正在放置的 pieces。复用仅属于一个目标、一个宿主实例、一个 local seed；结束或异常时清空所有来源，关闭后 resident 必须为零。原 `-Lightweight` 路径仍不复用，保留作 Oracle。

该套件另在模板尚未被串行 fixture 预热时执行七类结构、五线程、21 项结构来源对照，写入 `structure-locks.json`。缩小锁的 B 路径必须与保守锁控制的完整 start/pieces NBT 和 references 相同；要塞、下界要塞和末地城的静态生成状态仍受各自独立锁保护。完整 B 数据和逐次邻区读取对照继续执行，不把结构来源相同替代最终生成结果。
