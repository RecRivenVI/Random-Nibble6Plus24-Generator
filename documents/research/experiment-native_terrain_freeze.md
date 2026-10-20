# 两端精确部分地形冻结对照

调查日期：2026-10-20。26.3 Fabric 与 NeoForge 的精确部分 TERRAIN、私有装饰副本组合，均已完成七十二项装饰前、十八项完整结构和十九项独立磁盘 B 对照；以下证明内容等价，不代替生产调度及性能验收。

## 问题

物理依赖只请求 BIOMES／TERRAIN 时，能否不提前执行完整 B，同时把已有地形安全地交给后续目标装饰，不改变 stored biome、结构、地形适配、随机流或目标内副作用？

## 方法

两端分别使用本版本原生入口生成部分地形，和独立原版进程比较。完整 B 的每个场景使用两份独立原版世界、两个无关宿主；宿主先将部分输入写入压缩 NBT，再经原生加载器读回后复制为私有工作状态。装饰不能修改源地形。

完整内容矩阵只在冻结组合上运行一次。后续探针或生命周期改动按实际影响复验，不因无关源码指纹变化重跑整套内容矩阵。只读 CheckOnly 使用对应证据原始输入身份，不伪称为后来修改的全部源码身份。

## 发现

| Target | 装饰前 | 完整结构 | 独立磁盘 B | B 独立进程／存档 |
| --- | --- | --- | --- | --- |
| 26.3-fabric | 72/72 PASS | 18/18 PASS | 19/19 PASS | 76 |
| 26.3-neoforge | 72/72 PASS | 18/18 PASS | 19/19 PASS | 76 |

装饰前覆盖结构 starts／references、BIOMES 和完整 TERRAIN 的内部检查点、原生 NBT 编解码、关闭后读取及绑定清理。完整高度 Beardifier 按实际结构检查 98,304 或 65,536 个 float 原始位，不接受近似高度或密度。

十九项 B 覆盖原点、非原点、三维度、Village、Mineshaft、Trial Chambers、Monument、Fortress、End City、Stronghold、邻区结构、树、矿物及精确回退。每端目标内副作用正例合计为实体 6、BlockEntity 8、pending BlockEntity 2、方块 tick 1,280、流体 tick 292、后处理 1,856；比较完整快照、装饰 RNG 和源地形不变性，保留既定有限原版随机验收规则。

装饰前入口为 `validations/conformance-prefeatures/result/native_terrain_matrix_goal_integrated_native_matrix/<target>` 和 `native_terrain_structures_goal_integrated_native_structures/<target>`。Fabric inputHash 为 `6E1A8C867D21E5752BCD46317EDE0E4E178CDB77473D810DC708C2A8B67A985B`，NeoForge 为 `732234353FE26666A9EC9A765481D02733516C4735A0D114381CEA5221A9C28A`。

B 入口为 `validations/conformance-feature_b/result/goal_integrated_retained_terrain/independent/<target>`。Fabric inputHash 为 `C39AC9D2D53950201294B1E74A874455EC7C2F5F821E765BB3641E069595BB2E`，NeoForge 与上述装饰前一致。两端十九项、七十六份结果均已只读复核。

NeoForge 旧启动器引用旧 Loader 的 export 失败和重复启动配置的工具失败保留原样；新标签显式采用当前启动器，不改写旧结果，也不计作通过的游戏运行。

## 结论

当前精确部分输入与私有副本的内容门禁通过，没有以对象数量减少代替内容比较。性能保留依据分别见[物理交接实验](experiment-physical_terrain_handoff.md)和[NeoForge 组合实验](experiment-neoforge_partial_handoff.md)。生产优先级交接是另一项候选，需单独验证，不由本报告推定通过。
