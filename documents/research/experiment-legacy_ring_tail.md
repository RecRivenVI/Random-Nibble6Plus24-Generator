# 1.20.1 有限环原生尾查询与歧义回退

调查日期：2026-10-17。H120-R 第二轮闭合了已发现的原生气候 cursor 组件边界，并通过有限环组件；候选仍默认关闭，独立世界、物理生命周期和性能保留另行验收。

## 问题

在保留原生 slot、父 RNG/fork 与有限范围的前提下，能否省略几何无关的完整群系搜索，同时不丢失其线程内出口状态？首版风险见[组件反例](incident-legacy_ring_climate_cursor.md)。

## 方法

本版原生完整搜索最后访问中心 X/Z 各加 112 方块的 quart 坐标。RandomState 创建 sampler 时展开 Holder 并去掉 NoiseChunk marker；这些原生密度节点的单点求值不携带群系 RTree cursor。候选只求最后一点，再以原生 `ParameterPoint.fitness` 对实际参数列表证明唯一最近记录，最后调用原生 `MultiNoiseBiomeSource.getNoiseBiome(TargetPoint)`，而非自行设置 RTree leaf。

没有唯一最近记录时，证明过程不访问 RTree，保留原始入口并执行完整原生搜索。重复最小记录即使对应相同 biome 也不放宽；未知 ParameterList、source、placement、模组环境和排除关系保持原生回退。参数或采样值超出可证明 long 距离计算不溢出的域时同样回退，不截断值、不近似距离。未引入跨目标缓存、额外池、世界数据 authority 或新存档字段。

原生气候探针双向比较入口 A、主体 B、出口与下一次等距查询，再交换 A/B。分别验证唯一出口和歧义时的完整搜索；只在 probe 中读取实际 leaf 身份，没有把反射或诊断读取带放入产品算法。完整环组件保留四个种子、1536 个相关正例、1000 个普通目标、原生父随机 API/入口/出口/fork/续流和 180 秒预算。

## 发现

当前组件输入指纹为 `29CF837192253E66666555A548B30B6AC4906DAB46337C01771C605682C5C8B3`。原始证据在[独立 smoke 目录](../../validations/conformance-legacy_terrain/result/ring_scope_smoke_climate_tail/1.20.1-forge)：`ring-climate-tail.json` 的四个方向/歧义案例全部通过，原始“直接省略搜索”反例仍可见；修正后的入口、真实出口 leaf 身份和下一次查询严格一致。超界数值、重复最小记录和未知 ParameterList 均拒绝快路径。

`ring-proof.json` 完成 1536 个相关正例，零漏检；1000 个普通目标的 128000 个 slot 仅执行 34 次完整群系搜索，其余执行已证明的原生尾查询。全部相关正例共 196608 个 slot 执行 1536 次完整搜索。组件用时约 63.18 秒，父随机流与续流一致、supplier 绑定清空。这是同 JVM 原生组件证明，不是默认世界普遍无差异或吞吐提升的结论。

首版对应另一指纹的十五项六阶段对照和十五项完整 B 交接也已完成；它们只保留其当次证据，不替代第二轮的独立 Oracle。原生全局 StructureState 不绑定候选，有限 scope 只属于显式本地生成上下文。普通 Vanilla 及未知环境仍走原路径。

## 结论

已发现的 cursor 组件反例有精确、有歧义回退的修正；没有新增忽略字段或固定生产排序。H120-R 已使用两轮算法预算，不继续第三轮同义算法。必须在本轮源码下补独立 Oracle、实际 FULL/保存重载与受控端到端性能；未达保留门槛继续默认关闭，不能用搜索次数替代相对 Vanilla 的吞吐和延迟证据。
