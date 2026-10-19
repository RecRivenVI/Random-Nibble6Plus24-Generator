# 1.20.1 原生票据前沿性能探针边界

调查日期：2026-10-17。实际 Forge 47.4.28 的玩家构造、Chunk future 与发送前置条件和 1.21.1 不同；本记录只确定接入点，没有运行或宣称完成 1.20.1 的探索性能测量。

## 问题

如何以本版原生 PLAYER ticket、固定轨迹与真实物理生命周期测量 Vanilla/B，而不把单个 FULL 模块耗时、人工预热区域或新版不存在的 API 冒充端到端结果？

## 方法

对本 Target 实际 moddev patched Minecraft JAR 执行 Java 17 `javap -p -c`，核对 ServerPlayer、ChunkHolder、ChunkMap、DistanceManager；与 1.21.1 的现有探针只比较职责，不机械复制方法名或 future 类型。审计期间不修改正在验证的 Target 源码，也不与正确性队列并行测吞吐。

## 发现

| 职责 | 本版实际入口 / 判定 |
| --- | --- |
| 票据 token | ServerPlayer 构造器只有 server、level、GameProfile 三参数；没有 ClientInformation 参数 |
| 冷起点 | 构造器实际调用私有 fudgeSpawnLocation(ServerLevel)，需只在性能 token 的动态作用域阻止这次预热，再断言所有维度 loaded chunks 与 Mosaic work 为零 |
| 自然 loading frontier | DistanceManager.addPlayer/removePlayer(SectionPos,ServerPlayer)，保留本版原生 tracker 与 scheduler |
| 视距 | ChunkMap.setViewDistance 实际直接 clamp 到 2..32 并传给 updatePlayerTickets，没有额外加一；不能沿用旧版本记忆 |
| 原生请求 | ChunkMap.schedule(ChunkHolder,ChunkStatus)，返回 CompletableFuture of Either，右值是 ChunkLoadingFailure |
| generation FULL | getFutureIfPresentUnchecked(ChunkStatus.FULL) 的已完成左值；没有新版 getChunkIfPresentUnchecked |
| physical promotion | getFullChunkFuture、getTickingChunkFuture、getEntityTickingChunkFuture 分别观察真实完成左值；不能用 ticket 派生枚举代替 future 就绪 |
| 当前视野 | 本版公开 ChunkMap.isChunkInRange 五整数方法，使用实际配置视距；不自己硬编码新版圆角范围 |
| send-ready 前置条件 | updateChunkTracking 在 visible holder 的 getTickingChunk 非空时才进入 playerLoadedChunk；本版没有 getChunkToSend |

原生 playerLoadedChunk 随后构造 ClientboundLevelChunkWithLightPacket 并调用 player.trackChunk。只使用票据 token 而不建立网络连接的命令行探针，最多报告这项原生发送前置条件，不能报告已发包、客户端渲染或玩家出生 PASS。正常 Vanilla 不加载性能 token 时不得跳过 fudgeSpawnLocation。

Mosaic 计时应分别观察 prepare 首次请求、generate 真正开始、B preparation、物化、POI、LIGHT/SPAWN/FULL 与三类 promotion；只读订阅原 future，不替换 executor、future 返回值或调度优先级。原生 getTickingChunk 的发送前置条件与 BLOCK_TICKING 相关，但仍按本版代码分别记录原始观测，不伪造新版时间点。

## 结论

可按本版窄接入建立独立性能探针，保留旧探针和原始结果。必须先在游戏内验证冷启动 token、原生请求放大和真实 promotion，再执行四 CPU、单实例的 Vanilla/eager/ring 固定前沿筛选；结果不可与双队列正确性耗时混用。该工作属于验证基础设施，不改变 B、SPAWN、LIGHT、scheduler 或旧存档合同。
