# C 完整装饰与树木贡献实验

## 目标

验证 Minecraft 26.3 完整目标 FEATURES 在 C 策略下只执行一次：通过审计的树木形成跨界贡献，其余装饰和结构严格切断。原版代表树种的跨界结果可以脱离生成 session 保存，接收记录在正序、逆序、并发重复投递、磁盘重载和独立宿主 JVM 中保持一致。完整流程、单树内核、纯数据接收、普通原版 FULL 基底的封闭批次物理接收，以及完整 C 基底的有限 pre-light 交接分别留证；不据此声称已有完整可玩的 C Mosaic 世界或真实玩家事件接入。

使用已注册白桦、带蜂巢白桦、倒木、橡树及有限松树/云杉配置，在真实地形边界位置调用原版 feature；与独立完整地形副本的原版调用比较目标、跨界方块、原始蜂巢 NBT、ticks、后处理与 RNG。主动选择的内核位置不算默认 biome decoration 自然正例。

## 运行

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1
.\gradlew.bat :worldgen_validation:verifyFeatureC
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Flow
.\gradlew.bat :worldgen_validation:verifyFeatureCFlow
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Frontier
.\gradlew.bat :worldgen_validation:verifyFeatureCFrontier
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -Replay
.\gradlew.bat :worldgen_validation:verifyFeatureCReplay
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
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -AcceptedFrontier
.\gradlew.bat :worldgen_validation:verifyFeatureCAcceptedFrontier
```

两个独立宿主依次启动；使用 4 logical CPU、BelowNormal、单个重负载 JVM。内核/接收输出覆盖 `result/current/26.3-fabric/`，完整流程另写 `result/flow/26.3-fabric/`，不覆盖对方证据。探针只操作自己的临时实例和独立 NBT 记录，不修改日常存档。已有验证实例/结果先移入回收站。

`-Frontier` 将完整 C 来源与完整 C 接收者串联，固定输出 `result/frontier/26.3-fabric/`。在最多 128 个确定位置中寻找四个自然跨界来源，使用同一个 master seed 和未经修改的 Seed V1 分别派生每个目标。仅显式生成这些来源的直接接收者；接收者再次写出的贡献保留，不递归要求新 FEATURES。它仍是独立数据重放，不向 physical Chunk 应用。

当前 `-Frontier` 尚未通过：master `123456789` 的 Overworld `(-14,-13)`，local seed `804143585224698994`，海底废墟溺尸的原生属性在同输入重复控制中不同。脚本保留失败、原始实体 NBT 与完整快照，不产出 PASS；`verifyFeatureCFrontier` 也不应被当作已通过的门禁。这个入口保留用于后续解决验收边界，不覆盖 `-Flow` 和内核套件的证据。

`-Replay` 是独立的固定输入接收验证，输出到 `result/replay/26.3-fabric/`。第一个 JVM 生成上述有限 C 前沿，冻结完整目标快照、包含 UUID 的原始实体 NBT、外来贡献与接收位置的真实基底。第二个 JVM 只从磁盘读取这同一份输入，不重新运行 worldgen；接收结果必须完全相同。它只证明接收算法，不判断重新生成的实体是否相等，状态明确为 `PASS_FROZEN_INPUT_REPLAY_ONLY` / `generationParity = NOT_EVALUATED`，不会覆盖或替代 `-Frontier` 的失败。

独立原版随机性诊断使用下面的入口；它不运行 C，不覆盖前沿失败证据：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -NativeRepeat
.\gradlew.bat :worldgen_validation:verifyFeatureCNativeRepeat --max-workers=1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -NativeShulker
.\gradlew.bat :worldgen_validation:verifyFeatureCNativeShulker --max-workers=1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-feature_c/Run-FeatureC.ps1 -AcceptedShulker
.\gradlew.bat :worldgen_validation:verifyFeatureCAcceptedShulker --max-workers=1
```

该入口依次创建三个不同 JVM、不同路径的全新普通世界，种子均为原前沿溺尸案例的 S(C)，通过原版物理 ChunkMap 串行请求九个 FEATURES writer。比较相同的 writer/biome/feature-seed 轨迹，禁止进入后续阶段，不运行 isolated/A/B/C。完整目标快照和含 UUID 的原始实体保留在 `result/native_repeat/26.3-fabric/`。逐字段差异工具没有忽略名单，不修改输入；原有完整 canonical 快照仍只使用 UUID 例外。

属性按名称建立的辅助视图只用于解释原始列表换序，不参与快照 hash 或判等；原始列表差异、UUID、数值与缺失字段全部留证。

`-NativeShulker` 复用相同物理过程，在 seed 0 的 End `(-77,-79)` 创建三个独立 JVM/save；输出到 `result/native_shulker/26.3-fabric/`，不覆盖溺尸证据。每次目标内三只潜影贝；仅 UUID 与水平朝向不同，其余完整数据及九 writer 轨迹相同。严格快照仍为 FAIL，诊断本身不放宽验收。

`-AcceptedShulker` 先核验这三份独立原版证据，再在宿主 17/23 分别运行末地城 seed 0 与同坐标 seed 1：原 B/按需 B、完整 C/完整地形 C 控制、逆序和三会话并发，共每宿主 13 次生成。只按显式末地城规则允许有限水平朝向变化，溺尸例外不同时生效。结果固定在 `result/accepted_shulker/26.3-fabric/`，每个 C/B 基底保存全快照和 raw NBT，第二 JVM 逐一比较。三个潜影贝的数量、位置及其它数据还要与独立原版直接对照。没有跨界贡献、物理应用或客户端视觉验收。

使用者批准的有限实体验收由 `-AcceptedFrontier` 单独执行，输出固定为 `result/accepted_frontier/26.3-fabric/`。运行前核验上述三份原版证据；每个扫描目标与生成目标保存完整快照和原始实体 NBT，第二个宿主逐项重读比较。规则只包含[方案边界](../../documents/design/architecture-features.md)列出的溺尸字段，原有 canonical hash 和 `-Frontier` 的失败不修改。所有控制/逆序/并发比较也输出原始差异与严格 verdict；不能只在最终汇总跳过 hash。

`-Physical` 读取本套件 `current` 的真实树木内核贡献，使用 seed 23 的全新普通物理世界。准备接收区块及一圈正常 FULL 邻区仅由探针驱动；接收器自身只查已加载区块，不请求、生成或持有 ticket。第一 JVM 经主线程的原版 `setBlock`、BE 加载和 tick scheduler 应用封闭批次、正常保存停服；第二 JVM 重开同一测试世界，检查原始方块、BE、两类 tick、结构及 biome 数据，并再次投递同一批次。结果固定在 `result/physical/26.3-fabric/`，不会读写日常实例。

这项刻意把物理机制与 C 基底生成分开：接收基底是实际普通原版 LevelChunk，既没有替换为 C，也没有修改地形来制造空气。它只接受已有 journal 投影中的真实空气位置；保护挖空通过探针正常放块再挖空模拟，不称人工玩法。批次写入期间的存档崩溃一致性、后来的冲突替换、玩家事件监听和完整 C 存档仍未实现。完成回执保存在验证结果中，只验证正常停服重载，不充当产品 SavedData。

`-PhysicalHives` 另取同一内核证据中实际包含蜂巢的两个完整贡献包，不更改包内方块或 NBT；使用 seed 1 的 fresh 普通物理世界，输出到 `result/physical_hives/26.3-fabric/`。不清地形制造接收空间，不把该内核正例称为自然完整 decoration loop 的蜂巢正例。其余流程复用封闭批次接收与第二 JVM 的原存档重载。

## 通过标准

`-Append` 固定输出 `result/append/26.3-fabric/`，四个串行 JVM 分别测试正序首次接收/重载及逆序首次接收/重载，使用两份 fresh seed 1 普通物理世界。贡献来自未修改的真实树木内核包，接收基底明确为 `NATIVE_PHYSICAL_NOT_C`，不清地形制造空气。包从工作线程发布，接收只能主线程执行，已有方块修改通过原生入口自动观测。

门禁状态只能为 `PASS_MONOTONE_APPEND_ONLY`：正序存在多波正数写入、蜂巢和 block tick 正例；逆序必须实际复现较小来源键改变旧结果的冲突。冲突时整份原版 Chunk 序列化数据完全不变，不能先写非冲突位置；重复包也不增加 BE、tick 或写入。未加载依赖不由接收器主动加载，工作线程物理调用拒绝，异 seed 回执拒绝，所有实例绑定清理。

实际已接受蜂巢的在巢计时经原版 BE 加载入口修改一个值，方块不变，下一次接收必须保护该位置并保留完整修改后 payload；这不是人工玩家操作或通用 BE 事件监听。两次独立重启先核对全部相关方块、BE 原始数据、ticks、biome 和结构，再重放重复包；实际 FEATURES 生成与写入均为零，保护位置及未解决冲突保留。正逆序最终物理世界并不要求相同，因为冲突替换仍是明确未实现能力；不能将待处理状态计为 C 完整动态接收通过。

`-Recovery` 复用完整 C 的有限 pre-light patch，固定输出 `result/recovery/26.3-fabric/`。四个 JVM 串行使用同一个新临时存档：第一个准备全部真实 C 输入，持久化交接记录，在只写入 12/25 个区块后以明确 exit 73 直接中断；第二个补写缺失区块，经原版加载、POI、LIGHT 与正常存档后，在完成标记之前以 exit 74 再次中断；第三个从已保存 LIGHT 恢复并提交记录；第四个正常重载已完成记录。两个非零退出只在指定阶段、指定证据和指定退出码均符合时算预期，其它失败照常报错。

恢复记录绑定世界路径、维度、宿主 seed、每个接收者的派生 seed 和有限前沿；完整原始 payload 校验包含 UUID 与未知字段，Compound 键排序仅用于文件完整性摘要，不修改内容或现有 canonical hash。恢复前检查所有已有区块；不请求 holder，不覆盖内容改变或已进入 SPAWN/FULL 的区块。未提交临时文件不能替代已发布记录；记录发布要求原子移动，不支持时失败。

必须为 `PASS_C_PRELIGHT_RECOVERY_ONLY`：仅首个 JVM 生成 C，其余生成调用为零；第二阶段只补写 13 个缺失区块，第三及第四阶段零交接写入。所有原始方块、biome、结构、BE、实体、ticks、后处理与保存高度保持，正常 POI 和整批 LIGHT 的数据一致。记录损坏/异世界/重复接收者/未知封装字段与激活数据拒绝。该项证明进程中断后的激活前恢复；不证明电源故障、已 FULL 区块的事务、玩家交互回滚、动态冲突替换或产品 C 存档格式。

`-EditProtection` 使用同一组真实蜂巢内核贡献、seed 1 的新普通物理世界，固定输出 `result/edit_protection/26.3-fabric/`。先登记接收区块的原生方块修改监听，再通过 ServerLevel 正常放玻璃、挖回空气；不手工标记保护位置。交替选择立即接收和等第二 JVM 重启后才首次接收的区块。已经接收的区块再实际挖空、重复投递、保存重载；重载前的所有方块/BE、ticks、biome 与结构数据须先核对，再允许延迟批次应用。

该项必须为 `PASS_PHYSICAL_EDIT_PROTECTION_ONLY`：实际直接贡献写入与精确豁免计数相等，修改记录自动产生；所有前置/后置空气保护生效，蜂巢和 block tick 有接收正例。重复投递零写入、重载零实际 FEATURES 生成、接收不请求区块、世界身份隔离、工作线程拒绝、注入异常传播与许可清理、实例绑定清理均须通过。记录在验证结果中，不写入 Mosaic profile。它是通过真实物理 API 模拟玩家修改，不是 GUI 人工玩法或 BE 内容交互验证；迟到冲突替换与崩溃事务明确未验收。

`-PreLight` 独立检查完整 C 的有限物理交接，固定输出 `result/prelight/26.3-fabric/`。所有准备区块分别以同一 master 经 Seed V1 派生自己的 local seed 并执行完整 C；所需范围从原版 LIGHT 的 FEATURES 依赖推导。自然贡献在接收者自己的完整 C 基底上按 journal 接收，再由原版序列化写入全新临时世界，普通 ChunkMap 请求实际读盘并推进到 INITIALIZE_LIGHT。随后用真实 physical holders、原版依赖和 `ChunkMap.applyStep` 调用原版 LIGHT。不是九个同种子 writer，也不真实执行邻区的原版 FEATURES 来恢复装饰。范围外贡献留为 pending，不递归扩展。

LIGHT 的普通请求同时具有 FULL 级 ticket，可能推进 SPAWN；本项刻意采用上述显式阶段边界，不改写 scheduler，也不称完整生产调度通过。光照快照在整批 LIGHT 完成后取得，不能在每个早完成区块仍会接收后续邻区光照时捕获。summary 的 stages 是实际 ChunkStep 调用数，包含磁盘加载的 pass-through；不能将其全当作重新生成次数。准备范围外可能执行普通原版早期依赖，但不得进入 FEATURES。

探针只对本次明确登记的区块给原版结构 NBT 解码传入 local seed，不改变产品加载器或已有 profile。必须逐项核对原版持久化 payload（方块、biome、结构、BE、实体原始 UUID、ticks、后处理和保存的高度图）；状态、保存时钟和光照封装单独检查，不修改 canonical 比较规则。来源完整快照、入站后的完整快照与原始 payload 全部保存。第二 JVM 重开同一个临时存档，C 生成必须为零，内容、POI 和原版光照保持。

此门禁状态只能为 `PASS_C_PHYSICAL_PRELIGHT_ONLY`，必须有自然 C-to-C 方块与 tick 接收正例；严禁物理 FEATURES 重新生成或进入 SPAWN/FULL。它不证明已 FULL 接收者的动态贡献、真实玩家修改、崩溃事务或 C 生产世界；普通原版 FULL 封闭批次仍由原门禁另验。

`-AcceptedFrontier` 必须为 `PASS_FINITE_ENTITY_ACCEPTANCE`，仅表示批准规则下的完整有限 C 前沿通过，不表示 UUID-only 哈希相同。全部非实体字段、每槽返回值/随机位序列、跨界原始贡献严格相同；实体字段仅允许已批准且独立原版验证的有限差异。原始实体与快照的绑定必须核对，缺失、重复 ID、非法类型或超范围数值拒绝。所有扫描与生成目标都必须通过跨 JVM 对照；意外差异不得输出成功。旧失败证据、原 B 和原版 Oracle 保持。

`-Physical` 单独标为 `PASS_SEALED_PHYSICAL_BATCH_ONLY`，不能替代 C 生成或动态接收门禁。必须实际接收非零树木方块与附属 block ticks；BE、fluid ticks 的正例数量单独报告。重复投递不得修改区块或重排/新增 ticks；挖空和保护状态正常保存重载，已有 FULL 接收区块不重新运行 FEATURES。加载路径的 ChunkStep 调用与真正生成分别计数。工作线程、变化批次、过期物理空气基底和其它世界的回执必须在写入之前拒绝；没有加载邻区时返回等待，不自动加载。非空后处理列表在该 post-FULL 接收探针中明确拒绝，不能静默丢弃。整批操作完成后再捕获保存边界，保留邻接通知产生的正常 ticks。

`-PhysicalHives` 额外要求真实非零蜂巢接收与重载，所有蜜蜂实体数据、蜂巢计时、components、类型和位置逐字段保留。先比较源 ProtoChunk 的完整 BE payload 与物理 BE 的 `saveWithFullMetadata`，再检查 LevelChunk 的存储封装恰好多出原版 `keepPacked=false`；不是删除字段的 canonical 例外。每个源/物理 payload 和落盘封装均保存于回执。BE 实例数只增加接受数量，重复投递不增加实例或实体管理器数量；重载逐项核对原 payload、存储内容、高度、光照和 ticks。

独立原版诊断的成功标准是证据能区分原版实体不确定性与非实体差异：每个重复都有真实溺尸，方块及实体之前的全部 metadata 相同，而 canonical 实体字段确实不同，`fullParity` 必须明确为 `FAIL`。检查器成功仅表示不确定性诊断成立，不是 C 完整生成等价通过；没有复现、其它数据不同或程序/顺序不一致都报错留证，不自动放宽验收。

末地城独立诊断按同一标准检查三只真实潜影贝，不要求溺尸字段；只能观察到 UUID 与 `Rotation[0]` 差异。有限验收状态须为 `PASS_END_CITY_FINITE_ACCEPTANCE`，显式规则为 `26.3-native-end-city-shulker-yaw-1`；每宿主四份完整 C/B 检查点、第二宿主四次跨 JVM 比较、三会话峰值与零 context 残留。数量、位置、俯仰、NBT 类型、非法朝向范围或其它数据变化均失败。Map 键序不影响辅助 JSON 轨迹比较，键集合及逐键值仍必须完全一致。

- 完整流程每宿主 21 个自然目标：C 与完整只读 TERRAIN 的同策略控制比较完整快照、每槽返回值、真实装饰 RNG 位序列及 seed/index/step、原始外部方块与附属数据；原 B 另作差异对照。包含化石模板资源入口的实际命中与按需 B/原 B 对照。新增两个 taiga 目标必须实际进入完整 `trees_taiga` 槽，外部针叶树贡献不能全部为零；同时纳入逆序和三会话并发。仅同策略控制等价，不称九 writer Vanilla parity。
- 每次只有目标执行一次 FEATURES，无外围 FEATURES 或 LIGHT。树木 permission 以完整 placed slot 为边界，未知 selector/decorator/provider 整项切断；目标内所有自然数据仍保留。实例化 BE、实体、block/fluid ticks 与后处理均有实际正例；pending NBT 自然零值如实报告。
- 逆序与三会话并发包含同坐标不同 local seed；两个 host seed 17/23 的独立进程对比完整快照、原始贡献和随机轨迹。实际跨界方块必须为正，不能由单树强制放置充数。
- C region 七类外部写能力均拒绝，辅助区域 RNG 与 sub-tick 次序跨树木子调用共享；真实异常仍报告，失败后 registry 无绑定，关闭后的装饰能力拒绝调用。
- End City 保留原 B 与 UUID-only hash 的诊断；另由 `-AcceptedShulker` 补齐有限例外下的完整比较，不把它计入原 21 项严格 C 套件或更改快照规则。
- 56 次原版单树调用的返回值、随机消费、完整目标快照和跨界贡献与完整副本严格一致；spruce、pine、fallen_spruce_tree 各有实际跨界正例，保留白桦、橡树与蜂巢。真实另种子 B 接收基底必须实际接受针叶树方块，不能仅证明贡献编码成功。完整目标和内核扩展证据版本均为 2，原始字段不裁剪。
- contribution 关闭 session 后可独立编码，保留全部原始 NBT 字段，不扩展忽略名单。
- 来源、dimension、合同、种子与有限 radius-1 外延受检查；越界拒绝。原版边缘 shape update 对未改写方块的 tick/后处理保留原始证据，但接收模型不授予它们写入权限，单独计数。
- 未提供 receiver 基底时为 pending，不构造假空气；实际接收以 receiver 自己另一种子的真实 B 装饰完成数据为基底，只覆盖未保护的空气，接收者自己的植被、结构和 BE 也受到保护。
- 固定 key 优先级、重复幂等、冲突报错、删除、正逆序与四线程重复投递一致；持久化包括基底、去重和保护空气。
- 真实接收蜂巢与 block ticks 有正例；另报告 fluid tick/后处理接受数量，为零时不能声称自然接收覆盖。未附属方块写入的原版副作用必须实际命中并明确拒绝。
- 两个独立宿主 JVM 的 raw 贡献一致，第二个进程直接读取第一个进程的接收记录并复核；记录不是 Mosaic format V2。
- 物理加载区块数、实体、block/fluid tick scheduler 不变；session binding 为零。
- 构造的冲突/破坏数据单元测试只证明协议，不充当自然装饰、物理写入或游戏内保存重载证据。
- 完整 C 接收套件的每个已准备目标与完整 TERRAIN 的同策略控制一致；实际逆序再生成和三会话并发不能改变完整快照、真实 RNG 位序列、返回值与贡献。来源和接收方的 local seed 都由同一 Seed V1/master 推导，不用任意邻种子替代。
- 接收方自身执行完整 C；按位置选择最小来源键的独立参考与按贡献包遍历的 journal 对照。先投递后提供基底、分批提供基底、相反顺序、重复并发投递均一致；自然空气接收与实体方块拒绝都必须为正。
- 未准备基底时先写盘再重载，仍为 pending；接收/保护/删除再投递和两个 JVM 直接重读持久记录均一致。整份 C 基底的所有快照字段在重放前后不变，外来贡献不反馈进来源的 worldgen，不丢失已有 BE、实体、ticks 或结构数据。
- 固定输入接收须覆盖真实完整 C 基底、自然空气接受与非空气拒绝；逐位置分批准备、逆序与三线程并发重复投递、pending 磁盘重载、保护挖空位置和删除再投递均与逐位置独立参考相同。第二个 JVM 的生成目标数必须为零，原始实体 NBT 与快照文件字节不得改变。原版随机属性原样保存，不新增任何 canonical 例外；构造的保护与冲突仍不是实际玩家事件证明。
