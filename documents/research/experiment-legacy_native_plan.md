# 1.20.1 原生生产依赖计划核验

调查日期：2026-10-17。依赖元数据适配已与实际原生方法严格一致；尚未注册生产生成器或接通 physical FULL，不能据此称该 Target 可用。

## 问题

1.20.1 没有较新版本的 ChunkPyramid/GenerationChunkHolder，是否能沿用本版原生 ChunkMap，而不是移植另一套 scheduler 或写死较新版本的半径？

## 方法

审计实际 Forge 47.4.28 补丁 JAR。`ChunkMap.schedule` 区分 EMPTY 的磁盘加载、已有状态的 load task 与缺失状态的 generation task；future 使用 Either。依赖来自 `ChunkStatus.getRange` 和私有 `ChunkMap.getDependencyStatus`：中心取 parent，非中心按原生 distance/statusAroundFullChunk 查表。mainThreadExecutor 是 BlockableEventLoop，不是 1.21.1 的 ProcessorMailbox；SavedData 使用 Function/Supplier/String，save 只接 CompoundTag。

产品适配只实现依赖值的深度优先展开，尚无生产调用者。独立 probe 对真实 ChunkMap 私有方法做队列展开，比较全部十二状态与三个坐标 `(0,0)`、`(125,-37)`、`(-125,37)` 的闭包及只读性。坐标/状态任务分别去重，不能因同坐标已有较高状态就跳过较低 parent 的展开。

在 fresh 普通世界、真实 ServerStartedEvent 内执行，四逻辑 CPU、低优先级。元数据 proof 与种子 proof 分开结果；完成后另行 CheckOnly，不依赖 JUnit 伪造 Forge 注册表。

## 发现

输入指纹 `991D222CE74021AA676540082F3685756E01673DADF2DD47BD73E2CE33FC06E3` 的三十六项原生比较（本机证据：`../../validations/conformance-seed/result/native-plan-binding/1.20.1-forge/native-plan.json`）全部通过，原四十五项种子与存档合同断言（本机证据：`../../validations/conformance-seed/result/current/1.20.1-forge/native-seed.json`）重新运行并复核通过，包含十八个冻结 seed vector。结果没有执行 physical Mosaic 生成。

| 请求状态 | 依赖闭包坐标数 | 当前保守 Artifact 数据义务数 |
| --- | --- | --- |
| EMPTY | 0 | 0 |
| STRUCTURE_STARTS | 1 | 1 |
| STRUCTURE_REFERENCES / BIOMES | 289 | 1 |
| NOISE / SURFACE / CARVERS | 361 | 9 |
| FEATURES / INITIALIZE_LIGHT | 441 | 25 |
| LIGHT / SPAWN / FULL | 529 | 49 |

这里只是原生依赖与数据义务规划，不等于会计算同样数量的完整地形，也不表示外圈 metadata 已真实提供。后续接线不能用改 status 或空数据冒充履约，必须继续复用已验证 Artifact，并正确区分磁盘已有与缺失数据。

初次元数据运行因 conformance-seed 没有加载新增的 private-method Invoker，发生 ClassCastException，零项比较；原始失败（本机证据：`../../validations/conformance-seed/result/native-plan/1.20.1-forge/native-plan.json`）与原世界、崩溃报告保留。独立的 `mosaic-native-plan-test.mixins.json` 只加载该 Invoker，随后新 `binding` 结果通过，没有加载整套结构诊断或把旧失败改写为 PASS。

## 结论

本版可从实际 getRange/getDependencyStatus 建立准确有限计划，无需虚构 ChunkPyramid 或重写 scheduler。下一步是沿本版原生加载、生成和 main-thread completion 边界接入；身份、取消、POI、LIGHT/SPAWN/FULL、Overlay 及重载仍须独立实现与验证。

本次没有性能结论，不根据闭包坐标数推算吞吐。原生种子、世界格式与完整 B 算法未改变；现有 Target 仍不注册可用 generator codec。
