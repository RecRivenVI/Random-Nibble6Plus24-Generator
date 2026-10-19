# 多 Target 准备复用实验

调查日期：2026-10-16。本文保留H121-S、H1 及关联的独立对照和性能实验的历史记录，从里程碑的逐轮正文迁出；没有重跑、改写证据或变更验收结论。当前状态以[里程碑矩阵](../project/milestone-features.md)为准。

## 问题

如何区分模块、实际游戏、历史指纹、当前生产生命周期和性能证据，避免把某一阶段的通过扩展为整个 Target 或其它维度的通过。

## 方法

整理里程碑中已有的H121-S、H1 及关联的独立对照和性能实验记录，按原有先后保留内容、数值、失败和限制；仅调整文档归属、相对链接与失效的账本维护声明。原始 result、世界、日志、SHA、签名及源码不改动，不把本次文档整理记作新实验。

## 发现

以下保留候选假设、失败、纠正和分阶段结果。筛选计时不提升为正式端到端收益，旧源码指纹不替代当前源码验证。

1.21.1 的移植基线另登记候选 H121-S（实施 1/2、已编译，独立 Oracle 执行中）：主世界 B 模块的历史初始样本约 121～138 秒。源码进一步确认不是只建九套状态：九份 CARVERS 输入各自又为八个邻区群系准备状态，共 81 套 generator/RandomState/StructureState/StructureCheck。候选只在一个 B 目标的存活准备阶段借用同一原生世界状态，保留九份精确地形、所有 stored palette 与原始结构算法；借用范围从 FEATURES 地形圈和 SURFACE 群系圈相加推导，当前为二圈。外部部分阶段查询仍保守独立求值。不跨 physical Target、seed/save 或已关闭 owner 共享，不缓存整个虚拟图。旧独立准备入口不变，新增入口未注册生产。

H121-S 首个原点 fixture 的两个 Native 与两个无关宿主完整结果均已一致；消费者实际为一套 world state、80 个借用、一次 ring preparation、128 次 biome search、liveContexts=0，模块总耗时约 4.4～4.7 秒。这只是筛选样本，不能与历史单次时长拼成正式 A/B 或 physical request→ready 收益。不同 seed/维度资源分离、owner/borrow 关闭和范围拒绝的真实游戏断言已通过；八项 JUnit、13 项工具测试、49 项脚本语法检查通过。完整三维度、寒冷与结构 B 仍在顺序验证，之后才决定三组交错 A/B 与生产接入，不移植该候选到其余 Target。

H121-S 初始完整三维度 smoke 4/4、cold 1/1 已通过，分别 16/4 个独立 JVM/save，仍为根 UUID 唯一例外。首批结构 B 的村庄三项、矿井两项、试炼密室三项已对齐；真实数据包括目标结构放块、pending BE、矿车实体、block/fluid ticks 和后处理，宿主 POI/实体/scheduler 不变。但整套尚未通过：Monument 起点的 Native 控制在 seagrass_simple → CarvingMaskPlacement → PlacementContext.getCarvingMask 将 ChunkAccess capability 转为 ProtoChunk 时崩溃。该失败发生在非复用的 Native 控制，不归因于状态共享；原始 crash、输入、部分 RNG 链与失败世界保留在 first_content_failure/structures-shared/1.21.1-neoforge 及验证实例归档，未启动性能对照。

掩码能力修复只接入明确的 StrictFeatureRegion：目标使用精确原生掩码，邻区的只读视图逐项委托 bits、stream 和 additional predicate；不把整个 capability 改成可变 ProtoChunk，也不跳过海草。Native 控制另直接读取自己的真实物理 ProtoChunk。该版原生双参数 getChunk 的 EMPTY 要求已从 bytecode 核对，未强加 CARVERS。两项读写/附加谓词回归与既有八项单测、13 项工具检查、50 项脚本语法检查已通过。修复后 ocean 1/1、四个 fresh JVM/save 已实际 PASS，四次实际 mask 读取和完整结果相同、物理 POI 不变、bindings/liveContexts 为零；新源码的三维度 smoke 4/4 也已重验通过，完整结构仍待完成。

1.20.1 的同名原生接口也已逐条核对：PlacementContext 同样强转 ProtoChunk，CarvingMask 的五个公开读写入口相同，原生独立结构 fixture 恰好也位于 seed 0 的 (86,-12)。本版独立源码已补同一能力边界和 SRG 安全 probe，不共享 Minecraft 源码；main/probe 已编译，ocean 1/1、三维度 smoke 4/4、cold 1/1，共 24 个 fresh JVM/save 已实际 PASS。实际掩码读取为正、寒冷地形有真实雪层和原生亮度逐次交叉核对、物理 POI 不变、绑定归零；仍只证明本版 B 模块，不冒充生产生命周期或性能收敛。

1.21.1 共享状态完整结构套件已比较前十二项（村庄、矿井、试炼密室、Monument、Fortress 的 start/邻区/halo），随后在 End City 第二个原生重复发生严格失败：仅三个 minecraft:shulker 的 Rotation[0] 不同，装饰前、实际外圈输入、结构与 feature RNG 均相同。原始失败另完整归档在 original_yaw_failure/1.21.1-neoforge，没有改为 PASS，也没有启动准备期 A/B。实际本版字节码表明 Sentry marker 创建 Shulker 后未覆盖 LivingEntity 构造器的 Math.random 朝向。

该版末地因果专项已通过四个 fresh 原生 JVM/save：两个实际 Math.random 三次输入捕获、两个交叉重放，三个真实目标 Shulker 均关联到原生 marker，完整 UUID-only NBT 在两个方向精确复现，读取/结构/feature RNG 和其它字段相同。原始输入、double/float raw bits 与双向差异在 end-yaw/1.21.1-neoforge，scope 明确为 NATIVE_CAUSAL_PROOF_NOT_B_PARITY。首次诊断因 WrapMethod 重命名导致既有物理 escape 守卫拒绝，失败保存；改成单个 create 调用的 WrapOperation 后通过，未放宽生产守卫。

有限 Sentry Rotation[0] 验收现已编译，并以当前源码重新通过四进程双向因果专项；每个比较值必须对应本次真实构造器观察，未观察值拒绝，恢复 yaw 后其余 NBT 不变。随后独立 End City start 的两个 Native 和两个无关宿主 1/1、四个 fresh JVM/save 已 PASS，每侧三只真实 Shulker、host liveContexts=0，保留 raw、UUID-only NBT/hash 与实际 Math.random raw bits，生产随机输入没有重放或确定化。固定诊断目录为 structures-shared-one-end_city-start/1.21.1-neoforge；这是单案例而非完整 18 项 B 通过。完整套件和 H121-S 配对性能仍待完成，因果证据与当前 Target 输入指纹须一致才可用于后续验收。

参考 Target 原版组合的首轮前三项审查为：按需邻区及目标噪声/气候计算、仍需真实生成的结构、物理就绪依赖。当前锁和 GC 证据没有支持优先优化它们；不沿用历史锁热点结论。

候选 H1 已开始最小模块实现，实施 1/2、尚无性能实验：核对 B 的物理原版装饰前依赖是否触发不必要的其它目标 canonical 工作。该基线窗口内 canonical span 711 次，而 FULL 418；这包含未完成及必要邻区工作，不能直接把差额视为浪费。真实字节码中 ChunkGenerationTask 多处直接读取静态 generation/loading pyramid，没有可直接覆盖的 GeneratingChunkMap.getGenerationPyramid 方法。

H1 的预期是仅减少被证明确实不被 B 读取的物理装饰前邻区需求，保留 canonical 内部依赖、旧存档语义、真实 LIGHT/SPAWN/FULL 与 ticking 邻区。先用请求/状态追踪闭合因果；不能因 B 单 writer 就粗暴取消后续邻区就绪。若必须大范围重写调度器、使用假 BIOMES/空区块或不能保持生命周期，则拒绝该候选。最多两轮实现/实验；筛选有收益后才做三组交错 A/B，仍须达到至少约 5% 的端到端改善、无 p95/内存与正确性退化。当前没有改动 scheduler、物理依赖或生产算法，也没有新增优化收益结论。

本版已执行的 plan 单元测试确认 LIGHT 的物理前沿为 529 个 holder 状态、其中 49 个 biome-bearing Artifact 义务（中心 LIGHT、8 个 INITIALIZE_LIGHT、16 个 TERRAIN、24 个 BIOMES，其余 structure-only）；这不能直接解释成 529 次 canonical。基线 JFR 的直接请求为 FULL 430 次、INITIALIZE_LIGHT 515 次，涉及 520 个 key，时间线有 718 个 canonical 开始。请求/完成/必要光照邻区不是同一集合，后续须按真实因果归属验证，不把所有差额认定为可删除工作。

本次进一步核对同一基线的计划依赖边：718 次开始全部属于不同 key，同目标重复开始为零；198 个开始目标没有直接 FULL/INITIALIZE_LIGHT 请求，其中 100 个只收到 BIOMES 计划要求、98 个只收到 BIOMES/TERRAIN 要求。该事实把 H1 收敛到物理装饰前就绪要求，而不是去重或锁优化；尚未证明这些邻区在后续环境属性、光照和 ticking 中完全无用，不计作可删除工作或已取得收益。

H1 源码审计进一步确认，当前 materializationObligations 会把任何非空装饰前请求提升为完整 FEATURES Artifact，包括仅需要真实 BIOMES 的物理环境查询。候选仅在新 B 实现中区分“精确 S(N) 的原生 BIOMES 准备”与“必须完整 materialize 的目标”，而不是删除 Vanilla 邻区或伪造 palette：TERRAIN/LIGHT/ticking 仍必须真实生成，旧 mosaic 路径不变。当前新增未分派的 DetachedBiomeInputs 模块和 biome-only 原生对照入口，复用本版 createBiomes 与紧凑结构；26.3 实际实现自行释放 density-buffer pool，ChunkAccess 不持有旧版 NoiseChunk，不能照搬旧版缓存脱离假设。模块已编译，三维度独立 smoke 4/4、八个 fresh JVM/save 已通过，包含全部原生 NBT、部分区块 codec 精确往返及关闭 owner 后重读；七类结构与 72 项矩阵正在串行执行。这不是生产接入、服务器重载通过或优化收益。生产切换前须验证 holder future 升级竞争、部分 BIOMES 保存重载及后续完整 Artifact 内容；任一无法精确保留即拒绝。

H1 首次结构套件在第十项 Monument 邻区被新增的完整原生 NBT 检查拦下：仅两个 Mineshaft reference long 的顺序互换，palette、快照和其它数据一致。实际本版字节码确认 addReferenceForStructure 创建默认 LongOpenHashSet，序列化直接 toLongArray，装饰会按集合迭代消费 starts；不能盲目把顺序当作无意义差异。原因是紧凑输入复制时重建较小表容量。新部分阶段入口现使用克隆原生表布局的独立复制方法，未知容器拒绝；现有 B copyReferencesTo 入口不改，未扩大生产优化。两个针对性回归和参考 Target 全部 230 项单测已通过，修正后 smoke 4/4、八个 fresh JVM/save 已重验 PASS；结构套件在完整保存 DIVERGED 原始数据和失败世界后重新运行。此为同一候选的正确性校正，实施仍 1/2、性能实验 0/2，不计作收益。

修正后的 H1 biome_only_structures/26.3-fabric 已完整 18/18、36 个独立 JVM/save PASS，并另用 CheckOnly 复核：七类结构 start/邻区、村庄及试炼密室 halo、两个派生种子全部严格一致，包括完整原生 BIOMES NBT、结构投影、逐 bit Beardifier；十八份消费者均在关闭 owner 后重读快照和原生序列化数据，virtual holders=0、一个真实 ProtoChunk，FEATURES=0。原始失败与失败世界已确认分别存在 first_failure/biome_only_structures/26.3-fabric 和验证实例的 first-failure 归档。随后同源码 biome_only_matrix/26.3-fabric 完整 72/72、144 个 fresh JVM/save PASS，亦另用 CheckOnly 复核；保持原点 8、非原点 32、下界 16、末地 16 的全部原生 NBT 与脱离 owner 后重读门槛。仅完成独立模块正确性，未接入生产或证明优化收益。

H1 的候选生产接线已编译，参考 Target 全部 231 项 JUnit、十三项工具测试和 51 份脚本语法检查通过，运行验收中：partial BIOMES 使用单独类型的 in-flight/publication，完整 Artifact 的 AWAITING_PUBLISH 不混入部分对象。部分完成仅删除确切 BIOMES 义务，不覆盖更高要求；worker 使用既有世界关闭边界，正常/异常关闭清理各自的 future、待发布对象与计数。原生部分存档重载后，后续完整生成仍由独立 S(C) canonical 入口完成。仅 validated B 且已核对的原版模组环境可试用，未知模组组合保留完整 Artifact 回退；Loader 模组集只在 JVM 初始化时取值，世界身份仍逐实例验证、热路径不 stat 文件。依赖图、半径、SPAWN、LIGHT 与旧路径不改。

候选的新增自动验收按四个 fresh JVM 保存/重启推进：普通 BIOMES 的十六次重复请求单实例、与 FULL 同时升级只捕获一次 Artifact、保存部分阶段后原生加载控制严格一致且目标不重算、再升级 FULL 并验证修改/save/reload。命令见 integration-production_lifecycle 的 -PartialBiomes；新增 plan 单测要求所有原版 physical dependency 完全不变。性能工具新增仅 probe 的完整 Artifact 参考开关，待上述门禁通过才做同条件 H1 筛选；实施仍 1/2、性能实验 0/2。当前不可把接线代码当作生产已通过或已取得收益。

首次 H1 生产试验的 BIOMES 十六请求与同时 FULL 升级均已通过，target-only BIOMES 捕获 Artifact 为零、并发升级目标恰好一次；重启后被严格快照拦下，仅 Heightmap keys differ。实际 26.3 SerializableChunkData.read 会为 persisted status 补建缺失高度图，不能把“只重载 starts”的控制当作完整原生加载。probe 已改成原生 parse/read 全流程，并在控制对象上显式以 S(C) 解码 starts，既保留全部高度图比较，也避免由物理 seed hook 共同误答；生产生成算法未为此改变。首次 raw/expected/crash/失败世界由任务完整归档到 partial-biomes-first-failure，修正后的四 JVM 验收正在运行。此为控制边界修正，不计性能收益或扩大忽略项。

修正后的 H1 已实际完成三维度各四个 fresh JVM/save 生命周期：主世界 (125,-37)、下界 (-11,-6)、末地 (2,0)。每处部分 BIOMES 的十六请求同对象且 Artifact 为零，另一个有限前沿的 BIOMES/FULL 同时升级得到同一 FULL、恰好一份 Artifact；部分重载完整数据与 native-load 控制一致，目标 biome preparation 为零；之后真实 FULL 目标一次生成/发布/SPAWN，修改后重启 canonical/Artifact/SPAWN 为零、marker 保留。实际 partial in-flight/pending 在观察时为零，没有新增崩溃；全部 raw generation/native-load/reload NBT 保留。此项不替代全部实体/tick 激活或客户端验收。首次受控 H1 参考/候选 8/8、60 秒筛选正在串行采样，性能实验 1/2，尚无收益判定；三组正式配对尚未开始。

H1 首轮筛选现已完成，旧 Artifact 参考/候选为同源码指纹 63B54C32DB33C3965C0C328C470C28CB21C646CDBB9E442D3802F3A13A1915E2、四 CPU/低优先级、相同 357 个实际请求。60 秒内 canonical 完成 696→598（这是工作量减少，不能将 canonical/s 下降当作性能退化或提高）；FULL 406→418、send-ready 326→340。全部需求的有限观察等待均值 38.00→35.21 s，改善 7.35%；共同完成的 293 个 key 的中位数 31.29→27.79 s、p95 52.45→50.04 s，未完成需求 64→51；峰值已用堆约 956→766 MB。首次中心 send-ready 20.25→15.58 s 仅为 CLI 代理，不是玩家登录或渲染。候选实际产生 581 份部分 BIOMES，表明后续 FULL 可能再次准备其结构/biome；不能把它都说成免费工作。

两侧各自完成子集的原始 p95 为 52.45→56.73 s，但子集数量不同，不能据此下同输入 p95 结论。新增有五项工具回归的配对归并器明确保留未完成请求的观察下界、共同完成 key 的条件分布和未完成数量，不伪称完整无删失延迟，也不扩大世界生成忽略项。以上只是 SCREEN_NOT_RETENTION，不足以保留候选；三组 AB/BA/AB 物理对照已启动，记录六个 fresh JVM 与 world nonce、同源/同资源、全部需求及 p95/内存，不达门槛即放弃，不自动继续细调此候选。

配对前还收拢了 H1 的诊断边界：普通探索不为每个 BIOMES 目标长期保留计数，只给显式 probe 选中的 key 建表、世界关闭清除；原版能力名单改为本次真正验证的基础模组和三个官方 Fabric API 模块，不放行任意 fabric- 前缀。参考 Target 231 项单测、工具检查和主世界四 JVM 生命周期已再次 PASS；生物群系准备、生成结果与旧世界分派未改变。原生加载控制文件从后续运行起按维度/坐标分别命名，避免不同场景共享临时控制路径；各次 raw native-load NBT 一直按目标独立保留。正式配对使用此次收拢后的同一源码，不混入前一轮筛选指纹。

H1 三组交错物理对照已完整运行并另 CheckOnly 复核，证据为 physical_biome_comparison/pair-0 至 pair-2、六个不同 PID/world nonce；同一源码 7D1AFBDCCCE9F3E6504F9433BE772C87389825CB1F8E7AEC5C3FD02D7F854F8B，每侧都为相同 357 个需求。全部需求的有限观察等待均值分别改善 10.44%、7.01%、9.16%，改善率中位数 9.16%；共同完成 key 的 p95 三组均下降（54.34→46.94、52.55→49.73、53.66→49.34 s）。FULL 中位数 389→418、send-ready 311→340；是同固定窗口的结果，不称最大吞吐或真实玩家看到。

内存审查没有只挑有利数据：采样堆峰值中位数约 909→773 MiB，但第三组候选单次峰值约 995 MiB，高于参考约 852 MiB；从同一原始 JFR 补提取的 GC 后中位堆三组为 349/347/353→348/325/332 MiB，GC 后峰值中位数约 838→739 MiB，未见持续保留增长。加权分配总量中位数约 43.71→46.11 GiB，随完成 FULL 数增加而上升，按 FULL 归一约 115→113 MiB；GC 暂停中位数约 1.065→1.110 s/60 s，仍需保留为成本，不称零分配或零 GC。部分 BIOMES 总数约 580，不能声称消除了所有重复结构准备。

本轮 H1 在主世界端到端有稳定、超过门槛的初步证据，暂保留进入后续门禁，不继续微调本候选；实施 1/2、性能实验 1/2。仍欠其它维度/高视距性能、当前完整 B 顺序/并发、旧路径与原版、全部实体/tick/Overlay 持久化等验收，不能提升为 Target 或整里程碑 PASS。新增五项归并器单测已纳入 tool check，现工具共十八项、52 份脚本语法检查均通过。当前只运行 1.21.1 H121-S 完整结构 B；成功后队列顺序补 26.1.2 下界/末地普通 FULL 保存重载、32 重复请求及 3×3 physical patch，期间这两个 Target 的指纹源码不改。

1.21.1 的 H121-S 完整结构 B 现已 18/18、72 个 fresh JVM/save 全部运行通过，并另用 CheckOnly 复核；七类真实结构 start/邻区、村庄及试炼密室 halo、原点与非原点全部严格对照九份装饰前、实际外圈、引用结构 NBT、writer/RNG 和最终完整目标。末地城仅使用本版已双向证明、逐个构造器输入校验的有限 Shulker 朝向规则，原始 UUID-only 数据保留；零新增忽略项。源指纹为 41D822753CD9D29CA5ECCE5DA6F65C8E5D19F2F81A0F2F834D42DA01F05BFD6A，证据位于 conformance-legacy_b/result/structures-shared/1.21.1-neoforge。仍是生成模块，不是已注册生产 codec、FULL/ticking 或端到端性能通过；不据模块加速直接保留生产优化。队列随后开始 26.1.2 下界/末地 FULL 保存重载与并发/patch。

26.1.2 的新增生产队列已完整通过：下界 (-11,-6) 和末地 (2,0) 各两个 fresh JVM，普通 FULL 请求目标只生成/发布一次 Artifact、SPAWN 一次，修改后保存重启 canonical/Artifact/SPAWN 均零、marker 保留；连同先前主世界场景构成三维度。32 次主世界同目标请求得到 32 个 FULL 返回、目标只生成/发布一次；3×3 patch 的九个普通请求全部得到真实 lit LevelChunk，LIGHT/SPAWN/FULL 各九次。两种场景均 in-flight=0、lifecycleDuplicates 为空，正常保存关闭，无新崩溃。外围真实依赖成本单独保留：patch 为 25 份 Artifact，不能将九个 FULL 说成只做九份生成；尚不提升为 tick/entity 激活、完整 Overlay 持久化或性能收敛通过。结果位于 integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production/features-2/development-b。

参考 Target 的新 probe 已编译，231 项单元测试、十八项工具回归与 52 份 PowerShell 语法检查通过。StateProof 完整运行四场景、八个 fresh JVM：主世界 (125,-37)、村庄 (-10,11)、下界要塞 (-11,-6)、末地 (2,0)，普通 FULL 请求前后十一项持久化组件和修改严格一致，raw SerializableChunkData NBT 保留。村庄和要塞分别确有本 owner 内索引定位 (-146,78,178)、(-172,63,-96)，重载 Artifact/SPAWN 均零，正常保存关闭。证据在 integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/state-proof；tick/entity 当时状态只是单独诊断，不提升为其激活通过。

同一源码的 VanillaControl 三维度已实际完成六个 fresh JVM/save：原生 normal preset、NoiseBasedChunkGenerator 和普通 FULL/save/reload，无隐藏 Mosaic preset/身份；同场景两个 PID 不同，修改保留，Mosaic generation/Artifact/物化/POI/部分 BIOMES、生成及生命周期路由观察均零，context 绑定为零。新配置混合身份在改动世界和 properties 前拒绝。证据隔离于 vanilla-control，不把这项当作玩家、tick/entity 激活或客户端发送完成。本机部分 JVM 启动时 OSHI 无法取得进程内存信息的非致命告警在普通 Vanilla 也出现；任务均正常完成且没有新 crash report，未修改或吞掉生成异常。串行队列已终止，未叠加高负载实例。

下一项固定为补齐 1.20.1 完整严格 B 结构对照，并启动 1.21.1 的真实生产交接适配；参考 Target 仍欠旧 Ordering 1/2、顺序/并发、实体/tick 激活和其它维度/高视距性能。工具末次格式/检查再次通过，1.20.1 的完整套件已启动，manifest 为十五项、计划六十个 fresh JVM/save，不虚构不存在的 Trial Chambers；当前四 CPU、BelowNormal、单实例，运行期间其 Target 和共享合同源码保持冻结，任何新差异仍拒绝且保存原始失败。H1 暂保留继续门禁，不重开相同模块矩阵或为凑收益细调候选；所有原版 Target 门禁完成前不启动 BOP 优化。

1.21.1 已开始生产交接边界源码适配：新增本版脱离式 CanonicalChunkArtifact、八邻区 stored SPAWN snapshot 和 BFeaturePreparation，所有输出不引用旧 context/ProtoChunk/RandomState；仍未注册生产 codec，不冒充 FULL 可用。实际字节码确认 ChunkSerializer.write 会读取物理光照、BE 可能同时有 instantiated/pending 同坐标数据，因此候选用原生 codecs/structure helpers/tick containers 单独编码并分别保留原始 pending NBT。新增独立 Handoff 入口保留原 B 完整判等，消费者在 context 关闭后核对 raw 目标、全部 heightmaps、原始 pending/instantiated 分离和八份 stored palettes，并与另一原生 JVM 的真实 ChunkSerializer.read 结构加载结果比较；只读 seed 观察要求 native 与消费者实际 loader 均为 S(C)。产品与 probe 已完整编译，工具十八项测试及 52 份脚本语法通过；两处编译接线错误已修正，未改算法。编译单独限定 Idle、一核、单 worker、1 GiB，与 Oracle 共用原四核范围，不运行第二个 Minecraft，不将期间模块耗时用作性能证据。最后的安全校验收拢后亦再次编译通过。当前 1.20.1 完整套件仍在原指纹运行，随后串行执行 1.21.1 Handoff smoke 和结构套件；游戏验收未发生前明确未通过，不借用已有 shared 生成模块的十八项证明新增交接。

本次 1.20.1 完整结构 B 在 End City start 的第二个独立原生控制失败：仅三只 Shulker 的 Rotation[0] 不同，其余完整 NBT/结构/读取/RNG 未出现差异；原始失败与世界保留。这不是可直接豁免的消费者差异。已核对本版原生字节码：LivingEntity 构造器第三次 Math.random 经精确 double 常量乘法再转 float 设置朝向，EndCityPiece 的 Sentry marker 没有覆盖它；新增本版四进程捕获/交叉重放探针，但在实际双向完整 UUID-only 重现通过前仍记未解释，不能借用 1.21.1 的证明。

1.21.1 新的世界身份基础类、SavedData 包装及 fail-closed generator 已编译，五项身份生命周期单测通过；其中包括跨世界、同 seed 的不同 WorldOptions、更换 authority、关闭后重新使用和 10,000 次纯内存查询。原生版本没有 LevelResource.DATA，已只修为本版真实 ROOT/data 路径；codec 与服务器生命周期 hooks 仍未注册，普通世界不启用这些候选。当前源码的末地朝向因果证明已重新完成四个 fresh JVM 双向严格重现，之后才串行进入 Handoff smoke/结构套件。旧指纹证明拒绝当前源码是有效守卫，不绕开或扩大实体忽略规则。

后续每项继续登记 Target、模组组合、热点、假设、两轮次数、证据、收益和保留/放弃结论；不把环境下载失败、空入口编译或测量工具修正算作优化收益。

## 结论

本文是本次整理时固定的研究记录，不承担当前状态维护。历史失败、中断和未解释项保持原始证据语义；后续采纳或推翻结论时，写入新的研究记录，并同步更新里程碑或设计文档，不倒改本文。
