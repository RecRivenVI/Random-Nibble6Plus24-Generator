# 世界生成验证工具

`worldgen_validation` 组件保留现有 PowerShell 编排与分析器；必须在游戏中运行的独立探针位于 `src/probe`，只随验证实例加载。产品直接引用的默认关闭验证类继续保留在产品中。

## 运行

各验证的 `validation.md` 提供仓库根目录命令。服务端首次执行前由使用者在本机 `local.toml` 声明 EULA 同意，不从日常实例复制授权文件。

26.3 Fabric 的第三方群系验证见 [BOP 本地种子隔离](../../validations/compatibility-bop/validation.md)。其模组输入只在专用实例中加载；独立普通原生世界与无关种子宿主分别启动，不将原版矩阵的历史结果当作 BOP 兼容证据。

客户端启动前使用下列准备工具；它调整窗口尺寸和音量，保留视距、模拟距离和存档，并拒绝在本仓库客户端运行时修改设置。自动客户端的分辨率在对应 validation.toml 声明为 1920×1080；人工客户端的 2560×1440、玩家名 RecRivenVI 与附加模组放在本机 local.toml，不改变服务端验证或产品依赖。

```powershell
python components/worldgen_validation/scripts/development/prepare_client.py --target 26.3-fabric --mode automatic --validation performance-worldgen
python components/worldgen_validation/scripts/development/prepare_client.py --target 26.3-fabric --mode acceptance
```

自动测试全局声音关闭；人工验收全局声音 50%、音乐关闭。两类实例互不借用；Custom Skin Loader 与 Zume 只在人工实例按上游声明的 Minecraft/Loader 兼容范围安装，不污染独立原版控制。

人工附加模组尚不能替代无附加模组的性能基线：若触发未证明模组组合的精确兼容回退，记录实际回退与模组清单，不放宽能力守卫来维持快路径。

```powershell
.\gradlew.bat check verifyRelease
```

## 数据

根目录格式配置保持模板的文本输入集合，但在文件树遍历前排除实例、结果和其它不属于根格式的目录，避免检查输入时枚举大量本地运行数据。范围一致性由工具组件的布局回归核对，模板文件与证据均不改写；Windows 脚本仍由组件的 CRLF 格式器处理。

验证只使用各自的 `instance/<target>/`。脚本默认仍选择 26.2；验证配置登记了 26.3 时，可以显式选择：

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
```

新结果写入 `result/current/<target>/` 固定位置，不在版本之间复用原生证据；`result/legacy/` 是不可改写的历史证据，不等于当前测试通过。临时 profiler 文件和世界不进入发行 JAR。26.2 的 SURFACE/CARVERS golden 与实验性轻量研究探针只在原 Target 运行；26.3 的合并 TERRAIN 及 FEATURES 必须使用同版本的原生对照。

## 装饰前独立基线

[26.3 装饰前对照](../../validations/conformance-prefeatures/validation.md)使用独立 JVM、全新普通原版世界与无关种子的参考宿主，捕获四个实际状态检查点。它在任何 FEATURES 之前结束；不复用已经装饰过的 TERRAIN，也不把测试 hook 放入发行 JAR。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1
.\gradlew.bat :worldgen_validation:verifyPreFeatures
```

完整矩阵为 72 项；`-Suite smoke` 只测四项，不替代完整验收。工具先用模板验证任务准备运行环境并导出真实 JVM 启动配置，再串行启动每项的独立进程，避免为每个 fixture 重复启动 Gradle；仍使用原版 physical scheduler，没有第二套原版生成实现。

## 紧凑结构输入

1.21.1 NeoForge 与 1.20.1 Forge 的初次适配由[独立原生结构输入](../../validations/conformance-structure_inputs/validation.md)单独验证，仅证明元数据和地形适配，不借用较新版本的完整地形结果。1.20.1 的[原生种子与存档合同](../../validations/conformance-seed/validation.md)在真实 Forge 生命周期执行，避免普通 JUnit 缺少加载器注册表变换；不把其通过当作生成流水线已可用。

同一验证的结构套件另写固定 `result/compact/26.3-fabric/`，不覆盖普通矩阵。它主动寻找七类真实 start、邻区投影和纯地形适配 halo，使用全新普通世界独立复算；consumer 关闭创建上下文后，以新的紧凑结构输入替换目标的原版结构消费者。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures
.\gradlew.bat :worldgen_validation:verifyCompactStructures
```

验收包括完整结构 NBT 与 references、可持久化 start 投影、逐 bit Beardifier 密度、BIOMES 和纯 TERRAIN。Probe 不进入产品 JAR；实际结构算法与模板锁不替换，尚未将新模块接入旧世界的生产请求。结果及限制见[生成实现状态](../project/status-generation.md)，当前开发范围见[FEATURES 里程碑](../project/milestone-features.md)。

## 轻量生物群系与地形

轻量地形消费者不运行依赖调度图：目标使用完整 `ProtoChunk`，八个邻区只保留原版批量生成的 biome palette，结构由紧凑输入提供。原版 `buildTerrain` 的 null carver region 分支直接求值 source biome 和洞穴，仅写目标；表面仍读取已经生成的 palette，不改成 uncached 点采样。模块没有接入生产或存档分流。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Lightweight
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures -Lightweight
.\gradlew.bat :worldgen_validation:verifyLightweightTerrain :worldgen_validation:verifyLightweightStructureTerrain
```

固定结果目录分别为 `result/terrain_matrix/26.3-fabric/` 和 `result/terrain_structures/26.3-fabric/`，快速四项另写 `result/terrain_smoke/26.3-fabric/`，不覆盖前两个阶段的证据。独立原版和轻量消费者比较四个状态检查点、九份 palette、原版实际密度 buffer 的 raw float bits、填充后/表面后/洞穴后的完整目标数据、289 个 carver source biome 的调用序列和临时 CarvingMask。Probe 只读实际容器与 buffer，不重算密度或改 Chunk 状态。

## B 严格切断基线

[B 验证](../../validations/conformance-feature_b/validation.md)只测试独立模块，不改现有生产路径或世界身份。目标复用紧凑结构与轻量地形入口，八个邻区保留真实同种子 TERRAIN 供原版装饰读取，但只有目标 writer 执行 FEATURES。返回的邻区 section、BE/NBT、ticks、实体与后处理均不能提供越界写权限，未知 mutable API 与 physical escape fail closed。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1
.\gradlew.bat :worldgen_validation:verifyFeatureB
```

按需局部读取 B 的独立新旧对照使用下列命令；原 B 的固定结果不被覆盖：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -Lightweight
.\gradlew.bat :worldgen_validation:verifyLightweightFeatureB
```

结果在 `conformance-feature_b/result/lightweight/26.3-fabric/`。原版装饰入口的八邻区 biome 扫描使用精确 stored palette；方块、流体、四种高度及批量矿物读取使用已验证的局部列。未证明的规则、bulk 容器、BE/NBT、tick 与元数据能力按需回退真实同种子 TERRAIN，不用空数据替代。每次实际读取与原 B 校验，最终目标完整数据、RNG/结构/写入轨迹和回退邻区的不可变性另比较；End City 的非实体诊断不升级为完整实体 parity，也不扩展 canonical ignore。

两次独立宿主进程串行运行，均限制四 logical CPU、低优先级；每个进程覆盖 15 个真实目标、十个目标的逆序/打乱/五目标并发，包含同坐标不同种子的同时请求，以及物理前沿已生成/被修改、异常清理。固定结果写入 `conformance-feature_b/result/current/26.3-fabric/host_0/` 与 `host_1/`，保存完整目标快照、raw entity UUID、全部 referenced start 的 NBT 投影和 feature/biome 诊断。

它不是九 writer FEATURES parity 或生产性能验收；自然 pending NBT 正例未命中、End City 原版 Shulker Rotation 未确定化等边界见[生成实现状态](../project/status-generation.md)。验证世界在结束后移入回收站，原日常存档不变。

## A 邻区矿脉贡献

[A 验证](../../validations/conformance-feature_a/validation.md)实现首个受支持子集，不代表完整 A 已完成。B 的目标装饰完成后，按绝对 Z/X 顺序求值八个同种子 source 的安全矿脉；复用原版 feature 选择、placement modifier 和随机派生，只复刻深度优先候选遍历与投向目标的矿脉几何，不调用邻区完整装饰入口。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1
.\gradlew.bat :worldgen_validation:verifyFeatureA
```

十个目标覆盖三维度、派生种子、边界与真实结构环境。两个 fresh host JVM 串行执行；比较 B、A、选择性原版矿脉控制，并验证目标打乱请求与五目标并发。原点和非原点另保存旧合法九 writer 参考的差异，它仅用于测试，不参与 A 实现。固定结果写到 `conformance-feature_a/result/current/26.3-fabric/`。

支持矩阵及未支持原因随结果保存。选择性原版控制保留原版 feature 循环和矿脉实现，但过滤非支持类型，不是独立 physical Vanilla 全量 FEATURES parity。目标现有附属数据完整保留；邻区仅提供纯 TERRAIN 与 stored palette。高度门用原版批量噪声、含水层与结构适配建立保守包络，不能证明时仍查询精确地形并计数，不把上界当精确高度。

验证另读取既有独立 physical Oracle 的 72 项地形与 18 项结构输入，重新计算 23,040 列，逐项比较实际密度 raw bits、噪声水面/海床和最终高度包络。输入证据保持原样，新的复算结果只写在 A 的目录；它不等于本轮重新启动了 90 个原版控制世界。

代表树种套件保留上述矿脉结果，另验证白桦选择器、倒木、树叶距离及蜂巢。A 复用原版单个 feature/selector，临时稀疏状态只保留需要反馈的写入，最终只有目标可以改变；不是运行周围八套完整 FEATURES。高度读取区分原版保留的 WG 图与可更新的 runtime 图，未知能力和基础方块使用明确拒绝或精确回退，不能返回近似高度/假空气。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Trees
.\gradlew.bat :worldgen_validation:verifyFeatureATrees
```

树木结果固定写在 `conformance-feature_a/result/trees/26.3-fabric/`；两个独立宿主、六个目标和 160 个内核案例比较稀疏 A 与完整 ChunkAccess 的选择性原版控制，检查随机数、顺序、并发、严格切断和异常清理。它不是全量物理原版 FEATURES 对照或生产性能验收；其它树种、roots 与落叶等未支持项以能力矩阵为准。

局部地形列的独立性验证使用保留的 90 项纯 TERRAIN 物理原版输入，每项八个边角/内部列逐方块与四类高度图比较，未证明的列走完整精确回退并单独计数。默认白桦的四个正例必须不再生成额外完整地形；查不到证明不得返回空气。噪声仍按原版完整批量体积求值，只有实际请求列执行表面/洞穴应用，不把减少地形对象等同于探索性能提升。验证同时拒绝越界、未审计高度图与已关闭的 query/owner。

## A 复用按需 B

A 的新准备入口分别运行矿脉和白桦的新旧路径与选择性原版控制，不覆盖原 A 证据：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Lightweight
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Trees -Lightweight
.\gradlew.bat :worldgen_validation:verifyLightweightFeatureA :worldgen_validation:verifyLightweightFeatureATrees
```

转交贡献阶段的仅是目标、实际精确回退地形和结构上下文；已经关闭的 B 局部查询不复用。矿脉补充高度优先精确局部列，读取不能证明时仍明确回退。实际减少量、完整数据与随机对照见本项验证结果，不将对象减少等同于生产探索性能提升。

A 的针叶树扩展使用独立结果；保留上述六项并增加两个真实 taiga 目标、120 次 spruce/pine/倒云杉内核对照。C 的跨界能力另外由下方 C 套件验证：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Conifers
.\gradlew.bat :worldgen_validation:verifyFeatureAConifers
```

固定输出为 `conformance-feature_a/result/conifers/26.3-fabric/`。同样四个逻辑 CPU、低优先级、两个宿主 JVM 串行运行；不是生产探索性能测试。支持边界与当前证据见 A 验证及生成状态。

## C 树木贡献与独立接收

[C 验证](../../validations/conformance-feature_c/validation.md)复用按需 B 和树木局部查询，调用实际注册的白桦、橡树、有限松树/云杉、倒木及蜂巢内核，与独立完整地形副本比较目标、外部方块、原始 NBT、ticks、后处理和随机消费。不执行周围完整 FEATURES，也不向 physical 世界写入贡献。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1
.\gradlew.bat :worldgen_validation:verifyFeatureC
```

结果写入 `conformance-feature_c/result/current/26.3-fabric/`。两个受限独立 JVM 使用不同 host seed，后者直接读取前者的贡献和接收 NBT；接收者以自己的另一 local seed 完成 B 的数据为基底。验证等待、重复、正逆序、四线程投递、删除和保护空气，原始树木 shape update 的无所属写入 tick 保留证据但不授予接收权限。

上述内核/接收证明不包含真实玩家事件、LevelChunk 接收或现有世界保存重载。构造的冲突与玩家挖空输入只证明接收协议，不能代替自然装饰、物理副作用或人工视觉验收。

完整目标 C 流程使用独立套件，不在 B 装饰完成后再补跑一轮 FEATURES：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Flow
.\gradlew.bat :worldgen_validation:verifyFeatureCFlow
```

输出固定为 `conformance-feature_c/result/flow/26.3-fabric/`。原版目标 loop 只运行一次，整项已审计 placed feature 使用稀疏树木反馈，其余走严格 B。对照保留八份完整只读 TERRAIN，但也只运行一个目标 FEATURES；不以该控制冒充完整 Vanilla 九 writer parity。两个独立宿主比较完整目标、随机位序列、原始跨界贡献，并运行逆序、三会话并发、异常清理与权限检查。真实 pending NBT、自然蜂巢和外部附属操作数量分别记录，零值不称自然正例。

完整 C 来源与相邻 C 基底的有限前沿另行串联验证：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Frontier
.\gradlew.bat :worldgen_validation:verifyFeatureCFrontier
```

输出为 `conformance-feature_c/result/frontier/26.3-fabric/`。使用同一个 master seed 和 Seed V1 派生各相邻目标，不修改现有 profile。自然跨界来源与直接接收者均执行完整 C，按需输入与完整地形控制严格比较；再次外溢的贡献不会递归生成更远目标。接收仍是 detached journal，按位置的独立参考检查冲突与附属能力；独立 JVM 重读 NBT，不代表 integrated server 的保存重载。

当前该串联套件因海底废墟溺尸的原版随机属性差异保持失败；没有放宽完整快照规则。失败时额外保存同输入的完整控制重复、原 B 重复、原始实体 NBT、随机轨迹和逐字段 diff，具体位置见验证说明。现有 `-Flow` 与内核/接收套件仍独立运行，不能用它们替代此项未通过的证明。

固定输入的 C 接收算法可单独验证，不改变上述完整生成等价门槛：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Replay
.\gradlew.bat :worldgen_validation:verifyFeatureCReplay
```

输出为 `conformance-feature_c/result/replay/26.3-fabric/`。生产者只生成一次有限前沿并冻结输入，消费者在新的无关宿主 JVM 中仅重读磁盘；完整快照及原始实体 NBT 保持原样。验证接收记录的独立参考、并发/延迟/重复投递、保护、删除和重载；`PASS_FROZEN_INPUT_REPLAY_ONLY` 不能用于宣称 `-Frontier`、原版随机实体重复性或物理应用已通过。

原版实体随机性另由三个 fresh ordinary world 独立诊断，不使用上述冻结输入或 C 生成器：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -NativeRepeat
.\gradlew.bat :worldgen_validation:verifyFeatureCNativeRepeat --max-workers=1
```

结果在 `conformance-feature_c/result/native_repeat/26.3-fabric/`；相同 seed、目标和合法九 writer procedure，普通 physical ChunkMap 生成并精确停在 FEATURES。保存完整快照、原始实体和逐字段差异。诊断确认不确定性时，完整 parity 仍为 FAIL；不把成功取得反例当成 C 通过，也不新增 canonical 忽略字段。

## C 有限实体验收

使用者批准保留原版随机行为后的独立门禁：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -AcceptedFrontier
.\gradlew.bat :worldgen_validation:verifyFeatureCAcceptedFrontier
```

结果单独写入 `conformance-feature_c/result/accepted_frontier/26.3-fabric/`，原 `frontier` 的严格失败不覆盖。核验三个独立普通原版重复证据后，按[有限验收规则](../design/architecture-features.md#原版随机字段的有限验收)比较 C 前沿；每个扫描和生成目标都保留完整快照、原始实体和跨 JVM 比较。其余 metadata、随机轨迹与贡献继续严格判等，不更改现有 hash 或原版随机源。

末地城使用独立规则和独立输出，不把潜影贝例外加进所有实体的默认比较：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -NativeShulker
.\gradlew.bat :worldgen_validation:verifyFeatureCNativeShulker
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -AcceptedShulker
.\gradlew.bat :worldgen_validation:verifyFeatureCAcceptedShulker
```

前者在三个 fresh 原版 JVM/save 重复相同 End City，记录仅 UUID 和水平朝向不同；后者在两个无关宿主验证原 B、按需 B 与完整 C，包含同坐标另一种子、逆序、三会话并发及跨 JVM 完整快照。输出分别为 `native_shulker`、`accepted_shulker`；原 21 项严格 C 流程与原 B 失败诊断保留。规则、范围检查与未完成物理边界见[方案边界](../design/architecture-features.md)。

## C 物理接收机制

C 的封闭贡献批次另有物理机制验证，先运行上述内核入口取得当前真实输入：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Physical
.\gradlew.bat :worldgen_validation:verifyFeatureCPhysical
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -PhysicalHives
.\gradlew.bat :worldgen_validation:verifyFeatureCPhysicalHives
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -PreLight
.\gradlew.bat :worldgen_validation:verifyFeatureCPreLight
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -EditProtection
.\gradlew.bat :worldgen_validation:verifyFeatureCEditProtection
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Recovery
.\gradlew.bat :worldgen_validation:verifyFeatureCRecovery
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Append
.\gradlew.bat :worldgen_validation:verifyFeatureCAppend
```

结果固定为 `conformance-feature_c/result/physical/26.3-fabric/`。两个 JVM 依次生成/保存和重开同一个 seed 23 普通世界，使用真实 FULL 区块接收其它种子的树木贡献；没有 C 基底物化或生产分流。验证重复、前后挖空、主线程权限、过期基底、缺失邻区和正常停服重载。接收批次激活后若内容变化明确失败，不假装可以撤回已经执行的副作用。回执只在结果目录，不能据此宣称 C 的产品存档、动态冲突或崩溃恢复已实现。

蜂巢专项另使用 seed 1、固定输出 `physical_hives`，从相同真实内核输出筛选包含蜂巢的完整包，补齐 BE payload 和存储封装逐字段检查、实例数、重复投递与正常停服重载。原 `physical` 无蜂巢的证据不覆盖；不是完整 C 世界或实际玩家蜂巢互动测试。

`-PreLight` 不依赖内核结果，另生成每个目标均使用自身 local seed 的完整 C patch；批次接收完成后写入真实临时存档。普通 ChunkMap 读盘推进至 INITIALIZE_LIGHT，再显式调用原版 LIGHT 步骤，避免普通 LIGHT ticket 继续推进 SPAWN/FULL。第二 JVM 重开存档，严格检查内容、所属结构 seed、POI 与整批完成后的光照，C 生成必须为零。输出固定为 `prelight`；这是阶段交接验证，不是 C 生产调度、动态迟到贡献或玩法验收。

`-EditProtection` 的固定输出是 `edit_protection`。它在真实物理区块上自动观测原生方块修改，而非调用方手写保护标记；同一临时世界的第二 JVM 验证保护空气、已接收后挖空、重复回执与此前尚未应用的真实树木贡献首次到达。普通原版基底不冒充 C，使用 API 模拟修改不冒充人工游戏输入。动态冲突、BE 内容交互和崩溃事务仍不在此门禁内。

`-Recovery` 依次运行四个独立 JVM，用同一 fresh C pre-light patch 验证两次明确进程中断、恢复和已完成重载。输出固定为 `recovery`；指定 exit 73/74 必须同时具有对应阶段证据，否则仍失败。不会进入 SPAWN/FULL，不将激活前交接事务用于玩家已修改区块。完整通过条件见 [C 验证](../../validations/conformance-feature_c/validation.md#通过标准)。

`-Append` 使用同一组真实内核贡献，依次运行正序首次接收/重启、逆序首次接收/重启四个 JVM。固定输出为 `append`；两个临时普通原版世界均为 seed 1，不是 C 基底。每个包单独发布并尝试主线程增量接收；正序须有多波新增方块，逆序须复现真实迟到冲突并拒绝整批写入。保护挖空及实际修改后的蜂巢 payload，独立重启核对完整存档数据和待处理回执。只证明安全追加和冲突拒绝，不证明全部到达顺序产生相同物理世界、动态冲突替换或已 FULL 崩溃事务。

## 三种 FEATURES 设计的横向比较

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_a/Run-FeatureA.ps1 -Compare
.\gradlew.bat :worldgen_validation:verifyFeatureDesignComparison --max-workers=1
```

12 个共同目标、两个独立宿主，A/B/C 每项各运行三次并轮换次序。比较完整装饰前输入、同策略重复结果、A 的支持/遗漏集合、C 的原始跨界贡献、完整 TERRAIN 实际调用量，以及目标到保留九 writer reference 的差异。计时排除快照和写盘，不代表生产探索性能；C 接收和人工视觉仍未验收。结果与通过标准见 [A 验证的横向比较](../../validations/conformance-feature_a/validation.md#三种设计的同输入比较)。

## 新地图试验

单区块装饰的生产生命周期验证使用 `Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -VerifyReload -Mutate`，默认参数仍验证 Ordering 1。新规则只在 26.3 实现，不用它替换旧合同的原生 parity。

人工地图准备工具需要 Python 3，只读取本项目已经正常保存关闭的 26.3 客户端世界身份。它创建新存档，保留世界种子并把三个维度显式设为 Ordering 2，启用 Creative 和命令；不复制地形、玩家或结构索引，不覆盖已有目录。

```powershell
python components/worldgen_validation/scripts/development/prepare_target_only_world.py --source "instances/26.3-fabric/client/saves/Mosaic 26.3 Upgrade"
.\gradlew.bat :version:26.3-fabric:runClient
```

这是开发准备工具，不是验证实例中的探针；地图名默认为 `Mosaic Target Only Fresh`。实际游戏中的进入、探索与性能体验由使用者完成，不用 GUI 自动化代替验收。

## 按需 B 里程碑验收

完整 detached 交接与物理流程分开运行，均不迁移旧世界。实验客户端使用产品、不加载 probe；只有新 `development_b` 地图需要显式 opt-in。后续验收不再要求使用者手动游玩：使用验证实例中的游戏内探针自动创建专用世界、加载真实区块并采样；不能自动覆盖的玩家或渲染行为仍标记未验证，不用 CLI 数据冒充。禁止 GUI 自动化；只有核实命令行、实例目录及启动记录属于本线程的游戏进程，才可直接正常关闭并重启，无须再次询问。

26.3 的物理计划使用反向依赖与分状态引用计数。专项单元测试以全扫描测试 Oracle 逐操作对照最高状态与 owner 集合，覆盖重复替换、失败清理、并发访问、跨世界关闭与无关计划不参与完成查询；不改变 Vanilla 依赖形状。运行验证继续使用下面的真实生产请求、修改后重载和既有关闭取消验证，不把单元测试等同于客户端体验通过。

B 准备中的结构来源复用只属于一个目标生成，包含原版求值后的空结果，最多半径九的 361 个无地形载体。装饰前将目标拥有和引用的可变结构移交出去，邻区随后需要相同来源时重新原版求值，再只读复用；结束时释放全部来源。`Run-FeatureB.ps1 -StructureReuse` 对每次实际读取与原 B 作比较，覆盖逆序、并发与宿主隔离；`-Preparation` 继续核验 detached 交接。独立装饰前结构 Oracle 使用 `Run-PreFeatures.ps1 -Suite structures -Lightweight -StructureReuse`，完整保留原来不复用的入口。测试开关仅在 probe 中，不进入日常客户端。

结构短锁回归包含在 `-StructureReuse`：在串行套件预热模板前，用五线程执行七类结构的 21 项来源对照，再与保守整段锁的原版入口比较完整 start/pieces NBT 和 references。实际缩小锁的入口必须被观测到，不以回退路径成功冒充通过；结果分别写入两个宿主的 `structure-locks.json`。单元测试检查三种静态生成状态的独立 JVM 锁和嵌套扩展 pool element 的保守回退。

日常 B 交接使用独立的轻量读取计数，不构造整列方块字符串证明或计算证明专用密度 hash。完整 `metrics()` 证据只用于明确启用证明采集的验证查询；未采集时请求完整证明立即失败，不能以空列表冒充通过。`-StructureReuse` 检查轻量计数与完整证据中的数值一致；`-Preparation` 继续比较原 B 的完整结果与真实交接，并断言交接的证明列计数为零。地形查询、精确回退、Artifact 完整性检查和旧存档分流不变。

局部查询先用实际 stored palette 审计表面规则；无法证明时直接精确回退，不先重复构建密度。支持的输入按读取位置请求原版小体积密度批次，复用查询内稀疏整高度角点列，保留插值的原版 Y 前缀；密度与含水层/表面分别持有采样上下文，只恢复已证明共享的二维缓存。未证明的缓存耦合、节点与网格继续完整回退，不使用标量近似。高度扫描找到水面和海床后停止，表面规则按局部体积编译，邻近梯度高度按需计算。只问高度时，必须证明规则保持对应高度且洞穴掩码没有触及最高有效格或其上方，才复用精确噪声高度；否则构建完整精确列。计数记录实际密度输出格、角点输入、批次数、原始状态采样与高度复用，不记录新的存档权威。

`Run-FeatureA.ps1 -Trees -Lightweight` 对既有 90 项独立原版输入先查四种高度、重复查询，再逐格读取整列并重新比较高度和密度 raw bits；无法证明的列执行真实完整回退。这个输入来源与本轮新建原版世界分别报告，不把重算查询称为重新生成了 90 个控制世界。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_b/Run-FeatureB.ps1 -Preparation
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate -KeepWorld
python components/worldgen_validation/scripts/development/prepare_b_world.py --source "validations/integration-production_lifecycle/instance/26.3-fabric/server/phase3c2-production-123456789-minecraft-overworld-125--37-features-2-lightweight-b"
$env:JAVA_TOOL_OPTIONS = '-XX:ActiveProcessorCount=4 -Drandomnibble6plus24generator.developmentB=true'
.\gradlew.bat :version:26.3-fabric:runClient --max-workers=1
```

地图默认名为 `Mosaic B Milestone Fresh`，仅复制关闭且已验证的世界身份，不复制地形、玩家或结构索引、不覆盖同名世界。脚本生成实例数据，不能手工编辑现有世界数据。

`-Preparation` 使用独立原版猫证据；首次没有结果时先运行 `Run-FeatureB.ps1 -NativeCat`，正常核验后再运行交接套件。猫的有限规则只对齐属性项目顺序，所有值保持严格，不修改原始快照。

端到端受控测量的命令与边界见[性能验证](../../validations/performance-worldgen/validation.md)。CLI 前沿没有实际玩家出生或客户端发送，不能据此宣称首次进入、渲染或视觉 PASS；同条件普通世界与保留的 scheduler Ordering 2 路径分别对照。

## 工程检查

1.20.1 的独立六阶段地形入口使用[旧版地形验证](../../validations/conformance-legacy_terrain/validation.md)，与 1.21.1 的实际密度切片同样逐 bit 对照。运行脚本按 Target 选择模板验证任务，不在 Target 构建脚本中硬编码验证启动配置；本版的 15 项结构输入 PASS 不自动提升完整地形或 B 状态。

1.20.1 的[B 独立 smoke](../../validations/conformance-legacy_b/validation.md)使用另外的验证实例，目标为两个 Native 重复与两个无关宿主的四项对照。保留完整原始 NBT、严格 UUID-only 视图、feature seed 序列和实际可见群系；当前源码、完整套件和有限原生例外的结论见[里程碑矩阵](../project/milestone-features.md)，不借用其它 Target 的结果。

根 `spotlessMisc` 使用上述项目级提前排除规则，格式检查范围不变，不遍历本地实例和结果。模板文件不修改、不跳过任务；[输入扫描调查](../research/investigation-format_input_scan.md)记录了边界与回归。完整检查使用以下命令；单元测试、发行检查、Markdown 和严格合规均须通过。

```powershell
.\gradlew.bat spotlessApply check verifyRelease verifyCompliance --strict --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4'
```
