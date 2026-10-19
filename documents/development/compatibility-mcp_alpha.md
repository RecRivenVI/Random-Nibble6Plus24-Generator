# MCP alpha 自动测试兼容边界

当前仅接入参考 Target `26.3-fabric`，固定快照与 JAR 校验值见 [MCP 验证输入](../reference/dependency-build.md#mcp-alpha-自动化输入)。只安装该端一个原样 JAR，不补依赖，不操作外部实例；MCP 与项目产品分别安装到本验证的独占目录。

## 当前覆盖

正常安装的 Fabric 只装 MCP 时，endpoint、身份握手、READ 和标题界面语义树已通过。随后真实 Mosaic B 客户端已通过进入世界、原生保存退出、重新进入和再次退出；输入释放后键、鼠标按下数均为零。截图已实际取回 PNG，不仅取得链接。

参考端的[真实玩家生命周期](../research/experiment-fabric_client_lifecycle.md)已覆盖普通 Vanilla 与 B：首次出生、死亡／原生重生、命令触发的下界／末地往返、保存重进及独立客户端 JVM 冷重载。项目只读观察器核对真实物理 ENTITY_TICKING、二十个玩家 tick、九个 physical 邻区、实体注册、结构保存和具体 owner 的 Artifact 去重。

界面、按键和本场景的 gamemode／kill／execute-tp／setblock 均走 MCP 的 GAME_ROUTED 原生回调，没有 Computer-Use、宿主鼠标或键盘模拟；这些命令是明确的诊断夹具，不当作天然 gameplay 证据。内部世界生成、线程归属、保存 authority 与性能的断言仍由本项目探针承担。尚未验证另四 Target 的 MCP 安装、连续移动／视角、真实传送门／末地返回流程、seed／locate 命令或人工画面；不从参考端或本场景推定通过。

## 缺陷清单

当前没有确认属于 MCP 的新增缺陷。新增条目须写明批次、JAR SHA-256、最小复现步骤、预期与实际，并保留原始响应；由使用者转交 MCP 线程，不改 MCP 或补依赖绕过。

Quit 引起 `503 RUNTIME_SHUTTING_DOWN` 是快照手册列出的边界，不当作退出成功回执，也不盲重放；另外检查本次 PID 退出、正常 Stopping 日志和 endpoint 移除。测试驱动的漏传 B 显式启用参数、重复 acquire 和错误 POST 截图均是本项目启动／调用错误，不记成 MCP 缺陷；原始失败保留。

玩家生命周期另需 diagnostics scope 查询按键释放情况；它不是 world DEBUG 或 storage 权限。初次控制组因驱动漏配该权限而 FAIL，原始结果与实例保留，修正后用独立新图重跑；不能把实际步骤已完成当作整轮通过。单实例守卫会拒绝前一客户端尚未完成退出时启动下一客户端，核实 PID 和 endpoint 结束后才能继续。

## 资源与证据

自动实例为 1920×1080、静音、四逻辑 CPU 和低优先级。验证安装、存档、截图与记录合计受 [验证配置](../../validations/integration-mcp_alpha/validation.toml)的存储上限约束；默认不连续录像。令牌与 Lease 只留实例目录，结果不保存认证令牌；每次连接核对目录、PID、OS 启动时间与 Runtime UUID，重启只通过显式重新连接绑定。

MCP 注入本身可能影响性能测量；未建立同一 alpha、同一采样配置的 Vanilla/B 配对前，不将本次进入耗时并入已有性能比。纯 MCP READ 和 Mosaic 输入验证分开，固定快照在用时登记，试用结束显式释放。

还有一个已核实的能力边界：固定 alpha 的模组及其随包 Fabric API 不在当前原版快路径的已验模组集合中，因此结构 source 复用和部分 physical BIOMES 按既有检查精确回退。真实 B 生命周期日志中 structureSourceQueries 为零，与此一致；这不是 MCP 缺陷，也不能据其耗时评判无 MCP 的生产快路径。性能基线单独使用原版模组集合，MCP 客户端测量须另记模组集合与快路径状态。没有独立证明前，不为测试放宽生产能力检查或隐藏已安装模组。
