# B 模式验收证据索引

本页集中提供里程碑 1 与产品化依赖的证据入口；每项只覆盖其记录中的版本、输入和门禁。当前范围与下一步仅由[里程碑 2](../project/milestone-productization.md)定义，不因探针改动要求整套重跑。使用者已接受原版 B 的性能 PARTIAL，A/C/BOP 暂缓，既有研究原文和失败证据保留。

## 检查点

[产品化边界整理](../research/audit-productization_cleanup.md)记录独立探针迁移、六个失效入口和三个临时归因文件的删除原因、共享合同边界及全端工程检查；不借此提升暂缓端的游玩状态。

[产品化后单配对抽查](../research/experiment-productization_screen.md)记录 8 视距原版相对比与缺测边界，保留性能 PARTIAL，不把一次测量升级为新的优化保留或视觉验收。

两端冻结组合的七十二项装饰前、十八项完整结构、十九项磁盘 B 与副作用正例见[精确部分地形冻结对照](../research/experiment-native_terrain_freeze.md)。内容矩阵已完成，不为后续仅调度或探针改动整体重跑。

[读取／生成交接筛选](../research/experiment-native_load_handoff.md)未达到收益阈值，产品候选及运行开关已撤回，不以正确性测试通过推定性能通过。

[关联查询结构原点复用](../research/experiment-related_structure_origin.md)正确性筛选通过但发送就绪收益不足 1%、平均等待改善约 2.63%，候选已撤回；不以 CPU 热点下降替代端到端收益。

[两端三档原版性能差距](../research/investigation-native_b_delivery_cost.md)汇总三十六个独立新世界的交错配对、延迟删失范围、逐 FULL 工作和剩余代价；数值目标未通过，不把交付等同于原版级性能。

[实际独立服验收入口](../research/incident-review_client_entry.md)覆盖皮肤客户端与原生 B 服务端连接、查询和正常关闭；记录 UTF-8、多人返回界面、IPv6 入口问题及固定 MCP quit 回执边界，不改写早期 FAIL。

真实传送门的有限分支、两次测试缺口及当前来源见[传送门实验](../research/experiment-native_portal_lifecycle.md)，不把测试探针修正描述成生产崩溃修复。

NeoForge 交接输入的四场景、十六独立 JVM 磁盘 B Oracle 位于 `validations/conformance-feature_b/result/retained_terrain_disk_current_loader/independent_smoke/26.3-neoforge`，inputHash 为 `E513DB04A5068FE42F69FB97714515FAD7FEE19B0268592491BDFB961F39090B`。三维度四角色部分 TERRAIN 生命周期位于生产验证的 `partial-terrain/retained_terrain_handoff`。旧 B launcher 引用旧 Loader 的 export 失败保留在 `retained_terrain_disk_reload`，没有当作 worldgen 失败或改写；新运行显式使用当前原生启动元数据。

Fabric 真实传送门位于 `validations/integration-mcp_alpha/result/client-lifecycle`：Vanilla 的 `lifecycle_vanilla/native_real_portals_ready_floor`、B 的 `lifecycle_b/native_first_credits_portals`。四段原生下界／末地往返及真实客户端／服务端目标维度通过；首次 End 返回由 EndPortalBlock 原生 credits 分支独立观察，不伪造普通 PortalProcessor destination。脚本等待位置失误和缺该分支观察的原始 FAIL 仍保留。

NeoForge 五个自然保存场景为末地水晶、硫磺模板 pending BE、地牢刷怪笼、试炼结构、下界箱子，分别十份 fresh／reload JVM；输入为 `B5E7B85BFAF6696FC7708368D9DA31FC64D97973BC4126B9DAAA312BB61F4502`。入口为 `validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier` 的对应维度及 `native_natural_<fixture>` 目录。证明原生激活、实际 tick、唯一实例与保存重载，不把自然运行中 NBT 可变字段当作不可变。

部分地形交接的独立磁盘 Oracle、票据诊断和三组受限配对见[物理地形交接实验](../research/experiment-physical_terrain_handoff.md)，不代表完整交付或 Vanilla 性能目标通过。

依赖更新前为 6d78e99f。[原账本快照](../research/audit-b_goal_checkpoint.md)保留截至 2026-10-18 的完整矩阵、输入指纹、旧性能比和边界，不当作新 Goal 的任务安排。大体积原始结果留本机，以下研究记录给出原始路径。

## 26.3 Fabric

- [装饰前与独立原版](../research/experiment-fabric_state_oracle.md)、[完整高度结构](../research/experiment-modern_structure_height.md)、[结构／顺序与宿主](../research/experiment-fabric_structure_order.md)。
- [普通 FULL 请求与去重](../research/experiment-fabric_production_requests.md)、[全 owner biome 与重载](../research/experiment-fabric_owner_biomes.md)。
- [自然实体／BE 保存](../research/experiment-fabric_natural_activation.md)、[下界正例](../research/investigation-fabric_nether_entities.md)、[猪灵有限验收](../research/experiment-fabric_piglin_attribute_order.md)。
- [真实客户端出生／重生、命令往返和冷重载](../research/experiment-fabric_client_lifecycle.md)：不含真实传送门。
- [三档性能基线](../research/experiment-fabric_current_frontier.md)、[结构历史诊断](../research/experiment-fabric_source_state_audit.md)：旧停止规则不替代新 Goal 对主要吞吐问题的要求。
- [当前三组逐 FULL 工作基线](../research/investigation-b_full_work_amplification.md)：新增整机负载、实际 owner 工作和物理 BIOMES 锁归属；不是优化保留结论。
- [物理 BIOMES 缩锁筛选](../research/experiment-physical_biome_lock_scope.md)：限定正确性和三组性能筛选通过，完整集成与最终 Vanilla 门槛待验。
- 精确部分 TERRAIN 候选：本机 `validations/conformance-prefeatures/result/native_terrain_smoke_native_detached_terrain/26.3-fabric` 的四项独立原版输入对照，以及 `validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/partial-terrain/native_partial_terrain` 的普通请求／保存重载。不是已通过的性能结论。
- 私有 TERRAIN 副本已接入经 capability 检查的原生 B 生产组合；完整内容证据与输入身份见精确部分地形冻结对照，三组收益见物理交接实验。未知模组组合仍走精确原路径，不以此宣称其已验证。

## 26.3 NeoForge

- [实际客户端与真实传送门](../research/experiment-neoforge_client_portals.md)：普通原版／B 的二十八检查点、冷 JVM 重启与正常退出；不代表性能或官方安装器验证。
- [部分阶段组合交接](../research/experiment-neoforge_partial_handoff.md)：三维度部分保存重载、四场景独立磁盘 B 对照及三组受限性能筛选，不替代最终 Vanilla 比值。
- [原生缓存状态守卫](../research/incident-neoforge_source_climate_state.md)、[完整高度结构](../research/experiment-modern_structure_height.md)、[运行期／重载](../research/experiment-neoforge_runtime_frontier.md)。
- [三维度普通 FULL 请求](../research/experiment-neoforge_native_requests.md)：32 去重、pair、3×3，非内容顺序或玩家门禁。
- [三档性能基线](../research/experiment-neoforge_state_frontier.md)、[入口历史筛选](../research/experiment-neoforge_source_state_review.md)：不把累计 supplier 时间当作可删除的 critical path。
- 精确部分 BIOMES：本机 `validations/conformance-prefeatures/result/biome_only_smoke_native_detached_biomes/26.3-neoforge` 与 `validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/features-2/development-b/partial-biomes/native_partial_biomes`；后者三维度十二次独立 JVM 保存／重载通过。首轮性能结果位于 `validations/performance-worldgen/result/b_milestone/26.3-neoforge/native_partial_biomes_screen`，收益尚不足保留。另一个调用点缩锁候选的四项输入对照在 `biome_only_smoke_native_detached_biomes_narrow/26.3-neoforge`，不借用前一组性能结论。
- 内容顺序与多会话：本机 `validations/conformance-feature_b/result/sessions/26.3-neoforge/native_b_sessions/summary.json`，五目标、五项逆序比较、五任务并发比较通过；实际原版宿主未增加已加载区块、实体或 ticks，context 绑定归零。范围不是客户端或自然保存证明。

## 其余 Target

1.20.1、1.21.1、26.1.2 首版暂缓游玩支持，保留源码、构建与原始失败，不再扩展其性能优化。研究组合的通过不冒充默认生产路径可用。

- 26.1.2 的原版门禁只读复核：`conformance-prefeatures/result/terrain_matrix/26.1.2-neoforge` 为 72 项，`terrain_structures` 为 18 项；来源身份 `400DF91C` 开头的既有独立世界，不冒充本轮重新生成。完整 B 的 `conformance-feature_b/result/independent_handoff/26.1.2-neoforge` 为 18 项、72 个 fresh JVM，来源身份 `D31DCB` 开头，天然 entities／BE／block ticks／fluid ticks／post-processing 均有正例，pending 为零，不拿人工数据充数。
- [26.1.2 玩家冷末地前沿](../research/incident-neoforge_player_cold_frontier.md)：全局加载数量的误判已修正，但真实玩家冷切换仍触发看门狗；使用者选择首版暂缓，不把内容一致提升为可玩。
- [五 Target API 与移植边界](../development/porting-worldgen_targets.md)、[多端 Oracle 初始调查](../research/investigation-b_multitarget_oracle.md)。
- [1.21.1 当前矩阵](../research/experiment-legacy_reuse_matrix.md)、[原生运行期](../research/experiment-legacy_reuse_runtime.md)、[冷 SPAWN](../research/experiment-legacy_cold_spawn.md)。
- [1.21.1 默认生产前沿与首版暂缓](../research/incident-neoforge_legacy_release_deferral.md)：原预算内零完成，不自动启用未获保留的研究组合。
- [1.20.1 原预算失败与对照](../research/experiment-forge_native_runtime.md)、[线程／前沿观察](../research/experiment-forge_dimension_frontiers.md)、[半成品恢复](../research/experiment-forge_partial_recovery.md)。
- [1.20.1 实际任务图与 FULL 放大归因](../research/incident-forge_full_preparation.md)：原三百秒仍失败，原因与最小方案已交代，未启用生产优化。
- [1.20.1 有限环绑定后的 FULL 复核](../research/incident-forge_release_deferral.md)：有限环确实执行，但原预算内仍未完成，自动绑定已撤回；使用者决定首版暂缓。

## 验证入口

产品化的定位禁用在两个 26.3 端与保留的 26.1.2 均实际执行普通原版／Mosaic fresh 与独立 JVM reload。入口为 `integration-production_lifecycle/result/current/<target>/phase3c2-production/` 的 `vanilla-control` 和 `features-2/development-b`，场景 `productization_search`；Fabric 使用 `runtime-proof`，另两端使用 `runtime-proof-frontier`。记录真实原生眼、两类地图、三类交易与海豚消费者，没有额外 Artifact 或加载 Chunk，不以命令 mock 代替消费者。

参考端内部索引的六类结构、真实锚点与幂等／删除／重载位于 `feature-structure_index/result/current/26.3-fabric/phase3c3b-locate`。fresh／reload 的 indexed entries 为 59，重载无新增 Artifact、加载数量保持 2850；公开三类 locate 命令拒绝，内部存储仍可查，不改变存档格式。

[世界生成工具](../development/testing-worldgen.md)、[生产生命周期](../../validations/integration-production_lifecycle/validation.md)、[性能测量](../../validations/performance-worldgen/validation.md)、[MCP 自动化边界](../development/compatibility-mcp_alpha.md)。既有 BOP 调查保留，但正式兼容工作延期。
