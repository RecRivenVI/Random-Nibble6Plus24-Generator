# 1.20.1 生成准备关闭与真实失败边界

调查日期：2026-10-17。实际未完成 FULL 请求的正常关闭、真实异常和意外取消已分离验证；下界取消后的同存档恢复、保存和新 JVM 重载通过。主世界 FULL 的原 300 秒失败仍未解决，不提升为可游玩或性能验收。

## 问题

[首次原生请求故障](incident-legacy_native_request.md)中，主世界准备耗时超过预算，关闭后仍继续构造同种子地形。需要证明关闭能在安全边界终止工作，并保证真实生成错误不会被生命周期取消规则吞掉。独立模块的正常完成不能证明这两项。

## 方法

使用本地 Forge 47.4.28 的 MinecraftServer、ChunkMap、ChunkGeneratorStructureState 和 Util 实际字节码核对调用；运行[原生物理与生命周期验证](../../validations/integration-legacy_physical/validation.md)。游戏限四逻辑 CPU、低优先级；完整 B 对照使用第二个独立实例、世界、端口和 CPU 集合，源码在运行期间冻结。性能测量未与正确性验证重叠。

正常关闭在真实 getChunkFuture(FULL) 未完成且已有 preparation/context/in-flight 时触发；不改看门狗、不延长 20 秒关闭门槛。错误注入仅针对根目标 (125,-37)，位于实际 context 分配后的原生调用前；分别注入 IllegalStateException 和不携带 owner 关闭凭据的 CancellationException。验证实际上下文关闭、worker/in-flight/作用域归零，以及原生报告中保留的错误类型和调用链。

关闭与错误最终输入指纹为 3C21767703E9873BBAE91DF04A28A317764472358B1895634659A6FE4297A72E，五个独立 CheckOnly 均通过。下界恢复沿用成功 shutdown 的原 save，不重建 datapack/profile；随后普通请求到 FULL，再保存、重启、重载。恢复的 fresh 角色表示首次完成该目标，不是全新世界 Oracle。

## 发现

### 关闭检查必须位于原生调用之间

只在完整邻区地形前检查仍会继续执行一次地形内部的大批准备：[首轮失败](../../validations/integration-legacy_physical/result/first_failure/lifecycle_shutdown/1.20.1-forge/b/minecraft-overworld)关闭耗时 110.917 秒，36 个上下文虽最终关闭，仍不满足门槛。

检查点收敛到 context、紧凑结构来源遍历、原生环等待前后及地形阶段边界。已启动的原生 future 必须完成，不抛弃其后台子任务；FEATURES 内的上游异常处理可能消费取消，因此不在该作用域内部抛出生命周期取消，交接前仍有关闭守卫。没有修改 RNG、环搜索、结构生成、地形或装饰算法。

最终主世界 B 在 3 个真实 in-flight preparation 下关闭，用时 6.241 秒，3/3 上下文关闭；普通 Vanilla 为 2.153 秒且 Mosaic preparation 为零。下界 B 在 15 个 preparation 下关闭，用时 0.176 秒，全部 74 个已打开上下文关闭。worker、in-flight、绑定均为零；正常关闭无新增 crash report。报告分别在[主世界关闭](../../validations/integration-legacy_physical/result/lifecycle_shutdown/1.20.1-forge)和同目录的 minecraft-the_nether。

### 真实错误还需要异常停服的排空保障

真实异常可使 stopServer 在 RETURN 注入之前退出。[实际清理失败](../../validations/integration-legacy_physical/result/preserved_failure/context_cleanup/genuine/1.20.1-forge/b/minecraft-overworld)保留了未排空 worker/context，不因已有错误日志而算通过。

实际 Forge runServer 的正常及异常终止分支均发布 ServerStoppedEvent。在该事件先关闭并等待活跃 owner worker，再失效服务器身份；正常 stopServer RETURN 仍保留排空和真实错误重抛。该保障不清除已保留异常，不把普通 CancellationException 当作预期取消，不给 PoiManager 加锁，也不替换原生调度器。

### FULL 聚合不是失败阶段的及时观察点

本版 ChunkMap.getChunkRangeFuture 调用 Util.sequence；后者实际使用 CompletableFuture.allOf，并非 sequenceFailFast。一个生成阶段已异常时，顶层 FULL 仍可能等待其它依赖。[原始观察失败](../../validations/integration-legacy_physical/result/preserved_failure/root_future_wait/genuine/1.20.1-forge/b/minecraft-overworld)保留，不用延长预算通过。

最终探针记录并消费实际异常完成的生成阶段 future，让原生 server tick 报告它；不自行构造替代错误。真实异常及意外取消两例的 fullFutureDoneAtFailure 均为 false，证明观察边界的区别。工具拒绝观察超时、缺少原生报告或带 failure 的结果，不能仅凭 status 字段判通过。

真实异常 JVM 37160 与意外取消 JVM 91724 各产生一份包含注入原因的原生报告，retainedFailureIsExpected 均为 false；上下文分别 5/5、4/4 关闭，worker/in-flight/绑定均为零。证据在[真实异常](../../validations/integration-legacy_physical/result/lifecycle_genuine/1.20.1-forge)与[意外取消](../../validations/integration-legacy_physical/result/lifecycle_cancellation/1.20.1-forge)。这是错误传播反例通过，不是无崩溃游戏验收。

### 同存档恢复与完整 B 对照

当前指纹的下界恢复使用四个独立 JVM：Vanilla 首次完成/重载 92536/103936，B 首次完成/重载 108096/112600。[恢复证据](../../validations/integration-legacy_physical/result/recovery_shutdown/1.20.1-forge)及源码绑定复核通过。B 首次完成准备 49 个 Artifact/物化/POI，根目标只生成一次；全部 49 个 owner 的 50,176 个 quart cell 严格一致。重载 canonical/Artifact/物化/POI/SPAWN 为零，标记、群系、结构原始 NBT 和光照标志保持。普通 Vanilla 没有进入 Mosaic 生成路径。

关闭生产修复在输入 8CB652F36CCF6FF36516D7ABC85CCDE555CFDF1C3F3324982526CE6C15A37962 下还通过[四类完整 B/Artifact 独立对照](../../validations/conformance-legacy_b/result/smoke-handoff-close_boundaries/1.20.1-forge)：16 个独立 JVM/save，4 PRODUCED、12 PASS；[潜影贝原生随机双向重现](../../validations/conformance-legacy_b/result/end-yaw-close_boundaries/1.20.1-forge)亦通过。之后只调整生命周期 probe 的错误观察点，没有再修改该 Target 产品源码或合同组件。这不把 smoke 扩张为当前完整结构矩阵、自然 ticking 或客户端验收。

## 结论

1.20.1 的原生准备关闭与真实错误分离已获得运行证据；取消未完成任务后，下界同存档可恢复并正常保存重载。冻结生成合同、主线程物化/POI、原生 LIGHT/SPAWN/FULL 和存档权威不变，失败证据未改写。

主世界仍受原生完整环搜索成本阻塞，没有新探索吞吐、send-ready p50/p95 或相对 Vanilla 的有效比值。此处关闭秒数和单 FULL 正确性计时不是性能优化收益。有限环候选仍需本版独立证明；自然 ticking、出生/往返、并发和世界功能仍由[当前矩阵](../project/milestone-features.md)列为待验。
