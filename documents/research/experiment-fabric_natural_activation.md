# Fabric 自然生成数据的激活与保存对照

调查日期：2026-10-17。结论：参考 Target 的四个自然正例在八个独立新生成／重载 JVM 中通过实际激活、保存与引用清理。范围限主世界和末地这些正例，不是三维度全部副作用、出生／往返或新性能结论。

## 问题

原有区域 RuntimeProof 验证真实票据、自然 tick 和物理邻区，但测试放置的物品、熔炉与流体不能替代生成内容的激活证据。需要从真正的生产物化输入追踪自然实体、BlockEntity 和 pending NBT，避免测试主动实例化后将结果当成原生生命周期成功。

## 方法

只增加 probe 与工具，不修改生产生成算法、Seed V1、Ordering 1/2、存档或 Overlay。当前全 src 输入为 86DB7F2A2CBCC14D38655D8B48F7FFE8AA756419150C01F3555AA6103B525CC6；既有装饰前、独立 B、模块并发和生产请求保留其原始指纹，本项不重写或替代它们。

在服务器 runServer 开始时安装默认关闭的 Publication 观察器，以便捕获可能发生于开服阶段的目标物化。保存真实 ProtoChunk 的实体 UUID、BE 原始 NBT、Artifact 指纹、维度、目标、local seed 和 save 身份。pending 数量只计算尚未出现在实例化 Map 中的位置，不把重复保留的 NBT 条目当成 pending 正例。

首次 BE 安装与相同原生创建／加载分支的完整 saveWithFullMetadata 比较，不删除字段。控制对象没有 Level、manager 或 ticker，不被添加到 physical 世界。实际 LevelChunk.setBlockEntity 返回点观察安装，运行前仅检查 getBlockEntities Map，不用会主动实例化的 getBlockEntity。末影水晶在实际 EndCrystal.tick 返回点计数；实体原始 UUID、类型与唯一物理对象身份在保存／重载后保留。

每项均要求真实票据、49 个 FULL、目标与邻区 ENTITY_TICKING、220 个自然 tick，以及原有单列测试设施通过。停服时从 SerializableChunkData.copyOf 的实际返回数据保存 BE NBT；新 JVM 重载的安装与这份真实保存数据经原生加载后的完整 NBT 比较，而不是要求运行后 NBT 仍等于初生值。关闭后释放观察器、实体／BE Map、source、level 和 server 引用。

八个 JVM 串行运行，四个可见处理器、affinity 3840、BelowNormal；Gradle 两个可见处理器、一个 worker。没有性能队列重叠，没有运行客户端或 GUI。Run-FabricNatural 的当前源码 CheckOnly 核对条件、不同 PID、正例数量、原生保存、清理与重载零再生成，结果范围明确为 NATURAL_INPUT_ACTIVATION_AND_IDENTITY_NOT_RUNTIME_NBT_IMMUTABILITY。

## 发现

### 原生 pending 分支与保留失败

第一版探针错误地把所有 NBT 都交给 BlockEntity.loadStatic。硫磺模板产生的两个真实 pending 标签使用 id=DUMMY，并非注册的方块实体 ID。实际 26.3 LevelChunk.promotePendingBlockEntity 对 DUMMY 走 EntityBlock.newBlockEntity(pos,state)，普通标签才调用 loadStatic；随后才进入 setLevel 和 addAndRegisterBlockEntity。这是原版定义的占位符激活，不是忽略损坏数据的许可。

[原始失败](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof/minecraft-overworld/natural_worldgen_sulfur_template/first_runtime_failure)保留 C990 输入、两个 DUMMY 原始 NBT、日志、180 秒超时报告和世界。异常来自验证控制对象的错误加载，不据此断言生产硫磺数据损坏。服务器进入关闭后仍等待未完成区块；保存等待栈已采集，确认进程归属后终止专用 PID 116240，不把该次当作正常退出通过，也不改写为重试成功。

探针按原生两个分支修正，并把异步捕获异常在下一次探针 tick 直接暴露，避免只留下后续超时。新证据域独立运行；原先三个 C990 正例和失败均保留。本次没有在 production 增加 DUMMY 特判或异常吞并。

### 自然正例

证据位于 [runtime-proof](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof) 各维度的 native_pending_activation 前缀目录。

| 正例 | 主种子 / 维度 / ChunkPos | 新生成 PID | 重载 PID | 实体 | BE / 真正 pending |
| --- | --- | --- | --- | --- | --- |
| 硫磺模板 | 123456789 / 主世界 / (129,-42) | 78872 | 104368 | 0 | 2 / 2 |
| 末地尖塔 | 123456789 / 末地 / (2,0) | 92752 | 71648 | 1 | 0 / 0 |
| 地牢区域 | 246813580 / 主世界 / (645,-623) | 59036 | 104940 | 1 | 2 / 0 |
| 试炼密室 | 123456789 / 主世界 / (-13,-14) | 53640 | 88964 | 0 | 18 / 0 |

合计两个生成实体与二十二个 BE，两个 pending 是真实 potent_sulfur，原生生命周期完成后成为已安装实例。末影水晶在两个 JVM 中各实际 tick 220 次，没有同 tick 重复；另一实体是 chest_minecart。普通 BE 包括 chest、barrel、decorated_pot 与 trial_spawner。全部初次／重载安装按完整原生加载控制通过，没有新增 NBT 忽略字段。

八次运行均保存原始输入、运行观察与实际 native 保存数据；关闭引用清理通过。四个重载世界的 B generation、Artifact、POI、LIGHT、SPAWN、FULL 生成计数均为零，测试修改保持。fresh 的目标 Publication 恰好一次，但请求观察窗口 Artifact 差值可以为零，因为票据和开服准备已完成目标生成；不能据此声称 cold 没有生成，也不能拿世界累计 generation 与窗口差值相除推定性能。

地牢区域仍有原生 MonsterRoomFeature 的刷怪笼读取告警，实际目标保留两个箱子；不把它报成普通 spawner 激活正例。其严格切断／箱子保护分支的既有因果说明见[回退输入实验](experiment-b_fallback_inputs.md)，本次不消音或修改 B 装饰合同。试炼刷怪笼为独立的实际 BE 类型。

## 结论

补齐参考版这些自然实体、BE 与 pending 的生产输入→物理安装→自然模拟→保存→新 JVM 重载门禁。没有重新生成旧存档、固定实体随机字段或要求运行期 NBT 不变；不将控制对象或测试设施当成自然生成内容。

本项不是新内容 Oracle 或性能 A/B，也不证明下界自然副作用、全部 BE ticker、实际玩家出生／重生、维度往返或客户端收包／渲染。环搜索异步续流、其它 Target 和当前性能收口仍按账本分别执行，不从本项继承 PASS。
