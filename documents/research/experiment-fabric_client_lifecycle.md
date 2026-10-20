# Fabric 真实玩家重生与维度往返

调查日期：2026-10-17。26.3 Fabric 的普通 Vanilla 与 B 均通过真实客户端出生、死亡／原生重生、命令触发的三维度往返、保存重进和独立客户端 JVM 冷重载；仅本场景通过，不提升整个 Target、真实传送门或性能为通过。

## 问题

此前真实客户端接入只证明进入和退出，服务端 EmbeddedChannel 组件不能替代真实玩家。需要用 MCP 执行原生客户端输入，同时由项目观察真实 server player、ChunkStatus／FullChunkStatus、物理邻区及保存 authority，排除旧 JVM 临时缓存依赖。

## 方法

使用[固定 alpha 接入](experiment-mcp_alpha_integration.md)的同一 JAR，参考 Target 源码指纹 `59a505cf1c9442d1fa6828549cbfb2f5f97612e45609b682b50ff7e3a2ed375c`。普通 Vanilla 与 B 采用独立新实例和新存档，仅复制已校验的世界身份，不复制地形或玩家。每次一个游戏 JVM，四逻辑 CPU、BelowNormal、4 GiB、1920×1080、静音；没有 Computer-Use、桌面输入或手工驱动 worldgen 阶段。

MCP 通过真实 Chat 执行 gamemode、kill、execute-tp 和 setblock；死亡后的 Respawn 使用新鲜语义按钮。这些是明确诊断 Arrange，不冒充自然游戏内容或命令执行确认。每步另外核对客户端状态和项目 server-thread 只读检查点，不能仅凭 accepted、聊天反馈或标题页通过。

ClientLifecycleEvidence 不创建、移动、重生或手工 tick 玩家，不请求新 Chunk。只观察原生 PlayerSpawnFinder、票据、postProcessGeneration、实体管理器及 tickChildren 返回。检查点要求真实 lit FULL LevelChunk、ENTITY_TICKING、二十个实际 player.tickCount 和九个真实 physical FULL 邻区；请求绑定本次 serverRunId，重启拒绝旧请求。结构使用完整原生 NBT 严格比较，玩家修改由实际命令放置金块，再检查保存重进／冷重启保持。

## 发现

| 对照 | JVM PID | 检查点 | 本次证据 |
| --- | --- | --- | --- |
| 普通 Vanilla：fresh／死亡重生／下界与末地往返／保存重进 | 110716 | 8，两个 integrated server 实例 | 只读复核 PASS（本机证据：`../../validations/integration-mcp_alpha/result/client-lifecycle/lifecycle_vanilla/native_roundtrip_diagnostics/check.json`） |
| 普通 Vanilla：客户端冷重启，同一已保存 owner | 53568 | 1，新 server 与新 JVM | 只读复核 PASS（本机证据：`../../validations/integration-mcp_alpha/result/client-lifecycle/lifecycle_vanilla/native_cold_restart/check.json`） |
| B：fresh／死亡重生／下界与末地往返／保存重进 | 113260 | 8，两个 integrated server 实例 | 只读复核 PASS（本机证据：`../../validations/integration-mcp_alpha/result/client-lifecycle/lifecycle_b/native_roundtrip/check.json`） |
| B：客户端冷重启，同一已保存 owner | 88516 | 1，新 server 与新 JVM | 只读复核 PASS（本机证据：`../../validations/integration-mcp_alpha/result/client-lifecycle/lifecycle_b/native_cold_restart/check.json`） |

十八个检查点均通过。两类世界首次各一次出生搜索，死亡／原生重生再触发一次；真实 ServerPlayer 对象替换一次、UUID 保持，实体管理器与 PlayerList 始终对应唯一玩家。保存重进和冷重启出生搜索均为零，目标 Artifact 捕获为零；B 初次进入三个维度的目标各捕获一次。B 九邻区保留不同 local seed，普通 Vanilla 九邻区均为自己的 world seed，没有 Mosaic Artifact 工作。

每次世界关闭后的 inFlight、GenerationContextRegistry、SPAWN binding、PlayerList 数均为零，观察器不再持有该 server/player。两个对照中，保存重进和冷重启的完整目标结构 NBT 与金块都保持；不依赖同一 JVM 的 session 或内存缓存。

不能将原生方法调用误判成重复生成：两个对照保存重进时 postProcessGeneration 方法都调用 49 次，属于原生加载／promotion 路径，不等于产生 49 次重复后处理副作用。没有跳过该方法、catch 后继续或伪造邻区。B 首次会话关闭累计 1075 个自然 production targets，第二次和冷重启的日志 generations 都为零；这不是受控吞吐或性能比。

首轮 Vanilla 的八项实际流程已执行，但最终输入释放检查被 diagnostics scope 拒绝，整轮原始 FAIL 保留于 result/client-lifecycle/lifecycle_vanilla/native_roundtrip；实例保留到 instance/26.3-fabric/retired/lifecycle_vanilla-no_input_diagnostics，映射另记 result/retained。只补该流程需要的 diagnostics 后用全新地图重跑，没有开放世界 DEBUG／storage，也没有修改 MCP。一次在前一客户端尚未退出时的启动被单实例守卫拒绝，核实结束后才继续；不是新 MCP 缺陷。

实际游戏日志的视距／模拟距离为 2/12；配置中的模拟距离 2 没被原版采用。因此本轮完全不当作 2/2 或 8/8 性能对照，不报告 Vanilla 比值。主世界、下界与末地使用命令触发原生维度转换，没有测试真实传送门搜索、坐标缩放、末地字幕或返回入口。其它 Target 不继承本结论。

## 结论

参考端真实玩家限定门禁通过，生产生成算法、冻结合同与存档格式均未修改；原服务端传输组件的未验结果不倒填。后续继续当前状态修正后的受控性能与其它 Target 独立生命周期，五 Target 原版门禁完成后才进入正式 BOP。移动／视角、真实传送门及 seed／locate 保持各自待验边界，不扩大本实验结论。
