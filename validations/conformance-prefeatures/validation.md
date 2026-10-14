# 26.3 装饰前独立原版对照

## 目标

固定新的轻量生成合同的 Oracle：原版 physical scheduler 与当前隔离参考路径分别在全新世界、独立 JVM 中执行，比较 STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES 和纯 TERRAIN。26.3 的地形、表面及洞穴合并在 TERRAIN，不借用 26.2 的阶段名或 golden。

在选择出生点前阻止验证实例的出生区生成；任何 FEATURES 或后续任务均使验证失败。前两个检查点记录已定义的结构数据，不读取尚未生成的 biome；BIOMES 与 TERRAIN 记录完整目标数据，以及全部 references 实际指向的 start、pieces、junction NBT、bounding box 和 terrain adaptation，不仅比较 piece 数量。保留所有 NBT 字段与列表顺序，只归一化 compound 键顺序。

原版生产世界使用目标 local seed；参考进程使用不同 host seed，通过既有 resolver 和隔离 session 生成。环境记录包含 Minecraft、模组版本、构建输入指纹、generator、dimension type、生成 registry 值及 tags 的指纹。普通矩阵不替换参考算法；结构套件单独消费新的紧凑输入，不修改生产分流或存档合同。

结构套件在普通原版世界中寻找 Village、Mineshaft、Trial Chambers、Ocean Monument、Nether Fortress、End City、Stronghold 的真实 start；每类分别验证 start 目标及邻区投影，另验证 Village 与 Trial Chambers 没有 piece 方块相交、但原版地形适配非零的 halo 目标。所有 fixture 均在新的普通物理世界中独立复算，不复用发现阶段的已生成数据。

新模块复用原版 `createStructures`、`createReferences` 和 `Beardifier`，只保留目标 starts、完整 references 及它们实际指向的结构数据。邻区结构载体没有 sections、biome palette、ticks、holder 或任务；请求缺失地形立即失败。26.3 原版 references 半径由 metadata 取得并与实际八区块实现校验；不能缩成装饰 writer 的 3×3。

命中后的 weighted selection、biome/height checks、pieces、Jigsaw 与模板锁归属全部保持原版/现有参考行为；特殊 placement/exclusion 仍由既有 scope 记录 eager 候选回退。默认结构套件仍借用参考依赖图；显式 `-Lightweight` 的第三阶段消费者另外使用一个完整目标与八个 biome-only 载体，不构建 virtual holders 或邻区地形，未接入生产分流。

## 运行

先在本机 `local.toml` 明确接受 EULA。执行完整 72 项矩阵，再由工具任务核验固定结果目录：

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1
.\gradlew.bat :worldgen_validation:verifyPreFeatures
```

紧凑结构输入套件与普通矩阵使用各自固定结果目录：

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures
.\gradlew.bat :worldgen_validation:verifyCompactStructures
```

结构套件写入 `result/compact/26.3-fabric/`。七类结构 fixture 显式固定 local seed 为发现世界的 seed，用于隔离结构算法本身；另保留 master 123456789 的 origin 与 (125,-37) 派生种子回归。所有 consumer host seed 均与 local seed 不同。每个 fixture 比较四个检查点、完整可持久化 start 投影，以及目标 16×16×完整高度范围内每个方块位置的 Beardifier raw float bits。

快速定位可在脚本后添加 `-Suite smoke`，覆盖 Overworld origin、non-origin、Nether、End；它不代替完整矩阵。探针先使用 `:version:26.3-fabric:runValidationConformancePrefeaturesServer` 导出模板任务的实际 JVM 配置，再按该配置逐项启动独立普通原版进程，不读写日常实例。每次先覆盖 `result/current/26.3-fabric/`；临时世界结束后移入回收站，失败证据保留。单进程五分钟测试上限只用于识别挂起，不改变原版任务或生产超时。

所有进程串行执行，验证 JVM 限制 4 个逻辑 CPU，Gradle worker 为 2；编排进程使用低优先级，不修改生产调度模型。

轻量消费者的完整矩阵与结构正例使用独立固定目录，不覆盖前两个阶段：

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Lightweight
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures -Lightweight
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Suite structures -Lightweight -StructureReuse
.\gradlew.bat :worldgen_validation:verifyLightweightTerrain :worldgen_validation:verifyLightweightStructureTerrain
```

结果写入 `result/terrain_matrix/26.3-fabric/` 与 `result/terrain_structures/26.3-fabric/`。轻量 smoke 使用 `-Suite smoke -Lightweight`，写入 `result/terrain_smoke/26.3-fabric/`。每个 fixture 的原版控制均重新生成，不能拿已有 reference 结果冒充新实现的独立对照。

`-StructureReuse` 的结果另存 `result/structure_reuse_<suite>/26.3-fabric/`。同种子同目标范围先构造目标、八个邻区结构输入，再从复用仓库重建目标输入；关闭仓库后复用完整 pieces 做原版地形入口。与全新独立原版比较相同完整检查点、密度、Beardifier、表面、洞穴和结构元数据，不增加任何忽略字段；仓库最多 361 个元数据载体、关闭后 resident 为零。此项不执行邻区或目标 FEATURES。

## 通过标准

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
