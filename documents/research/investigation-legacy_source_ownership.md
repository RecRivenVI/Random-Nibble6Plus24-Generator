# 1.21.1 结构来源复用的所有权审计

调查日期：2026-10-17。本文记录 H121-U 候选的原生调用和隔离边界；实现尚未完成独立 Oracle 与端到端性能验收，不表示默认启用。

## 问题

原生冷前沿的缩锁组合移除了模板粗锁等待，却仍反复构造同一局部世界的原生结构。需要判断是否可以只复用已经完成的精确 source，而不改变 placement、Jigsaw、RNG 或 stored-biome 输入。

## 方法

审阅本版 `ChunkGenerator.createStructures/createReferences` 字节码、`StructureGenerationContext`、`CompactStructureInputs`、`LightweightTerrainGenerator` 与完整 B 准备入口，并核对参考 Target 的独占移交机制。只在一个准备调用内复用，未知模组/结构/池继续保守回退；不共享不同 physical target 或 local seed。

## 发现

本版 FEATURES 直接读取半径一的 CARVERS；每个地形的 SURFACE 又读取半径一的 stored BIOMES。当前实现实际创建九份完整地形，各准备九份 palette，其结构输入分别查询半径八的 source：共 81×289 次查询，query owner 联集半径二，source 联集最多 21×21＝441 个坐标。外圈 `FeatureOuterInputs` 不是这次复用的隐含范围，仍按自身原生阶段精确求值。

原生 `createStructures` 从传入 carrier 读取旧 start 的 reference count，并调用原生 `Structure.generate`；生成结果写回该 carrier。`createReferences` 按原生顺序遍历 source、读取原生膨胀包围盒、向查询 owner 写 reference set。因此缓存只能保存 starts-only carrier，查询 owner 必须另建；`StructureCheck.onStructureLoad` 仍在每个消费者按既有顺序调用。空结果也是实际求值结果，不能用未查询过的空数据替代。

FEATURES 放置会修改 start/piece 内部状态，不能让目标的可变图继续作为邻区读取权威。候选在目标完整地形生成后、八个邻区完整地形构造前，将目标保留的所有 start 来源移出复用表。目标 resolver 保留这些对象，后续读取若再次需要对应坐标，必须重新计算独立的原生对象；不克隆或改写 piece 算法。

作用域核对 host 实例、local seed、同一 generator/state 身份、query/source 范围及所属线程。关闭清空 carrier 和所有外部引用，只输出数字计数；Artifact 和 SPAWN palettes 仍是脱离式值，不依赖缓存存活。候选由显式开关及本版已审计原生能力守卫共同控制，未证明前默认关闭。

本版 JUnit 进程没有 NeoForge 游戏加载器的注册状态，直接运行 `Bootstrap.bootStrap` 会在 `FeatureFlagLoader` 失败；不初始化又调用 `ChunkPyramid` 则触发未 bootstrap 注册表。单元测试因此只注入有限范围数值来验证缓存所有权与边界，生产构造仍从真实 ChunkPyramid 推导并校验半径；真实结构与地形等价性必须在独立游戏 JVM 中验证，不伪造加载器注册表。

## 结论

可以形成有界、默认关闭的精确复用候选，没有发现必须改变 B 合同的数据缺口。仍需独立六阶段/完整 B、可变图身份检查、关闭清理和同负载前沿证明；内部 source 数量减少本身不能作为性能保留依据。后续结果由当前里程碑及独立实验记录承载，不回写本次调查。
