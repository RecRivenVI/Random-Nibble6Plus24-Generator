# 生产 Chunk 请求

## 目标

通过普通服务器 Chunk 请求验证 Mosaic 从缺失 Chunk 到 FULL 的生产流程；首版交付端另验证双种子反馈、禁用定位与原生消费者的“找不到”行为。

这是 Minecraft 26.2 Fabric、26.3 Fabric、26.3 NeoForge、26.1.2 NeoForge 与正在适配的 1.21.1 NeoForge 分 Target 验证。登记或新增接线不代表本版本已经运行通过。工具属于 `worldgen_validation`，独立游戏内入口属于 `src/probe`，产品 Mixin 直接链接的默认关闭 hooks 留在产品中。

## 运行

定位消费者的原版对照使用普通 `generate-structures=false` 世界，取得真实“找不到”分支；Mosaic 保留结构生成。两者均实际调用末影之眼、地图函数、制图师交易与海豚目标，不改原生行为，也不把此组件证明当作客户端画面验收。此入口支持三个首版交付端，普通运行不加载它。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -VanillaControl -RuntimeProof -WorldSearchProof -VerifyReload -Offline -EvidenceLabel productization_search
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -WorldSearchProof -VerifyReload -Offline -EvidenceLabel productization_search
```

上述直接 Gradle 启动共用验证配置的监听端口，逐个运行；不要跨 Target 并行。中断后只补已有存档的重载可用 `-Mode reload` 替换 `-VerifyReload`，不重建世界或重新生成已通过的 fresh 数据。

26.3 两端的自然实体／方块实体保存验证共用工具入口；名称保留为 `Run-FabricNatural.ps1`，Target 参数选择独立的原生适配。以下为 NeoForge 的五个真实正例，默认四逻辑 CPU、低优先级，逐个运行。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1 -Target 26.3-neoforge -EvidenceLabel native_natural
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1 -Target 26.3-neoforge -Fixture nether_chests -EvidenceLabel native_natural
```

同源已通过结果只复核，不覆盖重跑；单项使用 `-Fixture`。自然激活与 native load control 不等于运行期 NBT 永不变化。NeoForge 检查真实群系依赖，Fabric 另核对逐 stored-biome cell；不把两个字段数量混为同一证明。

从仓库根目录执行。先由使用者在 `local.toml` 顶层写入 `eula = true`，不要代替使用者接受 EULA。只启动单个高负载 JVM；所有运行限制为 4 个可见处理器。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric' # 旧版回归改为 26.2-fabric
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1
# 26.3 新地图单区块装饰试验与重载；旧合同回归不传此参数
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -VerifyReload -Mutate
# 新按需 B 实验世界；序列化身份不同于已有 Ordering 2
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate
# 参考 Target 的完整物理持久化组件；仅 probe，不作为 tick/entity 激活证明
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate -StateProof
# 参考 Target 的普通原版 FULL/save/reload 零 Mosaic 工作对照
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -VanillaControl -VerifyReload -Mutate
# 参考 Target 原生票据与正常 tick 循环；不直接调用 tick callback 或手工 promotion
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -VerifyReload -Mutate
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -VanillaControl -RuntimeProof -VerifyReload -Mutate
# NeoForge 保留自己的原生 DevLaunch 参数文件，以 server.properties 选择世界
$env:MOSAIC_VALIDATION_TARGET = '26.3-neoforge'
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate
# 本加载器的完整物理状态持久化组件；不作为实体/tick 激活完成证明
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate -StateProof
# 26.1.2 独立 B 接线；仍需本版本运行验收，不支持上述 StateProof
$env:MOSAIC_VALIDATION_TARGET = '26.1.2-neoforge'
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate
# 1.21.1 独立原生 holder/ChunkSerializer 路径；StateProof 尚未适配
$env:MOSAIC_VALIDATION_TARGET = '1.21.1-neoforge'
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate -CpuAffinity 61440
& .\components\worldgen_validation\scripts\integration-production_lifecycle\Run-Phase3C2Production.ps1 -VanillaControl -VerifyReload -Mutate -CpuAffinity 61440
```

其他矩阵与子场景的脚本在同一工具目录。脚本负责隐藏 preset、输入与系统属性，不直接使用日常实例。人工客户端操作仍由使用者完成。

原生服务器玩家生命周期是独立的 probe 场景，不直接请求逐阶段生成，也不等同于客户端握手、画面或真实网络连接验收。它调用 PrepareSpawnTask 与原生出生搜索，使用仅限验证的 EmbeddedChannel 承接真实服务器数据包；正式 Connection、PlayerList、ServerPlayer 以及服务端正常网络/tick 循环均保持原版。传送确认包含 26.3 的坐标和旋转字段，未知相对传送明确失败，不用旧版协议猜测。场景覆盖初次／返回玩家、原生死亡及重生请求、下界和末地往返，再新 JVM 重载同一世界；不验证门户发现或客户端渲染。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -VanillaControl -PlayerLifecycle -VerifyReload -EvidenceLabel native_player_transport
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -PlayerLifecycle -VerifyReload -EvidenceLabel native_player_transport
```

该场景不得与 RuntimeProof、清点或单次请求场景混跑；标签、世界和结果禁止覆盖。各阶段要求原生玩家唯一注册、真实 ENTITY_TICKING LevelChunk、正确物理光照和二十次正常 server tick。返回玩家从原生 SavedPosition 读取实际保存坐标，不假定玩家在服务器仿真期间完全静止。保留结构原始 NBT、明确的测试修改、票据／post-processing 计数和发送的包类型；重载的已访问 FULL owner 不得再次 canonical capture，Vanilla 全程 Mosaic 计数为零。退出后另核对 prepare task、玩家、通道、隔离绑定和 in-flight 清理；任一异常保留 FAIL、日志与世界，不跳过原生 postProcessGeneration。

1.21.1 的冷 SPAWN 单独保存真实 LIGHT：探针只挂起选定目标尚未执行的 SPAWN future，正常关闭时按该 world lifecycle 取消；不回写旧快照或手改磁盘状态。新 JVM 必须实际读取 LIGHT，再通过普通 FULL 请求恢复。该项使用显式候选开关做正确性诊断，不代表优化获准默认启用。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '1.21.1-neoforge'
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -ColdSpawn -LazyRings -NarrowLocks -ReuseSources -EvidenceLabel light_restart_idempotent -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Check-LegacyColdSpawn.ps1 -EvidenceLabel light_restart_idempotent
```

## 通过标准

冷 SPAWN 的 stage/resume 必须为不同 PID、当前同一源码指纹，真实磁盘输入为 LIGHT；目标恢复 canonical prepare 为零、原版 population 恰好一次，最终为 lit LevelChunk。已有八来源 stored biomes 和 CARVERS 方块读取 hash 与冷重建完全一致。额外八次读取发生在原版 population 返回后，只计组件覆盖，不冒充自然刷怪越界正例；原始差异保持 FAIL。两次原生 level close 后 in-flight/worker 均零，不新增崩溃报告。

两个 26.3 Target 的部分 BIOMES 准备用以下命令验证；运行前通过 MOSAIC_VALIDATION_TARGET 选择加载器：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -PartialBiomes -VerifyReload -Mutate
$env:MOSAIC_VALIDATION_TARGET = '26.3-neoforge'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -PartialBiomes -VerifyReload -Mutate -EvidenceLabel native_partial_biomes -Offline
```

此项连续启动四个独立 JVM、同一专用存档：十六次同目标普通 BIOMES 请求得到同一个真实 S(C) ProtoChunk，不捕获 Artifact；保存重启后仍停 BIOMES，完整快照与原生结构加载控制一致，目标 biome preparation 为零；再通过普通 FULL 请求完成完整 B Artifact、光照与生命周期，修改方块后再次保存重启，canonical/Artifact/SPAWN 为零且修改保留。结果固定于 `result/current/<target>/phase3c2-production/features-2/development-b/partial-biomes/`，标签额外隔离证据。独立目标的十六次并发 FULL 升级必须只生成一个目标 Artifact。这不是删减物理邻区、空 palette 或近似采样；未知模组组合保持完整 Artifact 回退。通过此项尚不代表有性能收益，仍需同条件端到端配对对照。

缩锁候选复验使用独立 `-EvidenceLabel biome_lock_scope`，在上述 partial-biomes 下隔离结果与世界，不覆盖原始证据：

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -PartialBiomes -VerifyReload -Mutate -EvidenceLabel biome_lock_scope -Offline
```

不靠手动逐阶段调用；去重、保存、重启、已修改 Chunk 和各维度生命周期保持正确。

Fabric 的部分 TERRAIN 候选使用同一原生请求／保存／并发升级流程，停止状态和证据目录分别为 TERRAIN 与 partial-terrain，不复用 BIOMES 结果冒充通过：

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -PartialTerrain -VerifyReload -Mutate -EvidenceLabel native_partial_terrain -Offline
```

`-DevelopmentB` 仅创建全新 `development_b` 隐藏生成器世界，校验 marker 与显式运行 opt-in，使用按需 B 后复用既有物化和生命周期。结果写入 `features-2/development-b`，要求新生成计数大于零、重载为零；缺失 marker、混合维度身份或未启用时 fail closed。既有 `mosaic` codec 的 Ordering 1/2 世界不切换，也不公开 WorldPreset。

26.1.2 的接线先复用本版已经独立对照的严格 B 模块：只运行目标 writer，九份精确装饰前读取和 stored palette 暂时保守保留，不能宣称已移植 26.3 的按需密度优化。新身份的 pipeline 从本 Target 的 Minecraft 版本派生，拒绝其它版本的 marker；该版本原先只接受 Ordering 1，新 Ordering 2 仅在 distinct B 身份与显式 opt-in 下启用，不把旧世界转成 B。完整 Artifact、SPAWN palettes 和外部结构元数据必须在会话关闭前脱离；保存重载、并发、三维度与性能均另行验收。

26.3 冷区请求额外检查所有 BIOMES 物理依赖的实际状态和 palette，并执行运行期刷怪使用的环境属性查询；`physicalBiomeChecks` 必须大于零，不允许用未物化 ProtoChunk 完成 biome future。FULL 重载继续要求已有 target 不重新生成。

按输出 JSON、异常链、资源与副作用断言判断，真实错误不能被吞掉。构建通过不代替 Runtime、Functional 或 Visual 通过。

工具在启动前移走旧结果，只接受本次新生成且目标、维度、seed、算法版本和物理 FULL 状态均匹配的证据；新增崩溃报告即失败，零退出码不构成通过。失败世界与日志保留，失败输出首份另存；成功后才回收临时世界。编排及其游戏进程采用四核、低优先级、单实例，不更改产品调度器。

26.3 两种加载器各自的显式 -StateProof 使用 probe 只读观察普通生产 FULL 请求，不直接驱动生成阶段。保存前与新 JVM 重载后严格比较方块、stored biomes、逐位置光照、高度图、完整 owner/external start NBT、references、owner 内真实索引定位、BE NBT、后处理与 POI 类型/位置/空闲票据；原生 SerializableChunkData 完整 NBT 另存，不删除字段。tick 相对时间与当时实体管理器可见数据单独记录在 lifecycleDiagnostics，未完成其状态推进与原生计时验证前不据此宣称 tick/entity 生命周期已通过。结果隔离写入 state-proof 子目录，不覆盖此前冷区标记验证；与 -PartialBiomes 的部分原生加载控制分开运行。Fabric 四个场景已实际完成，结果以各自文件及里程碑记录为准，不继承 NeoForge 状态。命令行只输出简要计数，完整证据仍保存在 JSON/raw NBT。

参考 Target 的 `-VanillaControl` 在另一专用世界中使用原生 normal preset 和 NoiseBasedChunkGenerator，不安装隐藏 Mosaic preset 或身份文件。由普通 FULL 请求完成生成，修改方块后正常保存退出、新 JVM 重载保留修改；全世界 Mosaic Artifact、物化、POI、部分 BIOMES 和生成路由计数均须为零，context 绑定清空，保存/重载 PID 不同。结果单独写入 vanilla-control；这里只覆盖真实 FULL/save/reload 和零侵入，不代替 tick/entity 激活、客户端发送或画面验收。本次三维度六个独立 JVM 已实际通过。

`-RuntimeProof` 已分别适配参考 Target、26.1.2 NeoForge 与 1.21.1 NeoForge；各自游戏结果见里程碑，不继承其它版本的 PASS。它使用完整 FULL/save/restart 探针，与 StateProof/PartialBiomes 分开。先短期添加本版原生票据（26.x 的 PLAYER_LOADING/PLAYER_SIMULATION，1.21.1 的 PLAYER），由服务器自己的 tick 循环推进真实 holder future；票据派生的全部 7×7 FULL 与两侧 ENTITY_TICKING 完成、生成会话清空后，再调用普通 FULL 观察器取证，避免它的单目标空闲断言与邻区活动请求竞争。不直接修改 holder level、调用 promotion、Level.tick 或私有 tick callback。在专用世界的空中小区域安装明确标记的运行期 fixture：掉落物按原版物理跨边界、STONE scheduled callback、真实水/下界岩浆传播、熔炉用煤烧圆石。须在至少 220 个自然 game tick 后观察到实际回调、唯一实体注册与烧炼产物，随后恢复方块并移除探针票据。它们只证明运行期机制，不冒充自然 worldgen 的副作用正例；中间 FULL 输出为 RUNTIME_PENDING，不能被编排当成 PASS。各维度实际结果以里程碑矩阵为准，不改变已通过的持久化结果。

参考 Fabric 的当前 schema 2 全 owner 运行／重载使用以下入口，单实例、四 CPU、低优先级，不与性能或会改写同一原生启动配置的队列重叠。新条件为 all_quart_biomes，已有结果只运行最后的只读命令；不能覆盖原批次。复核绑定 Target 全部 src 与 worldgen_contract 的指纹，与装饰前 main/probe 的输入集合不同。

```powershell
$ErrorActionPreference = 'Stop'
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
foreach ($runtimeDimension in @('minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end')) {
    & components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension $runtimeDimension -VanillaControl -RuntimeProof -EvidenceLabel all_quart_biomes -VerifyReload -Mutate -CpuAffinity 61440 -Offline
    & components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension $runtimeDimension -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -EvidenceLabel all_quart_biomes -VerifyReload -Mutate -CpuAffinity 61440 -Offline
}
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Check-FabricRuntime.ps1 -EvidenceLabel all_quart_biomes -CpuAffinity 61440
```

Fabric schema 2 观察全部 49 个 FULL owner 的原生 generation/loading BIOMES 依赖，检查 holder、无创建读取与服务器群系路径一致；每个实际 section 的 64 格都必须读取。保存／重载的 49 个 owner 完整群系哈希必须一致，观察不得增加 loaded count、B generation 或 Artifact。另核验两侧当前 ENTITY_TICKING、220 个自然 tick、跨界实体／流体、方块 tick、熔炉及重载不再生成。它不是每个 owner 的独立地形 Oracle，也不是玩家出生、发包或画面验收。schema 1 仅供显式 ExpectedRuntimeSchema=1、ExpectedInputHash 的历史只读复核；原 all_owner_biomes 漏读顶层的批次仍被拒绝，详见[探针故障分析](../../documents/research/incident-fabric_biome_quart_probe.md)。

参考 Fabric 的普通请求布局门禁独立运行，不与上述自然 tick/重载混合：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricRequests.ps1 -EvidenceLabel climate_state_requests -CpuAffinity 3840
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricRequests.ps1 -EvidenceLabel climate_state_requests -CpuAffinity 3840 -CheckOnly
```

三维度分别执行同目标 32 次请求、相邻不同种子 pair 与九 owner patch，共九个 fresh JVM/save。入口只调用普通 getChunkFuture(FULL)，要求每个返回位置准确、重复请求为同一 LevelChunk 对象、全部请求的真实 owner 各捕获/物化一次，光照和 BIOMES 物理依赖就绪，context 绑定归零。每个请求批次观察上限 300 秒；这不是生产超时或调度变更。专用条件目录绑定全 src 指纹、PID、实际世界和四个可见处理器；失败数据保留、已存在的目录拒绝覆盖，源码不得在批次期间修改。CheckOnly 的显式历史指纹只用于只读复核，不得用于生成。该门禁不证明 BLOCK_TICKING、玩家出生、发送或自然副作用持久化。

26.3 NeoForge 使用自己的 probe 只读观察既有入口中实际完成的 join；不修改生产 main，不新增请求、不替换 future，不改写原始 JSON。旁车文件为原结果文件名加 .requests.json，包含原文件 SHA-256、实际对象身份／逐 owner 次数、源码指纹和清理状态；原生方法正常返回且观察器移除 ThreadLocal／释放 Chunk、server 引用后才写入。真实异常继续传播，不生成成功旁车；300 秒仅是该显式验证入口的观察边界，不改变生产调度。两份 WrapOperation 接入分别要求唯一匹配，未知字节码直接失败。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricRequests.ps1 -Target 26.3-neoforge -EvidenceLabel native_joins -CpuAffinity 3840
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricRequests.ps1 -Target 26.3-neoforge -EvidenceLabel native_joins -CpuAffinity 3840 -CheckOnly
```

脚本名称保留既有入口，Target 必须显式选择 NeoForge；Fabric 仍读取自己的内嵌证据，不借用 NeoForge 旁车。两端的通过分别登记，不将请求去重提升为多目标生成哈希顺序独立或自然激活证明。

26.3 NeoForge 的独立 RuntimeProof 使用本加载器实际 patched JAR 核对的票据、ServerLevel block/fluid/entity callback 和熔炉 ServerLevel ticker。schema 2 检查 49 个 FULL owner 的原生 generation/loading BIOMES 输入及当前 ENTITY_TICKING 状态；不假设 Fabric 的 PhysicalBiomePreparation 快路径存在，不伪造该项零计数。结果在本 Target 的 runtime-proof-frontier，实际结论只写入里程碑；源码编译不算运行通过。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-neoforge'
foreach ($runtimeDimension in @('minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end')) {
    & components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension $runtimeDimension -VanillaControl -RuntimeProof -EvidenceLabel native_frontier -VerifyReload -Mutate -CpuAffinity 61440 -Offline
    & components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension $runtimeDimension -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -EvidenceLabel native_frontier -VerifyReload -Mutate -CpuAffinity 61440 -Offline
}
& components/worldgen_validation/scripts/integration-production_lifecycle/Check-NeoRuntime.ps1
```

26.1.2 的运行期探针已按本版实际源码核对 PLAYER_LOADING/PLAYER_SIMULATION 票据、ServerLevel.tickNonPassenger、block/fluid callback 和 AbstractFurnaceBlockEntity.serverTick(ServerLevel, ...)；仅在 integration-production_lifecycle 的显式 runtimeProof/vanillaControl 下加载。各维度运行结论只在里程碑登记；不继承其它格式或 Target 的范围，也不改变生产接线或将普通 Vanilla 分派到 Mosaic。增强观察格式 schema 2 对票据前沿全部 49 个 FULL 目标，分别按本版原生 ChunkPyramid 核查 BIOMES-or-later 输入（fresh 使用生成图，已保存 FULL 的 reload 使用加载图），要求 holder 中的数据实际至少完成 BIOMES；只完成 future、却仍持有 EMPTY palette 的路径不能通过。普通 Vanilla 使用相同 readiness 观察，不把待验证的静态推演当作已经成立的生命周期保证，也不在 reload 重新要求已经不需要的生成前沿。输出记录 dependencyPyramid；原生生成/加载依据与原失败见[依赖观察调查](../../documents/research/incident-loading_dependency_observer.md)。增强结果另存 runtime-proof-frontier，保留初始 runtime-proof 原始结果；这是探针格式，不是 Mosaic 存档格式变化。无人玩家的专用 RuntimeProof 实例显式关闭原生空服自动暂停，普通 Vanilla/B 条件一致，结束后恢复原配置；不改变生产行为或模拟 tick。使用以下命令分别检查三维度；每次只有一个游戏实例，保存重启由工具完成：

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.1.2-neoforge'
foreach ($runtimeDimension in @('minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end')) {
    & components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension $runtimeDimension -VanillaControl -RuntimeProof -VerifyReload -Mutate
    & components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension $runtimeDimension -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -VerifyReload -Mutate
}
```

独立正确性队列可用 `-CpuAffinity 61440` 分配另一组四个逻辑 CPU；掩码必须恰含四个当前可用 CPU。默认 3840 与另一验证队列分离实例/世界/端口，运行期间 Target 源码冻结，不与性能测量重叠。运行期失败的原始结果、日志、crash report 与世界由工具保全到 first_runtime_failure，之后重跑不覆盖该现场；仅启动成功或 Gradle 零退出码不计通过。

1.21.1 使用自己的 ChunkSerializer、原生请求与 holder 接线；尚不支持 StateProof。所有需 BIOMES 的物理依赖必须持有真正生成的数据；未证明部分阶段交接时保守使用完整精确 Artifact，不发布空 palette future。新接线与保存重载分别以实际结果判断，普通 Vanilla 控制的通过不提升为 B 通过。已有 FAIL 可用相同场景参数加 `-PreserveFailureOnly` 仅保全现场、不启动游戏；每次真正重跑会先回收同名旧 NBT sidecar，避免失败轮继承前次产物。

1.21.1 的 RuntimeProof 使用实际同时更新加载与 TickingTracker 的 PLAYER 区域票据，半径 3 对应 level 30，而不是套用新版本的拆分票据 API。schema 3 逐一核对 49 个 FULL owner 的原生生成/加载 BIOMES 依赖，观察 ServerLevel 的真实实体与 block/fluid tick、以 Level 参数调用的熔炉 ticker。输出实际 MosaicPhysicalLevelState 创建数与 finalPhysicalMetrics；普通 Vanilla 创建数必须为零，B 在自然模拟期间不得新增 Artifact 或重复 SPAWN。它不伪造新版本才存在的 materializer 指标。无玩家实例在冷生成期间可能耗尽原生 300 tick 实体活跃窗口，所以仅在设施安装时调用一次 resetEmptyTime；记录之前的 emptyTime，随后 220 tick 仍完全自然执行，不循环唤醒或直接驱动回调。原因与保留失败见[空服休眠调查](../../documents/research/incident-empty_level_runtime.md)。以下场景在独立固定证据域中运行，具体通过范围只登记于里程碑：

```powershell
$env:MOSAIC_VALIDATION_TARGET = '1.21.1-neoforge'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension minecraft:the_nether -VanillaControl -RuntimeProof -EvidenceLabel ticking_activity -VerifyReload -Mutate -CpuAffinity 61440
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension minecraft:the_nether -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -LazyRings -EvidenceLabel ticking_activity -VerifyReload -Mutate -CpuAffinity 61440
# 三维度 Vanilla/B 的 fresh/reload 全部执行后，再独立只读复核当前源码和证据
& components/worldgen_validation/scripts/integration-production_lifecycle/Check-LegacyRuntime.ps1
```

只读复核要求当前输入指纹、独立 JVM、真实光照正确的 ENTITY_TICKING LevelChunk、完整自然回调和清理，并分别核对 fresh/loading 依赖、B 重载零再生成以及普通 Vanilla 零 Mosaic 状态。可显式用 `-Dimensions` 复核已完成的维度，但输出只声明所选范围，不能将两维度结果当成三维度完成。

1.21.1 新的物理交接验证使用 `-PhysicalHandoff`，只为工具区分独立世界与固定证据路径，不改变产品流程或提供区块。结果按 physical-handoff/维度隔离，防止不同维度互相覆盖，也不覆盖既有完整 Artifact/失败证据。探针每三十秒记录原生计划、实际 Artifact/部分 BIOMES 准备和发布数量；这些诊断使用控制台输出，判读须同时检查 console.log 与 latest.log，不能假定两条日志通道内容相同。全程仍用原有 180 秒预算；加载已有 FULL 的群系依赖按 loading pyramid 验证，不额外生成邻区。

该版本显式 `-LazyRings` 仅用于候选筛选，必须同时使用 DevelopmentB 与 PhysicalHandoff 或 RuntimeProof；世界与结果另隔离到 lazy-rings，不覆盖 eager 失败。它不是已默认启用的性能结论，仍需独立 Oracle 和相同负载配对验收。

本版 `-NarrowLocks` 另外要求 `-LazyRings` 和非空证据标签，结果放在独立 narrow-locks 目录。只改变候选共享状态的锁边界，不改变 180 秒预算或 physical dependency 前沿；fresh 必须记录实际缩锁 context，重载与普通 Vanilla 必须为零。

1.21.1 的 `-ReuseSources` 可与上述 cold SPAWN 或 RuntimeProof 单独使用，仍须 DevelopmentB、LazyRings、NarrowLocks，默认关闭。RuntimeProof 的结果另存 source-reuse；fresh 必须实际观测到复用命中，每份来源状态均已关闭且驻留为零、峰值不超过 441，reload 必须为零次 preparation。它只增强诊断，不改变生产时序或允许延长 180 秒；使用同指纹的独立普通 Vanilla 控制，再传 `Check-LegacyRuntime.ps1 -NarrowLocks -ReuseSources -EvidenceLabel source_state -Dimensions minecraft:overworld` 复核所选范围。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '1.21.1-neoforge'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension minecraft:the_nether -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -LazyRings -NarrowLocks -EvidenceLabel template_lock -VerifyReload -Mutate -CpuAffinity 61440
# 对应普通 Vanilla 控制不启用候选；其它维度分别运行，不借用控制结果
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension minecraft:the_nether -VanillaControl -RuntimeProof -EvidenceLabel template_lock -VerifyReload -Mutate -CpuAffinity 61440
& components/worldgen_validation/scripts/integration-production_lifecycle/Check-LegacyRuntime.ps1 -NarrowLocks -EvidenceLabel template_lock -Dimensions minecraft:the_nether,minecraft:the_end
```

需要区分源码/守卫的固定证据域时，physical-handoff 或 runtime proof 可传 `-EvidenceLabel guarded`：同时隔离世界名称与结果子目录，并在启动前记录源码输入哈希、CPU、合同和场景参数。标签描述验证目的，不按运行次数建目录；显式仅保全失败不会重写其条件文件，未传标签的既有路径不变。

当前 1.21.1 guarded 并发门禁先分别生成三维度的单请求 FULL/save/reload，再对每个维度执行 `-Mode concurrent -Repeats 32`；最后运行下列只读复核。它核对当前源码/输入、独立 JVM、相同 CPU、实际同一个 LevelChunk 以及单请求与多请求完全相同的 Artifact/BIOMES/POI/LIGHT/SPAWN/FULL 计数，不以“两个 future 都成功”代替去重，也不把这项正确性计时作为性能 A/B。

```powershell
& components/worldgen_validation/scripts/integration-production_lifecycle/Check-LegacyConcurrency.ps1
```

```powershell
$env:MOSAIC_VALIDATION_TARGET = '1.21.1-neoforge'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Dimension minecraft:the_nether -FeatureOrderingVersion 2 -DevelopmentB -PhysicalHandoff -VerifyReload -Mutate -CpuAffinity 61440
```

1.21.1 的 `-CompletionOrder` 单独验证 native worldgen 调用线程在输入 future 已完成后才注册回调的合法交错。探针只等待一个 focus EMPTY 请求的真实结果，不提供数据或替代 holder；必须记录一次强制时序及原生 holder 完成线程，越权即失败。独立世界与结果均使用 completion-order 后缀，不覆盖标准 FULL 或 runtime 证据；该项不作为吞吐测量，不能与 reload/runtime proof 混用。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '1.21.1-neoforge'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -FeatureOrderingVersion 2 -DevelopmentB -CompletionOrder -CpuAffinity 61440
```

### 自然生成数据的激活与保存

寻找自然正例时可用独立的被动清点模式；只记录真实 Production Publication，不额外请求 Chunk，不将候选存在当成激活通过。完整 NBT 与每个 owner 的种子、Artifact 身份分开保留，重载发布必须为零；此模式不能与 NaturalProof 共用单一观察器。

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -MasterSeed 123456789 -Dimension minecraft:the_nether -ChunkX -11 -ChunkZ -6 -FeatureOrderingVersion 2 -DevelopmentB -RuntimeProof -PublicationInventory -VerifyReload -Mutate -EvidenceLabel nether_natural_discovery -CpuAffinity 3840 -Offline
```

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1 -EvidenceLabel native_pending_activation -CpuAffinity 3840
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1 -EvidenceLabel native_pending_activation -CpuAffinity 3840 -CheckOnly
```

该入口分别使用真实硫磺模板、末地尖塔、地牢区域与试炼密室目标；-Fixture 可以选择单项，只证明所选范围。每项以当前 B 生产身份生成，保存退出后用新 JVM 重载。观测原有物化 Publication 的 ProtoChunk，保存原始实体 UUID、BE NBT、未实例化 pending 数量和 Artifact 指纹，不生成替代 physical 对象或修改 canonical terrain。硫磺模板还要求未实例化 pending 数量为正，不以同时存在于已实例化 Map 的 NBT 充数。

生成态 BE 按 26.3 原生激活分支建立不属于 Level、没有 ticker 的控制对象：普通 NBT 使用 loadStatic，DUMMY 使用实际方块的 EntityBlock.newBlockEntity，而不是把占位符当注册 ID 或丢弃后报通过。完整 saveWithFullMetadata 核对首次 LevelChunk 安装；保存后比较实际 SerializableChunkData.copyOf 数据经同一原生分支的重载安装，不假设运行期 NBT 不变，不删除字段。普通 getBlockEntity 会主动实例化，因此此处只读已安装 Map。天然实体核对原始 UUID、类型、唯一 manager 身份与重载；末地水晶还在实际 tick 方法返回点取证，重复同 tick 执行即失败。它不宣称全部运行期实体 NBT 冻结。

仍要求原生票据、49 FULL、ENTITY_TICKING、220 自然 tick、标记保存与重载不重新生成；原有人工设施单列，不充当自然正例。完整源 NBT、运行观察与真实保存 NBT 分开保留，关闭后释放 probe 引用。条件与源码绑定，已有自然结果或世界拒绝覆盖；未知加载转换、未命中和任何未解释差异均不通过，异步捕获异常在下一次探针 tick 直接暴露，不放宽 B 合同或有限实体规则。

下界 nether_chests 为单独显式正例，不改变 all 的既有四项选择；清点发现的 (-10,-10) 使用相同主种子 123456789。它核对真正的源实体与箱子激活，完整内容仍须另外运行独立原版对照，失败不能用生命周期结果抵消。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1 -Fixture nether_chests -EvidenceLabel native_nether_activation -CpuAffinity 61440
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1 -Fixture nether_chests -EvidenceLabel native_nether_activation -CpuAffinity 61440 -CheckOnly
```

## 结果

新结果写入 `result/current/<target>/` 固定位置，同一场景覆盖。实例目录为 `instance/<target>/<角色>/`；旧研究运行目录原地保留并忽略。`result/legacy/` 原样保留历史证据，不能据此宣称当前版本通过。具名的普通请求与自然激活证据拒绝覆盖，重验必须使用独立证据域，既有失败不改写。
