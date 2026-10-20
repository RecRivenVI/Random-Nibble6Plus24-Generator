# 三种 FEATURES 方案边界

本页保留三种 FEATURES 方案的技术边界，不选择生产默认方案、不新增存档版本。当前首个里程碑只完成 B、人工体验与性能评估，A/C 研究保留但不继续推进，也不作为完成门槛。B 与 A 的受支持子集已有独立运行证据；C 包含完整目标 decoration loop、代表树木内核、独立接收记录和有限 pre-light 交接实验，尚未接入完整可玩的 C 物理世界。普通原版 FULL 基底的封闭接收机制与完整 C 基底的阶段交接分别验证，不代替动态玩法交接。

## 共同输入

装饰前以同版本 Minecraft 26.3 为 Oracle：真实绝对坐标、目标的 local seed、原版缓存 climate sampler 与 stored biome palette、紧凑结构输入，以及精确密度、表面和洞穴。邻区读取只使用明确的同种子输入，不借用 physical master seed。

安装 TerraBlender 时，隔离上下文不能复用宿主已经按其它种子初始化的 biome 参数表或末地群系区域。根上下文复制原生参数条目或末地来源配置，独立持有表面规则配置，通过 TerraBlender 的原生初始化入口绑定本地种子；不重置宿主对象，也不在缺少该模组时加载可选接口。同一目标的邻区子上下文仅复用这份同种子配置，RandomState、结构状态与会话资源仍独立。stored palette 仍是已生成生物群系权威，不以诊断采样替代。

SPAWN 的备用 uncached resolver 也使用该本地种子配置，不重建地形或替换已经携带的 stored palette。未审计的 TerraBlender 来源类型或初始化接口明确失败；未证明的表面规则继续完整精确回退，不尝试把第三方规则当成已审计原版节点。

BOP 兼容对照保留完整方块判等门槛。当前 TerraBlender 的末地 possible-biome 无序集合可使原版 feature index 跨 JVM 改变；独立原生 `end_reef` 重复已产生不同方块。不能通过新增方块忽略规则或擅自重排装饰把此限制标为通过，见 [BOP 验证](../../validations/compatibility-bop/validation.md)。

FEATURES 消费者使用原版入口、注册配置、feature step/index、placement modifier 和随机派生。替换表示方法或查询实现不授权修改随机调用次序。完整地形回退只用于无法证明的装饰前读取，不允许借回退真实执行八个邻区的完整 FEATURES。

新 B 开发世界通过独立隐藏 `development_b` codec、固定 marker 与显式运行 opt-in 区分既有 `mosaic` Ordering 1/2 世界。只在世界加载边界验证文件，运行期纯内存分流；缺失或混合身份拒绝加载，不回退旧实现。profile 格式、Seed V1 与既有世界语义不变，未公开 WorldPreset。

B detached 交接携带已生成的八份 stored SPAWN palette 和紧凑输入中的引用 start NBT，不保留关闭后的可变 session。原版模板实体的结构变种查询使用当前目标的紧凑 StructureManager，不借用 physical master-seed graph；超出已审计范围失败。`TemplateFeature.place` 的已审计 `getLevel()` 仅获取模板资源，实际读写仍经过严格 B WorldGenLevel。

原版 26.3 Jigsaw structure loader 会按模板重新计算包围盒；验证分别保留生成态与加载态，完整 Artifact 还原必须匹配直接使用原版 loader 的控制，不删除结构字段或新增忽略规则。

原版 FEATURES 的单 writer 读写范围从 `ChunkPyramid` 取得并检查；当前已审计范围为 radius 1。A 多源读取再派生 radius 2 与 biome-only halo，超过审计范围明确失败。原版未确定化实体字段保留原始证据；canonical 快照仍只排除 UUID，有限验收单独执行。

独立普通世界重复进一步确认此限制不是 C 造成的：同一溺尸目标在三个全新 JVM/save、相同 seed 与九 writer 顺序下，方块和非实体 metadata 相同，增援概率与击退抗性仍不同。26.3 `Entity` 构造器用无 world seed 参数的 `RandomSource.create()` 建立实体随机源；`Zombie.handleAttributes` 消费它。属性列表本身还存在跨 JVM 换序，诊断按名称对齐仅用于说明数值差异，不参与 canonical hash。`Mob.finalizeSpawn` 的 `follow_range/random_spawn_bonus` 使用 region RNG，本次重复值相同，因此不能把整个 attributes 或所有 random_spawn_bonus 都视为可忽略。原始快照和现行完整判等均未修改。

扩展到实际派生种子的相邻目标后，海底废墟的溺尸暴露了另一类原版非确定字段：`OceanRuinPiece.handleDataMarker → Drowned.finalizeSpawn → Zombie.handleAttributes/randomizeReinforcementsChance` 使用实体自身的 `RandomSource.create()`，影响增援概率与击退抗性，而非 decoration RNG。原 B 和完整地形控制自身重复也不同。保留原版行为与“除 UUID 外完整实体快照唯一”不能在此案例同时作为验收结论。

## 原版随机字段的有限验收

使用者已批准保留原版随机行为并明确有限验收例外。`NativeEntityAcceptance` 只属于 probe：不改原版随机源、不改现有快照哈希、不写入旧存档，也不删除原始 NBT。严格比较与有限验收分别输出；原 `-Frontier` 的失败证据继续保留，新增 `-AcceptedFrontier` 使用独立结果目录。

溺尸规则由三个独立普通原版物理世界支持，只用于 `minecraft:drowned`：

- 属性列表按唯一 `id` 对齐；重复或缺失 ID 拒绝，保留每个属性的全部成员。实体顺序、数量与 modifiers 列表顺序不重排。
- `minecraft:spawn_reinforcements.base` 不要求数值相同，但必须是有限 double，满足 `0 <= value < (double) 0.1f`。
- `minecraft:knockback_resistance` 中 `id = minecraft:random_spawn_bonus`、`operation = add_value` 的 `amount` 不要求数值相同，但必须是有限 double，满足 `0 <= value < (double) 0.05f`。

两个上界取自 26.3 实际字节码的 float-to-double 常量，不替换为近似十进制上界。字段存在性、NBT 类型、操作类型与其它成员继续严格比较；不把整个 attributes、所有 random_spawn_bonus 或其它实体类型视为例外。`follow_range` 的 region-RNG 加成尤其保持严格。

B 出生区域村庄猫另用三个全新普通原版 JVM/save 核验属性列表的容器换序：仅对这个已证明的猫案例按唯一属性 `id` 对齐整项。所有值、modifiers 次序、缺失/重复 ID、变种、位置、朝向和其它成员仍严格；默认实体验收未放宽，B 交接探针在核验独立原版证据后显式使用此规则。原始列表、UUID 和 strict hash 保留，不能把列表换序误报为宿主种子改变生成结果。

堡垒 nether-chests 的猪灵采用使用者另行批准的 `26.3-native-bastion-piglin-attribute-order-1`。只有显式选择的这一诊断 fixture 开启；仅 `minecraft:piglin` 的 attributes 列表可按唯一 id 对齐，成员集合必须恰为 `minecraft:follow_range` 与 `minecraft:movement_speed`。两个属性的全部内容、数值、modifier 成员和次序、其它实体字段及 brute 仍严格；未知、缺失或重复成员拒绝。四份独立原版自然捕获及有效双向干预证明合法身份哈希遍历能产生两种次序，重放完整 UUID-only 快照；验收前只读复核原生因果证据。默认比较、生产序列化、原始 NBT 和 canonical hash 不改变，不推广到其它实体、Target、模组或任意猪灵属性集。

输入的原始实体必须能重新对应其 UUID-only 快照，不能配一份空实体列表绕过比较。所有非实体快照字段、feature seed/随机位序列、槽返回值和跨界贡献仍要求一致。每处原始差异与严格哈希都保留，有限验收不是新的 canonical hash。

末地城另有三个 fresh 普通物理原版世界的证据：seed 0、End `(-77,-79)`，相同九 writer 顺序、486 次 feature-seed 调用，目标内各有三只潜影贝。重复只在 UUID 和 `Rotation[0]` 不同，方块、非实体 metadata 与其它实体字段一致。原版 `EndCityPiece.handleDataMarker` 对 Sentry 只执行 `EntityType.create → setPos → addFreshEntity`，不调用会重置朝向的 `Shulker.finalizeSpawn`；`LivingEntity` 构造器留下 `entity.random.nextFloat() * 6.2831855f` 的水平朝向。

`26.3-native-end-city-shulker-yaw-1` 因此只在独立末地城验收中显式开启：仅 `minecraft:shulker` 的 `Rotation[0]` 可不同，必须为有限 float、`0 <= yaw < 6.2831855f`，Rotation 必须为两个 float 的列表。俯仰 `Rotation[1]`、位置、数量、顺序、附着面、生命与所有其它字段仍严格比较；没有把任意 0～360 度视为合法范围。该规则不同时授予溺尸例外，默认比较仍沿用原溺尸规则，不能扩大为全局实体忽略名单。

`-AcceptedShulker` 同时对照原 B、按需 B 和完整 C，保留全部 raw NBT、严格 hash 失败与有限验收结果。独立原版实体列表还与 C 逐字段比较；这不把九 writer 的全目标 FEATURES 当成 B/C 的合同。异常值、尚未证明的字段和其它数据差异继续失败，不自动扩大范围。

## A：恢复邻区贡献

先执行 B 的目标装饰，再以绝对 Z/X 顺序计算八个 source 的受支持贡献。每个 source 都属于同一个 S(C) universe，不调用该物理邻区自己的种子 resolver。只枚举相关 feature 槽和候选，不运行邻区完整 decoration loop。

矿脉使用已经对照的几何和确定性 replacement；树木复用审计过的原版单 feature/selector。边界外临时稀疏状态保留碰撞、返回值、BE/NBT、ticks 和后处理反馈，供同一贡献求值读取；最终只留下目标内数据，临时状态关闭后不可再访问。

支持矩阵是结果的一部分。安全矿脉与代表白桦不能代表湖泊、其它植被、roots、其它树种或邻区结构已还原。未支持项必须使输出明确标记为部分 A，不能静默呈现为完整 A，也不能回退完整邻区 FEATURES 来冒充轻量方案。

A 的针叶树能力审计允许有限直干的 Spruce/Pine foliage 和完整 `trees_taiga` selector（含倒云杉分支），命中后仍执行原版内核。26.3 Pine 的 radius 还会加上由树干高度减 foliage height 得到的随机值，不能只看配置 radius；当前树高至多 10，Pine 基础 radius 至多 1、foliage height 为 3～4，保守总半径至多 8，再计入原版叶距与 shape 邻接读取，仍在每个 source 的有限读取范围内。Spruce radius 至多 3、offset 为 0～2、trunk height provider 为 1～2。只接受常量/均匀整数 provider、无 roots/新 decorator、有限 clearance；巨型针叶树、混合未审计分支和超范围配置明确拒绝。C 复用这个有限求值审计，但另检查所有输出和贡献传输，不由 A 的通过自动取得权限。实际验证范围见状态页，不把几何审计当作完整原版 parity。

A 的真值是固定 procedure 的支持子集控制，不是所有合法 Vanilla 探索顺序的唯一答案。对九 writer 参考另保存差异，不能用选择性控制相同声称全量 parity。

## B：只保留目标装饰

目标 writer 只执行一次原版 FEATURES。外部读取使用 S(C) 的纯 TERRAIN，优先局部列和 stored palette，未证明能力明确精确回退。边界外保持只读，拒绝发生在实际副作用之前，覆盖 region、直接 ChunkAccess、sections、BE/NBT、entities、ticks 和后处理能力。

邻区密度按实际读取位置请求原版小体积批次，默认输出为 4×32×4；更大的已审计插值周期相应扩大高度。保持原版沿 Y 累加的浮点路径，不以 `sampleValue` 替代批次。当前查询内复用原版插值输入的稀疏整高度角点列，避免细分后重复计算相同角点；这些不是完整邻区地形。高度与表面梯度只计算实际需要的列，表面规则也限定到局部批次，含水层仍保留原版完整边界。密度查询使用独立 sampler context，含水层/表面上下文仅还原原版真正共享的二维输入及其缓存网格，避免局部批次改变随后标量读取的缓存命中语义。未证明的三维缓存耦合、不同网格共享缓存、嵌套插值、未知节点、未对齐网格、冰山/荒地反馈和洞穴表面修复继续精确回退。角点复用只绑定当前密度上下文；采样线程绑定在正常返回和异常时清理，缓存与 buffer pool 在查询关闭时释放。目标地形与普通原版不启用该局部采样钩子。

B 的只读生物群系输入由单个目标作用域持有，覆盖 Vanilla 元数据要求的 TERRAIN 邻区及其 BIOMES halo。原版 `createBiomes` 对同一位置最多执行一次；已完成的 palette 按完整容器复制到精确回退的 ProtoChunk，保留未使用的 palette entry，不以 uncached sampling 重建。每个邻区的 possible-biome 集合、有限 stored-biome manager 与只读高度范围准备可复用；噪声、含水层、表面 sampler 和可变结构会话不借用。作用域绑定宿主实例、local seed、生成器与有限坐标范围，完成或异常时清空，不写入存档，也不成为重载 authority。完整回退后的直接读取仍使用已冻结的完整数据。

26.3 B 的结构来源复用入口不以服务器模板管理器锁覆盖整轮结构生成。模板管理器自身用 ConcurrentHashMap 发布加载完的模板；Palette 的普通 HashMap 过滤缓存与 Jigsaw 缓存按 palette 同步，模板池的 maxSize 缓存按 pool 同步。所有共享缓存消费者使用同样的短锁，包括原版生成与重载；不修改缓存结果、列表顺序或随机消费。旋转、候选拼装、局部高度和碰撞求值留在锁外。

要塞的静态 piece 权重、下界要塞共享权重对象与末地城静态 shipCreated 不属于模板缓存，分别以 JVM 范围的独立锁保护原版 Structure.generate 的完整 reset/expand 区间。锁顺序为结构类型锁到 pool/palette 缓存锁，palette 不回调 pool 或结构生成；锁内不等待 ring futures。普通原版仍使用原版算法；这些同步点只保护共享可变状态，不改变世界身份或生成规则。

只有经审计的原版结构类和原版 pool element 类型采用缩小后的结构入口；自定义类与嵌套自定义 pool element 回退模板管理器整段锁。未复用的参考入口和旧 scheduler 入口保持整段锁，不修改旧存档的装饰方式。原版资源重载和结构模板编辑的生命周期仍由原版管理，不新增跨服务器或跨资源重载的缓存。

拒绝写入的返回值及后续随机消费按此 B procedure 对照，不声称等于先生成完整装饰后裁剪。目标内数据完整比较，已经保存的 Ordering 1/2 世界不重解释为本实验。

## C：有限的跨界贡献候选

第一候选只允许通过能力审计的树木与其必要附属数据。其它装饰和结构仍按 B 切断；不能因为 block 可跨界，就默认 BE、实体或任何 tick 都获得相同权限。每种附属能力都需要完整 provenance 和实证。

当前完整流程实验采用同种子 Pre-FEATURES 读取：source A 的树读取 S(A)，读到自己此次求值的临时写入；不读取邻区 FEATURES 或玩家运行期状态。它避免 physical 邻区完成顺序影响树的候选和随机消耗。读取各邻区 S(N) 的装饰前数据是另一种语义，当前未实现，也未冻结生产选择。

`SelectiveFeatureGeneration` 仅在显式调用时创建 C region；完整目标 `applyBiomeDecoration` 恰好执行一次。结构、biome feature 集合、step/index、原版 seed 设置和选择器都保持原版入口。只有整项顶层 `PlacedFeature` 的所有分支、修饰器、几何、provider 与 decorator 均通过审计，才借给它树木临时区域；混合未知树种的 selector 整项使用 B，而不是挑掉未知分支。其它装饰及结构在 C region 上仍严格切断所有附属写权限。

临时反馈边界是一次顶层 placed-feature 调用，包含该槽原版生成的所有候选和 selector 分支。目标内写入立即保留；边界外写入只供本槽反馈，并在槽结束时形成不可变贡献包。下一槽的边界外读取重新观察纯 TERRAIN，不看到前一槽的临时树冠；其它装饰不会获得隐式树木权限。这是本次可复现的候选语义，不声称等于多个原版 writer 的世界，也不写入旧存档合同。

装饰 RNG 直接使用原版已设好 seed 的同一个对象，不补抽随机数；区域辅助 RNG 和 sub-tick 序号由目标 loop 持有，树木子调用共享而非重置。每个完整树木槽汇总为一个包，key 的 step/index 区分槽，attempt 固定为 0，包不是单棵树的逐操作日志。原版 feature 返回值、全部随机位序列、最终目标快照及原始外部副作用与保留完整 TERRAIN 的同策略控制逐项比较；该控制不执行邻区 FEATURES。

跨界写入先形成有限贡献包，而非 worker 直接修改邻区。包至少区分来源世界、dimension、source ChunkPos、source local seed、生成合同、feature step/index、候选与操作序号、接收位置和相关副作用。包的外延必须在已证明的有限范围内，未知 decorator 或超范围操作拒绝。

以下规则是接收设计，独立记录与重放已实现，生产调度尚未接入：

- 接收者未生成时保留贡献，不为它主动生成或创建假 Chunk。
- 相同内容的重复包幂等；同一包键出现不同内容是错误，不能悄悄以最后到达者为准。删除后重放不得遗留旧记录。
- 冲突按固定来源键排序，从接收者不可变的基底重放，不能按实际到达时间决定最终结果。当前候选按 world、dimension、contract、source Z/X、seed、step、index、attempt、feature 排序，较小键优先；没有把这个顺序写入旧存档合同。
- 同次树木求值保留最终方块、原始 BE NBT 和原版 tick/后处理列表顺序；BE 与对应位置的附属状态一起接受或拒绝。这不是完整操作日志，不具备撤销已经执行的运行期事件的能力。
- 跨 seed 接收需检查接收者自己的可替换基底；source 看到空气不代表 receiver 也是空气。外来的树冠不能据此覆盖石头、结构方块或已有 BE，候选白名单和拒绝后的树形必须另作对照。
- 接收范围随自然探索扩大：后来的合法贡献可以增加树冠，但不得越过保护边界覆盖玩家修改。初次 FULL 不代表所有未来入站贡献已经稳定，也不能偷偷重新引入九 writer 等待屏障。
- 玩家修改和贡献去重必须可持久化，重载不得恢复旧自然方块覆盖修改。仅靠“当前还是空气”的判断不足以保护玩家挖空位置。
- 对已经 LIGHT/FULL 的接收者，实际改变方块要走正常 physical lifecycle，更新高度、光照、POI 和 BE/tick，不能复用 isolated region 修改已激活的 Chunk。

当前 `TreeContribution` 接收通过能力审计的白桦、橡树、有限松树/云杉、倒木及蜂巢内核输出，外延最多为 source 的 radius 1。针叶树仅新增云杉原木与树叶两种输出，不能据此传输石头、未知方块或放行巨型针叶树；完整 selector 的任意分支未通过仍整项使用 B。使用 source 同种子轻量读取与临时状态，不真实执行八个邻区 FEATURES。完整地形副本的原版内核另作对照，原始 NBT 不删除字段。主动选取的内核与默认 biome 自然装饰正例分开记录，不能用蜂巢内核正例代替自然完整流程的蜂巢覆盖。扩展套件使用独立的候选 provenance 标识，不改 Mosaic format 或旧世界的 Ordering。

`FeatureContributionJournal` 不加载 Chunk、不接触 `ServerLevel`。内核接收实验以另一个 local seed 完成 B 的真实方块和 BE 为基底，只允许写入未保护的空气，因此接收者自己的结构、植被和实体方块不会被覆盖；玩家挖成的空气也有独立保护记录。缺少基底时等待，不能默认空气。正逆序、并发重复投递、删除和跨 JVM 重载已验证，但尚无真实玩家修改事件监听。

完整 C 对完整 C 的验证前沿进一步要求来源与接收者都由同一 master seed 经 Seed V1 派生，并分别执行自己的完整 C；不把 B 基底当作 C 基底。只显式准备直接接收者，再次外溢保留为 pending，不递归创建外围 FEATURES。UUID-only 严格生成对照在溺尸案例失败；有限验收使用上述独立规则，不将已通过的 B 基底接收证据套用到这里。

固定输入接收是独立门禁：一个进程只生成一次有限 C 前沿，保留整份目标快照和含 UUID 的原始实体 NBT，再导出接收所需的真实位置基底及贡献包；另一进程不运行生成，直接重放完全相同的输入。接收的顺序、并发、分批就绪、保护和删除重载可以据此独立验收。重放前后校验完整目标及原始实体未变，文件字节也不变；实际生成调用计数必须为零。它不要求也不宣称两个重新生成的溺尸相等，`generationParity` 明确未评估，不绕过原 `-Frontier` 的失败门槛。

原版树木的 `StructureTemplate.updateShapeAtEdge` 还会触发未被树木改写位置的 tick。贡献包保留这些原始证据，接收时只授予同包已接受方块位置的附属操作权限，未归属操作单独拒绝计数。这是 C 的保守候选边界，不声称最终接收结果等于完整 Vanilla 树木世界。

C 的最大未证明点仍是完整 C 世界的动态物理接收、持久化交接和玩家修改保护，而不是允许树叶写到 x=16。独立记录只保存到验证结果，不扩大现有 format V2 的含义；真实生产接收和存档接入需单独授权。无法在该边界内证明时明确报告，不将线程先后顺序作为默认语义。

`FeatureCPhysicalBatch` 只存在于 probe，用来隔离验证封闭批次的物理写入机制。它不生成 C 基底；把已核对的真实树木贡献投影到另一种子的普通原版 FULL 区块，前置空气检查全部通过后，在主线程调用原版 `setBlock`、BE 加载和 tick scheduler。接收器不加载邻区，由验证准备必要邻区。非空 post-processing 在已 FULL 场景中尚未审计，明确拒绝。正常停服后另一 JVM 检查区块与独立回执，不能把这称为产品存档事务或崩溃恢复。

`FeatureCPreLightVerifier` 同样仅在 probe 中，补上完整 C 基底的有限交接证据。每个准备目标使用自己的 S(C)，接收批次封闭后写入真实磁盘 Chunk 数据，经普通 ChunkMap 加载、POI 检查和原版光照步骤推进到 LIGHT；重启不重新生成 C。原版结构解码仅在本次登记的实验范围传入所属 local seed。外围未准备的接收者保留 pending，不递归补齐，也不把这份普通宿主上的实验 patch 标记为旧 Ordering 1/2 世界。普通 LIGHT 请求可能同时触发 FULL 级 ticket，所以本项显式调用原版 LIGHT 步骤，禁止 SPAWN/FULL；产品 scheduler、动态迟到贡献和存档事务没有因此获得验收。

激活前交接另有 `FeatureCPreLightTransaction` 候选：先持久化完整接收 payload，再写物理区块，完成原版加载/POI/LIGHT 与存档后才发布完成标记。记录由验证工具显式管理，路径/维度/seed、所属局部 seed、前沿和全部原始 NBT 都受校验。Compound 键排序只用于完整性摘要，列表顺序、类型、UUID 和未知 payload 字段保留；没有扩大 canonical 忽略规则。

恢复只在前沿尚未加载或激活时运行：先检查全批已有磁盘数据，内容一致的 FEATURES/INITIALIZE_LIGHT/LIGHT 区块保留，只补缺失区块。数据改变、未知字段差异、SPAWN/FULL 状态或已有 holder 均拒绝，不能重新覆盖玩家数据。完整记录先写临时文件并同步，再原子发布；不能原子移动时直接失败，不用部分复制作为回退。验证通过范围见状态页，进程中断与电源故障分别保留边界。

该物理探针有意不实现“新贡献改变已激活批次”的重放：批次内容不同就失败，完全相同的重复才无操作。已发生的 tick 与交互不可由纯数据排序撤销；在完整动态方案确定前，不把先来先占当作 C 最终规则。封闭批次的基础测试由调用者提供前置保护；自动修改观测另用独立入口验证，不将两者混为真实玩家验收。回执不进入旧 Mosaic profile 或 SavedData；物理测试原始输入、自然 BE/tick 正例及未验证范围见状态页。

`FeatureCEditTracker` 只在 probe 中为明确登记的 ServerLevel 实例与接收 Chunk 观测原版 `LevelChunk.setBlockState` 的成功修改。记录位置而非伪造基底；即使尚未收到贡献，放块后挖回空气也保留保护。接收器自己的直接方块写入使用精确位置、状态、一次消费的许可，原版嵌套邻接修改没有通用豁免；真实异常原样传播，许可在 finally 清理。读取保护时只取已加载物理状态，不请求区块，工作线程调用拒绝。

保护记录绑定存档路径、维度、seed 和接收者，正常关闭时解除实例绑定；独立验证结果中的记录在新 JVM 显式恢复，不能当作产品存档 authority。它覆盖通过原版方块写入口产生的修改，不覆盖单独更改 BE 内容、直接改 section 或完整玩家交互。`-EditProtection` 以原生放块/挖空模拟行为，检查贡献延迟到重启之后首次接收、已接收后挖空及重复回执；不证明崩溃原子性、后来冲突替换或动态 C 世界完成。

封闭蜂巢接收必须区分原版数据层次：ProtoChunk 中已实例化 BE 的 payload 由 `saveWithFullMetadata` 产生；LevelChunk 存储还添加 `keepPacked=false`。验证完整 payload 逐字段不变，再单独精确验证存储封装，不能直接把两者字面不等称为内容丢失，也不能泛化成忽略 BE 字段。该检查不改变 canonical hash 或实体例外。

重放顺序独立只针对相同贡献集合、相同基底与相同保护状态；不能承诺“一个 source 尚未生成”和“全部 source 已生成”有相同画面。已经发生的运行期 ticks、掉落或玩家交互也不能通过重放倒转。因此 C 必须分别验收静态生成数据与 physical lifecycle，不能用纯数据排序测试冒充真实世界时序安全。

`FeatureCAppendReceiver` 是独立 probe 原型，将待处理 journal 与实际应用的 journal 分开保存。贡献包可在工作线程纯数据发布，物理接收只能在主线程、接收者及必要邻区均已加载时进行；不获取 ticket 或加载 Chunk。每次先计算整个差量，只有新增位置且原位置的方块、BE 原始 payload、两类 tick 与后处理均不改变，才激活新增位置。旧位置不会重复创建 BE 或调度 tick。

较小来源键迟到、删除或附属数据变化若要求替换已经激活的位置，整个接收批次标为 `PENDING_CONFLICT`，写入为零；未冲突的新位置也不部分写入。待处理输入、已应用输入和保护记录都保留，重启不得将冲突伪装成 READY。这个候选只解决单调追加，不把先来先占冻结为最终 C 规则，动态冲突替换仍未实现。

增量原型除观测原生方块写入外，还在接收前比对自己先前应用的 BE payload；发现内容变化即保护该位置。它不是完整 BE 交互事件监听，也不能发现内容修改后又还原的全部历史。实际 API 修改与重载证据单独记录，不用这些测试宣称真实玩家交互或完整 C 世界已验收。物理应用异常会让该 receiver 失败并原样传播；不发布完成回执，不声称已 FULL 的部分写入具备崩溃原子性。

## 比较与验收

三种设计共用装饰前证据和目标内完整 schema。A/B 严格切断要验证邻区零持续修改；C 需另外验证贡献包、冲突重放、正逆序与并发、保存重载、玩家保护，以及缺失邻区不会触发生成。

运行成本分别记录准备、邻区轻量求值、完整回退、装饰和快照，不把减少 ProtoChunk 数量直接换算为生产探索提升。人工比较关注硬边界、树冠/矿脉截断和跨界结构的视觉代价；GUI 操作由使用者完成。

三方案横向探针对相同输入轮换执行顺序，目标的完整 TERRAIN 快照先判等，再分别比较最终内容。A 的 B 准备快照必须等于独立 B；A 支持集取矿脉与树木能力的并集，分别未通过某一类审计不等于整体未支持。邻区结构仍单独标为未重建。C 输出贡献包不能算作邻区已经接受的方块。

完整 TERRAIN 调用在实际生成入口计数，包含树木临时区域内的精确回退；局部列、噪声批次、biome-only 输入另作区分。因此“完整邻区为零”不是“邻区成本为零”。保留九 writer 参考仅用于展示内容取舍，和它不同是方案语义，不能把差异率等同于正确率。具体实测统一记录在生成实现状态。

当前代码与证据边界见[生成实现状态](../project/status-generation.md)，整体完成条件见[里程碑 1](../project/milestone-productization.md)。
