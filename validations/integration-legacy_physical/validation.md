# 1.20.1 原生物理 Chunk 交接

## 目标

通过实际 Forge 服务端的普通 FULL 请求验证 B 的 Artifact、主线程物化/POI、原生 LIGHT/SPAWN/FULL、保存与新 JVM 重载。普通 Vanilla 使用相同请求作零侵入控制。可单独启用 RuntimeProof，覆盖原生区域票据、真实 FULL/ticking promotion、自然模拟与重载；不代替玩家出生、维度往返、完整并发或性能收口。

另外验证真实未完成请求的原生停服与作用域排空；注入真实生成异常及非生命周期 CancellationException，要求继续生成原生 crash report，不被正常关闭取消规则吞掉。

## 运行

先由使用者在 local.toml 明确接受 EULA。每次只运行一个四逻辑 CPU、低优先级游戏；不读写日常实例。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension overworld -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension overworld -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension overworld -RingScope -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -RuntimeProof -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -RuntimeProof -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -FailureProof -EvidenceLabel section_probe -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -FailureProof -EvidenceLabel section_probe -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -PartialPreLight -EvidenceLabel section_causal -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -PartialPreLight -EvidenceLabel section_causal -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -RequestProof -Requests 1 -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -RequestProof -Requests 32 -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Check-ForgeRequests.ps1 -Dimension the_nether -Requests 32
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -TargetOrder forward -EvidenceLabel held_full -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -TargetOrder reverse -EvidenceLabel held_full -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -TargetOrder parallel -EvidenceLabel held_full -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Check-ForgeTargetOrder.ps1 -Dimension the_nether -EvidenceLabel held_full
```

下界和末地用 Dimension 选择 the_nether 与 the_end。工具建立独立隐藏 B 与普通 Vanilla fresh world，每侧停服后以新 JVM 重载同一 save；不手工调用 generation 阶段。

RuntimeProof 单独使用 runtime_current（或显式 RingScope 的 runtime_ring_scope）结果与世界，不覆盖普通 FULL 或首次失败。它使用本版 PLAYER 区域票据同时更新 loading 与 TickingTracker，等待真实 7×7 FULL 和相邻两个 ENTITY_TICKING future，检查本版动态生成依赖与实际 biome 输入；再让真实服务器 tick 运行 220 次，观察实体跨界、block/fluid tick、流体跨界及熔炉 ticker。测试设施明确不算 canonical generation 正例，结束后恢复并移除票据。完整请求及模拟不延长原有 300 秒上限；fresh 与新 JVM reload 都必须通过，已有 FULL 不重新生成 Artifact/SPAWN。

FailureProof 单独使用 observation_failure 结果与世界，在实际请求后注入探针观察失败。它只验证原始错误与停服次生错误分开保留，不能代替 FULL 成功或真实生成异常隔离；原始 summary 的 FAIL 不改写为 PASS，不覆盖既有负例。

PartialPreLight 单独使用 partial_pre_light，按本版 ChunkLevel 反推票据强度，只请求 INITIALIZE_LIGHT，保存真实 ProtoChunk；不用 FULL 票据或手工降级状态制造半成品。每侧 fresh、resume、reload 各用独立 JVM：从原生磁盘读取确认半成品，再普通请求 FULL，最后重启验证 FULL 重载。下界/末地分别执行，不把本项当作出生、自然碰撞或性能证明。

EvidenceLabel 只用于区分探针条件；section_causal 按磁盘读回的真实 section 身份记录写入，覆盖 WorldGenRegion 与矿脉 BulkSectionAccess 的直接写入，保留实际原生邻区 FEATURES 首尾状态链，不覆盖观测不完整的原始失败。普通 Vanilla 在推进 FULL 时可以继续接收邻区装饰，不能假定其 FEATURES 后永远不变；Mosaic B 不采用该规则。

RequestProof 分别使用 requests_1 与 requests_32 独立控制，沿本版公开的非服务端线程请求入口连续发出未等待完成的请求，不建生成线程池或手工调用阶段。下界/末地分别执行。fresh 必须在首个完成前发完全部请求且达到对应 pending 数；reload 只检查身份与次数，不宣称冷生成并发。每组各四个独立 JVM，完成单请求与重复请求后运行 Check-ForgeRequests 比较；不替代不同目标并行或目标执行顺序独立。

TargetOrder 使用中心与四个正交相邻区块，共五个不同 S(C) 的 physical owner；分别在 targets_forward、targets_reverse、targets_parallel 独立世界运行。顺序模式只在前一个实际 FULL 完成后发下一个请求，parallel 模式让五个公开请求共同等待。票据随各自请求创建，不在 reverse 开始前预先提升中心；最后正常释放，不修改 Native scheduler 或内部 generation stage。每个模式普通 Vanilla/B fresh/reload 各四个独立 JVM；三模式完成后运行 Check-ForgeTargetOrder，下界/末地分开。

held_full 条件校验五个已持票 FULL 的实际 stored biome palette，各自对照独立 S(N) Artifact，重载仍校验这五个 owner。不会要求单目标生成闭包中的临时 dependency 在多个请求全部结束后仍可用，也不为探针额外扩张票据。原始 targets_reverse 的依赖观察失败保持不变；EvidenceLabel 隔离不同探针条件，不重标历史源码或结果。

RingScope 为默认关闭的有限环候选，单独使用 ring_scope 结果与世界，不覆盖未优化路径或原始失败。物理 Vanilla state 不绑定 scope；B 首次生成须有真实 ring context/slot 计数，关闭时 supplier 已排空。它不能与 RecoverShutdown 混用未优化关闭存档；不把正确性探针计时当作正式探索吞吐。

关闭与错误隔离分别在独立世界和固定结果路径运行，不覆盖 FULL 证据；先串行运行下列三项，再将 Offline 换为 CheckOnly 复核。它们共用本验证实例和端口，不能与 FULL 验证同时运行。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgeLifecycle.ps1 -Kind shutdown -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgeLifecycle.ps1 -Kind genuine -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgeLifecycle.ps1 -Kind cancellation -Offline
```

保留一个下界未完成世界，再通过同一存档的普通 FULL 请求恢复、保存、新 JVM 重载；结果单独写入 recovery_shutdown，不覆盖 fresh control。主世界原预算失败未闭合时，不用它执行恢复 FULL 的长负载。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgeLifecycle.ps1 -Kind shutdown -Dimension the_nether -KeepWorld -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -RecoverShutdown -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension the_nether -RecoverShutdown -CheckOnly
```

## 通过标准

实际 Native getChunkFuture(FULL) 返回光照正确的 LevelChunk，主世界 master/local seed 不同。新 B 的真实生成、物化与 POI 数量匹配，原生 LIGHT/SPAWN/FULL 实际执行；不能把未物化的 BIOMES 或默认 palette 当作就绪数据。关闭后 worker、in-flight 与临时作用域清空。

保存与重启后标记方块、完整 structure NBT 保持；reload 的 canonical/Artifact/SPAWN 为零。普通 Vanilla 的 Mosaic 生成与物化计数为零。四个独立 JVM、源码指纹、维度和 save 角色都匹配，无新的未解释异常或 crash report。

RuntimeProof 额外要求 49 个真实 FULL、两个真实 BLOCK_TICKING/ENTITY_TICKING future、220 次自然 tick 与正向实体/流体跨界、熔炉烧炼；回调均在原生 server thread，设施期间没有新增 canonical/lifecycle 工作。fresh 与 reload 的 49 个 stored biome owner 严格比较，runtime 票据和 generation scope 在退出时清除。测试设施不算世界生成正例，不能由本项推定玩家出生或 packet 发送已通过。

FailureProof 要求 Vanilla/B 两个独立 fresh JVM 的原始状态均为 FAIL，failure 精确保留注入的观察异常，stopFailure 单列未完成请求的停服异常；停服后 in-flight、worker 与作用域绑定为零，无新增 crash report。CheckOnly 只能输出预期探针失败已保留，不提升为世界生成验收。

PartialPreLight 要求六个独立 JVM、两个独立 save，fresh 没有 LIGHT/SPAWN/FULL；resume 从真实 INITIALIZE_LIGHT 磁盘状态推进 FULL，目标 canonical/Artifact 重生成为零，SPAWN 一次；最终 FULL 重载不再生成或 SPAWN。B 的目标标记、全部方块/群系单元和结构 NBT 保持，冷载同种子八邻区 stored-biome 输入与 fresh 携带快照逐项一致，关闭无任务残留。Vanilla resume 的每处方块变化必须被实际原生邻区 FEATURES 写入的完整状态链解释，无写入的单元、群系与结构仍严格一致；不修改 native 方法调用、参数、返回值或执行顺序。比较解码后的实际单元，不把 palette 编码次序当作方块差异。FULL 重载对各自 resume 保存的状态严格比较，原始半成品快照不覆盖。resume 可以生成原生 FULL 新需要的其它物理依赖，不能把邻区工作混记为目标重复生成。其余 FULL 重载仍遵守原有零生成要求。

RequestProof 要求每次请求成功、全部返回同一个实际 Chunk，结束 pending 为零；fresh 的全部请求实际重叠，B 目标只生成一次。独立单请求/重复请求控制的 canonical/物化/POI/LIGHT/SPAWN/FULL 与碰撞计数完全相同，Artifact 的持久化 payload、stored-biome 和结构证据相同，保存修改保持且重载不重生成。本代表目标不提供实体/BlockEntity 正例，不据此提升其独立 sidecar 或运行期并发覆盖。只有同目标 pending 去重通过，不据此宣称多目标并行或不同请求顺序均通过。

TargetOrder 要求五个真实 lit FULL、不同 local seed、请求/完成轨迹与所选模式匹配；fresh parallel 的 pending 峰值为五且首次完成前全部发出。所有实际 canonical owner 不重复生成；B 三模式各目标 Artifact payload、pending/实例化 sidecar 与物理 projection 严格相同；保存修改和 native NBT 重载保持，canonical/Artifact/SPAWN 不重执行，关闭无绑定或任务残留。Vanilla 保持原生路径，不要求其自然多 writer 结果跨请求顺序一致；B 不采用该豁免。该门禁不代替 BLOCK_TICKING、ENTITY_TICKING、玩家进入或性能测量。

shutdown 必须在真实 FULL 请求尚未完成时触发，B 已有实际 preparation/context/in-flight；20 秒内原生关闭，worker、in-flight、context 与绑定均清空，只有 owner 生命周期取消被正常归类，不产生 crash report。genuine 与 cancellation 反例必须保留精确注入类型，未归类为预期取消，产生包含真实原因的原生报告；其它关闭任务正常取消不构成豁免。探针消费实际失败的生成阶段 future，不等待原生 Util.sequence 尚未完成的 FULL 聚合，也不自行构造替代错误；观察超时不可改写为通过。错误反例不计为可游玩世界 PASS。

恢复必须与当前源码的成功 shutdown 使用同一 save，不重建世界或改写 profile，停止前 canonical/FULL 均未完成。新请求可完成真实 FULL；随后再次保存、重启时 canonical/Artifact/SPAWN 为零，测试修改和原始结构/群系数据保持。超出关闭门槛只记录失败并结束核实属于本编排器的 JVM，不提高预算或跳过原生保存。

探针只跳过启动出生准备以保留冷区，不以此证明第一次玩家进入。1.20.1 的公开 getChunkFuture 在服务端线程会 managed-block，因此从调用线程提交该原生接口，再由服务端 tick 观察结果；不手工驱动内部阶段，不关闭看门狗，观察预算固定为 300 秒。票据有明确持有/释放边界，不使用永久 forceload；结果固定在本验证 result 中。验证结束再核对源码指纹，运行中变更输入不计通过。
