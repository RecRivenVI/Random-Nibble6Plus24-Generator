# 1.20.1 状态安全有限环的结构六阶段对照

调查日期：2026-10-17。H120-R 第二轮在输入指纹 29CF837192253E66666555A548B30B6AC4906DAB46337C01771C605682C5C8B3 下完成十五项独立原生六阶段结构专项，另行 CheckOnly 通过。

## 问题

有限环的原生尾查询与歧义回退，是否保持实际结构 start、邻区引用、terrain halo、完整 pieces/junctions，以及噪声和表面结果？

## 方法

运行 Run-LegacyTerrain.ps1 的 1.20.1-forge structures、RingScope 与 climate_tail 组合；十五项分别使用原生生产者和轻量消费者的独立 JVM/save，共三十个实例。全部结束后在源码未变化时单独执行 CheckOnly，输出 CURRENT_SOURCE 与上述指纹。

## 发现

十五项证据（本机证据：`../../validations/conformance-legacy_terrain/result/ring_scope_structures_climate_tail/1.20.1-forge`）全部通过。覆盖本版本实际具有的 Village、Mineshaft、Ocean Monument、Nether Fortress、End City、Stronghold，以及邻区 start、地形 halo、原点与非原点回归；1.20.1 不存在 Trial Chambers，不虚构七类覆盖。

STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES、NOISE、SURFACE、CARVERS 的 canonical 数据与原始结构文件一致，stored-biome frontier、Beardifier 和实际原生密度切片 raw bits 一致。没有引入新忽略字段，没有近似高度或空邻区替代。

## 结论

本指纹下十五项结构六阶段通过；与[七十二项矩阵](experiment-legacy_ring_matrix.md)、[完整 B 交接](experiment-legacy_ring_handoff.md)互补。候选仍默认关闭，单实例正式性能、并发、自然运行期和玩家生命周期另行验收；这些正确性运行的耗时不作为性能对照。
