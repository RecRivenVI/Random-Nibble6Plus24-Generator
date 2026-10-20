# 世界生成性能与研究

## 目标

集中保存端到端 profiler、结构隔离、Lazy/Candidate 与 lightweight Oracle 研究；这些模式不是新的生产合同。

这是 Minecraft 26.2 Fabric、26.3 Fabric 和 26.3 NeoForge 分 Target 验证。工具属于 `worldgen_validation`，独立游戏内入口属于 `src/probe`，产品 Mixin 直接链接的默认关闭 hooks 留在产品中。NeoForge 仅启用普通原版/B 测量，不启用尚未适配的历史结构禁用或 Oracle 诊断模式。

## 运行

从仓库根目录执行。先由使用者在 `local.toml` 顶层写入 `eula = true`，不要代替使用者接受 EULA。只启动单个高负载 JVM；所有运行限制为 4 个可见处理器。

```powershell
& .\components\worldgen_validation\scripts\performance-worldgen\Run-LazyVerification.ps1
# 26.3 首个里程碑，分别 fresh 运行；默认全部结构开启，不使用结构禁用模式
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline b
# 同条件比较 B 无复用与会话内结构来源复用；开关仅在 probe 生效
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline b -ReferenceStructures
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline ordering2
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline vanilla
# NeoForge 原生生命周期入口；先短 smoke，再进行标准对照
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Target 26.3-neoforge -Pipeline b -View 2 -Simulation 2 -Seconds 20
# 三维度分别使用 fresh world，串行运行；每个维度都同时测原版与 B
foreach ($dimension in @('overworld', 'the_nether', 'the_end')) {
    & .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline vanilla -Dimension $dimension
    & .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline b -Dimension $dimension
}
# 只归并本次真正运行的原版/B，不要求不存在的 ordering2 对照
& .\components\worldgen_validation\scripts\performance-worldgen\Analyze-BPerformance.ps1 -Target 26.3-fabric -Dimension the_nether -Pipelines b,vanilla
# NeoForge 按同一三维度流程显式传 -Target 26.3-neoforge；六组及各自 Analyze 完成后归并
python components/worldgen_validation/scripts/performance-worldgen/compare_native_frontier.py validations/performance-worldgen/result/b_milestone/26.3-neoforge
```

当前参考端状态修正后的三维度对照使用 `-EvidenceLabel climate_state_frontier`，运行与 Analyze 均传同一标签；原始历史结果不覆盖。8/8 为 60 秒，高视距 12/12 和 16/16 为 45 秒短测，仍保持第 15/30 秒两次相同移动。只改观察视距与窗口，不改变生产调度。完成对应的三维度六组后分别归并：

```powershell
python components/worldgen_validation/scripts/performance-worldgen/compare_native_frontier.py validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier
python components/worldgen_validation/scripts/performance-worldgen/compare_native_frontier.py validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier --view 12 --seconds 45
python components/worldgen_validation/scripts/performance-worldgen/compare_native_frontier.py validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier --view 16 --seconds 45
```

结构状态覆盖诊断只在已适配的 26.3 Fabric／NeoForge B 显式开启；两端分别绑定自己的原生 NativeBiomeSearchState 与 source supplier。只更改 probe，不更改生产缓存，观察原生实际求值和最多十六个输入 token 的历史，并在结构 graph 交接时失效。诊断有额外开销，不能作吞吐对照，也不进入正常游戏或发行 JAR；其它 Target 仍明确拒绝。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Pipeline b -StructureReuseAudit -EvidenceLabel source_state_audit
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Analyze-BPerformance.ps1 -Pipelines b-source-audit -EvidenceLabel source_state_audit
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target 26.3-neoforge -Pipeline b -StructureReuseAudit -EvidenceLabel source_state_screen
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Analyze-BPerformance.ps1 -Target 26.3-neoforge -Pipelines b-source-audit -EvidenceLabel source_state_screen
```

NeoForge 本版筛选对三个 Dimension 分别传参；当前固定批次使用 source_state_screen，结束后不覆盖。另用 source_state_screen_control 运行默认关闭控制，要求没有 reuseAudit 计数。openScopes 与 closedScopes 必须相同、实际 supplier evaluations 与仓库计数逐项一致，关闭后不保留 token。只观测原生输入对象身份与耗时，不恢复入口／出口、不返回历史结构对象、不修改读写或异常；有限重复不自动等于已证明缓存等价或达到性能保留门槛。

诊断必须实际命中原生求值和关闭，最终 reuseAudit.openScopes 与 closedScopes 一致，evaluationCountMismatch／retainedAfterClose 为零。只观察真正调用 Supplier.get 的原生求值；单条缓存命中不改变输入历史的最近顺序。repeat2／4／16 只表示原生实际重算时相同输入曾在对应容量历史内出现；不是已实现的缓存命中、正确性证明或预测提速。

其他矩阵与子场景的脚本在同一工具目录。脚本负责隐藏 preset、输入与系统属性，不直接使用日常实例。后续不要求人工游玩；自动采样没有真实客户端发送时，发送与渲染仍标记未测。

### 当前主世界 Goal 最小配对

当前主世界 Goal 的最小样本先运行同一固定条件的一个 Vanilla／B 配对，不要求先测无关维度；三个交错配对及高视距门槛仍由当前里程碑要求。以下是四逻辑 CPU、低优先级、8/8、六十秒、两次固定移动的第一配对；结果标签固定，不覆盖已有证据。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target 26.3-fabric -Pipeline vanilla -View 8 -Simulation 8 -Seconds 60 -EvidenceLabel goal_baseline_pair_one
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target 26.3-fabric -Pipeline b -View 8 -Simulation 8 -Seconds 60 -EvidenceLabel goal_baseline_pair_one
& components/worldgen_validation/scripts/performance-worldgen/Analyze-BPerformance.ps1 -Target 26.3-fabric -Pipelines @('vanilla', 'b') -View 8 -Simulation 8 -Seconds 60 -EvidenceLabel goal_baseline_pair_one
python components/worldgen_validation/scripts/performance-worldgen/compare_native_frontier.py validations/performance-worldgen/result/b_milestone/26.3-fabric/goal_baseline_pair_one --dimension overworld
```

新 JFR 汇总保留整机 machineTotal 与游戏 jvmUser／jvmSystem 原始 CPU 样本；缺测写 null，不以游戏 CPU 冒充整机负载。B_WORK_METRICS 只在显式 profiling 的 probe 中发出轻量计数，不保存地形／NBT／证明快照。fullCohortWork 只统计窗口内真正 FULL 的 owner，列出缺测；windowWorkPerFull 另列包含未完成工作的整窗摊销，不能把后者直接称为某个已完成 Chunk 必需的额外成本。

物理 BIOMES 缩锁候选只复用参考端已审计的 scoped source 入口，不改变候选、pieces 或 stored palette。仅 probe 的 `-ReferenceBiomeLocks` 恢复整段模板锁；生产不读取该属性。先通过独立 BIOMES Oracle 与部分保存重载，再进行同源受限筛选；没有显著端到端收益不保留。以下结果标签禁止覆盖：

```powershell
& components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target 26.3-fabric -Pipeline b -ReferenceBiomeLocks -EvidenceLabel biome_lock_screen
& components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target 26.3-fabric -Pipeline b -EvidenceLabel biome_lock_screen
& components/worldgen_validation/scripts/performance-worldgen/Analyze-BPerformance.ps1 -Target 26.3-fabric -Pipelines @('b-biome-lock-reference', 'b') -EvidenceLabel biome_lock_screen
python components/worldgen_validation/scripts/performance-worldgen/compare_biome_locks.py validations/performance-worldgen/result/b_milestone/26.3-fabric/biome_lock_screen
```

`PHYSICAL_BIOMES_STRUCTURE_READY` 区分真实缩锁与参考路径；JFR 的 workCpuSamples、storedBiomeCpuSamples 与 workMonitorBlockedMs 按最内层实际任务分类。worker 在 join 中帮助执行另一任务时不把后者错误归给外层等待者；线程累计阻塞不是单目标的 critical path。

nativeBuildNanos 等原有同步跨度包含 join 的等待和帮助执行其它任务，不能相加当作纯 CPU 成本。storedBiomeCpuSamples 只表示栈内出现调用者，nativeBiomeCpuSamples 才要求实际原生 doCreateBiomes 帧；后者没有 owner 时保留 other，不猜测归属。

准备义务的最小诊断使用二十秒主世界样本，只有第十五秒的一次移动，不冒充六十秒双移动性能门槛。修正后的 PLAN_SHAPE 只记录阶段分布，不为每条依赖发大批标记；PREPARE_REQUIREMENT／PREPARED_REQUIREMENT 只读取实际 ConcurrentHashMap，不创建计划、票据、future 或 Chunk。null 保留为未观察到的请求，不能当作无需生成。

```powershell
foreach ($target in @('26.3-fabric', '26.3-neoforge')) {
    & components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target $target -Pipeline b -View 8 -Simulation 8 -Seconds 20 -EvidenceLabel preparation_scope_smoke
    & components/worldgen_validation/scripts/performance-worldgen/Analyze-BPerformance.ps1 -Target $target -Pipelines @('b') -View 8 -Simulation 8 -Seconds 20 -EvidenceLabel preparation_scope_smoke
    python components/worldgen_validation/scripts/performance-worldgen/summarize_preparation_requirements.py "validations/performance-worldgen/result/b_milestone/$target/preparation_scope_smoke/b-duration20-v8-s8"
}
```

completedWithPreFeaturesObligationAtBothSnapshots 只列开始与结束均为 BIOMES／TERRAIN 的已完成准备；不含未完成任务，不表示整次探索的最高未来需求，更不能单凭它宣称安全移除 FEATURES。部分 TERRAIN 还须审计 holder 缓存中的旧对象、装饰后的光照更新及 native 保存升级边界。

## 通过标准

1.20.1 Forge 使用该版本独立实现的原生票据探针。三组 vanilla、b_eager、b_ring 分别为普通原版、默认保守 B、显式状态安全有限环候选；不支持其它版本的缩锁或 source 复用。候选默认关闭，算法未通过端到端保留门槛前不切换生产默认。

```powershell
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline vanilla -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline b_eager -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline b_ring -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline vanilla -Dimension overworld -View 8 -Case screen -CheckOnly
```

本版冷起点同时检查全部维度的 updating holders、loaded count、physical generation/publication/in-flight/SPAWN/FULL 计数与计时前 worker；仅跳过离线票据 token 构造期间的一次 fudgeSpawnLocation，不跳过正常玩家生命周期。视野按本版 ChunkMap.isChunkInRange，generation FULL 和三类 promotion 分别观察原生 Either future。SEND_READY 只代表原版 getTickingChunk 的发送前置条件，不代表已发包、客户端画面或玩家出生。

结果固定在 result/legacy_frontier_cold/1.20.1-forge 下，与其它 Target 隔离；其余单实例四 CPU、低优先级、固定两次移动与截尾规则相同。ringBiomeSearches 只在原生 ring supplier 内计实际完整搜索，不能把全局任意群系查找误算为要塞搜索。诊断 hook 只属于 probe，日常产品不加载。

1.21.1 NeoForge 使用本版独立原生前沿探针，不复用 26.3 的拆分加载/模拟票据 API。编排限制单个四逻辑 CPU、BelowNormal 游戏，不与正确性队列重叠；每组 fresh world，60 秒，15/30 秒各沿 X 移动两 Chunk。使用原生 DistanceManager.addPlayer/removePlayer 的离线 source token，不登录、不注册玩家实体；视野形状用原生 ChunkTrackingView。

```powershell
& components/worldgen_validation/scripts/performance-worldgen/Run-LegacyPerformance.ps1 -Pipeline vanilla -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-LegacyPerformance.ps1 -Pipeline b_eager -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-LegacyPerformance.ps1 -Pipeline b_ring -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-LegacyPerformance.ps1 -Pipeline b_narrow -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-LegacyPerformance.ps1 -Pipeline vanilla -Dimension overworld -View 8 -Case screen -CheckOnly
```

四个 pipeline 分别为普通 Vanilla、默认保守 B、显式有限环 B、显式有限环加缩锁 B；最后两项仍是默认关闭的候选。首次筛选有收益才使用固定 pair_a/pair_b/pair_c 角色做三组交错配对，不按运行次数建立目录。下界/末地通过 Dimension 选择，短测高视距用 View 12 或 16；不改变生产线程模型。

结果固定在 `result/legacy_frontier_cold/1.21.1-neoforge/<dimension>/v<view>/<case>/<pipeline>/`。summary.json 保留全部实际阶段、worker 等待/执行跨度、请求与 holder 可用状态，timeline-summary.json 由工具归约。没有在窗口内完成的请求保留截尾区间，不伪造为零，也不从 p50/p95 中删掉；无法识别的分位数保持未知或给出上下界。全请求与最后视野的保留请求分别统计，过时视野请求不冒充持续等待。getChunkToSend 只证明服务端发送就绪，不证明 packet、首次玩家进入或客户端渲染。

本版 ServerPlayer 构造器会执行实际出生点搜索。因此仅创建离线票据 token 的线程局部作用域绕过构造器这一次 adjustSpawnLocation，之后立即释放；不是正常玩家，也不以此证明真实出生。探针必须记录恰好一次隔离，并在 token 创建后检查所有维度没有已加载 Chunk、没有 Mosaic 准备状态，才开始计时。旧 legacy_frontier 的初版记录未满足此边界，原始失败保留，不用于 A/B。

JFR 只覆盖实际票据负载窗口，分析继续使用已有 AnalyzeRecording；累计线程结构耗时不等于端到端墙钟占比。60 秒停止后的保存/预期关闭取消不混入测量，真实失败、新崩溃报告或缺失证据仍失败。MEASURED 只表示负载与采样完成，不是 worldgen correctness PASS。

1.21.1 的 `b_reuse` 在 `b_narrow` 的同条件基础上仅增加默认关闭的目标内结构 source 复用；筛选使用 `-Case source_screen`，单实例依次运行 vanilla、b_narrow、b_reuse。归并使用 `python components/worldgen_validation/scripts/performance-worldgen/summarize_legacy_performance.py --compare-reuse <结果组目录>`，三组源码指纹和资源必须相同，复用命中与实际求值均须为正。只有端到端指标有辨别力才继续三组配对，不用内部结构调用减少冒充吞吐通过。

若固定 60 秒窗口的 FULL/send-ready 都被截尾，可用同一工具追加 `-Case source_observe -ObserveSeconds 180` 做一次有界诊断。请求轨迹仍只有第 15/30 秒两次移动，不增加移动、缩小视野或改变调度；只是延后关闭，让原生工作继续完成。结果独立写入 `source_observe-duration180`，以实际 180 秒时长归约，不能混入标准 60 秒组或称为最大吞吐；不修改任何生成/就绪/正确性超时。该诊断上限不继续延长，仍无法辨别时按停止规则结束当前假设，不以无限观察制造通过。

SPAWN 邻区碰撞修复后的同条件重验使用 `collision_screen`，三组交错配对使用 `collision_pair_a`、`collision_pair_b`、`collision_pair_c`，固定 180 秒。每组仍分别运行 vanilla、b_narrow、b_reuse 并归并；专门的 `collision_screen` 复用组必须实际命中 `populationCollisionReads > 0`。所有 fresh 复用组要求 `populationColdCollisionSources == 0`。配对的自然完成前沿可能尚未走到边界，零次读取只表示未覆盖，不能代替专门回归，也不能误记为游戏崩溃。原 `pair_b` 真实失败保持原样，不能拼接其它指纹的成功结果。

三组全部完成后执行下列汇总；它检查九个独立 PID/run identity、相同源码与资源条件、原始文件 hash。基线为零时只给绝对增加量，不制造无限改善百分比；重叠的截尾 p95 仍为 UNKNOWN，完成物理区块数量不同时内存门禁仍待审，不自动批准保留优化。

```powershell
python components/worldgen_validation/scripts/performance-worldgen/summarize_reuse_pairs.py validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_pair_a-duration180 validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_pair_b-duration180 validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_pair_c-duration180
```

固定资源与输入轨迹，分别报告 correctness、吞吐、等待与候选数量；D1 历史结果为 PARTIAL，不能以候选减少代替性能成功。

26.3 B 前沿测量使用四逻辑 CPU、低优先级、单 JVM、相同 8/8 与 60 秒轨迹，结果在 `result/b_milestone/26.3-fabric/<pipeline>-v<view>-s<simulation>`。当前源码重验使用 `-EvidenceLabel climate_state` 等明确条件标签，结果改存 Target 下对应标签目录，世界也加后缀；同标签已有结果或世界直接拒绝覆盖，不移动历史证据。条件文件继续记录当前输入指纹。普通 PLAYER ticket 驱动生产请求；不登录假玩家、不发客户端 packet、不修改 scheduler。CLI 跳过初始出生准备，不能作为首次玩家就绪或实际渲染证据。`ordering2` 是保留的 scheduler Ordering 2 路径，不冒充单独的原 B 模块。错误运行不能计入吞吐结果。

`-Dimension` 默认主世界，下界/末地在 pipeline 名后分别追加 `-the_nether`/`-the_end`，不混合或覆盖主世界计时。必须在 conditions.json 和实际请求事件中核对目标维度，不能以世界存在三个维度就宣称三维度采样通过。新 Target 要先完成对应 probe 适配才加入本验证，不以空入口接入冒充可测。

短 smoke 使用 `-View 2 -Simulation 2 -Seconds 20`，非 60 秒结果名追加 `-duration<seconds>`，不覆盖标准负载。短 smoke 只证明维度入口、真实请求、采样和关闭可运行，不作为性能收敛结论。

`compare_native_frontier.py` 读取三维度六组 8/8、60 秒结果，核对相同源码/资源/轨迹、六个独立 JVM 与 fresh world identity、实际四 CPU 和相同原生请求集合。当前视野使用原生 VIEW_DEMAND/VIEW_WITHDRAWN 集合，不用理论正方形代替；已撤销请求另列。未就绪请求继续保留在 p50/p95 区间，无法识别时不给点值；相对 Vanilla 的吞吐与延迟分别报告。固定输出 vanilla-comparison.json，单组对照只作基线，不自动通过优化保留门槛。

当前里程碑在两个 26.3 Target 的主世界分别测 8／12／16 视距，每档三组 Vanilla／B 交错配对。先按相同 EvidenceLabel 执行两种 pipeline 和 Analyze-BPerformance，再用以下归并；示例中的三个标签必须是实际独立运行目录，不复用 PID、世界或改变输入。输出保留每组的截尾延迟区间，吞吐比取三组比值的中位数，不把未完成需求剔除或改成零。

```powershell
python components/worldgen_validation/scripts/performance-worldgen/compare_native_frontier.py validations/performance-worldgen/result/b_milestone/26.3-fabric --dimension overworld --view 8 --pair-labels goal_final_first goal_final_reverse goal_final_forward
```

离线 JFR 汇总另外保留 RandomState、隔离上下文初始化、种子本地生成器和 Artifact 的包含式 CPU 样本，用于区分实际初始化与嵌套结构／地形工作。包含式调用栈不能相加，也不能直接换算为可删除的墙钟时间。

H1 的物理 BIOMES 候选控制只支持参考 Target：`-Pipeline b -ReferencePhysicalBiomes` 由 probe 关闭部分阶段能力、恢复完整 Artifact 参考，其余输入/资源/轨迹相同。结果独立存入 `b-physical-biome-reference-v8-s8`；无此开关为候选。必须先通过独立 72 项和结构部分阶段 Oracle、部分保存重载/完整升级门禁，再筛选并决定是否进行三组交错配对。不把减少 Artifact 数量或模块耗时当作端到端改善。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Compare-PhysicalBiomes.ps1
pwsh -NoProfile -File components/worldgen_validation/scripts/performance-worldgen/Compare-PhysicalBiomes.ps1 -CheckOnly
```

该命令先检查三维度部分 BIOMES/升级/重载门禁，再按 reference→candidate、candidate→reference、reference→candidate 执行三组同源、同资源测量，固定写入 `result/b_milestone/26.3-fabric/physical_biome_comparison/pair-0` 至 `pair-2`。六个实际进程 ID 和 fresh world nonce 必须不同；候选必须真正执行 partial BIOMES，参考必须为零。归并同时报告完整请求集合的有限观察时间、共同就绪目标的延迟，以及未完成数量；不能仅比较两侧不同的完成子集来宣称 p95 退化或改善。未完成请求只保留观察下界，不伪称已完成；报告始终为 MEASURED，不能越过正确性、内存和波动审查自动保留候选。

按输出 JSON、异常链、资源与副作用断言判断，真实错误不能被吞掉。构建通过不代替 Runtime、Functional 或 Visual 通过。

汇总保留请求至 canonical、物化、LIGHT、FULL、ticking 和 send-ready 的中心目标时间线，以及 p50/p95、CPU 时间差推导的核心占用和四核预算占比。可发送不等于已发包或已渲染；有限前沿提前完成时，固定 60 秒完成数不是最大吞吐能力。普通原版对照出现 Mosaic preparation 或 Mosaic Chunk 标记即失败。

## 结果

新结果写入固定 `result/` 位置；当前里程碑使用明确的条件标签，标签下已有结果或世界时拒绝覆盖，保留原始失败。实例目录为 `instance/<target>/<角色>/`；旧研究运行目录原地保留并忽略。`result/legacy/` 原样保留历史证据，不能据此宣称当前版本通过。
