# 1.21.1 物理发布越权与独立时序验证

调查日期：2026-10-16。游戏运行已复现之前源码审计发现的完成回调越权。主线程派发修正与部分 BIOMES 候选已编译，但后续 FULL 请求仍触及原有预算，不能据“未再见同一异常”宣称生产交接通过。

## 问题

GenerationChunkHolder 的非异步 handle 可以在注册线程执行。原候选只把准备结果的 future 完成派发至主线程，却在原生 completeFuture 的 RETURN 才发布 Artifact 元数据；既有 future 消费者也可能先于该发布继续执行。

此前 [静态调查](investigation-legacy_holder_completion.md)未记作已复现。本次须区分实际异常、受控 focus 交错是否真正执行，以及整体 FULL 是否完成，不能合成一个 PASS。

## 方法

先完成 1.21.1 精确物理 BIOMES 的 72 项、十八项结构及独立 CheckOnly；其冻结输入为 B9CC757105D79A4E782A913610E09B9B280E603216BCA0A2A4B890CA8DB224F3。然后在独立 completion-order 世界中，通过普通物理请求观察原生 holder，并准备让一个 focus EMPTY 输入先完成、调用线程再注册 handle；probe 不提供或修改区块数据。

两次游戏均四逻辑 CPU、低优先级、180 秒原有预算。原生错误、世界、日志与 crash report 由验证工具保留；不延长超时、减少依赖或改变装饰结果。

## 发现

- JVM 59072 于 17:52:05 在 Worker-Main-1 抛出 Artifact publication lost holder/thread ownership。实际链为 MosaicPhysicalLevelState.published → GenerationChunkHolder.completeFuture → 原生 lambda$applyStep$0 → CompletableFuture.uniHandle/uniHandleStage → worldgen 调用线程，直接复现同步晚注册路径的风险。
- 该轮尚未输出 focus 的 input-complete-before-handle 标记，不能说指定 focus 的受控时序已经执行。其它 holder 的真实越权本身仍是有效失败，不能因没有 focus 标记忽略。
- 普通 FULL 在 17:54:36 达到原预算并产生 server crash report；保存关闭未结束后只终止了命令行明确属于本验证的 JVM 59072，不把强制终止说成正常保存。原始现场在 completion-order 首次失败（本机证据：`../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/completion-order/first_runtime_failure`）。
- 候选修正把 Mosaic holder 的完成 handler 本身派发到原生 ChunkMap 主线程 executor，并把元数据发布移至 native completeFuture 释放消费者之前。普通 Vanilla 的原始 handle 分支不改；只有属于该世界关闭的 cancellation 可正常终止，真正准备或发布异常仍进入原生 fatal 路径。
- 已证明的 detached BIOMES 仅在本 Target 的原生模组集合下启用，未知模组或显式 exactFallback 仍用完整 Artifact；不扩大 BOP 能力。完整与部分交接均在准备 owner 关闭后发生，世界归属、原生 palette、结构数据和升级需求保持检查。
- 修正候选重新编译并实际执行十九项单测通过。JVM 62100 的后续 FULL 仍在 18:02:47 达到 180 秒，没有再次出现同一发布越权，但也没有 focus 完成标记或成功 FULL 证据。该轮随后正常保存所有维度并退出，不再出现此前关闭 watchdog。
- 两次只读线程采样显示多数准备 worker 等待原生 BIOMES/NOISE future，另有原生 stronghold biome search 工作；这只是当前等待栈，不足以把成本归因闭合为某个算法或宣称已经证明 shared-pool starvation。该轮没有足够的分阶段准备计数，不能猜测部分 BIOMES 实际节省了多少工作。

## 结论

发布越权已由真实游戏栈证明；候选修正尚未通过完整运行时验收。下一步须在明确标识的组件时序验证中确认已完成输入的回调线程，并在完整物理负载中记录实际数据义务、准备类型和阶段等待，再闭合 FULL/关闭/重载；两类证据不能互相替代。

保留原始失败，继续标未通过。不得以延长预算、改变生成合同、压低生产并发、空 palette 或模糊忽略真实异常取得通过。
