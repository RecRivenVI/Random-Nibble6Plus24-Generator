# MCP alpha 自动化接入

## 目标

在项目独占的 26.3 Fabric 实例中验证固定 MCP alpha 发行 JAR；先只装 MCP 验证 endpoint 与 READ，再接入 Mosaic。只使用原生游戏路由，不使用桌面自动化。内部生成正确性仍由本项目探针核对，不由 MCP 读取替代。

真实客户端生命周期另覆盖普通 Vanilla／B 的出生、死亡和原生重生、命令触发的下界／末地往返、保存重进与独立 JVM 冷重载。探针只旁观，不创建／移动玩家或生成 Chunk；客户端角色输入与服务端内部断言分开。

## 运行

从仓库根目录执行；工具核对未撤回快照、发行 JAR 和手册哈希，使用正常安装的 Fabric，而非 MCP 开发类路径。依赖只来自 Minecraft 和 Fabric 的原生安装元数据，不为 MCP 补依赖。

准备／启动时通过快照手册指定的工具登记本线程使用，最终 `check` 确认进程与 endpoint 均关闭后释放；该工具只管理分发登记，不执行 MCP 的 Gradle 或修改其源码。

```powershell
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py prepare
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py launch
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py read --output minimal-session.json
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py read --path /v0/ui/tree --output minimal-ui.json
```

最小实例检查完成后，通过 MCP 的 `menu.quit` 语义按钮正常关闭，再执行项目测试。`prepare` 只从本项目封闭验证存档复制已校验的 B 身份，不复制地形或玩家；运行产品 JAR 来自正常 `jar` 任务，不加载服务端手工阶段驱动。

```powershell
.\gradlew.bat :version:26.3-fabric:jar
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py prepare --stage mosaic
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py launch --stage mosaic
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py smoke --stage mosaic --output mosaic-smoke-ready
```

同样通过 MCP 的 `menu.quit` 正常关闭后复核。既有证据不覆盖；重跑时显式使用独立标签，复核参数使用对应标签。

```powershell
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py check --output mosaic-smoke-ready
.\gradlew.bat :worldgen_validation:verifyMcpAlpha
```

交互前先 READ，再显式 acquire；每项操作使用新鲜语义目标并等待终态，结束 release。录制、截图、安装与世界共用本验证的 512 MiB 上限；不覆盖原始失败或用户存档。

玩家验证需要先编译本项目 probe；发行安装只增加这份项目探针，不为 MCP 补依赖。以下为已保留证据的只读复核，默认核对当前 Target 源码指纹；历史指纹只能用于 `--check-only --expected-input-hash`，不能用于重跑。

```powershell
python components/worldgen_validation/scripts/integration-mcp_alpha/client_lifecycle.py --stage lifecycle_vanilla --label native_roundtrip_diagnostics --check-only
python components/worldgen_validation/scripts/integration-mcp_alpha/client_lifecycle.py --stage lifecycle_vanilla --label native_cold_restart --check-only
python components/worldgen_validation/scripts/integration-mcp_alpha/client_lifecycle.py --stage lifecycle_b --label native_roundtrip --check-only
python components/worldgen_validation/scripts/integration-mcp_alpha/client_lifecycle.py --stage lifecycle_b --label native_cold_restart --check-only
```

需要全新重跑时，先确认所有自有客户端已正常退出；用 `retire --stage <stage> --output <label>` 保全现有测试实例，不覆盖结果，选择独立证据标签。`<stage>` 为 lifecycle_vanilla 或 lifecycle_b，`<label>` 为本次明确的本地证据名称；每次只启动一个客户端。下面以 B 为例，普通 Vanilla 使用对应 stage。

```powershell
.\gradlew.bat :version:26.3-fabric:probeClasses :version:26.3-fabric:jar --no-configuration-cache
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py prepare --stage lifecycle_b
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py launch --stage lifecycle_b
python components/worldgen_validation/scripts/integration-mcp_alpha/client_lifecycle.py --stage lifecycle_b --label <label>
python components/worldgen_validation/scripts/integration-mcp_alpha/mcp_alpha.py quit --stage lifecycle_b
```

核实 PID 与 endpoint 都已结束后再次 launch 同一 stage，不执行 prepare 或重置地图；执行 `client_lifecycle.py --stage <stage> --label <cold-label> --cold-baseline <label>`，核对冷重启后再正常 quit。cold-label 是独立冷重启证据名称。诊断 scope 仅包含流程需要的 read、ui、control、input、command、capture、diagnostics，不含 world DEBUG 或 storage。命令回执不作为效果证明，所有步骤等待客户端状态与项目服务端检查点。

## 通过标准

- 固定快照未撤回，清单的 packagedRuntimeVerified 为 true，复制和安装的 JAR 哈希完全相同。
- 最小实例外部模组只有 MCP；endpoint 身份与本次自有进程、目录、Target 一致，完成 USER READ。
- 项目实例随后才能安装产品；游戏操作经过 MCP，数据检查仍使用项目探针。
- 正常保存退出，无崩溃，释放自己的输入与快照登记。
- 生命周期的两个对照各有八个检查点和一个冷重启检查点；重生只发生一次，物理邻区真实，关闭后 in-flight／绑定／玩家数为零。保存目标不重新生成 Artifact，结构数据与测试修改保持。
- 本场景不证明真实传送门搜索、末地返回／字幕流程、性能或其它 Target；不把 postProcessGeneration 方法调用次数当作实际重复后处理。
- MCP 缺陷保留批次、JAR 哈希、复现步骤、预期和实际，不改 MCP 或补依赖绕过。
