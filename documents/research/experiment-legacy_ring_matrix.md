# 1.20.1 状态安全有限环的七十二项装饰前对照

调查日期：2026-10-17。H120-R 第二轮在当前输入指纹下完成 72 项独立原生六阶段对照与只读复核，三维度全部通过；候选仍默认关闭，结构专项与性能不由本矩阵代替。

## 问题

原生尾查询与歧义回退加入有限环后，能否保持主世界原点/非原点、下界和末地的全部装饰前输入与结果，而不仅是 Stronghold placement 的组件一致？

## 方法

运行 `Run-LegacyTerrain.ps1 -Target 1.20.1-forge -Suite matrix -RingScope -EvidenceLabel climate_tail`，完成后另开 CheckOnly。每项使用不同 JVM/save 的原生生产者和轻量消费者，生产者 world seed=S(C)，消费者使用无关宿主 seed。输入指纹为 `29CF837192253E66666555A548B30B6AC4906DAB46337C01771C605682C5C8B3`，运行前后校验不变。

比较 STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES、NOISE、SURFACE、CARVERS 的完整 canonical 数据与原始结构文件；同时比较九份 stored-biome 输入、实际原生密度切片 raw bits 和 Beardifier 输入，不以重新计算的同路径噪声冒充独立观测。

## 发现

完整矩阵（本机证据：`../../validations/conformance-legacy_terrain/result/ring_scope_matrix_climate_tail/1.20.1-forge`）共 144 个独立 JVM/save：主世界 40/40、下界 16/16、末地 16/16，全部通过。只读复核明确输出 CURRENT_SOURCE，并核对全部原始文件哈希。没有增加比较例外，没有用浮点容差、近似高度或默认邻区值通过。

每个装饰前消费者保留一个完整目标、八份 biome-only 载体、289 个 carver 配置源和零 generation holder，FEATURES 未执行；这不表示完整 B 已取消八份完整邻区地形。scope 的父随机流、边界和气候续流组件见[尾查询实验](experiment-legacy_ring_tail.md)，独立完整 B/交接见[结构交接实验](experiment-legacy_ring_handoff.md)。

## 结论

当前状态安全有限环的 72 项装饰前范围通过。十五项结构六阶段专项仍单独收口；三维度 physical FULL 不替代自然 ticking、并发或出生，正确性队列耗时也不替代同资源单实例性能。未完成正式性能保留门槛前，不启用默认候选，不改变 B、旧存档或其它 Target。
