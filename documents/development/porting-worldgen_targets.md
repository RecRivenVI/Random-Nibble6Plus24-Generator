# B 模式五 Target 适配

26.3 Fabric 与 NeoForge 的原版 B 内容与有限生命周期已验证，性能 PARTIAL 已接受；1.20.1、1.21.1、26.1.2 首版暂缓游玩支持，保留源码和构建。不能把基础代码、登记或 Java 编译当作移植完成。范围和逐步门禁见[当前里程碑](../project/milestone-productization.md)。

## 当前适配

参考端真实客户端验证经 MCP 输入、项目 probe 旁观，不能照搬到其它 Target：26.3 的 ChunkPos 构造器接受两个区块整数，ChunkResult.orElseThrow 要求 Supplier，TicketType 名称从原生注册表取得。观察点是 MinecraftServer.tickChildren(BooleanSupplier) 返回和实际 player.tickCount，不手工 tick；游戏只读 checkpoint 绑定 serverRunId，重启拒绝旧请求。其它版本必须各自审计玩家、状态 future、NBT 与 ticket API，见[当前客户端边界](compatibility-mcp_alpha.md)。

| Target | 本版必须独立适配的接口 | 当前边界 |
| --- | --- | --- |
| 26.3-neoforge | NeoForge 的 SerializableChunkData 附件/辅助光照字段与十九参数构造；独立 FML 模块与启动参数 | 显式 B 已接入；附件能力仍保守拒绝，完整运行期与性能状态见里程碑 |
| 26.1.2-neoforge | NOISE/SURFACE/CARVERS 分离、二十参数 Chunk 数据、cachedClimateSampler 与原生 ticket promotion | B 与自然 tick/重载已接入；完整门禁与性能状态只在里程碑维护 |
| 1.21.1-neoforge | ChunkSerializer、ProtoChunkTicks、mainThreadMailBox 与非异步 future handle 的归属 | 显式 B codec 与生产接线已存在；有限环候选默认关闭，其独立证明和剩余生命周期见里程碑 |
| 1.20.1-forge | 旧包 ChunkStatus 的 GenerationTask/LoadingTask、Either future 与 getRange/getDependencyStatus；没有 GenerationChunkHolder | 独立 B、脱离式 Artifact、stored SPAWN 输入及原生 Either 调度已接线；显式 B codec 不公开，下界/末地 FULL/save/reload 有独立证据；完整状态见里程碑 |

所有 Target 的当前验收计数、源码指纹与证据只在[里程碑状态矩阵](../project/milestone-productization.md)维护，不在本页另建验收账本。26.1.2 与 1.21.1 已注册显式 development_b codec、marker 与 Ordering 2 身份；current()/旧参考合同仍保留 Ordering 1，不把缺少新身份的世界静默分派 B。1.20.1 只注册要求匹配 profile、marker 与显式 opt-in 的 development_b codec；不提供公开 preset。26.3 NeoForge 不加载 Fabric API 或 BOP 因果入口，不用其它 Target 的通过替代本加载器验证；任何未收口 Target 都不能当作正式可用版本。

FML 按模块拥有 Java 包，产品与 probe 不允许 split package；NeoForge 的新验证类使用独立 probe.verify 包，工具 check 检查这项边界。开发运行还需将 product 组件二进制作为同一个模组模块的输出加载；本 Target 使用原生 ModDev sourceSet 和 Sync 展开已登记组件，与发行 JAR 使用同一来源，不复制组件源码、不修改模板。不能把编译 classpath 中可见误当作 FML 运行期可见。

NeoForge DevLaunch 的程序参数是主类加参数文件，不能像 Fabric 那样用 JavaExec 的 --args 整体替换；探针通过本验证实例的 server.properties 选择 fresh world，保留模板提供的启动参数。首次调用原生 Gradle 任务，已验证的参数文件形状由后续 fresh JVM 原样交给 DevLaunch；未知形状保守回退原生任务。这部分启动时间不当作 worldgen 性能。

Forge 的 DevLaunch 还需要原生运行的 MOD_CLASSES 环境以定位同一模组的源码输出与 probe；独立进程只传递这个加载器键，不导出整个环境。Probe 的注入目标使用本版 SRG refmap，处理器与加载器实际 Mixin 版本一致，不依赖关闭 remap 或跳过原版初始化来启动。

1.20.1 的身份加载边界位于实际 createLevels 的 overworld 入表之后、首次 getDataStorage 返回处；SavedData.set 触发失效，显式读盘重验只能在 server thread。本版不为关闭接线新增 MixinExtras：level.close 开始拒绝该 level 查询，Forge 的 HIGHEST ServerStoppedEvent 清除 server authority，普通探针随后检查。原生 ChunkMap.schedule 与 getChunkRangeFuture 的窄接入区分外部请求和内部依赖，发布使用本版 BlockableEventLoop；初始化字段显式注入构造器返回边界，不依赖旧 Mixin 的字段初始化提取。不能把编译或普通世界身份门禁当作真实 FULL 通过。结构 Artifact 已通过原生 unpackStructureStart 的显式 localSeed 参数恢复，不照搬其它版本经 ServerLevel.getSeed 的调用形状。

1.20.1 的 preparation 取消只在原生调用之间检查 owner 关闭状态，不打断已开始的原生 future 或在 FEATURES 内抛出可能被上游消费的取消。正常 stopServer 的 RETURN 负责排空并重新抛出已保留的真实失败；真实异常可使该 RETURN 不执行，Forge runServer 的异常分支仍发布 ServerStoppedEvent，因此该事件先排空 owner worker 再失效身份。异步 FULL 的错误验证必须消费 exceptional future，不能仅凭日志或等待超时宣称原生 crash report 已产生。

1.20.1 的区域票据 API 同时更新 loading 与 TickingTracker，不能复制新版拆分票据接口。验证使用独立的 getFullChunkFuture、getTickingChunkFuture 和 getEntityTickingChunkFuture，返回值按本版 Either 处理；物理依赖从本版 getRange/getDependencyStatus 对应计划推导，不虚构 ChunkPyramid。自然模拟观察 tickBlock、tickFluid、tickNonPassenger 与接收 Level 的熔炉 serverTick，不自行调用 tick。慢冷区会消耗空世界的 entity idle timer，设施安装时只调用一次原生 resetEmptyTime；此验证边界不是玩家出生或生产 forceload 修复。

1.20.1 有限环候选通过 ModifyArg 仅包装原生 supplier，提交和 future 链仍由原版构造，不依赖 MixinExtras、合成 lambda 名称或局部变量槽号。作用域只覆盖单次原生 112 方块群系搜索，保留全部 slot 与父 RNG/fork，结果按有限域投影。MultiNoise 只有能证明原生最终最近 leaf 唯一时才省略中间搜索，并执行原生尾查询保留出口；等距、未知参数列表或数值域未证明时完整原生搜索，不能仅按 biome 值重置缓存。未知 source/placement、环排除关系、多个环集合和可能整数溢出的几何保守原生回退。候选默认关闭，游戏门禁和性能保留状态只在里程碑维护。

LegacyForge 的 MixinExtension 会直接把主 refmap 加入 JAR，开发运行同时需要 processResources 中的副本。仅该条目的重复打包被合并，执行前逐字节确认两份一致；其它重复资源仍失败。插件为已登记的 probe 同时汇总 AP 映射，因此 reobfJar 显式依赖 compileProbeJava 产生映射，按延迟注册任务配置；这不把 probe 类或资源加入产品 JAR，也不改变 main 的源码依赖。

实际 NeoForge 字节码为 SerializableChunkData 增加 attachmentData 和 auxLightData，原 17 参数构造器变为包可见并转发两个 null。当前编译适配调用公开的 19 参数构造器，只接受无附件的 pre-light 数据；捕获时发现 Chunk attachment、读取时发现这两类扩展字段均立即失败，不能静默丢弃。完整附件往返尚待验证与实现，不因此承诺第三方兼容。

## 版本边界

26.3 的 Climate.ParameterList(List) 委托到私有 (List,int)，原生索引在后者调用 RTree.create(List,int) 时建立；状态观察须限定真实构造器描述符，不能复制 1.x 的单参数工厂或未限定重载的 selector。桥接只暴露实际 ThreadLocal 引用，来源缓存命中必须验证入口并保留原生出口；桥接缺失、未知采样器或未审计模组环境不启用来源复用，走精确原生 evaluator。该接线当前只在参考 Fabric 实现，其余 Target 分别证明；运行结果与未完成门禁只在里程碑维护。

每个 Target 独立审计真实 ChunkStatus/ChunkPyramid、NoiseChunk 与 climate cache、随机 API、Structure/Beardifier、FEATURES 读写入口、ticks/BE/entity 存储、POI 线程和 FULL/ticket 生命周期。不同版本的状态命名、对象布局、NBT、方法签名和结构内容均不得机械假定一致。

已核对本机实际加载器补丁 JAR：26.1.2 仍分别执行 NOISE、SURFACE、CARVERS，RandomState 没有 26.3 的 seed()，不能照搬 TERRAIN/ScopedDensityBuffer 快路径；先沿用分离阶段参考实现，再分别验证轻量输入。26.1.2 的 SerializableChunkData 保留 carvingMask，NeoForge 增加附件和辅助光照后需公开的 20 参数构造器，不能复制 26.3 的 19 参数调用；未验证的附件同样 fail closed。1.21.1 存在 ChunkPyramid 与 GenerationChunkHolder；1.20.1 的 ChunkStatus 位于 chunk 包，使用 GenerationTask/LoadingTask，没有 GenerationChunkHolder，必须从该版原生接入点适配，不能以找不到同名类就虚构一个新 scheduler。此项仅为 API 审计，不提升功能状态。

26.1.2 的紧凑结构输入另设显式 probe 消费者：仅替换目标的 starts/references 与 BIOMES、NOISE、SURFACE、CARVERS 的结构读取，保留各阶段原版入口。289 个 source 仅承载结构元数据，所有地形 API 拒绝；已关闭 context 后可消费的完整 pieces/projection 必须独立证明。缩锁审计尚不从 26.3 继承，仍用本版完整参考路径的模板管理器锁。显式 B 生产交接已复用该保守模块；独立模块与运行期门禁仍分别验收，不因源码同步宣称完成。

实际 26.1.2 applyCarvers 必须有 WorldGenRegion：读取其 registryAccess，并遍历周围 17×17 source ChunkAccess 的 carverBiome(Supplier)。源范围的原生 requirement 是 STRUCTURE_STARTS radius 8，而不是 BIOMES radius 8；配置源不需要已生成 biome palette 或方块地形。当前独立轻量消费者保留该原版方法及其 RNG/遍历，提供只支持原生 supplier 缓存的 metadata 载体；地形 API 全部拒绝，smoke、结构正例及 72 矩阵已通过。不能照搬 26.3 的 null-region 入口。

26.1.2 createBiomes 会立即创建 Beardifier/NoiseChunk，再把 cachedClimateSampler 交给 fillBiomesFromNoise(resolver, sampler)。所以八个邻区 palette 各自解析该 owner 的原生结构输入，并在同次准备中复用已评估结构源；不能借目标 references 或 physical host 结构替代。SURFACE 调用本版公开的低层原生重载，读取九份 stored palette；不把直接 climate sampling 当作 palette 权威。

26.1.2 的 FEATURES 仍声明 STRUCTURE_STARTS radius 8。试炼密室 RuleProcessor 在裁剪放块范围前实际读取第二圈，其原生 accumulated/实际状态为 BIOMES；第三圈起为 STRUCTURE_STARTS。B 的外圈查询按原生 metadata 在同 S(C) 求值对应阶段，再提供严格只读 capability；Native 控制保留自己的真实部分阶段输入，逐个完整快照对照。不能把所有外圈伪装成 CARVERS，也不能返回假空气；该输入只用于显式 B，不改变旧存档分流。

本版 B 生产交接沿用已验证的九份 CARVERS 保守模块，不移植 26.3 的局部密度快路径；八邻区 stored palette 在原准备结束前复制为 detached SPAWN 输入。实际 ChunkAccess 没有 collectBiomesInPalette，按原版 section.getBiomes().getAll 收集可见集合。Artifact 按 Ordering 1/2 分别检查九 writer/单 writer 屏障，生命周期新身份只在加载时验证、普通 Chunk 查询只读内存。B 交接的独立原生控制分开核对生成态和原生结构加载态，未知数据差异保持失败；实际生产与重载状态仍以里程碑证据为准。

1.21.1 的紧凑结构已通过独立基线：createStructures 没有 26.1.2 的 dimension 参数；ChunkAccess 的 block write 为 boolean，ticks 序列化为无时间参数的 TicksToSave，StructureManager 使用 getMinSection。默认仍保留全量原生 placements/rings 与模板管理器监视器；有限环候选仅在显式测试属性和已审计能力下绑定，未证明环境保持完整原生调用链，不能从其它版本继承验收。独立结构验证覆盖真实正例与 raw-double Beardifier，不替代后续完整六阶段/B 验收。

1.21.1 的真实 API 还保留 GenerationStep.Carving 参数、boolean setBlockState 和 biome Registry 构造器，WorldGenContext 的末项为 ProcessorHandle；1.20.1 的资源标识构造器与 positional random factory 也不同。后续须从实际调用/随机续流适配，不能用批量改类名替代这些边界的验证。

两个早期 Target 的严格 B 模块均保守准备九份真实 CARVERS 数据，只执行目标一次装饰，不能宣称已有按需地形性能。1.21.1 已接显式 codec 与 Artifact/物理交接；1.20.1 的物理接线正在原生服务端验证，尚未通过完整生命周期。原生实体随机字段仅按各版本独立双向因果证明的有限规则验收，其余完整目标数据严格比较，原始 NBT 保留。消费者生成期间拒绝 physical Chunk 请求，不能仅凭 FULL loaded 数未变推断没有宿主读取。

本版 CappedProcessor.finalizeProcessing 实际经 ServerLevel 读取 seed，Cat.finalizeSpawn 实际经 ServerLevel 读取 StructureManager；只在同步 B writer 作用域分别路由本地种子与紧凑结构查询，普通 Vanilla 和后续运行期不绑定。资源/实体构造用的 getLevel 调用逐项审计；未知逃逸即使被原版模板的 Exception 捕获，完成时仍判失败。1.20.1 的开发命名与生产 SRG 方法名不同，能力边界按本版本已审计的类/方法描述符检查，不套用 26.3 或开发环境方法名。

两版 WorldGenRegion.setBlock 的物理 POI 回调必须在严格 B 虚拟区域内延后，等真实物化后重建；普通 Vanilla 区域仍执行原回调。寒冷装饰另经 getBrightness 读取光照，不能借 unrelated host 的已初始化光照。两版寒冷专项已逐次核对 512 次实际读取、188 个雪层，两个原生与两个宿主完整结果相同，物理 POI storage/dirty、实体和 tick scheduler 不变；1.20.1 旧消费者的保护失败继续保留。新查询只封装原生存储读取，不运行光照传播或返回固定亮度；真实村庄 POI 和已生成/修改光照宿主仍另行验收。probe 对本模组自有方法的观察不做 SRG 重映射，Minecraft 自身注入点仍使用真实 refmap。

1.20.1 轻量地形入口已通过独立三维度 smoke、结构地形 15/15 与完整矩阵 72/72。实际 createBiomes/fillFromNoise 都显式接收 Executor，WorldGenRegion 构造器使用 List 缓存而非 GenerationChunkHolder；构造时还读取 ServerLevel 的 seed、RandomState 和 structureManager，再调用 forWorldGenRegion。有限构造作用域分别提供本地种子状态与结构管理器，不能仅覆盖 getSeed 后留下 physical StructureCheck。原生 ChunkMap 的外圈需求为 getStatusAroundFullChunk(getDistance(stage) + distance)，中心取 parent；各阶段的 getRange() 不等于全部邻区需要完整地形。Blender 的 carving-mask 辅助在本版总会检查八邻区的 blendingData，不能照抄新版本的提前返回。独立运行配置登记于 conformance-legacy_terrain，以验证参数加载 SRG probe；日常实例不加载探针。

紧凑输入复制 references 时不能只保证 LongSet 成员相等。1.21.1 Monument 邻区严格原始 NBT 对照发现，重新插入 LongOpenHashSet 会改变遍历顺序；密度与结构语义投影相同并不能豁免该差异。当前三个早期 Target 克隆原生集合布局并隔离后续修改，未知容器拒绝；保留首次失败，不排序原始数组或扩大忽略规则。

两个较早版本的 Seed V1 适配只调用各自原生 XOROSHIRO、forkPositional、domain/dimension hash 和 absolute Chunk world position；不共享 Minecraft 源码。1.21.1 实际具备 fromSeed，但 1.20.1 的 positional factory 没有该方法；本里程碑两者都只适配 authoritative seed，不移植或近似范围外的预览种子接口。原点保留、三维度与自定义维度、负坐标及整数边界的 18 个冻结向量须逐项通过，未运行不宣称等价。此项不实现 Preview，也不为未接生命周期的 Target 启用 B codec。

1.21.1 的原生随机原语与 Codec 单元测试从自身 main 类路径读取 Minecraft API，JUnit 只取公共版本目录的固定版本。LegacyForge 没有 NeoForge 的 unitTest DSL；实际 1.20.1 ResourceKey.createRegistryKey 会触发 BuiltInRegistries，需要真实 Forge 注册环境。该版的三组全部原断言移到独立 probe，在 ServerStartedEvent 执行，见 conformance-seed；普通 JUnit 不绕过 bootstrap，也不伪造加载器。测试使用四核、单实例，不改变产品 worldgen executor。

1.20.1 的脱离式 Artifact 复用本版 ChunkSerializer 的 block-state/biome codec、structure pack/load、ProtoChunkTicks save/load 与 AIR/LIQUID CarvingMask；NBT I/O 使用 File 和 NbtAccounter.UNLIMITED，不能照搬新版 Path/构造器接口。BlockEntity.save/load 不传 RegistryAccess，pending 原始数据与 instantiated 位置分别携带，完整 entity NBT 在同进程交接时不忽略 UUID 或朝向。重建只创建 pre-light ProtoChunk，以明确 S(C) 加载结构，保留八邻区 stored SPAWN palettes，不保留 session、NoiseChunk 或 RandomState。Forge 的原生 serializer 只在 LevelChunk 分支写 ForgeCaps；当前捕获仍拒绝未知模组、非原生 ProtoChunk、retrogen/upgrade/blending，不据此声明第三方扩展兼容。运行范围与未通过项只在里程碑维护。

与游戏无关的 Seed/合同值类型继续从产品组件取值；Minecraft 相关源码不共享、不预处理。按需密度、结构裁剪和源数据复用只能在该版本独立证明后启用，不能证明时保留精确原生回退；能力检查失败不能以默认值继续。

1.21.1 的生产交接已实现显式 B 接线，但仍欠完整运行期与性能收口。该版没有 SerializableChunkData，原生 ChunkSerializer.write 无条件读取 physical light engine 的 section layers，即使 ProtoChunk 尚未完成光照也会读取，不能作为隔离 Artifact 的捕获入口。交接调用本版原生 section/biome codecs、tick container 和 structure pack/load，独立编码新鲜 pre-light 数据；重建时显式传 S(C)，不用宿主 seed。ProtoChunk.setBlockEntity 不移除 pendingBlockEntities，真实村庄与末地城各有 instantiated/pending 同坐标共存正例；分别携带原始 pending NBT 和已实例化位置/完整保存数据，不把二者合并丢弃。原生 ProtoChunkTicks.schedule 在本版把 delay 设为零，仍保留类型、位置、优先级和列表次序；交接复用其原生 save/load，不重写触发算法。Handoff 已在 owner 关闭后实际核对全部目标、原生结构加载、八份 stored SPAWN palettes 及跨 seed/维度误用拒绝，通过 smoke 和完整结构套件；游戏内模块验收不提升为生产 FULL/SPAWN 激活通过。

本版 MinecraftServer 没有新版本的全局 WorldGenSettings SavedData 接口；master 权威是 getWorldData().worldGenOptions()，自定义 profile 由 Overworld 的 DimensionDataStorage 持久化，再按服务器实例共享给各维度。初次校验必须在 Overworld 插入后、LevelEvent.Load 和出生生成前完成，不能在 createLevels RETURN 才补验证。SavedData.set 不自动标 dirty，namespace 目录也需创建；这些身份接线已实现；当前门禁范围与指纹见里程碑。已有内存身份模型使用每个服务器独有的 owner token，并验证 WorldOptions 对象而不只比 seed，关闭或替换后拒绝继续查询，不在 Chunk 热路径访问文件。

身份边界现已在独立 integration-world_identity 中通过原生启动、三维度归属、替换失效、显式重读和真实关闭验证，B/Artifact 四场景仍严格一致；生产 B codec 与 FULL 接线另行验收。1.21.1 的 WorldGenContext 使用 mainThreadMailBox，而不是新版 mainThreadExecutor；SectionStorage 也没有 prefetch。物理 POI 接入应使用本版 ChunkMap 的 BlockableEventLoop 队列，与原生 chunk loading 的线程归属及同步等待泵一致，不能把新版方法名直接移植或改用不被 chunk wait 泵处理的服务器普通队列。

1.21.1 的自然运行期验证不能复制 26.x 的拆分玩家票据。实际 DistanceManager.addRegionTicket 同时更新加载集合和 TickingTracker，PLAYER 区域半径 3 产生 level 30 和真实 7×7 FULL 前沿；移除时走同一原生 API。熔炉静态 ticker 接收 Level 而非 ServerLevel，高度上界为 getMaxBuildHeight()。本版 RuntimeProof 只观察自身真实物理 state 创建数、holder futures、自然回调和 generation scope，不伪造其它版本的状态指标；游戏结果及尚缺范围仍只在里程碑维护。

本版性能前沿改用原生 DistanceManager.addPlayer/removePlayer 与 ChunkTrackingView，独立实现 probe。ServerPlayer 构造器会同步执行 adjustSpawnLocation；离线票据 token 的构造必须在探针专用线程局部作用域隔离这一次无关出生搜索，并断言所有维度仍冷，不能把预热世界当作冷前沿。普通玩家保持原生操作，真实出生门禁另外执行；不能将本版 token 或票据接口机械移植到其它 Target。JFR 窗口标记没有 elapsedNanos 时，工具明确使用真实 JFR 时间戳，保留旧格式的原始值。

旧世界保持对应原有 Target 与算法版本，不做跨 Minecraft 版本降级加载。BOP 的版本、存在性和可选入口按各 Target 单独核对，不把 Fabric 的反射签名直接宣称为 NeoForge/Forge 兼容。

## 上游与构建

1.21.1 的 source 复用候选把原生 Climate.RTree 的线程入口/出口状态视为输入与续流，而不固定 tie 排序。该版 RTree 的 Java 可见性是 protected；适配在原生 ParameterList 构造时保留原树，只暴露其 ThreadLocal 句柄，访问失败或未知 biome source 不激活候选。叶身份匹配才可复用，并重放实际原生出口；不同入口精确重算，关闭不清空上游 ThreadLocal。组件反例和待验范围见[故障分析](../research/incident-legacy_source_climate_state.md)，不能把其它 Target 同名字段当成接口适配或正确性证明。

1.21.1 的默认关闭缩锁候选单独审计本版共享状态：模板 repository 和 palette block cache 使用 ConcurrentMap，不移植 26.3 的 palette jigsaws 缓存锁；pool 的普通 maxSize 延迟缓存用池对象保护。Stronghold、Fortress、End City 静态生成状态分别在本版十参数 Structure.generate 的完整调用内持有独立类型锁，其余已审计原生结构不持有整个模板管理器监视器。能力守卫拒绝未知模组、生成器、结构和池元素，保持原生整段锁回退；具体证明与尚缺门禁只在里程碑维护，不能从字段审计推定完整并发或第三方兼容。

1.20.1 的默认关闭缩锁候选不能照搬上述缓存边界：模板 repository 是 ConcurrentMap，但 palette blocks 缓存是 HashMap，必须保留 palette 对象保护；pool maxSize 的读写也需可见性保证。OctahedralGroup 原生旋转表在六项填充前已经发布，冷并发会读到不完整表；候选以枚举对象保护完整原生初始化及读取，不重写旋转算法。启动时未显式启用或发现未知模组时不安装这四项行为注入，context 再检查实际能力标记及结构/池元素，未通过则保留整段锁。不同 Target 的 Mixin 安装、缓存布局与并发证明不得互相替代；当前门禁状态只在里程碑维护。

Target 集合来自模板标签 2.0.2；新增 Loader 在 2026-10-15 从 [NeoForge Maven 元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)和 [Forge Maven 元数据](https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml)核对，具体版本只写在 target.properties。当前模板插件不修改。

26.3 NeoForge 注册入口采用 [NeoForge 原生 DeferredRegister](https://docs.neoforged.net/docs/concepts/registries/)，但文档本身不能代替实际本版本源码、加载和 Oracle 验证。

```powershell
.\gradlew.bat :version:26.3-neoforge:compileJava --max-workers=2 --no-daemon --no-configuration-cache '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 -XX:ActiveProcessorCount=4'
```

运行前将本线程测试进程设为四逻辑 CPU、低优先级。正确性最多两队列并行，各自独立实例、世界和端口，正在验证的 Target 源码冻结；性能测量只保留单实例，不与正确性队列重叠。每个新 Target 的错误和验证状态回写当前里程碑；不为通过编译删除关键世界生成行为。
