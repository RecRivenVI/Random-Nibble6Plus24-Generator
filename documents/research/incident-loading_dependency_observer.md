# FULL 重载的依赖观察边界

调查日期：2026-10-16。26.1.2 普通 Vanilla 对照证明：已保存 FULL 的重载不能被要求恢复原先整圈生成依赖。把 generation pyramid 当成所有加载的永久就绪条件，会制造并不存在的产品失败。

## 问题

增强运行期探针在 fresh Vanilla 完成全部 49 个 FULL、2401 次群系输入检查和 220 个自然 tick 后通过，但新 JVM 重载在位置 (119,-43) 报缺少 BIOMES 输入。需要判断是普通世界退化，还是观察器错误混淆生成与加载。

## 方法

保留 2026-10-16 17:16:53 的原始 crash、日志、已通过的 fresh 输出和重载世界。读取本机实际 26.1.2 ChunkGenerationTask 与 ChunkPyramid 源码，追踪 canLoadWithoutGeneration、getRadiusForLayer、scheduleChunkInLayer 的选择条件；不修改生产调度或要求额外生成邻居。

## 发现

- canLoadWithoutGeneration 在中心已有足够 persisted status 时检查 LOADING_PYRAMID。调度层范围与各 holder 的生成/加载步骤也按 needsGeneration 和 persisted status 选择，不是无条件使用 GENERATION_PYRAMID。
- 加载图的 SPAWN 是透传步骤；LIGHT 只需要半径一的 INITIALIZE_LIGHT 等加载输入，不重新执行生成图的结构、地形或装饰。
- 初始增强观察器却对 reload 仍读取生成图，从而要求已经不在加载范围的位置 (119,-43)。失败由 probe 抛出，不能据此修改 Vanilla 或扩张 Mosaic 的保存重载生成前沿。
- 原始现场位于 integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production/vanilla-control/runtime-proof-frontier/minecraft-overworld/first_runtime_failure；fresh 输出为原始 PASS，整个 fresh/reload 组合仍未通过，没有倒改失败。
- 修正只涉及观察器：当前受控 fresh 世界使用 generation pyramid，同一已完全保存前沿的 reload 使用 loading pyramid；仍核对全部 49 个 FULL 目标的实际群系数据，保留所有运行期、唯一实体、tick、保存修改与零 Mosaic 侵入断言。
- 修正后的主世界普通 Vanilla 已用两个 fresh JVM 完成：生成 2401 次输入检查，重载 441 次；两者均观察 220 个自然 tick、实体跨边界、block/fluid callback 和熔炉烧炼，Mosaic 生成计数为零。其它维度和 Mosaic 组合仍须各自验证，不能借此提升状态。

## 结论

生成依赖不是保存区块加载后的永久保持承诺。受控验证应记录所用依赖图，不能为了让过强断言通过而生成 Vanilla 没有要求的新区块。保留原失败与新输出；本次改变的是验证边界，不是 B 合同、存档格式或玩家可见行为。
