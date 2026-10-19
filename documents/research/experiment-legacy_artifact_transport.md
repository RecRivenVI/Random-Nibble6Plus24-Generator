# 1.20.1 脱离式 Artifact 与群系快照交接

调查日期：2026-10-16。Forge 本版的三维度四项 B/Artifact 交接已在十六个独立 JVM/save 执行并 CheckOnly；完整结构交接正在验证，物理 FULL 尚未接入。

## 问题

1.20.1 没有新版 SerializableChunkData、GenerationChunkHolder 或 ChunkPyramid，不能机械移植其对象布局。需要先证明完整 B 数据能在 isolated owner 关闭后安全重建，且 SPAWN 不必依赖存活的生成 session。

## 方法

审计本机实际 Forge 47.4.28 补丁 JAR 的 ChunkSerializer、ProtoChunk 与 BlockEntity API。捕获复用原生 section/biome codec、structure pack/load、tick save/load，分别保留 pending 与 instantiated BE；不调用会读取 physical light storage 的完整 serializer 作为捕获入口。重建传明确的 local seed，不读取宿主 seed。

输入指纹 2B59B439AF8A15D8AF00CD57FA304317850588E9091BB0B60F71B0F485CDC5DF。先在独立固定 artifact 证据域完成四 JVM 原生潜影贝双向因果控制，再执行两个原生控制与宿主 17/23 的四项 smoke；沿用 independent 准备，不引入共享结构状态优化。与另一独立目录的四核正确性队列并行，均低优先级，不测量性能。

## 发现

- [四项交接](../../validations/conformance-legacy_b/result/smoke-handoff-artifact/1.20.1-forge)为主世界原点/非原点、下界及 EndSpike，4 PRODUCED 加 12 PASS，十六个 fresh JVM/save；额外 CheckOnly 通过。
- [原生因果控制](../../validations/conformance-legacy_b/result/end-yaw-artifact/1.20.1-forge)四项完成，两方向的完整 UUID-only NBT 精确重现；没有新增随机字段例外。
- 八个消费者均验证 owner 已关闭、Artifact 不保留可变 section/tick 容器、全部生成高度图与 pending/instantiated 分离完整。跨 seed、坐标、维度和合同误用必须拒绝。
- 主世界每目标保留 12,288 个邻区 biome 格，下界和末地各为 8,192；逐格及纵向 clamp 与真实 stored palette 一致。实际重建不引用 session、NoiseChunk 或 RandomState。
- EndSpike 每个消费者有一个真实实体，交接中的原始 UUID/朝向严格保留，不用跨 JVM 的有限例外放宽同进程传输。physical host、实体/调度器/POI 检查通过，绑定清空。
- 本 smoke 没有 BlockEntity 或 StructureStart 加载正例，不能据此宣称结构重建、BE/tick 全类型已完成；完整十五项结构交接另行运行。原生 serializer 的 ForgeCaps 分支属于 LevelChunk，并不证明任意第三方 ProtoChunk 扩展可安全传输，当前能力门保守拒绝未知组合。
- 主世界消费者单项约 129～152 秒，仍为未经优化的 independent 准备；没有同资源原版性能控制，不计算吞吐提升或比值。此项是数据正确性而非性能保留证明。

## 结论

本版脱离式 pre-light 数据与 stored SPAWN palettes 的有限 smoke 通过，可继续完整结构交接。没有注册生产生成器 codec，没有执行物理 LIGHT/SPAWN/FULL，也不宣称本 Target 已可游玩或第三方兼容。
