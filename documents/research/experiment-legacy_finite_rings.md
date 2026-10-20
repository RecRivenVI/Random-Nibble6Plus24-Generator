# 1.21.1 有限要塞环搜索筛选

调查日期：2026-10-16。H121-R 首次候选消除了主世界 FULL 超过 180 秒的这次准备等待，fresh 117.886 秒、reload 0.241 秒；要塞环 CPU 样本接近消失。尚未完成完整矩阵、三组交错探索 A/B 与 Vanilla 比值，候选默认关闭，不是正式保留或 Target 收口。

## 问题

原生请求只有二十五份完整 Artifact 与二十四份 BIOMES 实际数据义务，但 1.21.1 的普通主世界 FULL 仍超原预算。需要区分环搜索、重复结构 source、模板锁与子任务等待，不能仅凭 join 栈判断死锁，也不能延长超时。

## 方法

单个高负载 JVM、四逻辑 CPU、BelowNormal；物理目标为主世界 (125,-37)，master 123456789，local -5161763991829980711。先保持未优化路径，用 JFR 与线程快照定位，再仅适配原生环搜索的有限视图。无其它高负载实例与性能筛选重叠。

核对本地 NeoForge 21.1.262 的实际 ChunkGeneratorStructureState 字节码：原生 Math.sin/cos、round、每次 random.fork 及环槽顺序不变；在 supplyAsync 调用前读取局部槽 17/18 的候选坐标。原生群系搜索半径为 112 方块，移动至多七个区块，查询保留动态派生且不小于十一的保守范围。命中后的结构生成、references、Beardifier、地形和 B writer 不变。

正确性队列各四 CPU、低优先级，使用不同实例、存档与端口。四 seed 的完整原生环作为同 JVM 组件控制，另跑独立 native JVM/save 与无关宿主消费者的六阶段地形、结构输入和完整 B/脱离式交接。组件对照不是独立物理 Oracle。

## 发现

- 未优化物理请求仍在 180 秒失败；该轮七个 Artifact、十四个 BIOMES 发布，二十八个在途，尚无 POI/LIGHT/SPAWN/FULL。原失败与新 crash report 保留，Gradle 退出零不算验证通过。
- 100.075 秒 JFR 的 6,229 个 CPU 样本中，要塞环 4,497（72.2%）、其它结构 806（12.9%）、地形/群系 595（9.6%）；GC 暂停 367.9 ms。模板锁阻塞合计 139.834 秒是多个线程的累加，不能视作墙钟百分比。
- 首次候选输入指纹为 7BA70184B3F8FB59FD7DFC16A4C877FFC7884D614DDE47565B57A1AC36CAB8EB。组件对照四个 seed、128 个原生位置及三个查询偏移，共 1,536 个正例，零漏检；候选实际完成 196,608 个原生槽位计算，仅执行 1,536 次昂贵群系搜索。组件控制实际准备四份完整原生环，即 512 次搜索；196,608 不是实际执行过的旧路径搜索计时，不能冒充同负载性能 A/B。
- smoke 四项（本机证据：`../../validations/conformance-prefeatures/result/ring_scope_smoke/1.21.1-neoforge`）与结构十八项（本机证据：`../../validations/conformance-prefeatures/result/ring_scope_structures/1.21.1-neoforge`）独立原生/消费者均通过，另经 CheckOnly。覆盖七类结构、start/邻区、Village/Trial Chambers 地形适配边缘、原点与原 blocker；六阶段 Chunk NBT、stored palettes、密度 raw bits、完整引用结构均严格比较。真实 Stronghold start 的消费者保留 128 槽、搜索一次，start 与邻区没有漏失。
- 完整 B/交接 smoke（本机证据：`../../validations/integration-world_identity/result/smoke-shared-handoff-ring-scope/1.21.1-neoforge`）四项、十六个 fresh JVM/save 全部产生预期结果，工具复核通过。工具的聚合复核最初漏传 RingScope 因而选到旧因果目录，随后又错误要求 lazy 普通目标必须搜索大于零；修正的是实验分流和对应计数门禁，没有放宽状态/实体内容比较。旧 eager 门禁仍要求实际环准备和搜索；候选允许零搜索但不允许负数或超过实际槽数。
- 四进程原生实体双向因果控制在单独目录通过；新增目录首次误写旧 native_a 的问题与不可恢复原 raw 的边界见[目录参数故障](incident-causal_output_variant.md)，不冒用旧聚合说明。有限随机例外没有扩大。
- 物理筛选（本机证据：`../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/physical-handoff/minecraft-overworld/lazy-rings`）fresh PID 90948：FULL 在 117,886.1541 ms 完成，二十五个 Artifact 与二十四个 BIOMES 各准备/发布一次，POI 九、LIGHT/SPAWN/FULL 各一、in-flight 零。原生状态为 FULL，lightCorrect 为真。reload PID 23292：241.2205 ms，Artifact/B generations/SPAWN 均零，测试修改保留，按 loading pyramid 检查九个群系义务。
- 候选 JFR 为 120.143 秒、5,616 CPU 样本：要塞环 21（0.37%）、其它结构 3,225（57.4%）、地形/群系 1,862（33.2%），GC 暂停 1,585 ms。模板锁累计线程阻塞 1,130.241 秒；说明其余结构与锁暴露出来，不能据此绕过锁或修改 Jigsaw。本轮没有优化这些部分。
- 上述是一个冷 FULL/save/reload 筛选，不是 8/8 持续探索、send-ready p50/p95、原版相对比值或三组配对收益。新旧 JFR 覆盖阶段与完成工作量不同，不据百分比推导等比例绝对 CPU 或内存改善。
- 首次候选后补强能力门：未知模组、BiomeSource、placement 或环 exclusion 不安装有限 scope，连其它模组的原调用链都保持原生；属性名从产品常量取值。该守卫补强形成新指纹，首轮游戏证据只证明上面的 7BA 指纹；新守卫仍需受影响门禁，不倒填首次运行。

## 结论

H121-R 有因果进展：主世界真实 FULL 从原预算内失败变成成功，要塞环热点基本消失，已有结构与 B smoke 未发现输出差异。但它仍默认关闭，未达到正式保留门槛。

下一步验证守卫补强后的独立 72 项及结构/部分 BIOMES/完整 B，随后才做同资源原版与 eager/candidate 的交错探索对照、报告吞吐与 send-ready 比值。最多两次候选迭代与原有止损不变；不得把本轮较慢但成功的 FULL 当成可游玩性能或完整生命周期通过。
