# 1.21.1 物理交接完成回调的线程归属

调查日期：2026-10-16。实际 1.21.1 NeoForge 字节码与验证 JVM 自带的 Java 21 源码表明：在主线程完成 future，不保证稍后注册的非异步回调仍在主线程运行。这是物理交接的源码级竞争风险，尚未以受控游戏交错重现，不记作新增已复现崩溃，也不改变已有验收结论。

## 问题

候选物理接线通过原生 ChunkMap 主线程队列返回准备结果，随后在 holder 完成通知中发布结构 Overlay 与 SPAWN 输入。是否可以仅凭“结果由主线程完成”保证发布通知的线程归属？

## 方法

读取验证实例实际 classpath 中的 `versions/1.21.1-neoforge/build/moddev/artifacts/neoforge-21.1.262.jar`，使用单逻辑处理器、128 MiB 上限的 javap 检查原生 ChunkMap、GenerationChunkHolder 与 ChunkGenerationTask，不启动额外游戏。该构件 SHA-256 为 `33AE4175A3CF9D0495F3C9ADF0E5B1DB6AEF510BA87D42B229E8D10C357CA7B2`。

同时读取本次验证 JVM 的 Temurin 21.0.11+10 自带 src.zip 中 CompletableFuture.java，核对 handle 与 uniHandleStage 的实际实现。产品侧检查 [MosaicPhysicalLevelState](../../versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/randomnibble6plus24generator/worldgen/materialization/MosaicPhysicalLevelState.java) 与 [MosaicHolderMixin](../../versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/randomnibble6plus24generator/mixin/MosaicHolderMixin.java)。本次只读审计，未改线程、调度或目标源码。

## 发现

- 原生 ChunkMap.runGenerationTask 将任务交给 worldgenMailbox；其回调调用 runUntilWait，不是将全部生成步骤固定在服务器主线程。
- 原生 GenerationChunkHolder.applyStep 对 GeneratingChunkMap.applyStep 返回的 future 直接调用非异步 handle，没有指定主线程 executor。
- 原生 lambda$applyStep$0 在成功分支直接调用 completeFuture；因此其线程由 handle 的执行条件决定。原生失败分支仍将真实异常交给 MinecraftServer.setFatalException。
- Java 21 的 handle 调用 uniHandleStage(null, fn)。如果注册时 future 已完成，uniHandleStage 直接调用 d.uniHandle，而非重新向原完成线程派发任务。
- 候选 Mosaic 接线把准备结果派发到原生主线程队列后，再把 future 返回给上述原生入口。主线程若抢先完成，worldgen 调用线程随后注册 handle，完成通知就能在 worldgen 线程执行。仅“始终先入主线程队列”的做法不能排除该合法交错。
- 当前产品发布通知在发现待发布 Artifact 后要求服务器主线程；其检查不会静默忽略越权，但可能使这类合法完成时序变成异常。已有普通 FULL smoke 不能证明所有交错安全。

## 结论

后续物理 BIOMES/Artifact 接线应明确保证发布回调的主线程归属，而非依赖 future 恰好尚未完成。最小候选接入点是已有 Mosaic 专用 handle 包装边界；普通 Vanilla 保持原生路径，真实失败与所属世界的预期关闭取消仍须区分。不能全局吞异常、修改生成算法或大范围改写 scheduler。

生产修改前须在原生生成调用线程受控制造“结果已完成、随后才注册完成回调”的时序，记录实际线程与发布次数；修复后验证该时序、普通异步完成、保存关闭取消、真实异常及 Vanilla 对照。当前两个正确性队列正在使用冻结源码，本项不在它们运行期间改动，亦不据静态推演宣称修复完成。
