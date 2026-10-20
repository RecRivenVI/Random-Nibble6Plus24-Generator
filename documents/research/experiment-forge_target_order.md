# 1.20.1 五物理目标请求顺序

调查日期：2026-10-17。默认 B 在下界与末地的五个相邻、不同本地种子目标，正序、逆序和共同等待请求均通过实际 FULL、保存和新 JVM 重载。二十四个独立游戏进程、十二份独立存档全部通过当前源码复核；不代表玩家出生、自然模拟并发或性能收口。

## 问题

同一目标的三十二次 pending 请求只证明去重，不能证明不同 physical owner 的请求交错没有污染目标数据。还需区分最终结果独立与生成工作量独立，不能仅凭五个请求最终完成就宣称二者均成立。

## 方法

使用[物理交接验证](../../validations/integration-legacy_physical/validation.md) TargetOrder 和 held_full 条件。master seed 为 123456789，中心为 (125,-37)，另外四个目标分别为 (126,-37)、(124,-37)、(125,-36)、(125,-38)。每个维度内五个 S(N) 均不同。

正序采用上述排列，逆序反转。顺序模式只在前一个实际 future 完成后提交下一次公开 getChunkFuture(FULL)；并发模式在首次完成前提交全部五次请求，要求 pending 峰值为五。票据随各自请求建立，按原版 ChunkLevel 推导 FULL 强度，不在逆序开始前预先提升中心，不手工驱动生成阶段或替换调度器。回调只更新原子计数和线程安全完成队列；请求集合与结果检查由原生服务端 tick 访问。

每种顺序各用普通 Vanilla/B fresh 与 reload 四个独立 JVM；每个维度共十二个。实际请求必须返回光照正确的 LevelChunk，五个 owner 的全部 stored biome 单元分别与其 S(N) Artifact 比较。保存标记与物理 NBT 后正常关闭，以新 JVM 重载原 save。共同等待不等于直接观测到五个生成 session 同时执行。

当前源码指纹为 C6E67039003CA18B3F2AEAA54BB91B01049A6E8F21BD6CA03799B6549EFD765F。只有本版 probe 与验证工具变更，没有改变产品生成算法。正确性与独立四项六阶段队列最多各一个游戏 JVM、四逻辑 CPU、低优先级，使用独立目录和端口；没有并行进行性能测量。

## 发现

### 观察范围纠正与原始失败

08C83DD95BF09C3F1909FC875AA77DF003D4D2226E3B8C9B0AC0C09780BBEF66 输入的原始逆序失败（本机证据：`../../validations/integration-legacy_physical/result/targets_reverse/1.20.1-forge/b/minecraft-the_nether/fresh/summary.json`）保留 FAIL。进程 85108 已完成五个实际 FULL、五次 SPAWN 和 LIGHT，随后 NativePhysicalProbe.verifyPhysicalBiomes 报 Unready native BIOMES input [123, -35]；停止后任务、worker 与绑定均为零。

探针在所有目标完成后仍按中心的单目标 generation plan 读取临时 dependency future。原版 getFutureIfPresentUnchecked 只返回该 status 的实际 future 或 UNLOADED_CHUNK_FUTURE；getNow 又将尚未完成者映射成同一默认值。这条异常本身不能区分 future 缺失、未完成或已不可用，更不能据此认定生成区块被错误卸载。它发生在所有五个实际 FULL 已通过检查之后，不是生成函数异常。

held_full 改为逐一校验自己持票、成功返回的五个真实 FULL 引用；fresh 对其 Artifact stored palette，reload 对保存的同五个 palette。没有额外加载临时依赖，没有扩大票据、生成区域或异常忽略范围。原始失败、首次失败归档和存档保留，不覆盖或重标；新条件使用独立世界与输出。

### 实际结果与工作量

三组完整证据分别为 forward（本机证据：`../../validations/integration-legacy_physical/result/targets_forward_held_full/1.20.1-forge`）、reverse（本机证据：`../../validations/integration-legacy_physical/result/targets_reverse_held_full/1.20.1-forge`） 和 parallel（本机证据：`../../validations/integration-legacy_physical/result/targets_parallel_held_full/1.20.1-forge`）。两个维度都通过当前源码 CheckOnly 与 Check-ForgeTargetOrder。

| B 维度 | 模式 | fresh canonical / 物化 / POI | LIGHT / SPAWN / FULL | pending 峰值 | stored biome 单元 |
| --- | --- | --- | --- | --- | --- |
| 下界 | forward | 49 / 49 / 49 | 5 / 5 / 5 | 1 | 5120 |
| 下界 | reverse | 49 / 49 / 49 | 5 / 5 / 5 | 1 | 5120 |
| 下界 | parallel | 77 / 77 / 77 | 5 / 5 / 5 | 5 | 5120 |
| 末地 | forward | 49 / 49 / 49 | 5 / 5 / 5 | 1 | 5120 |
| 末地 | reverse | 49 / 49 / 49 | 5 / 5 / 5 | 1 | 5120 |
| 末地 | parallel | 77 / 77 / 77 | 5 / 5 / 5 | 5 | 5120 |

每个实际 canonical owner 都由观察器拒绝重复发布；不存在同一坐标重复 canonical 的通过豁免。顺序模式的请求/完成顺序完全一致，并发组所有请求在首次完成前发出，结束 pending 为零。并发的独立依赖总量比顺序多二十八个，不是重复计算同一个 owner。MosaicPhysicalLevelState 的未完成 plan 合并义务范围，与观察到的额外依赖相符；不据此认为请求工作量与顺序无关。

每个维度将 reverse、parallel 各五个目标的 artifact.nbt、pending.nbt、instantiated.nbt、physical.nbt 与 forward 精确比较，共四十次文件比较，两维度共八十次，全部相同。物理投影包含实际 BlockStates、Biomes、Heightmaps、structures、BlockEntity、block/fluid ticks 与 PostProcessing；光照正确性单独检查，不以光照数组编码作为跨顺序哈希。各组重载对自己保存的原生 NBT 严格比较。

全部 B reload 的 canonical、publication、POI 和 SPAWN 为零，五个修改标记和结构数据保持，五个实际 palette 共 5120 单元再次一致。LIGHT/FULL 原生加载观察各五次，不记成重新生成。所有关闭后的 in-flight、worker、binding 为零；普通 Vanilla 的 Mosaic 计数为零，无新增 crash report。成功试验存档由工具移入回收站，证据保留。

### 范围与其它回归

本代表组的 Artifact 实体与 BlockEntity 数据为空，不提供其正例并发覆盖；没有扩大任何 canonical 随机字段例外。文件一致不能提升为实体或 BlockEntity 已发生且只激活一次。票据仅保证 FULL，不把本项提升为 BLOCK_TICKING、ENTITY_TICKING、玩家入口或包发送验收。普通 Vanilla 的自然多 writer 结果不要求跨请求顺序相同；B 没有使用该豁免。

当前输入的四项独立六阶段回归（本机证据：`../../validations/conformance-legacy_terrain/result/legacy_terrain_smoke_held_targets_probe/1.20.1-forge`）与当前源码 CheckOnly 通过，覆盖主世界原点、非原点、下界、末地。不倒填其它指纹的完整 72 项或结构套件。

## 结论

两个维度在这五个不同种子 owner 上，正序、逆序与共同等待具有相同目标数据，保存修改和新 JVM 重载保持。多请求会改变实际准备的独立依赖数量；本项不是性能优化，也没有新吞吐或相对 Vanilla 延迟比值。

主世界原 300 秒失败、出生/往返、实体与 BlockEntity 正例并发、更多布局和运行期并发仍待验。H120-R/T 的性能止损保持，不追加第三轮优化或延长原失败窗口；不改变 Seed V1、Ordering1/2、format V2、既有存档或 Overlay 语义。
