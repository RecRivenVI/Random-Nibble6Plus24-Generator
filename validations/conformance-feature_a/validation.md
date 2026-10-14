# A 邻区矿脉与代表树种贡献验证

## 目标

验证 26.3 的 A 受支持子集：在 B 的目标装饰后，以同一个 local seed、绝对源坐标和固定 Z/X 顺序恢复八个邻区的安全矿脉贡献；另在此结果上执行代表树种的 tree-only pass。矿脉复刻候选遍历与几何，树木复用原版单个 tree/selector 入口并保留临时稀疏状态；A 不调用邻区 `applyBiomeDecoration`、不创建 FEATURES task，也不改变存档或生产分流。

支持原版 `OreFeature`、无空气暴露概率、不替换空气的确定性 block/tag/height replacement 及 all-of、any-of、not 组合、有限 size，以及已审计的 Count/InSquare/HeightRange/Biome/Rarity 放置器。组合仅做无随机消耗与不匹配空气的能力证明，实际逐体素判断仍调用原版 RuleTest。树木支持 bounded straight/blob、无 roots 的树、有限倒木、蜂巢与附着原木的 decorator，以及全部分支均通过能力审计的 RandomSelector；A 另支持有限直干的 Spruce/Pine foliage 与整个 `trees_taiga` selector，不只保留常见分支而丢弃倒木。具体几何/provider 限制见 FEATURES 方案边界。巨型针叶树、落叶、复杂树干/未审计叶簇、roots、未审计放置器/状态 provider 仍明确拒绝；邻区结构、其它植被、湖泊、散布矿和暴露概率矿未重建。

边界外读取同种子纯 TERRAIN 与 stored biome palette，不读取 physical host。现阶段复用 B 的九份完整地形；高度门使用有限 radius 2 内的原版批量密度、含水层及紧凑结构输入，保存高度网格而非完整 ProtoChunk，不执行这些输入的表面和洞穴。原版 stored biome lookup 的 Voronoi 边缘需要 radius 3 内的 biome-only halo，不提供该范围的地形或写权限。

轻量查询不把噪声高度当最终 OCEAN_FLOOR_WG：它给出经证明的高度包络，计入荒地柱体与冰山，在严格上界低于门槛时才回答失败；未知液体/调试配置或无法证明的列仍回退精确 TERRAIN。高度门当前是证明式拒绝加精确回退，不是通用的精确高度 API。完整原版控制明确使用精确地形，不共享这个轻量决策。

树木候选使用真实 `OCEAN_FLOOR` / `WORLD_SURFACE` 反馈；保留的 WG 高度图按原版生命周期冻结，不用即时重算值替代。边界外树干、叶片、BE/NBT、ticks 和后处理仅进入临时稀疏状态，供后续碰撞与随机消耗读取，不修改 B 的邻区或 physical world。边界外基础方块先用原版噪声、表面包络与 density 0 的实际含水层结果证明为空气，其余按需还原完整地形列；不能用噪声高度直接返回假空气。

`LocalTerrainColumns` 不创建邻区方块 sections、generation holders 或 FEATURES task：原版体积的密度批量和 WG 网格保持精确，实际查询列复刻 26.3 表面遍历，复用原版 MaterialRuleContext、规则编译与洞穴 mask 应用。stored-palette、位于邻区的真实结构 start 和地形适配仍由既有紧凑输入供给。未知规则、会移除非空气或产生 BE 的 material 输出、荒地/冰山的先前列高度反馈，以及挖到草地/菌丝后的 topMaterial 梯度未证明，返回明确原因并沿用完整精确 TERRAIN 回退；不捕获真实生成异常继续运行。

## 运行

从仓库根目录执行，服务端需要使用者已在 `local.toml` 接受 EULA：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1
.\gradlew.bat :worldgen_validation:verifyFeatureA --max-workers=2
```

工具限制为四个逻辑 CPU、低优先级、单个高负载 JVM；两个 fresh host 世界依次执行。原版候选与原版 `OreFeature` 的选择性控制路径仅存在于 probe，其原版 decoration loop 在测试中被明确计数为九次，不能混作 A 的生产实现。原点与非原点另运行合法九 writer 全量参考并保存 A/B 到参考的 structured diff；不以选择性控制相同冒充全量参考相同。

每个真实目标还运行同支持集的精确高度 A，对照轻量高度 A 的完整结果与模块成本；两者都只执行一个完整 FEATURES writer，不改采样规则来制造提速。独立高度矩阵读取保留的原版物理证据，不重新装饰、不改写旧证据；输出写在本验证自己的固定结果目录。`-CheckOnly` 只核验这些已有结果，不代替实际运行。

局部准备入口另运行新旧 A 对照，不覆盖上面的原 A 结果：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Lightweight
.\gradlew.bat :worldgen_validation:verifyLightweightFeatureA --max-workers=2
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Trees -Lightweight
.\gradlew.bat :worldgen_validation:verifyLightweightFeatureATrees --max-workers=2
```

新入口复用按需 B，只有目标和 B 真正发生的精确回退地形转交 A，不借用已关闭的 local query。矿脉额外高度读取优先使用已证明的完整局部列，未知列仍生成精确 TERRAIN；保留旧 A 与完整地形原版控制。原 B 先构造的邻区不会在 height proof scope 中触发生成，因此控制侧在 scope 内重新采集实际原版密度与噪声，不能将缺少新采集当成“已验证”。

固定结果分别是 `result/lightweight_ores/26.3-fabric/` 和 `result/lightweight_trees/26.3-fabric/`。矿脉 proofVersion 4、树木 proofVersion 3 的汇总保存列的完整 BlockState 序列 SHA-256、数量、密度 hash 与高度，而不重复展开每次相同的方块字符串；实际对照仍逐方块执行，完整目标快照和 raw entity NBT 保留。此摘要压缩不改变 canonical snapshot 或忽略规则。

树木套件使用同一验证入口和独立固定目录 `result/trees/26.3-fabric/`，不覆盖矿脉或装饰前证据：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Trees
.\gradlew.bat :worldgen_validation:verifyFeatureATrees --max-workers=2
```

实际 stored palette 扫描选择四个白桦目标，另保留 origin/non-origin 无此类贡献的反例。两个不同 host seed 的独立 JVM 分别比较稀疏 A 和完整 ProtoChunk 的选择性原版树木控制，以及逆序请求、五目标并发。控制的完整邻区循环只存在于 probe，结构及未支持 feature 被明确过滤，它不是完整 Vanilla FEATURES parity。每个 source 的 region positional random 与真正原版 region 构造结果比较；post-feature RNG checkpoint 的额外观测 draw 只存在于 probe，默认 A 不消耗该 draw。

160 个内核案例使用注册的 `super_birch_bees`、birch、fallen birch 与 oak，覆盖边、角、失败与已有树的碰撞反馈；交替查询保留的 WG 与可更新的 runtime 高度图。蜂巢概率为 1 的注册配置是专项内核正例，不冒充默认白桦 biome 自然命中的蜂巢比例。保存原始目标快照与完整 BE NBT，不增加 UUID 之外的忽略字段。

针叶树扩展另用固定结果目录 `result/conifers/26.3-fabric/`，不覆盖六项白桦基线。此入口隐含 `-Trees -Lightweight`：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Conifers
.\gradlew.bat :worldgen_validation:verifyFeatureAConifers --max-workers=2
```

它保留原六项并按实际 stored palette 寻找两个 taiga 目标，比较完整准备 A、按需 A 和选择性原版控制，另执行逆序与五会话并发。新增 120 次注册 spruce、pine、fallen spruce 内核调用，必须三种都有成功和目标内改变，不能只靠零贡献 hash 相等通过。每次比较返回值、RNG 消耗/后继值、完整目标方块和 metadata；自然 `trees_taiga` 槽还必须有实际目标写入。轻量读取的空气证书和局部列继续对照完整 TERRAIN。新权限仅属于 A，C 仍拒绝该 selector；单元测试检查巨型树、越界 radius/height/clearance 与未审计分支不能被放行。

树木 proofVersion 2 另对既有独立原版物理输入的 72 项矩阵与 18 项结构专项重算局部列：每项八个边角/内部列比较完整高度范围内的 BlockStates 与四种高度图；无法证明的列实际运行完整精确回退再比较。输入不改写，不冒称本轮新生成 90 个原版世界。四个白桦正例还把每个实际还原的列与本次完整原版 TERRAIN 控制逐方块比较，要求额外完整地形为零，并记录局部列、NOISE 批次和时间。

同一局部列对照先查询四种高度并重复，再逐格读取整列后重查高度，覆盖未构建表面列的严格快速回答和完成列高度复用。`independent-column-work.json` 必须包含真实非零快速回答、复用及状态采样；缺失或只有回退不能代替该正例。原版全深度状态、密度 raw bits 和最终高度仍逐项严格比较。

## 通过标准

- A 实际目标 writer 数为一，邻区完整 FEATURES 次数为零；选择性原版控制只执行支持的矿脉，A 与其完整目标快照、源 feature 选择顺序一致。
- A/B structured diff 给出新增矿脉的数量与类型；有真实贡献正例，不以相同 hash 的无贡献样例充数。
- 目标内实体、BlockEntity、tick、后处理等已有数据保留，八个地形邻区的完整状态不变，没有 physical chunk/entity/tick/POI 污染。
- 打乱目标请求、五目标并发、同坐标不同 seed、两个独立宿主 JVM 的 A 输出一致；binding 清理为零。
- 矿脉内核在边、角、无相交与高度门拒绝情况下，和原版全几何调用的目标方块及下一次 RNG 输出一致；不同 seed/已关闭上下文拒绝使用 B 输入，physical level 与 entity/tick 能力不能逃逸。
- 高度包络的实际密度 raw bits、噪声阶段水面/海床高度与原版完整地形一致，包络不低于最终原版 OCEAN_FLOOR_WG；对保留的 72 项矩阵及 18 项结构专项独立物理证据重新计算 90×256 列，不改写这些输入证据。
- 同支持集的精确高度 A 与轻量高度 A 输出严格一致；记录实际完整地形回退数量、噪声批次数和模块耗时，不能仅以对象数量减少宣称端到端提速。
- 高度组合规则在高度边界、否定分支、嵌套随机规则与条件空气匹配上受限；空气暴露概率与随机 replacement 仍拒绝。支持数量和保守回退次数有实际记录，不宣称所有情况完全消除邻区地形。
- 未支持的类型明确记录；不宣称与全量九 writer Vanilla FEATURES parity，不扩展实体 UUID 忽略规则。
- 工程测试、发行包检查与严格合规通过，旧存档生产路径不改变，不进入 C、D2 或物理交接。
- 树木套件有实际目标叶片贡献；原版 selector 分支、feature RNG 消耗计数/后继值、source region random、完整目标方块和 metadata 一致。
- 稀疏空气证书的实际 density buffer 与完整原版逐 bit 一致，每个证为空气的位置都对照未装饰的完整 TERRAIN；无法证明时保留精确回退并计数。
- 非空气局部列及其 WG 高度也对照完整原版 TERRAIN，不只比较最终树叶；90 项独立输入完整执行，至少 300 列实际通过，存在真实精确回退正例。四个白桦目标有局部列读取且额外完整地形为零；B 的九份地形仍明确保留。
- 临时 BE、tick、post-processing 有正例，但八个 B 地形邻区的完整快照不变；无新增 physical chunks、entity 或 block/fluid scheduler 数据。未知能力、越过有限前沿或关闭后的查询被拒绝。
- 注入真实异常必须继续传播，正常/并发/失败的 context binding 均清零，临时 NoiseChunk 查询关闭，不把 scratch state 带进 Artifact、存档或 gameplay。
- 局部准备入口的 B 快照、A 最终完整目标、矿脉 feature 轨迹和树木 RNG 轨迹必须同时等于本次原 A 与选择性原版控制；记录实际准备/贡献各自的完整回退数量，不能把准备移到贡献阶段后仍宣称删除。
- 针叶树套件需要两个独立宿主各八个目标、120 个针叶树内核案例和既有 160 个白桦等内核案例通过；taiga 槽有正例，C 权限不随 A 扩展，严格切断及物理状态零污染保持。

## 三种设计的同输入比较

这项比较补充里程碑的横向验收，不扩大 A 支持范围、不改变任何生成器算法。它不是上述原版等价门禁的替代，也不是生产吞吐或人工画面验收。

### 运行

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Compare
.\gradlew.bat :worldgen_validation:verifyFeatureDesignComparison --max-workers=1
```

结果固定写在 `result/comparison/26.3-fabric/`。12 个固定目标覆盖原点、派生种子非原点、两处白桦、针叶林、村庄边缘、矿井、试炼密室、海底神殿、要塞、下界要塞与末地柱。两个独立宿主 JVM 依次运行，四逻辑 CPU、低优先级、单个会话；每个方案先预热，再对每个目标轮换 A/B/C 执行顺序并各取三次样本。第二宿主反转顺序，避免每次固定让某个方案先运行。

总耗时包括 context 建立、装饰前准备、按需回退和装饰，不包括快照观测与证据写盘。只读 probe 同时统计实际完整 TERRAIN 调用；包含贡献内部的回退，不把 C 的目标准备统计误当整个方案的完整地形开销。该数量不包含局部噪声批次或 biome-only carrier，因此不是“所有邻区工作”的计数。保留每次样本，不将中位数包装成客户端 chunks/s。

每个目标另执行一次保留的 Ordering 1 九 writer reference，记录 A/B/C 到它的方块类别、首个差异与 metadata 差异。此参考仍是已有 isolated V1，不冒充本轮新建的独立 native physical world。方案之间不同是设计结果，不以任何一个差异更少就宣布更正确；计时也不包括真实物理接收、光照、发送或客户端。

### 通过标准

- A/B/C 的完整装饰前目标 hash 一致；A 的 B 准备结果必须等于独立 B，同一方案三个样本和两个宿主完整目标 hash 一致。
- 三条实验路径各只有一个完整 FEATURES writer，不执行邻区完整 FEATURES；reference 明确为九 writer。目标停在 FEATURES，context 清理为零，physical chunk、实体及两类 tick 计数不变。
- A 保留矿脉与树木支持集合的并集、未支持原因和邻区结构未重建的标记；状态始终是部分 A，不因比较完成而升为全量 parity。
- C 保留完整贡献 NBT；同策略重复和跨宿主贡献完全一致。该输出只是待接收数据，不能计为 physical 邻区已改变。
- 保存完整目标快照、原始实体字段及目标内 BE/tick/后处理数量；不增加 UUID 之外的忽略字段。溺尸与 Shulker 的独立失败门禁保持原样，这 12 项不代替那些未通过的实体重复性证明。
- 至少有 A 目标内容改变和 C 实际跨界贡献正例；输出状态为 `PASS_COMPARISON_ONLY`，不代表里程碑、C physical 接收或人工视觉通过。
