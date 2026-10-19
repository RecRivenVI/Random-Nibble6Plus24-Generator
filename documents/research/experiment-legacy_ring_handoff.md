# 1.20.1 状态安全有限环的完整 B 交接对照

调查日期：2026-10-17。H120-R 第二轮完整 B 的十五项结构/普通目标对照与只读复核通过；候选仍默认关闭，装饰前完整矩阵及端到端性能另验。

## 问题

以唯一原生尾查询保留气候出口状态后，结构、单 writer 装饰、全部目标副作用与关闭 owner 后的 Artifact/SPAWN 输入交接，是否仍与本版独立原生结果一致？

## 方法

使用 `Run-LegacyB.ps1 -Target 1.20.1-forge -Suite structures -RingScope -Handoff -EvidenceLabel climate_tail`，固定输入指纹为 `29CF837192253E66666555A548B30B6AC4906DAB46337C01771C605682C5C8B3`。每项创建 native_0、native_1、host_17、host_23 四个独立 JVM/save；原生世界 seed 为 S(C)，消费者宿主 seed 与其不同。实际游戏记录 opt-in，原生物理 StructureState 没有有限 scope。

本版六类结构覆盖 Village、Mineshaft、Ocean Monument、Nether Fortress、End City、Stronghold，包含邻区 start、跨 Chunk pieces、Village 地形 halo，以及普通原点和非原点。1.20.1 没有 Trial Chambers，不虚构该结构。另以四个 fresh JVM 对照原生 Shulker 构造熵双向重放，保持已批准的有限原生随机规则，不增加字段豁免。

## 发现

[十五项、60 个独立 JVM/save](../../validations/conformance-legacy_b/result/structures-handoff-ring-scope-climate_tail/1.20.1-forge)全部完成，15 个原生 producer 为 PRODUCED，45 个 repeat/consumer 为 PASS；结束后 CheckOnly 再次通过。完整目标快照、装饰 RNG/可见群系、结构数据、越界拒绝、物理宿主不变，以及 owner 关闭后的 detached Artifact 与八邻区 SPAWN 输入均按工具原有标准比较。

[原生构造熵控制](../../validations/conformance-legacy_b/result/end-yaw-ring-scope-climate_tail/1.20.1-forge)的双向比较是完整 UUID-only 原生重放，不将它冒充 B parity，也不改变生产随机源。全部实际候选 scope 结束时没有 active supplier；未知配置的回退、父 RNG 与 continuation 的更广组件范围见[尾查询实验](experiment-legacy_ring_tail.md)。

双队列正确性耗时不能当作性能改善。实际主世界 FULL 的有限结论见[物理实验](experiment-legacy_ring_physical.md)，它没有验证玩家出生或完整自然运行期。装饰前 72 项与十五项结构六阶段套件另行绑定各自结果，不能由本套件代替。

## 结论

第二轮完整 B/交接的本版范围通过，未出现结构漏失或需要扩大验收例外的差异。仍需完成其它门禁和单实例性能测量；不提升为五 Target、BOP、默认启用或正式可游玩结论。
