# 原生玩家组件探针的传输边界

调查日期：2026-10-17。服务端玩家组件探针已编译，但完整独立 fresh/reload 门禁尚未通过；此前四次失败均保留，不计作产品崩溃或客户端验收。

## 问题

为原生 PrepareSpawnTask、PlayerList、重生和维度往返建立自动回归时，如何区分服务端组件探针与真正客户端生命周期。

## 方法

新增默认关闭的 PlayerLifecycleEvidence 与四个 probe Mixin，经原生 PrepareSpawnTask.spawnPlayer 和 ServerPlayer/PlayerList 执行，不构造假 Chunk。组件传输使用 Netty EmbeddedChannel，从 configuration 之后开始；因此显式标记 NOT_CLIENT_HANDSHAKE_OR_RENDER，不能当真实客户端或 integrated-server 证明。输出来自 [PlayerLifecycle 分支](../../components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1)，原始结果在本验证的 player-lifecycle/vanilla-control 下独立标签中保留。

## 发现

- native_player_transport：未正确安装原生协议切换 handler，InboundConfigurationTask 被传输探针当作普通数据。
- native_player_packet_tap：补上 Packet tap 后仍暴露相同 handler 初始化问题；组件必须明确建立它所声明的 post-configuration 起点，不能冒充完整握手。
- native_player_ready_protocol：组件误向普通 Vanilla 查询 Mosaic 的 per-owner artifact 指标，触发现有 fail-closed；修正观察边界，不放宽产品验证。
- native_player_vanilla_boundary：七段服务端操作已到 ENTITY_TICKING，但最终 ticket 检查误用 TicketType.toString。26.3 的 TicketType 是 record，应从原生 TICKET_TYPE registry 取标识。此错误修正后已编译，尚未运行完整 fresh/reload 对照。

这些标签保留各自源码指纹和原始失败，不因局部步骤到达就升级为整体通过。组件不证明真实窗口、连接握手、客户端接包或渲染。

## 结论

组件路径保持未验。使用者随后授权 MCP alpha，优先完成[独占客户端接入](experiment-mcp_alpha_integration.md)，其真实客户端进入／退出与本组件证据分开。后续将真实重生／维度往返交给 MCP 输入，内部 ticket／holder／保存 authority 仍由项目探针核对，不用传输组件替代真实客户端。
