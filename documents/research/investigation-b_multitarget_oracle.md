# 多 Target 独立 Oracle 与初始适配调查

调查日期：2026-10-16。本文保留装饰前、结构输入、严格 B 与早期移植验证的历史记录，从里程碑的逐轮正文迁出；没有重跑、改写证据或变更验收结论。当前状态以[里程碑矩阵](../project/milestone-features.md)为准。

## 问题

如何区分模块、实际游戏、历史指纹、当前生产生命周期和性能证据，避免把某一阶段的通过扩展为整个 Target 或其它维度的通过。

## 方法

整理里程碑中已有的装饰前、结构输入、严格 B 与早期移植验证记录，按原有先后保留内容、数值、失败和限制；仅调整文档归属、相对链接与失效的账本维护声明。原始 result、世界、日志、SHA、签名及源码不改动，不把本次文档整理记作新实验。

## 发现

以下记录迁出时账本所载的逐轮调查与证据边界；每次“尚未完成”仅描述当时进度，不覆盖后来结果。

当前整体未完成，不作五 Target 或 BOP 全面兼容承诺。

- 参考 Target 已有真实生产 B、三维度人工体验与多轮优化证据；其它四 Target 本轮才开始适配。26.2 维持既有语义，不能自动认为具有新 B。
- BOP 装饰前九项及两个宿主的历史输入对照已取得；完整 FEATURES 历史严格门槛仍为 16/18。最新末地因果验证为 15 个新 JVM、9 组同条件严格对照通过，历史完整读取/RNG 链缺失部分仍为 unexplained，原始失败未改写。
- 本轮起不再要求人工飞行；现有 CLI 自动前沿没有真实玩家/send，补齐自动生命周期与发送观测前不能宣称这些指标已经测到。
- 三维度选择入口已通过六个 fresh JVM 的 2/2、20 秒原版/B smoke；各维度均有真实 FULL 和 send-ready，正常保存关闭，身份文件检查保持每 JVM 两次。完整条件、JFR 与时间线在性能验证的固定 duration20 场景；这不是正式 8/8 负载，不据此计算收敛收益。
- 本轮接入后六个登记 Target 的 spotlessApply、check、verifyRelease 与 verifyCompliance --strict 全部通过，严格合规检查 3,371 个文件、零失败/警告；四核、BelowNormal、单个构建实例，用时 8 分 40 秒，工具 12 项回归与 45 个 PowerShell 语法检查通过。根格式任务的长等待已用本线程 daemon 栈确认是 Windows 文件遍历，不是生成死锁，未修改模板检查器。26.1.2 的 114 个同步产品源码已通过首次编译，尚未运行或验收 B；两个更早 Target 仍无生成代码，工程通过不提升完整功能状态。
- 参考 Target 当次独立装饰前矩阵为 72/72：主世界 40（原点 8、非原点 32）、下界 16、末地 16；每项为 fresh native 与 unrelated-host 独立 JVM/save，四检查点、完整结构 NBT、stored palette、density raw bits、noise/surface/carved 与洞穴掩码均一致，FEATURES=0。证据在 conformance-prefeatures 的 structure_reuse_matrix/26.3-fabric；一个完整目标、八个 biome-only 载体、零 virtual holders，复用仓库关闭后 resident=0。此项不是完整 B 装饰验收。
- 同次参考 Target 的 structure_reuse_structures/26.3-fabric 为 18/18：七类结构各有真实 start 和邻区目标，村庄/试炼密室各有 terrain-only halo，另有两个派生种子回归；完整 start/pieces、references、原生加载投影、目标地形及 Beardifier raw bits 均一致。试炼密室 halo 98,304 个采样中 1,824 个非零；FEATURES=0。
- 26.3 NeoForge 当次装饰前独立矩阵为 72/72（主世界 40、下界 16、末地 16），144 个 fresh JVM/save、四个完整检查点及逐文件结构/地形、stored-biome、Beardifier raw bits 对照通过，FEATURES=0。自身代码和加载器的证据在 structure_reuse_matrix/26.3-neoforge，不借用 Fabric。结构复用仓库关闭后 resident=0。
- 26.3 NeoForge 已修正 DevLaunch 参数、FML 探针包隔离和产品组件开发模块可见性。三维度生产 B 的 2/2、20 秒 smoke 均有真实 FULL/send-ready，保存关闭正常：主世界 48/24，下界 99/63，末地 99/63；身份生命周期文件检查各两次，无新崩溃报告。证据在 b_milestone duration20 目录，不冒充完整 B 正例、重载或性能收敛。
- 参考 Target 的三维度标准基线已完成六个 fresh JVM：单 JVM、四核、BelowNormal、3 GiB、8/8、60 秒、同 seed/轨迹；六份输入指纹相同，全部正常保存关闭，无新 crash report。普通原版 canonical=0，未进入 Mosaic preparation。B 的身份文件检查各两次（累计均小于 0.3 ms），SPAWN 携带输入分别命中 418/498/525 次。JFR、条件和原始时间线在 b_milestone/26.3-fabric 下各维度固定目录。
- 完整 B 的独立 physical 原版入口已在 Fabric 和 NeoForge 分别通过四项：每项两个 fresh native、两个无关宿主，共各 16 个独立 JVM/save，包含三维度和 EndSpike 实体；两种加载器分别核对输入、完整目标、引用结构、biome 可见集合及 RNG，不相互借用结果。NeoForge 的 DevLaunch 原生参数文件复用已实际执行通过。探针、结果和启动任务不进入产品 JAR。
- 参考 Target 的完整独立 B 为 19/19、76 个 fresh JVM/save，通过两个原生重复和两个无关宿主：七类真实结构放块及相交 pieces、完整 owner/referenced NBT、目标装饰前、完整 FEATURES、RNG 和 stored-biome 可见集合均一致。自然正例合计实体 6、BE 条目 8（其中 pending NBT 2）、block ticks 1,280、fluid ticks 292、后处理 1,856；pending 正例来自 template-rooted-sulfur。绑定全部归零，消费者未改变物理宿主；仅使用已有有限实体验收，UUID-only 原始 hash 和原始 NBT 保留。证据在 conformance-feature_b 的 independent/26.3-fabric。
- 上述套件最初在原生村庄猫谓词被工具拦下：猫通过 StructureCheck 查询自己的真实原版世界，但测试严格区域的 escape 守卫没有允许此已审计入口。修正仅在 native control 的短作用域放行该原版方法到它自己的 physical S(C)，生产隔离结构路由和未知 escape 拒绝不变；单案例四进程及随后完整套件均通过，原始失败、日志和世界保留。环境 Codec JSON 的 Float 与解析数值对象比较问题另由精确编码文本和两个精度回归测试修正，不引入数值容差。
- 26.3 NeoForge 同次七类结构正例为 18/18，36 个 fresh JVM/save：start、邻区投影、两个 terrain-only halo 和两个派生种子均与本加载器独立原版一致；完整结构 NBT、目标状态、stored biomes 和逐 bit Beardifier 通过，FEATURES=0。证据在 structure_reuse_structures/26.3-neoforge，不借用 Fabric 结果。
- 26.3 NeoForge 完整独立 B 已为 19/19、76 个 fresh JVM/save 通过，本加载器的两个原生重复与两个无关宿主全部一致；完整目标、owner/referenced starts、装饰前地形、stored-biome 可见集合和 feature RNG 均通过，物理宿主未变、binding=0、单 writer。自然正例合计实体 6、BE 条目 8（pending 2）、block ticks 1,280、fluid ticks 292、后处理 1,856。证据在 independent/26.3-neoforge；有限原生实体验收范围未扩大，raw hash/NBT 均保留。
- 26.3 NeoForge 的普通生产 FULL 请求、修改方块、保存关闭、新 JVM 重启及重载已通过：首次 target Artifact=1、49 次物理 biome 就绪检查、lightCorrect=true；重载 Artifact=0、SPAWN=0、B generations=0，修改标记保留，未新增 crash report，in-flight=0。证据在 integration-production_lifecycle 的 features-2/development-b/full-125--37 与 reload-125--37；此项只证明该真实冷区和修改保留，不冒充完整 POI/Overlay/实体/tick 或客户端生命周期门禁。
- 26.1.2 NeoForge 六检查点参考入口 smoke 4/4、八个 fresh JVM/save 通过，覆盖主世界原点/非原点、下界、末地；STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES、NOISE、SURFACE、CARVERS 完整快照及引用结构一致，九份已生成 biome palette 和本版 double 精度 Beardifier 逐 bit 一致，FEATURES=0。Seed V1 三个冻结向量通过。证据在 conformance-prefeatures 的 current/26.1.2-neoforge；这是完整参考图的基线，不宣称本版轻量 B 已实现。
- 26.1.2 的完整参考矩阵为 72/72（主世界 40、下界 16、末地 16），144 个 fresh JVM/save、六检查点、全部结构 NBT、九份 stored-biome 输入与 double raw bits 地形适配一致，FEATURES=0。紧凑结构首个村庄消费者的最早异常是缺少 LevelHeightAccessor.getMinSectionY；后续才出现 Virtual parent missing at NOISE。已只补同维度只读 section 高度路由并重验通过，原始 FAIL、日志和失败世界保持原样。
- 前一批根 spotlessApply、checkRepository、verifyConfiguration、verifyCompliance --strict 已通过，严格检查 3,864 个文件、零失败/警告；完整 check/verifyRelease 当时因较早 Target 的测试类路径/加载器启动环境失败。现已分离 1.21.1 原生 API 单测与 1.20.1 真实 Forge probe，专项通过；新增轻量地形与 1.21.1 结构模块后仍待完整根门禁，不拿前批结果冒充当前整库 PASS。
- 26.1.2 的 getMinSectionY 只读路由修复后，紧凑结构独立对照已完整 18/18，通过七类结构 start/邻区、村庄与试炼密室 terrain-only halo、两个派生种子；六阶段快照、完整投影 NBT、九份 stored palette 与 double raw bits Beardifier 一致。旧 FAIL、失败世界及日志继续保留在 first_failure/compact/26.1.2-neoforge，不倒填历史状态。
- 26.3 NeoForge 的新物理持久化 probe 已实际通过四个目标的全新生成/save/restart/reload：主世界 (125,-37)、真实沙漠村庄 (-10,11)、下界 (-11,-6)、末地 (2,0)。十一类组件严格一致，均保留完整原生 Chunk NBT；村庄有实际 owner/external start、索引定位点与 POI。每次首次目标生成一次、SPAWN 一次；每次重载 canonical/Artifact/SPAWN 为零，玩家 marker 保持。完整 tick/entity 激活和客户端 lifecycle 仍待独立证明，不以这些组件对照扩写。
- 1.21.1 NeoForge 的五项原生种子/格式单元测试已通过，其中包含 18 个冻结向量。1.20.1 Forge 原生 ResourceKey 会初始化 BuiltInRegistries，普通 JUnit 不具备加载器变换后的注册环境；三份检查代码已移入 probe，以真实 ServerStartedEvent 执行全部 45 个原断言（含 18 向量、五个派生调用链和 Ordering 1/2 编解码），已 PASS 并正常保存关闭。未 mock 注册表、未删断言；原 plain-JUnit 失败结果保留。两者仍不具备 Chunk 生成能力。
- 26.1.2 轻量六阶段消费者保留原生 createBiomes/fillFromNoise/buildSurface/applyCarvers，只构造一个完整目标、八份 stored-biome 载体和 carverBiome 配置载体；所有外部地形 API 拒绝，未注册生产 B。本版 BIOMES 会立即构造 Beardifier，邻区 palette 各自解析精确结构输入并复用同 session 的结构源，不能照抄 26.3 的准备路径。尚无同负载性能收益结论。
- 该轻量入口已实际通过 smoke 4/4、七类结构 18/18 和完整 72/72 矩阵（主世界 40、下界 16、末地 16）；六阶段完整目标、owner/referenced NBT、stored palette 与 double Beardifier 均一致，零 virtual chunks/holders。最初把 CARVERS 的源范围错误地按 BIOMES requirement 推导，触发 fail-closed；本版实际为 STRUCTURE_STARTS radius 8，已按原版 metadata 及原版 17×17 遍历修正，首次失败另存 terrain_smoke 首份位置。证据为 terrain_smoke、terrain_structures、terrain_matrix/26.1.2-neoforge。
- 26.1.2 的严格单 writer B 及两个 fresh native、两个无关宿主的 18 项验证入口已完成编译；使用九份精确装饰前地形作为保守起点，所有越界副作用拒绝。尚未注册生产 B，也不宣称按需邻区性能；下一项为实际独立 B smoke 和完整套件。
- 26.1.2 B 的独立 smoke 已实际通过 4/4、16 个 fresh JVM/save，含真实 EndSpike 实体。完整套件首次在试炼密室原生控制端拒绝外圈模板读取；原始 FAIL、日志与失败世界保留。真实 bytecode 与原生运行均证明 FEATURES 还声明 STRUCTURE_STARTS radius 8，accumulated 第二圈为 BIOMES，其后为 STRUCTURE_STARTS。已增加按真实原生阶段的只读输入，不把外圈升级到 CARVERS 或假填空气；试炼密室四个 fresh 进程重验通过，六个真实外圈 BIOMES 的完整快照一致，仍单 writer、宿主不变、binding=0。完整 B 套件仍待此修复后的重跑，完整证明计算只在 probe。
- 26.1.2 外圈读取与引用集合复制修复后的完整 B 已实际 18/18、72 个 fresh JVM/save PASS：两个 native 重复、两个无关宿主，完整目标、引用 starts/pieces、装饰前地形与外圈真实阶段快照、stored-biome 可见集合、feature RNG 与单 writer 一致；宿主不变、binding=0。自然正例合计实体 6、BE 6（pending 0）、block ticks 709、fluid ticks 292、后处理 1,544。当前 fixture 没有 pending 正例，不借用 26.3 证据。证据为 independent/26.1.2-neoforge；只证明严格 B 独立模块，不提升生产生命周期或按需性能状态。
- 1.21.1 紧凑结构独立对照已通过 18/18、36 个 fresh JVM/save：七类真实 start/邻区、村庄与试炼密室 terrain-only halo、两个派生种子，完整 owner/referenced NBT、pieces/junction 和全高 double raw bits Beardifier 相同，FEATURES=0。证据为 conformance-structure_inputs 的 current/1.21.1-neoforge。初版保留原版 eager placement/rings 和命中后的生成算法，不继承 26.3 Lazy/缩锁结论；完整地形、B 与生产生命周期尚未适配。FML 4 元数据、重复组件模块与 Mixin 辅助类包归属三项启动问题已按本版修正，首次失败保留；新增包归属长期检查通过。
- 1.20.1 的本版种子域结构上下文、零 section 结构载体、原生 references 与独立探针已编译；用本版 STRUCTURE_REFERENCES.getRange() 派生范围，未套用不存在的 ChunkPyramid。只登记六类真实结构及村庄 halo、派生种子的 15 项验证，不虚构试炼密室；尚待实际原生游戏运行，编译不算结构对照 PASS。探针 SRG 映射与验证参数分离，日常实例不加载 probe。
- 1.20.1 紧凑结构独立对照已实际 15/15、30 个 fresh JVM/save 通过：本版六类结构的 start/邻区、村庄 terrain-only halo 及两个派生种子，完整结构 NBT、pieces/references 与 double raw bits Beardifier 一致，FEATURES=0。Forge 启动的组件模块归属和 fresh JVM 的 MOD_CLASSES 传递已修正；只导出这一个非敏感加载器变量，不复制整个环境。首次模块失败与首次不完整启动证据保留。完整地形、B 和生产生命周期仍未完成。
- 1.21.1 的轻量六阶段入口已实际通过独立 smoke 4/4、八个 fresh JVM/save，覆盖原点、派生种子 blocker、下界与末地；六份完整原生 NBT、结构投影、九份 stored biome/可见集合和 Beardifier double raw bits 一致，FEATURES=0。一个完整目标、八份 palette、289 个 carver 配置源、零 generation holders；构造器本地种子/RandomState 绑定及注入异常清理通过。内部 density 数组 raw bits 尚未接入本版 probe，完整 72 矩阵、结构地形套件与 B 未完成，不借用 26.3 对应证据。
- 1.21.1 新密度观察器已实际运行：带切片的三维度 smoke 4/4、八个 fresh JVM/save 通过，两个主世界目标各 9,800 个原生 double 采样、下界 425、末地 297，所有实际切片逐 bit 一致，六阶段完整 NBT/引用结构与九份 palette 同时一致。每个目标恰好一次原生 doFill，采样直接复制 doFill/fillSlice 数组，不重新计算；关闭后无绑定。此前无切片的四项证据保存在 baseline_before_density，未补造历史字段。完整结构地形和 72 矩阵仍待完成。
- 1.21.1 带切片结构地形套件首次在 Monument 邻区失败：参考 LongOpenHashSet 经重新插入后引用迭代次序反转，原始 NBT 严格不一致；结构投影和全部 9,800 个实际密度采样仍相同，不能以此豁免。已改为独立克隆原生集合布局，并将真实反例、复制后修改隔离、未知容器拒绝加入单测，两个测试通过。三个早期 Target 同类复制已修正，首次失败/世界保留。修复后完整 18/18、36 个 fresh JVM/save 已 PASS，七类结构、邻区与 terrain-only halo 的六阶段完整 NBT、九份 palette、Beardifier 和实际密度切片逐 bit 一致；FEATURES=0，binding=0，单完整目标。完整 72 矩阵尚待执行。
- 1.20.1 新轻量六阶段已通过三维度 smoke 4/4、八个 fresh JVM/save：六阶段完整原生 NBT、九份 stored palette/可见集合、Beardifier 与实际密度切片逐 bit 相同；两个主世界目标各 9,800 个采样、下界 425、末地 297。只有一个完整 ProtoChunk、八份 biome-only 载体及 289 个 carver 配置源，无 physical host Chunk 修改、FEATURES=0、正常/注入异常构造绑定均清理。使用本版原生 SRG Mixin、Executor 与三项构造器种子域路由，未注册生产 B。证据为 conformance-legacy_terrain 的 legacy_terrain_smoke/1.20.1-forge；完整矩阵及结构地形待执行。
- 1.21.1 带实际密度切片的完整矩阵已实际 72/72、144 个 fresh JVM/save PASS：主世界原点 8/非原点 32、下界 16、末地 16；六阶段完整目标 NBT、引用结构、九份 palette/可见群系、Beardifier 和实际 density slices 全部严格一致。FEATURES=0、binding=0、消费者物理宿主 Chunk 数不变；不以这批装饰前证据提升 B、生命周期或性能状态。证据为 conformance-prefeatures 的 legacy_terrain_matrix/1.21.1-neoforge。
- 1.21.1 严格 B 入口已编译，保留九份精确 CARVERS 输入，只执行目标 writer；本版单个 short 的后处理 API、无 copy() 的 PalettedContainerRO 与旧实体 NBT API 已分别适配。CappedProcessor seed 和 Cat 结构查询按实际 bytecode 窄路由，未知 getLevel 即使被模板捕获仍不能通过；模块尚未独立 B 验收或注册生产。1.20.1 的相应源码、原生依赖公式、SRG 安全能力边界和四角色 B 探针也已创建，但尚待编译/运行。没有新增实体忽略字段。
- 1.20.1 初始 B 的首次 native repeat 在环境描述比较失败，目标完整 NBT、九份装饰前输入与 feature-seed 序列均相同。探针已定位到 bamboo_jungle.downfall：Gson 内存 Float(0.9) 与读回 LazilyParsedNumber(0.9) 的 equals 不同；改为两侧完整落盘 JSON 的严格比较，不忽略字段、不排序数组，原始失败与失败世界保留。修复后初始 4/4、16 个 fresh JVM/save 已 PASS，包括三维度、非原点与实际 End Crystal，两个不同宿主均严格一致；消费者没有 physical Chunk/entity/tick 访问或修改，binding=0。该批尚未覆盖真实村庄 POI，不以此冒充完整物理隔离或生产生命周期。保守九份地形准备在主世界约 103～140 秒，不是性能完成。
- 两个早期版本的实际 WorldGenRegion.setBlock 会直接调用 physical ServerLevel.onBlockStateChange。新严格 B 区域已用单调用点窄接入，暂缓虚拟放块产生的物理 POI 通知；正常 Vanilla 保留原调用，真正物化后的 POI 仍需独立生命周期验收。另阻止未审计的 physical light-engine 逃逸，独立 Native 控制继续读取自己的真实引擎，不能以共同假值冒充等价。上述新增边界已编译，真实村庄/寒冷地形正例尚待运行。
- 1.21.1 初始 B smoke 已实际 4/4、16 个 fresh JVM/save PASS：主世界原点/非原点、下界与实体正数的 EndSpike，两个 Native 重复及两个不同宿主的完整 NBT、九份装饰前输入、结构与 RNG 相同；只有一个 writer，宿主 Chunk/entity/tick 不变，binding=0。证据为 conformance-legacy_b 的 smoke/1.21.1-neoforge；仍是九份 CARVERS 保守基线，不把初始样本提升为按需 B、完整结构/POI/寒冷装饰或生产验收。
- 新增寒冷输入专项正在准备：实际原版 SnowAndFreezeFeature 经 Biome.shouldFreeze/shouldSnow 读取 block brightness，不能穿透 unrelated host 的 physical light engine。先以原生 fresh control 的每次实际读取核对原生未初始化 light storage 候选，再决定消费接入；不硬编码亮度或以默认空气/光照冒充等价。1.20.1 和 1.21.1 的 POI storage/dirty 快照与实际 light-read 轨迹已补到独立 probe，尚待运行。
- 寒冷专项已实际确认边界：1.20.1、Long.MIN_VALUE 原点的两个 fresh Native control 各有 512 次实际亮度读取、188 个雪层；候选原生 pre-light storage 逐次返回相同结果，Native 完整目标与 RNG 重复一致、POI storage/dirty 不变。消费者旧保护在 shouldFreeze → getBrightness → physical getLightEngine 处明确失败，原始失败/世界保存在 first_content_failure/cold，不以绕过保护通过。当前将已对照的只读原生 fresh-storage 查询接到两版 B 区域，仍禁止任意 engine 逃逸或光照传播，正常物理 LIGHT 不变；修复后整套重验待完成。
- 修复后两版 cold 套件各 1/1、四个 fresh JVM/save 已实际 PASS 并 CheckOnly 复核：两个 Native 和两个不同宿主均有 512 次相同亮度读取与 188 个雪层，完整目标 NBT、九份 CARVERS 输入、RNG、结构和切断副作用逐项一致。各进程 POI storage/dirty 前后完全一致，实体/物理 ticks 不变、binding=0；不将 fresh/unrelated-empty-host 证据扩张为已生成光照宿主或所有第三方引擎兼容。原生 pre-light storage 只公开读取，不调用任何 light update；未知 engine 实现仍拒绝。
- 引用集合复制额外固定了未知子类边界：即使某子类覆写 clone 返回自身，也必须拒绝，不给它保留目标数据的修改权限。1.21.1 新单测通过，连同两个已有引用复制测试和五个种子/合同测试共八项通过；三个早期 Target 的同类窄边界一致。正常 LongOpenHashSet 克隆行为不变，相关当前完整矩阵继续重验。
- 1.20.1 装饰前完整矩阵已实际 72/72、144 个 fresh JVM/save PASS：主世界原点 8/非原点 32、下界 16、末地 16。六阶段完整 NBT、引用结构、九份 stored palette、Beardifier 和实际密度切片逐 bit 相同，FEATURES=0、binding=0、消费者宿主 Chunk 数不变；结构地形另为 15/15、30 个 fresh JVM/save。固定证据分别在 conformance-legacy_terrain 的 legacy_terrain_matrix 与 legacy_terrain_structures/1.20.1-forge，不将此提升为完整 B 或生产通过。
- 当前下一项：26.1.2 新 B detached handoff 的完整 18/18、72 个 fresh JVM/save 已 PASS 并另用 CheckOnly 复核，包括目标完整数据、原生结构加载控制、八份 SPAWN palette、实际外圈读取与两个无关宿主；真实副作用仍为实体 6、BE 6、block ticks 709、fluid ticks 292、后处理 1,544，pending 0。主世界 (125,-37) 普通 FULL→修改→save/restart/reload 现已 PASS：首次目标 Artifact 捕获/发布各一次、SPAWN 一次；新 JVM 重载 canonical/Artifact/SPAWN 为零，marker 保留、bindings 与 in-flight 结束清零，无 lifecycle duplicate；尚不包含 ticking、三维度或完整持久化组件验收。现串行编译/验证参考 Target H1 生产候选，不并发重负载 JVM。H1 模块七类结构 18/18 与 72/72 已完成，生产候选门禁未完成；1.21.1 双向因果与单 End City B 已复核，完整 H121-S 全结构 B 和三组配对准备期测量仍待执行。随后补 26.1.2 下界/末地/并发与其余已生成宿主顺序并发、26.3 NeoForge 去重/tick/entity，以及所有 Target 的有界性能工作。有限实体验收、目标快照/hash 和原始失败不改写，模块通过不提升生产完成度。
- 26.1.2 的生产接线 main/probe 已编译，新增五项身份/格式 JUnit、十三项工具测试及 51 份脚本语法检查均通过：新增 distinct development_b codec、生命周期 B marker 和内存身份、严格 B 模块的 detached handoff，Artifact 屏障按既有 Ordering 1/2 计划分别核验，旧默认仍为 1。新值只在 distinct B 身份、全维度一致与 opt-in 下走新模块，不能回退九 writer 冒充 B。沿用本版已过独立 Oracle 的命中后结构/地形/装饰算法，不复制 26.3 密度查询或 H121-S；当前保守九份 CARVERS 输入只是待优化基线。实际版本差异分别修正为 section palette 遍历与原生测试 classpath；Level 的 NeoForge AttachmentHolder 要求真实 FML，工厂断言在游戏 probe 执行，JUnit 严格检查原始合同与 Codec，不伪造加载器。independent_handoff_smoke/26.1.2-neoforge 已 4/4、16 个 fresh JVM/save PASS，完整 18 项正在受限单实例队列运行，之后才运行普通 FULL/save/restart/reload。主线程 POI、预期取消、SPAWN snapshot、Overlay 与旧世界回归均未提升状态。

| 参考 Target / 维度 | 原版 FULL / send-ready | B FULL / send-ready | B canonical/s | 请求至可发送中位数：原版 / B | p95：原版 / B |
| --- | --- | --- | --- | --- | --- |
| 主世界 | 525 / 437 | 418 / 340 | 11.73 | 9.71 / 31.26 s | 12.08 / 57.41 s |
| 下界 | 525 / 437 | 498 / 410 | 13.40 | 4.81 / 10.50 s | 6.79 / 14.81 s |
| 末地 | 525 / 437 | 525 / 437 | 13.95 | 2.01 / 4.88 s | 2.67 / 6.25 s |

这是固定有限前沿的一次基线，不是吞吐上限或三轮配对优化收益；完成后的空闲时间也在 60 秒窗口内。没有玩家登录、packet 或渲染，不把 send-ready 当作玩家已见。主世界中心目标在约 4.27 秒完成准备，约 16.35 秒可发送，其中准备后至 INITIALIZE_LIGHT 约 11.88 秒，需进一步拆解真实邻区 readiness 的工作量，而非把整段记为本目标 CPU。主世界 CPU 叶热点为 NoiseStack/GradientNoise 和 Climate RTree，锁等待最大项约 18 ms，GC 累计约 1.01 秒，均未支持“锁或 GC 主导”的假设。装饰 CPU 桶包含按需邻区地形读取，不能把它全部当作树木等装饰内核。

## 结论

本文是本次整理时固定的研究记录，不承担当前状态维护。历史失败、中断和未解释项保持原始证据语义；后续采纳或推翻结论时，写入新的研究记录，并同步更新里程碑或设计文档，不倒改本文。
