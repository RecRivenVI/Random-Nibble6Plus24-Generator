# 1.20.1 有限环候选的首次主世界 FULL

调查日期：2026-10-17。状态安全的有限环候选首次在原 300 秒预算内完成主世界 FULL，并通过同存档保存/重载。候选仍默认关闭，本记录不是性能保留或 Target 收口。

## 问题

组件与独立 B 通过后，候选能否参与真实 physical 调度，保留 BIOMES、物化、光照、SPAWN、原生 LevelChunk 转换与保存重载，而不是只得到一份模块快照？

## 方法

使用 `Run-ForgePhysical.ps1 -Dimension overworld -RingScope`，原生控制与 B 分别建立 fresh world，并各自重新启动 JVM 重载。只调用普通 `getChunkFuture(FULL)`，目标为 `(125,-37)`，Mosaic master seed 为 123456789、local seed 为 -5161763991829980711；不手工驱动阶段，不延长 300 秒预算。显式 opt-in 在实际游戏内记录，普通物理 StructureState 始终没有有限 scope。

两个正确性队列各四逻辑 CPU、低优先级、独立实例和端口；因此此次 elapsed 只记录正确性工作负载，不作为正式单实例配对、send-ready 或玩家出生性能。原始[四 JVM/save 证据](../../validations/integration-legacy_physical/result/ring_scope/1.20.1-forge)绑定输入指纹 `29CF837192253E66666555A548B30B6AC4906DAB46337C01771C605682C5C8B3`，完成后另做只读 CheckOnly。

## 发现

B fresh 真实完成 49 次 canonical/Artifact、49 次 publication/POI，目标自身 LIGHT/SPAWN/FULL 各一次；75264 个实际 stored-biome 检查通过。根目标 generation 一次，保存后的标记保持，原始结构与持久化数据通过工具检查。fresh 用时约 227.737 秒，重载约 0.775 秒；重载 generation、Artifact、SPAWN 和环准备均为零。正常关闭后 worker、in-flight 与 binding 均清空，没有新增崩溃报告。普通 Vanilla 控制零次进入 Mosaic 生成和有限环。

本次共关闭 3969 个本地结构上下文、处理 508032 个原生环 slot，只执行 1485 次完整群系搜索。当前保守 B 每个 physical Artifact 为九份完整地形各准备一份目标和八份 stored-biome 输入，因此 49×9×9=3969；大量重复结构准备仍然存在。有限环修正没有删除这些结构、改 Jigsaw 或近似地形。

请求期间的只读线程快照保存为 `build/research/legacy-ring-physical/threads-before-full.txt`：八个 worker 等待同一个 StructureTemplateManager monitor，持锁线程位于 Jigsaw 高度查询，进入原生 NoiseChunk 的包装 map 与递归密度节点 hashCode；Server thread 在正常事件循环等待，快照没有环搜索栈。单张快照不证明 CPU 百分比、整段锁等待占比或死锁，后续请求实际完成也不能倒推“从未等待”。未在本项中修改锁或地形算法。

## 结论

主世界 FULL/保存重载的当前显式候选范围通过，原未启用候选的预算失败保留、不倒填。首次 FULL 仍很慢；搜索数量下降不是正式端到端性能 PASS。还需当前源码完整矩阵、结构套件、自然运行期/并发/出生和单实例 Vanilla/B 性能对照，不能由此宣称 1.20.1 已可正常游玩。
