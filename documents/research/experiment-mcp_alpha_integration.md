# MCP alpha 独占实例接入实验

调查日期：2026-10-17。26.3 Fabric 的固定 alpha 已通过 MCP-only READ，再通过真实 Mosaic B 客户端进入、保存退出、重新进入与再次退出；仅证明本次接入范围，不提升整个里程碑或其它 Target 为通过。

## 问题

将游戏操作从手工验收改为可自动执行，同时避免桌面自动化、外部实例污染，以及用客户端观察替代项目内部 Oracle。

## 方法

从分发 latest 选择一次 `20261017-1504-e83283fc`，完整读取该批手册，核对 `.withdrawn` 未登记此批、清单 packagedRuntimeVerified 为 true。用手册指定工具登记独占 consumer，只复制本端 JAR、清单与手册到本仓库 build/artifacts，逐项核对 SHA-256，不取 MCP 仓库 build、不运行其 Gradle 或修改其源码。

JAR SHA-256：`d922028c09ca1b93201af820eddd0915bdd3c68fe96630e95c7d097e51c95b03`；手册 SHA-256：`76c9076c7417c4640cfb0dde02d09a93f61ece5a96c450352995186806157c62`。MCP 当前仓库的 OpenAPI 已不同于该批源码清单，不能复制当前文档冒充该批合同；运行以固定手册和实际响应为依据。

使用本项目的[验证工具](../../validations/integration-mcp_alpha/validation.md)，按官方 Minecraft/Fabric 安装元数据准备普通 Fabric Loader。最小实例外部 mods 只有 MCP 原样 JAR，内含 Fabric API 由其自身发行包提供，没有另外补依赖。完成最小 READ 并正常 Quit 后，再启动仅增加本项目产品 JAR 的独立实例。自动实例为 1920×1080、静音、4 GiB、四逻辑 CPU、BelowNormal；不与其它游戏或性能测量并行。

## 发现

最小实例 PID 83792，Runtime UUID `e8012900-85a7-422f-b8f6-12a057edb70b`。READ 会话是 TitleScreen、模式 READ；界面树返回实际 1920×1080 窗口和 Singleplayer 等原生语义控件。endpoint 来自该实例，逐次请求绑定目录、PID、OS 启动时间与 Runtime UUID，不扫描端口、重启不自动重绑。

首次项目启动 PID 106908 被现有 B 身份门禁拒绝：启动工具漏传 developmentB 显式 opt-in；原始 19:27:29 server crash report 保留于本验证实例。这是测试配置错误，未修改产品安全检查。重复 acquire 返回冲突、POST capture 返回 404 也分别是测试驱动调用错误；调整调用，不修改 MCP、补依赖或忽略错误。首个驱动失败保留于 result/mosaic-smoke，未倒填。

修正启动配置后，PID 82400、Runtime UUID `104b0060-b595-4df9-924f-1d34536b2a8e` 完成真实客户端闭环（本机证据：`../../validations/integration-mcp_alpha/result/mosaic-smoke-ready/summary.json`）：通过原生语义控件打开独占 Mosaic MCP Alpha 新图，客户端实际获得 Creative 玩家 `(26.5,97,19.5)`，进入后保存并退出；重新进入同一地图，再次正常保存退出。角色读取明确标 CLIENT_KNOWN，不冒充服务器真值。

原生 B 日志显示首次关闭累计生成 200 个目标，重进关闭又生成 25 个；后者可能来自尚未完成的外围请求，不能仅凭总数宣称旧 FULL 重生成或完全无重生成。具体 owner 去重仍由既有项目探针证明。本次没有测量新的 Vanilla/B 性能比。

释放后 pressedKeyCount、pressedButtonCount 均为 0，模式 READ。GET capture 实际取得 2027252 字节 PNG，1920×1080；未用桌面截图或截图替代 UI 语义控制。正常 Quit 时 HTTP 503 RUNTIME_SHUTTING_DOWN 符合本批手册的未确认边界，另外观察 Stopping、PID 消失和 endpoint 移除，没有盲重放。新启动没有新增崩溃报告，先前配置失败报告仍保留。

最终只读检查（本机证据：`../../validations/integration-mcp_alpha/result/check.json`）通过；安装、存档和产物共约 134.4 MiB，低于该验证 512 MiB 上限。试用结束已显式释放快照登记，两个自有客户端均已关闭。

## 结论

MCP alpha 可以作为本项目自动化游戏输入层，内部生成状态、原生 Oracle 和性能断言继续留在项目内。未确认 MCP 新缺陷；当前[兼容／缺陷清单](../development/compatibility-mcp_alpha.md)保持边界与复现格式。移动／视角、命令、维度切换、其它四端与长时录制尚未验证，不凭本次接入推定通过。
