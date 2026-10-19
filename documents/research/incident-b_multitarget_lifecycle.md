# 多 Target 生命周期接线故障记录

调查日期：2026-10-16。本文保留运行期探针、身份、关闭和物理就绪接线的历史记录，从里程碑的逐轮正文迁出；没有重跑、改写证据或变更验收结论。当前状态以[里程碑矩阵](../project/milestone-features.md)为准。

## 问题

如何区分模块、实际游戏、历史指纹、当前生产生命周期和性能证据，避免把某一阶段的通过扩展为整个 Target 或其它维度的通过。

## 方法

整理里程碑中已有的运行期探针、身份、关闭和物理就绪接线记录，按原有先后保留内容、数值、失败和限制；仅调整文档归属、相对链接与失效的账本维护声明。原始 result、世界、日志、SHA、签名及源码不改动，不把本次文档整理记作新实验。

## 发现

以下保留实际故障、接线修正和后续观察；工程编译失败、游戏异常、探针预算与组件通过分别解释，不合并为产品收口。

参考 Target 新增仅 probe 的 RuntimeProof 已完整编译，main 未改；当前 231 项单测、十八项工具测试和 52 份脚本语法检查全部通过。探针在普通 FULL 请求前加有界、非持久的原生 PLAYER_LOADING/PLAYER_SIMULATION 票据，要求两个真实 entity-ticking future 完成，再由自然服务器循环观察实体跨 physical seed 边界、block/fluid scheduler 和熔炉 ticker；不调用私有 callback、手改 holder level 或手推 Level.tick。运行期 fixture 明确不作为自然 worldgen 的正例，完整 FULL 与运行期最终计数分别保存，中间状态为 RUNTIME_PENDING，当前尚未游戏验收。队列在两版旧 Target 对照完成后依次运行三维度各两 JVM 的原版/B 保存重启，不能重叠重负载实例。单测等待期间线程证据定位到 G1 refine 在“一核亲和性、任务显式四核”配置下挤占资源；只将该自有单测进程恢复既有四核/BelowNormal 预算后正常结束，未改产品线程数或 GC，相关耗时不计作 worldgen 性能。

1.21.1 的新增 Handoff 现已完整运行 smoke 4/4 与 structures 18/18，分别十六与七十二个 fresh JVM/save，结构套件另 CheckOnly 复核 PASS；源码指纹 D40DB46915D806685CB1D7A60B638EE1E51A3E3864D25F0AABF8A8A05E8EC97B。全部目标原生 NBT、关闭 owner 后的重建、全部高度图、八份 stored SPAWN palette、跨 seed/坐标/维度误用拒绝、独立原生结构加载控制均通过；七个实际 start loader 调用均观察到 S(C)。结构正例总量为实体 4、BE union 2，且 instantiated/pending 各 2（同位置共存，不能相加当四个 BE）、block ticks 779、fluid ticks 1,895、后处理 17,396。末地城交接保留本进程原始 UUID/朝向，不因已有跨进程例外而放宽搬运判等。这仍不证明已注册生产 FULL 或实体/tick 激活，接下来是本版生产接线，不重复已经通过的模块 smoke。

1.20.1 因果 probe 首次编译发现本 Target 没有 MixinExtras；已只把 marker 单调用拦截改为本版原生 Mixin Redirect，仍在 getLevel 之后、EntityType.create 的单个实际调用处捕获输入，没有新增依赖或修改生产逃逸守卫。后续队列在编译未通过时按门禁停下，没有误运行游戏或运行期探针；修正后继续四核、单实例重验。该工程接线失败不计作 worldgen correctness 结论或性能收益。

已有证据与操作入口：[生成状态](../project/status-generation.md)、[验证工具](../development/testing-worldgen.md)、[性能验证](../../validations/performance-worldgen/validation.md)、[BOP 验证](../../validations/compatibility-bop/validation.md)、[末地因果调查](../research/investigation-bop_end_causality.md)、[新旧 B 性能调查](../research/investigation-b_performance.md)、[结构复用](../research/experiment-b_structure_reuse.md)、[局部密度](../research/experiment-b_density_tiles.md)、[回退输入复用](../research/experiment-b_fallback_inputs.md)。

2026-10-16 恢复开发时以文件重新核验：HEAD 为 fa0a600，暂存区为空，没有本仓库游戏进程残留，其它仓库的进程不操作。1.21.1 structures-shared-handoff 的十八项重新 CheckOnly 通过；1.20.1 四 JVM 末地城朝向双向因果及 end_city-start 单项也分别 CheckOnly 通过。完整 structures 原目录只有 village_plains-start/native_0 PRODUCED，后续世界外部终止，没有内容 FAIL；工具将原结果及未完成世界保全到 interrupted/structures/1.20.1-forge，以 INTERRUPTED 标记，再完整重跑十五项。旧原始朝向 FAIL 保持原样，不用单项 PASS 替代整套。

恢复核验时建立了十五行 Target × 维度门禁与下三步。正确性采用两个独立实例/端口、各四逻辑 CPU/BelowNormal 的队列，当前亲和性掩码分别 3840、61440；性能期间仅单队列，无正确性采样叠加。用户的新并行规则同步到项目补充规范。现存单组 26.3 基线已按实际窗口时长补算相对原版比值，不能当作本次新测量或性能收口。

RuntimeProof 首次启动发现熔炉探针参数仍为 Level，而 26.3 原生 serverTick 实际为 ServerLevel，已仅修 probe 接线。主世界 Vanilla/B 自然 tick-loop、保存重启随后实际通过。下界 B 首轮在旧单请求观察器的 isIdle 断言处失败：当时实际 in-flight=0、物化 obligation=0，但票据驱动的邻区请求尚未完成。保留 crash、日志和世界到 first_runtime_failure；只将该观察器的执行边界移至全部票据派生 FULL、两侧真实 ENTITY_TICKING 和生成会话清空之后，不删除或豁免原断言。下界 B 再跑已通过两个 fresh JVM，220 自然 tick、实体注册一次并跨 seed 边界、block/fluid 实际回调与熔炉烧炼均通过，重载 canonical/Artifact/SPAWN 为零。仍需完成最终三维度同探针复核，不能从中推断客户端或出生链路已验收。

1.21.1 开始实际身份生命周期接线，仍不注册 B codec 或宣称生产 FULL：按本版 WorldData/Overworld DimensionDataStorage 建立内存 authority，在 Overworld 插入后、load event/出生生成前验证，关闭后释放；未知、混合或缺失 profile 拒绝。B marker 使用本版 Gson strict reader，版本从模板常量生成，不硬编码其它 Target。新增 marker/维度一致性四项单测，加原有测试共十九项已通过；后续 profile replacement invalidation 接线仍待编译/游戏验证。当前该 Target 不在运行队列，1.20.1 的运行源码未改，不把新建文件算作功能完成。

参考 Target 的最终 RuntimeProof 已完成三维度 × Vanilla/B × fresh/reload，共十二个不同 PID 的实际游戏验证。每次均观察 220 个自然 tick、实体注册恰一次并跨 seam、block callback 一次、流体跨 seam、熔炉真实烧炼；生成和 SPAWN 绑定为零，最终 in-flight 为零，探针票据正常移除，保存及重载均正常。B 各维度的有界票据 frontier 初次实际生成 156 份 Artifact，不把 49 个 FULL 或两个交互 Chunk 说成只有两份生成；重载全部为零，原版两轮全部 Mosaic 生成计数为零。结果隔离于各维度 runtime-proof，测试安装的小平台/实体/熔炉不是自然 worldgen 正例，不代替自然实体/BE/tick 交接、出生、维度往返、客户端发送或性能门禁。

1.21.1 的身份接线现已实际通过新增 integration-world_identity：十九项单测、完整 probe 编译、四 JVM 原生朝向因果守卫，以及四个 B/交接 fixture 的十六个 fresh JVM，末次 CheckOnly 再次通过。指纹 9B610EF4A93A5A5046A5CA95ACB1B3DC8E0C7734FB3116983107EE16A0D3EA1E。每个实例均实际验证三维度、10,000 次内存查询、SavedData.set 后立即失效、混合身份拒绝、显式磁盘重读恢复普通世界而 WorldOptions 对象不变，真实 stop 后关闭状态拒绝继续查询。原始 fixture、writer/RNG、目标完整状态、Artifact 和 stored SPAWN palette 对照保持一致；产品仍未注册 B codec/物理生成接线，不能据此宣称 1.21.1 FULL 可用。新验证用模板允许的独立 validation/server、25591 端口与 61440 四核组，不虚构模板不支持的额外 server 角色，也不与 1.20.1 的 25590/3840 队列共享运行目录。新增探针一处多余 checked IOException catch 的编译错误已修正，首次失败编译输出不计游戏结果；没有修改生成规则。

1.20.1 完整结构重跑已结束，十五项、六十个独立 JVM/save 全部通过，随后再次 CheckOnly PASS；源码指纹 C869D06641C151A553D9D833189E2F3E83597D9AF958B75F487DE875038A93C0。实际 start/邻区、强要塞及末地城有限原生朝向因果门禁保留，未扩充例外；中断归档和旧失败不改写。这证明独立 B 模块，不证明尚未接入的物理 FULL。

1.21.1 新增生产候选现已编译通过并通过十九项单测：distinct B codec、原生 ChunkMap/holder 窄路由、脱离式 Artifact、主线程 POI、原生 LIGHT/FULL、stored SPAWN palette、结构元数据与 local-seed 重载。只有这一版本独立源码，没有把 26.3 专属密度快路径默认打开。普通 FULL 探针只加临时原生 PLAYER ticket，并异步观察原版调度；pack_format=48 从本版实际资源 version.json 核验。当前开始游戏验证，编译和旧指纹的 Handoff PASS 不替代新接线结果。

1.21.1 普通 FULL 初始 smoke 已实际完成一轮 fresh/reload，两个 PID，S(C)=-5161763991829980711，初次九份 Artifact、一次 SPAWN，重载均零，修改保留。但加强原生物理 BIOMES readiness 检查后，本版尚未证明部分阶段交接，保守完整 Artifact 需求扩大，触发探针 180 秒预算，随后关闭等待原生结构计算又触发 watchdog；未通过，不将前一轮 smoke 提升为 Target PASS。原始失败、世界、console、crash 已由工具保全到 first_runtime_failure。归档中同名 physical NBT 与 reload JSON 是前一轮 smoke 的残留，只以各自时间/PID 解释，不能用作该失败轮的产物；工具已增加 sidecar 清理，后续尝试不接受这种残留。

根因采样显示多个同种子独立 target 在 CompactStructureInputs 的原生模板管理器锁前等待，关闭线程在 awaitClosedWorkers 等待；没有据此裁剪结构或缩放测试压力。增加结构源操作之间的本世界关闭检查点，仅抛所属 lifecycle 的预期取消，不吞真实错误。新检查点编译和十九项单测通过，真实关闭仍待重验。下一步先用独立原生 BIOMES 对照证明仅准备必要阶段的精确交接，再重跑三维度 FULL/reload，不以延长超时解决重复计算。

新增 Native ChunkStatus 计划断言依赖原生 registry bootstrap，不适合普通 JUnit；首次 JUnit 环境失败已定位，断言移至真实游戏 probe，未删除断言或伪造 registry。异步请求改为本版 getChunkFuture 使用的同一原生 getChunkFutureMainThread 请求入口，省去探针线程的 managedBlock，未手工驱动任何生成阶段。工具十八项测试、五十二份 PowerShell 解析与规范检查通过，零失败零警告；根 check/verifyRelease 和完整多 Target 收口仍待执行。普通 Vanilla 首次实际游戏已正常 FULL/保存退出、Mosaic 计数为零，但编排发现缺 normalVanilla 证据字段，仅补 probe schema 后三维度 fresh/reload 重跑，不把游戏零退出码当作整组通过。

1.21.1 上述 ordinary Vanilla 三维度 fresh/reload 随后全部通过，六个独立 JVM/save：主世界 PID 55360/40936、下界 49184/67168、末地 68984/69044。真实 FULL LevelChunk、lightCorrect、测试方块保存保留、正常保存退出，以及所有 Mosaic Artifact/SPAWN 计数为零；同时在真实已 bootstrap 游戏中跑完原生物理依赖计划断言。结果位于 integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/vanilla-control。它们不是性能采样，也不替代 B 加强就绪、自然 tick 激活或出生门禁。当前没有完整 B 生产通过结论，下一项仍是精确 BIOMES 输入与生命周期回归。

## 结论

本文是本次整理时固定的研究记录，不承担当前状态维护。历史失败、中断和未解释项保持原始证据语义；后续采纳或推翻结论时，写入新的研究记录，并同步更新里程碑或设计文档，不倒改本文。
