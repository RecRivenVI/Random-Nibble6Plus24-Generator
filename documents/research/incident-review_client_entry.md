# B 验收入口与 MCP 关闭边界

调查日期：2026-10-20。两端正常独立 B 服务端与携带皮肤模组的真实客户端连接、查询、断开和保存关闭通过；最初三项失败均属于测试入口，不是世界生成异常。

## 问题

原生快路径尚未验证未知客户端模组的集成服组合；人工实例又需要 Custom Skin Loader。验收应既保留客户端偏好，也不能用被额外模组触发的保守回退来冒充本轮已测性能。

## 方法

新增模板日常实例 b-review-server／b-review-client，服务端从普通 runBReviewServer 启动，不装 probe、MCP 或皮肤模组，不使用 Phase 验证属性；只保留既有隐藏 B opt-in。来源只复制三份身份／世界设置 NBT 和 datapack，不复制地形或玩家，不公开 WorldPreset。

自动验证另用本验证自己的 MCP remote 客户端，安装当前产品发行 JAR、固定 MCP alpha 和已登记的 Custom Skin Loader，1920×1080、静音。服务端与客户端各四逻辑 CPU、低优先级，分属 3840／61440 affinity；没有 Computer-Use。测试后两者均正常停止，日常验收客户端不安装 MCP。

## 发现

首轮工具读 properties 未显式 UTF-8，Windows 按 GBK 解析失败；发生在客户端启动前，服务端正常保存关闭，结果保留 FAIL。修正读取编码，不改产品。

Fabric 实际加入和断开成功，但工具把多人断开后界面误认成 TitleScreen；原版实际返回 JoinMultiplayerScreen。保留原始 FAIL，经新鲜语义树的 gui.back 原生输入返回，再正常退出；新检查按真实界面推进，不调用业务方法绕过玩家输入。

NeoForge 的 localhost 解析为 IPv6 ::1，服务端仅监听 127.0.0.1，出现原生 Connection refused。新入口明确使用 IPv4 回环；原失败保留，未延长连接超时或修改生成代码。

最终 NeoForge native_ipv4 与 Fabric native_reload 均 PASS：真实 RecRivenVI 加入、MCP READ、普通权限命令、原生 UI 断开和退出，服务端退出码零，世界正常保存，没有新 crash report。Fabric 首轮生成了 197 次 B，重启后的缺失区域继续正常生成；这里没有用全世界 generation count 宣称所有已有区块零重建，已 FULL 不重建由单目标保存／重载门禁证明。

两端 /seed 的客户端聊天反馈分别显示 World Seed 与当前 Chunk World Seed；/locate structure minecraft:mineshaft 返回物理索引的坐标。MCP 的命令包提交并不自动证明效果，当前反馈仍带 temporal_not_guaranteed／submitted_unconfirmed；另有普通服务器 locate 日志及既有 Overlay／保存索引门禁，不借此声称全局搜索或任何 DEBUG 服务端读取。

结果入口为 `validations/integration-mcp_alpha/result/review-delivery/26.3-neoforge/native_ipv4`、`26.3-fabric/native_reload`；较早的 FAIL 原样保留。日常世界位于各 Target 的 b-review-server/mosaic-b-review，不在自动验证客户端的 saves 中。

### MCP alpha 限制清单

这是固定手册已声明的关闭边界，不作为新的上游缺陷转报；以下保留使用方的实际观测与收尾方式。

- 固定批次：20261017-1504-e83283fc。
- Fabric JAR SHA-256：`d922028c09ca1b93201af820eddd0915bdd3c68fe96630e95c7d097e51c95b03`。
- NeoForge JAR SHA-256：`11a1a94c5cbbf1aa2aa099d915e0133fa0e6d682ff4082382729c0722a9da131`。
- 复现：当前标题界面、合法 TAKEOVER lease，通过 ui.action 点击原生 menu.quit。
- 期望：明确的已提交关闭处置，或允许调用者可靠取得关闭动作回执。
- 实际：可收到 HTTP 503 RUNTIME_SHUTTING_DOWN，executionOutcome=not_confirmed、retrySafe=false；客户端已正常 Stopping!，PID 结束且 endpoint 删除。
- 本项目处置：不重试该动作，不把 503 当成功；保存原始响应，并以所属 PID、endpoint、日志和无 crash report 的关闭边界独立确认。未修改 MCP 或补装依赖，未扩大其它错误的忽略范围。

## 结论

验收入口已恢复为真实生产世界和普通调度。独立服务端保持原生快路径，客户端皮肤模组不污染服务端能力集合；单人未知模组组合仍保守回退，不擅自放宽白名单。客户端退出时的 MCP 回执限制保留供上游判断，不是新的 Mosaic lifecycle crash。
