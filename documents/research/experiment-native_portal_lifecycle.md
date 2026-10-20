# 真实传送门生命周期验证

调查日期：2026-10-20。26.3 Fabric 的普通 Vanilla 与 B 均完成真实下界和末地双向往返，B 另覆盖第一次终末之诗返回；两个探针缺口的原始失败保留，不当作生产崩溃，也不改写为通过。

## 问题

已有命令维度往返不能证明原生传送门搜索、创建、玩家切换、终末之诗和返回生命期。需要实际客户端进入门，并独立确认物理邻区、光照与原生分支，不用跨维度 tp 代替。

## 方法

使用本项目隔离 MCP 实例、固定 `20261017-1504-e83283fc` alpha JAR。命令仅布置实体门方块、框架与同维度进入位置；原生 PortalProcessor 或 EndPortalBlock 实际执行转换。项目只读 probe 记录真实分支、玩家 UUID、源维度、原生目的维度，并等待客户端与服务端 ENTITY_TICKING、光照和九个真实物理 FULL 邻居。B 邻区分别保持自己的 local seed。

两组均四逻辑 CPU、低优先级、静音；MCP 实例不用于性能数据。它安装未知环境模组时可能走精确回退，不能把这里的等待时间当作纯净优化路径的 benchmark。

## 发现

### 测试布置失误

第一组 Vanilla 的等待点位于门正上方，玩家提前掉入门，脚本随后尝试输入命令时已处于 WinScreen。MCP 正确返回 PLAYER_COMMAND_SCREEN_ACTIVE，没有盲目重试命令。保留 `lifecycle_vanilla/native_real_portals` 的 FAIL 与独立 cleanup；后续将等待点放到门外玻璃支撑块，按服务端实际 portalCooldown 归零再进入。

### 首次末地返回观察点

B 的初次返回已真实回到主世界，但原 probe 只观察 PortalProcessor.getPortalDestination，缺少该次返回记录，因此保持 FAIL。26.3 字节码显示：EndPortalBlock.entityInside 在末地遇到未看过 credits 的 ServerPlayer 时，直接调用 showEndCredits；后续原生 UI 确认走玩家返回，不调用普通 PortalProcessor 解析。

新增只读观察点绑定这条实际调用，记录 END_CREDITS_NATIVE_RESPAWN，不伪造 destination。只有末地→主世界这条有限测试分支可用它，实际目的维度仍由独立客户端／服务端检查点确认。未知 route 或其它方向仍失败；单元测试覆盖这条限制。

原 B 失败位于 `lifecycle_b/native_real_portals_ready_floor`，保留记录；新地图只复制项目验证世界的身份文件，不复制地形或玩家，未更改用户存档。

### 当前通过证据

共同根目录为 `validations/integration-mcp_alpha/result/client-lifecycle`：

- Vanilla：`lifecycle_vanilla/native_real_portals_ready_floor`，PID 75020，输入 `1bab30f460f11b0aa606fa037e32f826203611e1180a03601dfe11809d6b8270`。
- B：`lifecycle_b/native_first_credits_portals`，PID 118424，输入 `d13d21a073ee16a0ed2a41bb6386fee89d64181faf80d42dd9e175cbdf1c15e5`。

每组五个检查点，四次原生往返；普通转换严格核对类型及源／目的，首次 credits 单独核对真实分支和返回效果。正常保存退出、输入释放、in-flight／generation／spawn binding 与玩家数均归零，随后正常关闭客户端。Raw 来源仍标为命令 Arrange，不宣称天然玩法或视觉验收。

## 结论

该范围内没有发现新的生产生命周期缺陷。修正的是场景准备与观察点，未修改门算法、生成合同、实体规范化或调度器。结果只证明本版本 Fabric 的上述流程，不替代 NeoForge 玩家门禁、冻结候选完整回归或性能门槛。
