# NeoForge 实际客户端生命周期与传送门

调查日期：2026-10-20。26.3 NeoForge 普通原版与 B 的首次出生、死亡重生、保存重进、独立客户端冷重启及真实下界／末地往返通过。只证明列出的实际运行路径，不证明人工画面或性能。

## 问题

服务端 FULL 和命令跨维度成功不能代替真实客户端出生、原生重生、传送门解析和保存退出。需要独立观察物理邻区与玩家生命周期，防止验证路径代替正常生产调度。

## 方法

固定 MCP alpha `20261017-1504-e83283fc`，NeoForge JAR SHA-256 为 `11a1a94c5cbbf1aa2aa099d915e0133fa0e6d682ff4082382729c0722a9da131`。先在只安装 MCP 的最小实例核对实际模组列表、endpoint、READ、正常退出，才安装产品与本项目只读观察探针。

启动沿用模板实际导出的原生 ModDev Loader／runtime，排除开发产品目录，安装独立发行 JAR；不是官方安装器验证，也不补 MCP 依赖。各 JVM 四逻辑 CPU、低优先级、1920×1080、静音，只操作本项目独占实例；没有 Computer-Use。

MCP 通过原生语义 UI 和玩家输入完成操作。诊断命令只安排死亡、保存标记及实际门方块；传送门用同维度进入位置，不使用跨维度 tp 冒充。项目探针另外核对真正的物理 ENTITY_TICKING、光照、九邻区、玩家 tick、Artifact 去重和关闭清理。

## 发现

两种世界各八个正常生命周期检查点、一个冷重启检查点、五个真实传送门检查点，共二十八个；四个独立客户端 JVM、八次集成服务端实例。普通原版的 canonical Artifact 始终为零；B 相邻区块保留各自 local seed，保存后的 owner 不重新捕获 Artifact，标记与结构保存正常，关闭后 in-flight／context／spawn 绑定／玩家均归零。

| 世界 | 正常生命周期 JVM | 冷重启及传送门 JVM | 结果 |
| --- | --- | --- | --- |
| Vanilla | 127096 | 77904 | PASS |
| B | 19816 | 122652 | PASS |

真实下界往返和末地往返各一；首次末地返回命中原生 `END_CREDITS_NATIVE_RESPAWN`，没有伪造普通 PortalProcessor destination，实际客户端与服务端目的维度另行核对。退出正常，四个自有进程和 endpoint 均已关闭，没有新增 crash report。

六项 CheckOnly 均通过，客户端源码身份为 `42ea3f76a67d6f01c92fce59277696bee5f1eb6e66fe0112b488eaf53930210b`。本机入口为 `validations/integration-mcp_alpha/result/client-lifecycle/26.3-neoforge`，各 stage 的 `native_roundtrip_current`、`native_cold_current`、`native_real_portals_current`；MCP-only 安装核对为 `minimal-neoforge-check.json`。

## 结论

NeoForge 实际玩家门禁已收口；没有修改生产生命周期或放宽生成比较。MCP 属于当前未证明的第三方模组集合，既有保守准确回退仍生效，不能拿此客户端耗时评判纯净 B 快路径性能。
