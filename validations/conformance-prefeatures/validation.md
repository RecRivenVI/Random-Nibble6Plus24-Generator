# 26.3 装饰前独立原版对照

## 目标

固定新的轻量生成合同的 Oracle：原版 physical scheduler 与当前隔离参考路径分别在全新世界、独立 JVM 中执行，比较 STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES 和纯 TERRAIN。26.3 的地形、表面及洞穴合并在 TERRAIN，不借用 26.2 的阶段名或 golden。

在选择出生点前阻止验证实例的出生区生成；任何 FEATURES 或后续任务均使验证失败。前两个检查点记录已定义的结构数据，不读取尚未生成的 biome；BIOMES 与 TERRAIN 记录完整目标数据，以及全部 references 实际指向的 start、pieces、junction NBT、bounding box 和 terrain adaptation，不仅比较 piece 数量。保留所有 NBT 字段与列表顺序，只归一化 compound 键顺序。

原版生产世界使用目标 local seed；参考进程使用不同 host seed，通过既有 resolver 和隔离 session 生成。环境记录包含 Minecraft、模组版本、构建输入指纹、generator、dimension type、生成 registry 值及 tags 的指纹。普通矩阵不替换参考算法；结构套件单独消费新的紧凑输入，不修改生产分流或存档合同。

结构套件在普通原版世界中寻找 Village、Mineshaft、Trial Chambers、Ocean Monument、Nether Fortress、End City、Stronghold 的真实 start；每类分别验证 start 目标及邻区投影，另验证 Village 与 Trial Chambers 没有 piece 方块相交、但原版地形适配非零的 halo 目标。所有 fixture 均在新的普通物理世界中独立复算，不复用发现阶段的已生成数据。

新模块复用原版 `createStructures`、`createReferences` 和 `Beardifier`，只保留目标 starts、完整 references 及它们实际指向的结构数据。邻区结构载体没有 sections、biome palette、ticks、holder 或任务；请求缺失地形立即失败。26.3 原版 references 半径由 metadata 取得并与实际八区块实现校验；不能缩成装饰 writer 的 3×3。

命中后的 weighted selection、biome/height checks、pieces、Jigsaw 与模板锁归属全部保持原版/现有参考行为；特殊 placement/exclusion 仍由既有 scope 记录 eager 候选回退。默认结构套件仍借用参考依赖图；显式 `-Lightweight` 的第三阶段消费者另外使用一个完整目标与八个 biome-only 载体，不构建 virtual holders 或邻区地形，未接入生产分流。

## 运行

26.3 Fabric 的受控环续流使用真实原生 generateRingPositions、RandomSource.fork 与群系读取，在两个 fresh JVM/save 中固定一种合法的单 worker FIFO 条件。仅 probe 可替换该组件的 executor、重放由原生前缀实际到达的气候 token；不进入生产 scheduler。四种种子、两个前缀和普通／真实要塞目标共十六组，每组检查原生 128 次 fork、目标范围位置、根随机源的两次后续抽取，以及 1445 个独立 native quart 读取。不同出口下的首个群系差异必须双向重现并保持未通过；有限负面证据不证明全部调度或参数表。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1 -Guarded -RingContinuation -EvidenceLabel legal_fifo -Offline -CpuAffinity 3840
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1 -Guarded -RingContinuation -EvidenceLabel legal_fifo -CheckOnly
```

每个 JVM 使用原版默认群系与结构定义，完整环控制不安装 Mosaic scope，Lazy 路径调用生产 ConcentricRingScope；检查实际任务数而不是手写环算法。两次运行必须具有不同 PID/save、相同源码指纹及完整相同条件结果，实际默认下界／末地的 concentric set 数也单独记录。任务线程关闭、物理加载数保持。它不生成 StructureStart 或地形、不读取 saved palette、不替代完整装饰前／B Oracle，也不将直接 uncached 查询当作 stored biome 权威。

26.3 Fabric 的原生气候缓存组件审计单独使用两个 fresh JVM/save，运行本版 Climate.ParameterList 原方法及实际 ScopedStructureSources。只验证相同条件重复和双向入口/续流，不生成结构或地形，不代替独立世界 Oracle。原始 COUNTEREXAMPLE 说明坐标缓存前提不完整，保留原输入只读；Guarded 则要求不同入口重新求值、相同入口命中后恢复精确出口和下一次查询。诊断反射只读取原生叶节点身份，不反射改写缓存；适配层恢复原生出口不替换气候算法、参数、返回值或 RNG，入口均由实际查询前缀到达。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1 -Guarded -EvidenceLabel native_ctor -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1 -Guarded -EvidenceLabel native_ctor -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1 -CheckOnly -ExpectedInputHash 2E00BC01E26AEE2EB0EC8832CAA59E5DBE44990F3DDC1A5631F36D238B5AE8E4
```

原始结果固定在 result/native_climate_state/26.3-fabric，Guarded 与 EvidenceLabel 分离新条件目录；已有证据不覆盖。源码冻结、单实例四逻辑 CPU、低优先级。ExpectedInputHash 只允许历史证据只读核对，不能据其重新运行或改写证据。原版 19-child 与重建 2-child 索引、两个查询方向全部八项须在独立 JVM 中一致；实际三维度的原生 sampler bridge 均须可用。不从自建气候参数推定默认世界已出现最终方块或结构差异，实际结构、群系和地形仍分别与原生 Oracle 比较。失效桥接的 crash/log 和字节码转储保留，不将其改记为通过。

26.3 NeoForge 分别验证其 patched 游戏和 FML 接线；不与产品分包，也不从 Fabric 的构造器或通过结果推定成立。两个原生反例与 guarded 组件使用各自独立目录，原始反例只允许显式历史输入只读核对。现代 NeoForge 的装饰前标签按相同隔离／哈希规则保存，不覆盖旧输入。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1 -Target 26.3-neoforge -Guarded -EvidenceLabel neoforge_native_ctor -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1 -Target 26.3-neoforge -Counterexample -EvidenceLabel neoforge_owned_module -CheckOnly -ExpectedInputHash AC201FD1D5DC51203CDC8B47A389C75D397CEEDAA811D423513A6EE23886B75D
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-neoforge -Suite smoke -Lightweight -StructureReuse -EvidenceLabel climate_state -CheckOnly
```

重新运行 guarded 时选择不存在的独立 EvidenceLabel、去掉 CheckOnly 并按需加 Offline；Counterexample 不能重新执行。组件前后物理加载不变，不生成 FEATURES，不能用组件通过替代结构或地形 Oracle。

状态安全输入的装饰前重验使用独立结果与世界名，不覆盖旧矩阵；先 smoke，再 matrix 和 structures。CheckOnly 默认绑定当前源码，历史输入只能显式只读。状态变化触发的重新求值属于正确性回退，不以每个 fixture 的缓存命中数作性能通过条件；总 queries、unique sources、重新求值和出口恢复逐项核对，最终全部数据仍严格对照。

26.3 结构套件的 Beardifier report 使用 coverageVersion 2：实际读取包含最高有效层，记录真实样本数、minY、maxYInclusive、height、sampleBytes 和原始 payload SHA-256。CheckOnly 在比较前分别检查两侧的 256 × height 个四字节 float 与文件长度；缺层、计数虚报、旧 report 或 hash 不符均拒绝，历史缺层数据不补写。

两个独立 Target 队列并行时，可从本验证此前实际导出的 launcher.json 取得原生 JVM 配置；只复用启动配置，不复用存档或 Oracle 数据。BootstrapLauncher 必须属于本验证且匹配 Target／实例目录。工具把模板的实际 DevLaunch／DLI 参数复制到本实例的独占文件，仅改已识别端口为 0；模板配置仍使用合法的固定端口取值。每次重新建立 fresh JVM/save 并重新导出 fixture，记录 PID、四 CPU、低优先级、端口及参数哈希。CheckOnly 还要求真实日志绑定端口 0；未知入口或所有权不符直接失败，不改模板文件。并行队列须使用不同 Target 实例和 CPU 掩码。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-neoforge -Suite structures -Lightweight -StructureReuse -EvidenceLabel climate_state_complete_height -BootstrapLauncher validations/conformance-prefeatures/result/structure_reuse_smoke_climate_state/26.3-neoforge/launcher.json -CpuAffinity 3840
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite structures -Lightweight -StructureReuse -EvidenceLabel climate_state_complete_height -BootstrapLauncher validations/conformance-prefeatures/result/structure_reuse_smoke_climate_state/26.3-fabric/launcher.json -CpuAffinity 61440
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-neoforge -Suite structures -Lightweight -StructureReuse -EvidenceLabel climate_state_complete_height -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite structures -Lightweight -StructureReuse -EvidenceLabel climate_state_complete_height -CheckOnly
```

EvidenceLabel 已存在时拒绝覆盖。再次实际运行须选择新的显式标签，以上固定批次仅只读复核；启动配置的来源日志本身不计为此次游戏通过。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite smoke -Lightweight -StructureReuse -EvidenceLabel climate_state -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite smoke -Lightweight -StructureReuse -EvidenceLabel climate_state -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite matrix -Lightweight -StructureReuse -EvidenceLabel climate_state -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite structures -Lightweight -StructureReuse -EvidenceLabel climate_state -Offline
```

这些运行共用本验证实例，不并行执行；不能与同目录的组件审计同时运行。未审计模组或采样器的实际 B 交接使用无结构输入复用的精确原生求值，不把本版 guard 的通过提升为其它 Target 或 BOP 通过。

1.21.1 的有限范围环搜索候选默认关闭，以下独立结果不覆盖基线。smoke 的导出进程另外比较原生完整环与候选的 1,536 个正例（四 seed、全部 128 槽及查询边缘），它是同 JVM 组件证明，不代替后续 fresh JVM/save 的地形 Oracle；六阶段数据、完整结构与密度位模式仍严格比较。未知模组、BiomeSource、placement 或环 exclusion 使用完整原生回退。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite smoke -RingScope
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite smoke -RingScope -CheckOnly
# 守卫补强与脱离式物理 BIOMES 联合门禁，目录独立，组件证明绑定当次源码指纹
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite smoke -RingScope -PhysicalBiomes
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite smoke -RingScope -PhysicalBiomes -CheckOnly
# 默认关闭的共享状态缩锁候选；原生进程不启用候选，消费者须记录实际激活
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite structures -RingScope -PhysicalBiomes -NarrowLocks
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite structures -RingScope -PhysicalBiomes -NarrowLocks -CheckOnly
```

1.21.1 使用本版独立入口，六个检查点逐字段比较完整原生 Chunk NBT（保留 raw NBT，不归一化任何字段）、完整引用结构、九份 stored palette/可见群系、Beardifier 及原生插值密度切片 double raw bits。初版只验证精确消费者，不接入生产 B，不借用 26.3 的内部 density buffer 证据或性能结论：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Suite smoke
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Suite structures
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Suite matrix
```

三个固定结果目录为 `result/legacy_terrain_smoke/1.21.1-neoforge/`、`legacy_terrain_structures/1.21.1-neoforge/` 与 `legacy_terrain_matrix/1.21.1-neoforge/`。每项 native 与轻量消费者分别运行于 fresh JVM/save，consumer 使用无关宿主；结构套件为 18 项，矩阵为 72 项。普通原版进入构造器时没有 seed 路由绑定；消费者只在自身 region 构造期间绑定本地种子与 RandomState，正常和注入异常后必须清除。所有 FEATURES 调用由探针直接拒绝。密度观察器只复制本次原生 doFill/fillSlice 实际数组，不重新计算；本目标必须恰好一次 doFill、采样非零，数值切片与完整方块分别严格比较，关闭和异常均清理。无切片的首份 smoke 保留在 baseline_before_density，不回填历史字段。

Run-LegacyTerrain 的 CheckOnly 默认绑定当前 Target/组件源码指纹，不只比较两个旧文件是否互相一致。只读复核归档时可显式传 `-ExpectedInputHash <sha256>`，其中 sha256 是研究记录中对应证据的完整输入指纹；输出明确为 EXPLICIT_HISTORICAL_INPUT，不能计为当前源码通过。该参数不得用于启动游戏或修改历史证据。

先在本机 `local.toml` 明确接受 EULA。执行完整 72 项矩阵，再由工具任务核验固定结果目录：

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1
.\gradlew.bat :worldgen_validation:verifyPreFeatures
# NeoForge 使用本加载器的普通原生控制，不借用 Fabric 证据
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-neoforge -Suite smoke -Lightweight -StructureReuse
# 26.1.2 先验证自己的完整参考路径，不冒充已适配轻量模块
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.1.2-neoforge -Suite matrix
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.1.2-neoforge -Suite structures
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.1.2-neoforge -Suite structures -Compact
```

紧凑结构输入套件与普通矩阵使用各自固定结果目录：

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures
.\gradlew.bat :worldgen_validation:verifyCompactStructures
```

结构套件写入 `result/compact/26.3-fabric/`。七类结构 fixture 显式固定 local seed 为发现世界的 seed，用于隔离结构算法本身；另保留 master 123456789 的 origin 与 (125,-37) 派生种子回归。所有 consumer host seed 均与 local seed 不同。每个 fixture 比较四个检查点、完整可持久化 start 投影，以及目标 16×16×完整高度范围内每个方块位置的 Beardifier raw float bits。

快速定位可在脚本后添加 `-Suite smoke`，覆盖 Overworld origin、non-origin、Nether、End；它不代替完整矩阵。探针先使用 `:version:26.3-fabric:runValidationConformancePrefeaturesServer` 导出模板任务的实际 JVM 配置，再按该配置逐项启动独立普通原版进程，不读写日常实例。每次先覆盖 `result/current/26.3-fabric/`；临时世界结束后移入回收站，失败证据保留。单进程五分钟测试上限只用于识别挂起，不改变原版任务或生产超时。

所有进程串行执行，验证 JVM 限制 4 个逻辑 CPU，Gradle worker 为 2；编排进程使用低优先级，不修改生产调度模型。

NeoForge 入口只支持本轮原版验证，BOP 尚不启用。首次由模板原生任务导出实际 JVM 和 DevLaunch 参数；符合已验证的单参数文件形状时，后续 fresh JVM 原样复用该命令，由 DevLaunch 自己读取不变的参数文件，世界通过本验证实例的 server.properties 选择。不解析或改写其参数文件，不把 Fabric launcher 当作本加载器证明；未知启动形状回退逐项原生 Gradle 任务。启动开销不计作生产生成算法性能。

每个进程正常结束后先核验真实 summary 和新增崩溃报告，再清理成功世界；退出码为零不能代替验证通过。失败世界和日志原地保留，首份失败证据另存固定的 `result/first_failure/<variant>/<target>/`，不被下一次运行覆盖。

轻量消费者的完整矩阵与结构正例使用独立固定目录，不覆盖前两个阶段：

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Lightweight
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures -Lightweight
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures -Lightweight -StructureReuse
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite matrix -Lightweight -StructureReuse
.\gradlew.bat :worldgen_validation:verifyLightweightTerrain :worldgen_validation:verifyLightweightStructureTerrain
```

结果写入 `result/terrain_matrix/26.3-fabric/` 与 `result/terrain_structures/26.3-fabric/`。轻量 smoke 使用 `-Suite smoke -Lightweight`，写入 `result/terrain_smoke/26.3-fabric/`。每个 fixture 的原版控制均重新生成，不能拿已有 reference 结果冒充新实现的独立对照。

`-StructureReuse` 的结果另存 `result/structure_reuse_<suite>/26.3-fabric/`。同种子同目标范围先构造目标、八个邻区结构输入，再从复用仓库重建目标输入；关闭仓库后复用完整 pieces 做原版地形入口。与全新独立原版比较相同完整检查点、密度、Beardifier、表面、洞穴和结构元数据，不增加任何忽略字段；仓库最多 361 个元数据载体、关闭后 resident 为零。此项不执行邻区或目标 FEATURES。

该入口先用原版创建九份 stored-biome 输入，再复制目标 palette 并复用只读查询准备。断言原版创建九次、目标复制一次、准备命中一次，关闭后 resident 为零；比较完整 palette、density raw bits、表面、洞穴及最终状态，不用 uncached 重新采样代替已生成数据。

## 通过标准

26.3 Fabric 另有 H1 的未接生产候选：`-BiomeOnly` 仅执行原生 starts/references/BIOMES，返回一个真实原生 ProtoChunk。Native 控制在 BIOMES 停止，消费者使用无关宿主；不运行地形、FEATURES 或 Artifact，不以 uncached climate 覆盖 palette。26.3 原生 doCreateBiomes 自行创建/释放密度缓存池，ChunkAccess 没有旧版的 NoiseChunk 字段，因此不额外复制第二个 Chunk。验证还在关闭 owner 后重读完整快照和原生序列化数据、检查原生部分区块 codec 精确往返，核对不保留 live sampler、virtual holders 为零。codec 往返不是服务器重启重载；该模块不改变任何物理 scheduler 或旧存档分流，通过不等于 H1 已有端到端收益。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite smoke -BiomeOnly
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite structures -BiomeOnly
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.3-fabric -Suite matrix -BiomeOnly
```

- 完整矩阵含 Overworld origin 8、non-origin 32、Nether 16、End 16，共 72 项；每项有独立 native producer 和 unrelated-host reference consumer。
- 四个检查点按原版状态顺序捕获且严格一致；任何不一致给出阶段、首个数据差异、方块差异数量、首个不同结构的完整 NBT 及具体字段路径。
- producer 和 consumer 的环境、目标、master/local seed 一致，JVM 与 save 不同，参考 host seed 不等于 local seed。
- FEATURES 执行次数为 0，目标严格停在 TERRAIN；context binding 清理为 0。
- 任一缺失证据、环境不一致或内容差异让脚本和工具任务失败；smoke 的成功只说明四项通过。
- 结构套件共 18 项：七类各两个真实正例、两个地形适配 halo、两个实际 Mosaic seed 回归；明确断言指定 native start 有 pieces，不能以空数据通过。
- 结构套件的完整 start NBT、pieces/junctions、bounding boxes、references、持久化投影、BIOMES、纯 TERRAIN 与独立 native 一致；逐 bit Beardifier 文件一致，halo 的非零样本数大于零。
- 紧凑输入不保留已关闭 session 的 chunk graph；关闭创建上下文后继续消费成功，导出的 NBT 被调用方修改不能反向改变结构权威。对非 owner 或未解析范围的查询 fail closed。
- 轻量套件另外比较原版实际 density buffer 的逐 bit 内容、noise/surface/carved 三个内部目标快照、临时 CarvingMask、289 个 source biome 顺序，以及目标和八个邻区的完整 stored palette；不是用二次采样代替实际内部状态。
- 每个轻量目标只创建一个完整 `ProtoChunk` 与八个 biome-only 载体；virtual holders 为零，未生成邻区地形。表面和洞穴须有实际改变方块的正例，不能以全空掩码或无表面变化矩阵通过。

所有套件不执行 FEATURES，不宣称三种装饰方案或客户端人工体验已验收。轻量装饰前模块通过也不等于已经批准切换旧存档生产路径。

26.1.2 NeoForge 单独比较 STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES、NOISE、SURFACE、CARVERS 六个检查点，目标停在 CARVERS。它的 Beardifier 以本版 double raw bits 比较，CARVERS 保留九份实际生成的 stored palette，不用 uncached sampling 替代。结构套件在本版普通原生世界重新寻找七类真实正例和两个地形适配 halo，结果写入 native_structures/26.1.2-neoforge；默认消费者仍是完整参考依赖图，不要求不存在的 compactInputs 指标。显式 -Compact 时改为 compact/26.1.2-neoforge，要求 289 个 source 元数据和完整原生 references、持久化投影一致，关闭首次 context 后再消费并检查导出 NBT 无别名；不使用尚未审计的 26.3 缩锁入口。

26.1.2 的独立轻量入口使用自己的原生分离阶段方法；BIOMES palette 保留缓存采样，邻区生成 palette 时使用该 owner 的精确结构输入。原生 CARVERS 遍历的 289 个 source 只承载 carverBiome(Supplier) 配置缓存，没有地形、假 BIOMES 状态或 GenerationChunkHolder。普通地形查询拒绝，所有 RNG 与 carve 仍由原版执行。六份完整目标快照、结构投影、double Beardifier 和九份 stored palette 继续与 fresh native 比较，不声称具有 26.3 的合并 density buffer 观测。结构复用已是此消费者内部作用域；单独的 -StructureReuse 对比尚未适配，明确拒绝。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.1.2-neoforge -Suite smoke -Lightweight
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.1.2-neoforge -Suite matrix -Lightweight
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Target 26.1.2-neoforge -Suite structures -Lightweight
```

同源码端口启动失败可显式使用 `-Resume`：必须保留原始 manifest/launcher 和端口绑定失败日志，没有内容 FAIL；工具重新核验完整连续前缀的全部状态文件与当前源码指纹，保全失败日志/crash/未完成世界，再为剩余项启动 fresh JVM/save。未知失败、源码变化或不完整证据拒绝续用；完整矩阵通过标准不变。正常重复运行仍覆盖固定结果位置，不把部分完成提升为 PASS。并行队列还必须使用不同 validation 角色的端口，仅目录不同不够。

1.21.1 的物理 BIOMES 交接候选使用原生 createBiomes 与其 cachedClimateSampler，复制原生 palette 容器后丢弃 NoiseChunk 和输入 resolver。以下对照仍分别运行同版本 fresh 原生世界与 unrelated-host 消费者，比较六阶段、结构、density/Beardifier，并额外将 detached BIOMES 的全部原生 NBT/结构快照与原生 BIOMES 检查点严格比较；源 owner 关闭后再次完整序列化必须不变。候选未证明前不接入生产，也不把一次 smoke 代替 72 项矩阵。结果固定于 physical_biomes_<suite>/<target>，不覆盖已有 legacy_terrain 证据。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite smoke -PhysicalBiomes -CpuAffinity 61440
& components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite matrix -PhysicalBiomes -CpuAffinity 61440
& components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.21.1-neoforge -Suite structures -PhysicalBiomes -CpuAffinity 61440
```

1.21.1 同目标结构 source 复用候选使用同一入口追加 `-RingScope -NarrowLocks -ReuseSources -EvidenceLabel source_state`，结果写入 `source_reuse_narrow_locks_ring_scope_physical_biomes_<suite>_source_state`。仅消费者启用复用，必须实际发生 9×289 次查询、361 个唯一坐标；原生 RTree 入口不同时精确重算，命中则重放原生出口状态，分别核对重算与重放计数。六阶段、结构、density/Beardifier、九份 stored palette 与独立原生保持同样严格判等。不在未证明的 Target 或第三方配置默认开启。
