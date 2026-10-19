# 里程碑 1：B 模式五 Target 正确性、性能与世界功能交付

状态：暂停。按使用者要求完成当前 NeoForge 普通请求验证、检查与账本登记后停止任务和持续目标，不启动下一门禁。里程碑整体未完成，A/C 暂缓。本文是唯一执行账本，恢复须等待使用者明确指示。

## 当前状态矩阵

更新：2026-10-18，26.3 NeoForge 新增三维度普通 FULL 的 32 去重、相邻 pair 与 3×3 请求九组通过；不提升为内容顺序或玩家门禁。参考 Fabric 已完成的装饰前、结构、独立 B、运行／重载及 MCP 客户端证据保持各自范围；两端当前三档性能基线及两次热点审查完成，按无新安全候选止损，不宣称性能追平。其它 Target 保持各自已验范围。PASS 只对应链接中已执行的源码指纹与范围，不等于 Target 收口；PARTIAL 为部分通过，待验为未执行，INTERRUPTED 为外部中断、既非通过也非失败。原版门禁全部完成后才进入 BOP。本文只维护当前状态；后续结果取代的表述直接更新，逐轮调查、实验和故障分析另存研究记录。原始证据、失败与签名按 X-07 保全，不随账本文字整理改写。

| Target | 维度 | 原版装饰前 72 项 | 结构输入 | 完整 B / 交接 | 生命周期 / 保存重载 | 并发 | 性能 / 相对 Vanilla | BOP |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 26.3-fabric | 主世界 | [PASS 40/40（总 72）](../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-fabric) | [PASS 18/18；完整高度 v2；七类及 halo](../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-fabric) | [PASS 14/14（总 19）独立原生](../../validations/conformance-feature_b/result/climate_state_launch_ports/independent/26.3-fabric) | [schema2 全 owner/tick/重载 PASS](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof/minecraft-overworld/all_quart_biomes)；[自然正例/重载 PASS](../research/experiment-fabric_natural_activation.md)；[真实客户端出生/重生/命令往返/冷重载 PASS](../research/experiment-fabric_client_lifecycle.md) | [模块并发/32 去重/pair/3×3 PASS](../research/experiment-fabric_production_requests.md) | [8/8 FULL/send 52.35%/47.11%；12/16 已测](../research/experiment-fabric_current_frontier.md)；[性能止损结束](../research/experiment-fabric_source_state_audit.md) | [待正式验收](../../validations/compatibility-bop/) |
| 26.3-fabric | 下界 | [PASS 16/16（总 72）](../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-fabric) | [PASS 18/18；完整高度 v2；七类及 halo](../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-fabric) | [原 2/2 PASS（总 19）](../../validations/conformance-feature_b/result/climate_state_launch_ports/independent/26.3-fabric)；[新增堡垒四角色有限验收 PASS](../research/experiment-fabric_piglin_attribute_order.md) | [schema2 全 owner/tick/重载 PASS](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof/minecraft-the_nether/all_quart_biomes)；[自然 10 实体/5 BE 重载 PASS](../research/investigation-fabric_nether_entities.md)；[真实客户端命令往返 PASS；真实传送门待验](../research/experiment-fabric_client_lifecycle.md) | [模块并发/32 去重/pair/3×3 PASS](../research/experiment-fabric_production_requests.md) | [8/8 FULL/send 91.99%/91.29%；12/16 已测](../research/experiment-fabric_current_frontier.md)；[性能止损结束](../research/experiment-fabric_source_state_audit.md) | [待正式验收](../../validations/compatibility-bop/) |
| 26.3-fabric | 末地 | [PASS 16/16（总 72）](../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-fabric) | [PASS 18/18；完整高度 v2；七类及 halo](../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-fabric) | [PASS 3/3（总 19）独立原生](../../validations/conformance-feature_b/result/climate_state_launch_ports/independent/26.3-fabric) | [schema2 全 owner/tick/重载 PASS](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof/minecraft-the_end/all_quart_biomes)；[自然正例/重载 PASS](../research/experiment-fabric_natural_activation.md)；[真实客户端命令往返 PASS；返回入口待验](../research/experiment-fabric_client_lifecycle.md) | [模块并发/32 去重/pair/3×3 PASS](../research/experiment-fabric_production_requests.md) | [有限完成量约 100%；8/8 p95 为 206.69%；12/16 已测](../research/experiment-fabric_current_frontier.md)；[性能止损结束](../research/experiment-fabric_source_state_audit.md) | [待正式验收](../../validations/compatibility-bop/) |
| 26.3-neoforge | 主世界 | [PASS 40/40（总 72）；7587 输入](../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-neoforge) | [PASS 18/18；完整高度 v2；七类及 halo](../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-neoforge) | [PASS 总套件 19/19，76 独立 JVM/save](../../validations/conformance-feature_b/result/climate_state_full/independent/26.3-neoforge) | [49 FULL/自然 tick/重载 PASS；7587 输入；出生往返待验](../../validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-overworld/climate_state) | [普通 32 去重/pair/3×3 PASS；内容顺序待验](../research/experiment-neoforge_native_requests.md) | [三档 send 64.73%/19.40%/12.43% Vanilla；两审查止损](../research/experiment-neoforge_source_state_review.md) | [待正式验收](../../validations/compatibility-bop/) |
| 26.3-neoforge | 下界 | [PASS 16/16（总 72）；7587 输入](../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-neoforge) | [PASS 18/18；完整高度 v2；七类及 halo](../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-neoforge) | [PASS 总套件 19/19，76 独立 JVM/save](../../validations/conformance-feature_b/result/climate_state_full/independent/26.3-neoforge) | [49 FULL/自然 tick/重载 PASS；7587 输入；出生往返待验](../../validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-the_nether/climate_state) | [普通 32 去重/pair/3×3 PASS；内容顺序待验](../research/experiment-neoforge_native_requests.md) | [三档 send 91.25%/90.04%/77.50% Vanilla；两审查止损](../research/experiment-neoforge_source_state_review.md) | [待正式验收](../../validations/compatibility-bop/) |
| 26.3-neoforge | 末地 | [PASS 16/16（总 72）；7587 输入](../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-neoforge) | [PASS 18/18；完整高度 v2；七类及 halo](../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-neoforge) | [PASS 总套件 19/19，76 独立 JVM/save](../../validations/conformance-feature_b/result/climate_state_full/independent/26.3-neoforge) | [49 FULL/自然 tick/重载 PASS；7587 输入；出生往返待验](../../validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-the_end/climate_state) | [普通 32 去重/pair/3×3 PASS；内容顺序待验](../research/experiment-neoforge_native_requests.md) | [三档有限前沿 send 99.97%/99.91%/97.29% Vanilla；两审查止损](../research/experiment-neoforge_source_state_review.md) | [待正式验收](../../validations/compatibility-bop/) |
| 26.1.2-neoforge | 主世界 | [PASS 40/40（矩阵总 72）](../../validations/conformance-prefeatures/result/terrain_matrix/26.1.2-neoforge) | [PASS 18 项总套件含本维度](../../validations/conformance-prefeatures/result/terrain_structures/26.1.2-neoforge) | [PASS 18/18；脱离式交接](../../validations/conformance-feature_b/result/independent_handoff/26.1.2-neoforge) | [加强就绪/自然 tick/重载 PASS；出生往返待验](../../validations/integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-overworld) | [主世界 32 去重/3×3 PASS；其余待补](../../validations/integration-production_lifecycle/result/current/26.1.2-neoforge) | [待测；无可靠比值](../../validations/performance-worldgen/result) | [待正式验收](../../validations/compatibility-bop/) |
| 26.1.2-neoforge | 下界 | [PASS 16/16（矩阵总 72）](../../validations/conformance-prefeatures/result/terrain_matrix/26.1.2-neoforge) | [PASS 18 项总套件含本维度](../../validations/conformance-prefeatures/result/terrain_structures/26.1.2-neoforge) | [PASS 18/18；脱离式交接](../../validations/conformance-feature_b/result/independent_handoff/26.1.2-neoforge) | [加强就绪/自然 tick/重载 PASS；出生往返待验](../../validations/integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-the_nether) | [主世界 32 去重/3×3 PASS；其余待补](../../validations/integration-production_lifecycle/result/current/26.1.2-neoforge) | [待测；无可靠比值](../../validations/performance-worldgen/result) | [待正式验收](../../validations/compatibility-bop/) |
| 26.1.2-neoforge | 末地 | [PASS 16/16（矩阵总 72）](../../validations/conformance-prefeatures/result/terrain_matrix/26.1.2-neoforge) | [PASS 18 项总套件含本维度](../../validations/conformance-prefeatures/result/terrain_structures/26.1.2-neoforge) | [PASS 18/18；脱离式交接](../../validations/conformance-feature_b/result/independent_handoff/26.1.2-neoforge) | [加强就绪/自然 tick/重载 PASS；出生往返待验](../../validations/integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/minecraft-the_end) | [主世界 32 去重/3×3 PASS；其余待补](../../validations/integration-production_lifecycle/result/current/26.1.2-neoforge) | [待测；无可靠比值](../../validations/performance-worldgen/result) | [待正式验收](../../validations/compatibility-bop/) |
| 1.21.1-neoforge | 主世界 | [状态安全复用 PASS 40/40；联合总 72；当前源码](../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_matrix_source_state/1.21.1-neoforge) | [状态安全复用候选 PASS 18/18 含本维度](../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_structures_source_state/1.21.1-neoforge) | [状态安全复用候选 PASS 18/18；72 独立 JVM/save；默认关闭](../../validations/integration-world_identity/result/structures-shared-handoff-ring-scope-narrow-locks-source-reuse-source_state/1.21.1-neoforge) | [复用候选 49 FULL/自然 ticking/重载 PASS；出生/往返待验](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/source-reuse/runtime-proof-frontier/minecraft-overworld/source_state)；[真实 LIGHT 冷载 PASS](../research/experiment-legacy_cold_spawn.md) | [32 请求去重 PASS；复用候选待验](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/physical-handoff/minecraft-overworld/lazy-rings/guarded) | [三组 180 秒：FULL/send 为 Vanilla 19.05%/14.04%；p95/内存未收口，默认关闭](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/reuse-paired-summary.json) | [待正式验收](../../validations/compatibility-bop/) |
| 1.21.1-neoforge | 下界 | [状态安全复用 PASS 16/16；联合总 72；当前源码](../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_matrix_source_state/1.21.1-neoforge) | [状态安全复用候选 PASS 18/18 含本维度](../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_structures_source_state/1.21.1-neoforge) | [状态安全复用候选 PASS 18/18；72 独立 JVM/save；默认关闭](../../validations/integration-world_identity/result/structures-shared-handoff-ring-scope-narrow-locks-source-reuse-source_state/1.21.1-neoforge) | [复用候选 49 FULL/自然 ticking/重载 PASS；出生/往返待验](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/source-reuse/runtime-proof-frontier/minecraft-the_nether/source_state) | [32 去重、相邻 pair/3×3 PASS；复用候选待验](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/physical-handoff/minecraft-the_nether/lazy-rings/guarded) | [待单实例配对；无可靠比值](../../validations/performance-worldgen) | [待正式验收](../../validations/compatibility-bop/) |
| 1.21.1-neoforge | 末地 | [状态安全复用 PASS 16/16；联合总 72；当前源码](../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_matrix_source_state/1.21.1-neoforge) | [状态安全复用候选 PASS 18/18 含本维度](../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_structures_source_state/1.21.1-neoforge) | [状态安全复用候选 PASS 18/18；72 独立 JVM/save；默认关闭](../../validations/integration-world_identity/result/structures-shared-handoff-ring-scope-narrow-locks-source-reuse-source_state/1.21.1-neoforge) | [复用候选 49 FULL/自然 ticking/重载 PASS；出生/往返待验](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/source-reuse/runtime-proof-frontier/minecraft-the_end/source_state) | [32 去重、相邻 pair/3×3 PASS；复用候选待验](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/physical-handoff/minecraft-the_end/lazy-rings/guarded) | [待单实例配对；无可靠比值](../../validations/performance-worldgen) | [待正式验收](../../validations/compatibility-bop/) |
| 1.20.1-forge | 主世界 | [R2 PASS 40/40；T2 完整 72 待验](../research/experiment-legacy_ring_matrix.md) | [T2 PASS 15/15 六阶段总套件；1EA1 输入](../../validations/conformance-legacy_terrain/result/narrow_locks_ring_scope_structures_rotation_safe/1.20.1-forge) | [T2 PASS 15/15 交接；60 独立 JVM/save](../../validations/conformance-legacy_b/result/structures-handoff-ring-scope-narrow-locks-rotation_safe/1.20.1-forge) | [默认 B 4578：300 s 未完成 FULL，未通过；Vanilla 两角色 PASS](../research/experiment-forge_native_runtime.md) | [T2 冷结构组件 PASS；完整 B 并发待验](../research/experiment-forge_cold_structure_locks.md) | [T2 60 s FULL/send 为原版 0%/0%；筛选不保留，停止追加优化](../research/experiment-forge_narrow_frontier.md) | [待正式验收](../../validations/compatibility-bop/) |
| 1.20.1-forge | 下界 | [R2 PASS 16/16；T2 完整 72 待验](../research/experiment-legacy_ring_matrix.md) | [T2 PASS 15/15 六阶段总套件；含本维度](../../validations/conformance-legacy_terrain/result/narrow_locks_ring_scope_structures_rotation_safe/1.20.1-forge) | [T2 PASS 15/15 交接；60 独立 JVM/save](../../validations/conformance-legacy_b/result/structures-handoff-ring-scope-narrow-locks-rotation_safe/1.20.1-forge) | [4578 自然 tick/重载 PASS](../../validations/integration-legacy_physical/result/runtime_current/1.20.1-forge/b/minecraft-the_nether)；[DB8B 半成品冷恢复 PASS；出生待验](../research/experiment-forge_partial_recovery.md) | [C6E6 五 owner 顺序/共同等待 PASS；正例/运行期待验](../research/experiment-forge_target_order.md)；[C163 同目标 32 去重 PASS](../research/experiment-forge_duplicate_requests.md) | [8/8 默认 B：60 s FULL/send 为原版 0%/0%；输入 1EA1](../research/experiment-forge_dimension_frontiers.md) | [待正式验收](../../validations/compatibility-bop/) |
| 1.20.1-forge | 末地 | [R2 PASS 16/16；T2 完整 72 待验](../research/experiment-legacy_ring_matrix.md) | [T2 PASS 15/15 六阶段总套件；含本维度](../../validations/conformance-legacy_terrain/result/narrow_locks_ring_scope_structures_rotation_safe/1.20.1-forge) | [T2 PASS 15/15 交接；60 独立 JVM/save](../../validations/conformance-legacy_b/result/structures-handoff-ring-scope-narrow-locks-rotation_safe/1.20.1-forge) | [4578 自然 tick/重载 PASS](../../validations/integration-legacy_physical/result/runtime_current/1.20.1-forge/b/minecraft-the_end)；[DB8B 半成品冷恢复 PASS；出生待验](../research/experiment-forge_partial_recovery.md) | [C6E6 五 owner 顺序/共同等待 PASS；正例/运行期待验](../research/experiment-forge_target_order.md)；[C163 同目标 32 去重 PASS](../research/experiment-forge_duplicate_requests.md) | [8/8 默认 B：60 s FULL/send 为原版 0%/0%；输入 1EA1](../research/experiment-forge_dimension_frontiers.md) | [待正式验收](../../validations/compatibility-bop/) |

1.20.1 中断证据在 conformance-legacy_b/result/interrupted/structures/1.20.1-forge 保全，不计通过或失败；完整十五项已重新执行并 CheckOnly。1.21.1 状态安全复用候选在当前 C146 输入下已完成 72 项六阶段独立 Oracle（40 主世界、16 下界、16 末地），144 个独立 JVM/save 与当前源码指纹复核 PASS，见[完整矩阵与证据绑定](../research/experiment-legacy_reuse_matrix.md)。结构完整 B 的十八项证据仍限定其对应指纹；候选默认关闭，不能把正确性通过提升为性能保留或 Target 收口。

26.3 Fabric 的 ACCC 原版矩阵／结构／独立 B、3B48 schema-1、CF39 schema-2 及 71F7/D6C8 模块顺序证据均保留原指纹。[all_quart_biomes](../research/experiment-fabric_owner_biomes.md) 已通过三维度全 owner stored biome、服务器读取一致性与重载；原缺顶层批次不升级为通过。[模块结构来源对照](../research/experiment-fabric_structure_order.md)和 05FD 全 src 的[普通生产请求](../research/experiment-fabric_production_requests.md)分别补齐完整实体／顺序／宿主，以及三维度 32 去重／pair／3×3。[自然激活与保存](../research/experiment-fabric_natural_activation.md)在 86DB probe 输入完成主世界／末地四正例、八独立 JVM：两个实体、二十二个 BE（含两个真正 pending）、原生安装与重载；设施不充当自然正例，原生 DUMMY 激活分支按本版实际规则核对。下界[自然激活](../research/investigation-fabric_nether_entities.md)与[猪灵顺序因果／四角色重验](../research/experiment-fabric_piglin_attribute_order.md)均已完成各自门禁：有限例外由使用者批准，严格哈希仍记录两种原生次序，原始失败不倒填。[MCP 真实玩家门禁](../research/experiment-fabric_client_lifecycle.md)已通过原生出生／重生、命令维度往返、保存重进与独立客户端冷重载，当前性能审查已按无安全候选止损，真实传送门流程仍待验。变化仅限 probe、工具与验收文档，生成算法未改；未受影响门禁不重跑，其它 Target 不继承本适配。[端口失败](../research/incident-fabric_validation_ports.md)、[探针边界审计](../research/incident-fabric_biome_quart_probe.md)和[原版矩阵实验](../research/experiment-fabric_state_oracle.md)均保留各自范围。

### 恢复后的三步

1. 补 NeoForge 内容顺序／多 session 并发、自然实体／BE 正例保存与玩家生命周期；[普通 FULL 请求九组](../research/experiment-neoforge_native_requests.md)已完成，不重复执行或提升其范围。其[三档基线](../research/experiment-neoforge_state_frontier.md)与[两次有界审查](../research/experiment-neoforge_source_state_review.md)已完成，无新合格候选而停止追加优化，不提升为 Target 收口。两端 26.3 的[完整高度结构门禁](../research/experiment-modern_structure_height.md)已通过；NeoForge 7587 输入的 72 项、十九项完整 B 和三维度十二 JVM 运行重载仍保持独立指纹与范围。
2. 补旧版正例副作用并发、出生/往返与更多布局。1.20.1 的 C6E6 五目标顺序/共同等待、C163 同目标 32 去重、DB8B 半成品冷恢复和 4578 自然运行期分别保持其证据范围；主世界原 300 秒失败仍未通过。H120-R/T 及 H121-U 均已到止损边界，候选关闭，不追加第三轮缓存、缩锁或延长窗口；1.21.1 当前 72 与自然运行期证据不代替尚缺世界功能。
3. 补两端 26.3 的真实出生／往返剩余路径，再独立审计 26.1.2 的原生气候状态入口／出口；本版坐标复用尚未建立这项守卫，不能从旧代表矩阵宣称安全，未证明前不提升其快路径验收。参考 Fabric 与 NeoForge 当前性能均已按两审查停止，不追加同类缓存。五 Target 原版门禁完成后才开始正式 BOP，不把已有有限样本当作任意入口状态保证。

### 兼容与性能计量边界

1.20.1 当前输入 C6E6 只新增本版多目标观测 probe，没有改变生产生成算法；[默认 eager 四项独立六阶段](../../validations/conformance-legacy_terrain/result/legacy_terrain_smoke_held_targets_probe/1.20.1-forge)与下界/末地五目标请求已通过当前源码只读复核。32 同目标的 C163、半成品冷恢复的 DB8B、自然运行期的 4578、完整 72 的 29CF、结构/完整 B 的 1EA1 与性能基线分别保留其指纹，不改写为新增 probe 后的全矩阵；正式 Target 收口时再跑完整矩阵。历史性能结果的只读复核必须显式声明原输入，不能按旧指纹重新生成。

版本专属快路径仅在该 Target 的独立 Oracle 已证明且能力检查通过时启用；未证明的 Target/模组组合默认关闭，保留精确原生回退。不得用某 Target 的 PASS 推定另一 Target 或 BOP PASS。进入正式 BOP 阶段前逐项复核此边界。MCP 安装也会触发部分保守回退，性能计量需遵守[接入边界](../development/compatibility-mcp_alpha.md)，不将客户端生命周期耗时混作原版模组集合的性能基线。

26.3 Fabric 的[原生环受控续流](../research/experiment-fabric_ring_continuation.md)在 2C7E main/probe 输入通过：两个独立 JVM、每个十六组环位置／根随机续流和 23120 次真实群系读取无差异，关闭探针的原生气候回归也通过。完整环／Lazy 的 worker 叶节点出口确实不同，不能称为隐式状态完全保持；自然交错与第三方参数表不由此证据覆盖，不改变现有 Stronghold 算法或新增任何验收例外。后续先补实际生命周期缺口，不继续用重复有限采样伪装普遍证明。

26.3 已核实 getMaxY 包含最高有效方块；全量群系探针已按真实 section ×64 校验。部分 Overlay／locator 仍用 getMaxY - 1，最高层结构投影是否遗漏列入有限世界功能门禁，尚未改动或宣称通过；A/C 不推进。原始调查见[边界审计](../research/incident-fabric_biome_quart_probe.md)。

每项保留优化和每个 Target/维度收口均记录 FULL/send-ready 吞吐及 send-ready p50/p95 相对同条件 Vanilla 的百分比。吞吐越高越好；延迟百分比越低越好。没有可比原版控制时标待测，不补造数字。参考 Fabric 当前输入 20B2 已完成三维度 8/8、12/12、16/16 的十八个独立 JVM 对照；8/8 为 60 秒，其余为 45 秒短测。每个组合仍只有一对基线，不是优化保留或 Target 收口，CLI send-ready 不是实际客户端已收到或已渲染。历史 H1 收益仅按其研究记录解释，不提升为当前性能。

| 26.3-fabric 当前 8/8 | FULL 吞吐 / Vanilla | send-ready 吞吐 / Vanilla | send-ready p50 / Vanilla | send-ready p95 / Vanilla | 证据目录 |
| --- | --- | --- | --- | --- | --- |
| 主世界 | 52.35% | 47.11% | 515.34–604.96%（截尾区间） | ≥469.57%（无有限上界） | [当前完整保留请求](../../validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier/vanilla-comparison.json) |
| 下界 | 91.99% | 91.29% | 244.89%（10.27 / 4.19 s） | 241.95%（17.75 / 7.33 s） | [当前完整保留请求](../../validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier/vanilla-comparison.json) |
| 末地 | 99.93% | 99.93% | 212.32%（4.41 / 2.08 s） | 206.69%（6.78 / 3.28 s） | [当前完整保留请求](../../validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier/vanilla-comparison.json) |

本表按实际窗口归一化，延迟保留原生最终视野全部请求。主世界仍有 83/289 未就绪，不能仅取已完成子集报告 p95。12/12、16/16 及主世界 21.28 秒中心关键路径见[当前性能实验](../research/experiment-fabric_current_frontier.md)。主要差距在累计结构／地形／邻区查询与前沿等待，P0 文件检查和 SPAWN 携带快路径未退化；锁累计阻塞不等于主线程墙钟，尚无新候选通过保留门槛。有限需求窗口接近清空时，接近 100% 完成吞吐不代表最大算力相当。

26.3 NeoForge 状态守卫后的[三维度三档基线](../research/experiment-neoforge_state_frontier.md)已在 7172 输入完成十八组独立 JVM／world；各组均使用四 CPU、BelowNormal、单实例和相同固定轨迹。8/8 为 60 秒，12/12、16/16 为 45 秒短测；下表只表示各一对基线，不作优化保留结论。新增的只读历史诊断在 probe 内默认关闭，诊断输入另存，不把其吞吐和基线混合。

| 26.3-neoforge 当前 8/8 | FULL 吞吐 / Vanilla | send-ready 吞吐 / Vanilla | 当前视野 send-ready p50 / Vanilla | 当前视野 send-ready p95 / Vanilla |
| --- | --- | --- | --- | --- |
| 主世界 | 67.96% | 64.73% | 525.12–544.52%（截尾区间） | 546.77–546.90%（截尾区间） |
| 下界 | 91.95% | 91.25% | 261.44% | 250.95% |
| 末地 | 99.97% | 99.97% | 259.25% | 238.96% |

数值取自[当前六组严格归并](../../validations/performance-worldgen/result/b_milestone/26.3-neoforge/climate_state_frontier/vanilla-comparison.json)，保留完整原生需求与截尾，不只选已完成样本。主世界中心 5.074 秒完成 preparation、19.159 秒发送就绪，其中 canonical 约 4.479 秒；大段等待仍在其余物理前沿依赖，不是 LIGHT 自身的 0.098 秒计算。主世界采样约 3.45 核，结构 CPU 31.86%、Noise/Surface/Biomes 27.94%、FEATURES 23.36%；GC 暂停累计 1.462 秒、最大 28.7 ms，模板锁累计等待约 67 ms，不能称为本组首要阻塞。身份文件检查仅生命周期两次，SPAWN 357 次均走携带快路径，无重建回退。

12/12 主世界最终视野只就绪 152/625，send-ready 吞吐为原版 19.40%；16/16 为 161/1089、12.43%，均保留未完成样本。下界三档为 91.25%/90.04%/77.50%，末地为 99.97%/99.91%/97.29%，对应完整延迟区间见[基线记录](../research/experiment-neoforge_state_frontier.md)。不能从有限前沿完成数推定最大性能追平。

NeoForge 的[有界筛选](../research/experiment-neoforge_source_state_review.md)已完成：两入口重访对应主世界约 9.38 秒、下界 1.39 秒累计 supplier 墙钟，末地为零；观察器 close 与真实求值逐项一致，关闭控制没有任何诊断计数。重复次数不能推导为端到端收益，额外多版本结构图的内存和 handoff 等价仍需证明；本轮不实施生产缓存。密度支持与锁能力审查各不足 1% CPU，批处理构造包含真正原生工作。两项审查未产生合格安全候选，按停止规则转正确性／生命周期，不宣称已经达到理论性能极限。

守卫之前 B9ED 输入的比值和关键路径仅保留在[历史性能调查](../research/experiment-neoforge_native_frontier.md)，不当作状态正确性修复后的性能证明。高视距、三组候选保留及停止审查仍分别验收；有限前沿接近清空时，完成吞吐接近 100% 不代表最大生成算力相当。

## 目标

完成五个主验收 Target 的原版 B 正确性、可收敛的性能优化，再完成官方可用 BOP 组合的相同工作，最后修复已有世界命令与运行功能，交付自动化验收。目标是尽可能接近同条件原版性能，不以“追平原版”作为无限优化的理由。

## 范围

主验收 Target 按当前模板 2.0.2 固定为下表；版本事实只定义在各自 target.properties。26.2 Fabric、旧实例、源码、研究和原始失败继续保留，不算这五个，也不强行迁移旧世界。

| Target | 本轮范围 |
| --- | --- |
| 26.3-fabric | 当前参考实现；先完成原版基线和优化 |
| 26.3-neoforge | 首个完整源码适配；按本加载器验证 |
| 26.1.2-neoforge | 独立适配本版世界生成与生命周期 |
| 1.21.1-neoforge | 独立适配本版世界生成与生命周期 |
| 1.20.1-forge | 独立适配本版世界生成与生命周期 |

保留 Seed V1、master seed 权威、Mosaic format V2 和已存在的 Ordering 1/2 存档语义。B 仍只运行目标一次 FEATURES、丢弃全部越界生成副作用、使用同 S(C) 的装饰前读取；FEATURES 后正常运行期看到真实 physical neighbors。不把九 writer Vanilla 装饰结果当作 B 的必须输出，也不把 B 的必要切断当作任意数据差异的豁免。

允许各 Target 有独立、有能力检查且可关闭的快路径；不能证明的配置精确回退。命中后的原版结构算法、stored palette 与缓存采样语义继续保持。不同 Target 不共享 Minecraft 相关源码，不假设 26.3 的 Mixin、缓存布局或数值路径在其它版本成立。

A/C 源码和证据保留但不推进。不引入 C2ME、Preview、新 WorldPreset 或新玩法；不扩展未生成区域全局 locate。已有结构索引与 Overlay 语义不擅自改变。保留脏工作树；不暂存、提交、推送、创建分支或 tag。

## 步骤

1. **完成**：登记四个新 Target、核对上游 Loader 和 Java，启动 26.3 NeoForge 代码同步，四个入口均已编译。此项只完成接入；源码适配、Oracle、生命周期与性能收敛分别验收，空壳或编译通过不等于可用。
2. **进行中**：参考 Target 当前门禁见顶部矩阵。原十九项独立 B、装饰前／结构、区域运行重载及并发已有对应范围证据，三维度自然正例保存分别通过。新增下界堡垒四角色完整 B 在获批猪灵属性换序有限验收下通过，canonical 与生产排序不变；真实客户端出生／重生、命令维度往返与冷重载已通过限定门禁，当前性能审查已止损，真实传送门仍待验。H1 的 9.16% 为历史输入收益，不提升为当前性能保留，不把 CLI send-ready 当作玩家或渲染完成。
3. **进行中**：顺序完成其余四个 Target 的源码与探针适配。26.3 NeoForge 已有本加载器 72 项装饰前、19 项完整 B 和四个物理持久化场景，三档基线与两审查性能止损完成，仍欠完整生命周期／并发与世界功能；26.1.2 已有独立地形矩阵、结构与 18 项 B Artifact 交接、三维度 FULL 保存重载及并发/patch，继续更完整持久化/运行期与性能；1.21.1 的装饰前、结构 B 与脱离式交接已有对应指纹的独立证据，物理 FULL/save/reload 与三维度同目标去重已通过，自然运行期仍分别验收；1.20.1 状态安全有限环候选的装饰前矩阵、完整结构 B 交接及三维度实际 FULL/save/reload 已通过，默认关闭、结构专项和运行期/性能仍待收口。七类结构按版本实际内容覆盖，不为 1.20.1 虚构 Trial Chambers。
4. **进行中**：逐 Target、逐维度执行同条件 Vanilla/B 自动性能对照；参考端三维度三档十八组基线与两次有界热点审查已完成，无新合格候选而止损，不将其解释为性能追平或 Target 收口。主世界吞吐和高视距仍明显不足；其余 Target 各自审查，历史 H1 不作为当前保留依据。完成五 Target 原版门禁后才进入 BOP。
5. **待办**：核对每 Target 官方 BOP/TerraBlender/必要依赖组合，执行同样的独立 Oracle、宿主隔离、生命周期和性能收敛。优先修复通用上下文/能力边界，不按注册名堆叠特例；上游无发行时保留不支持，不造兼容结论。
6. **待办**：在前两项基础门禁通过后，修复有限世界功能清单：/seed 世界及当前 Chunk 种子、/locate 已生成索引及范围诊断、出生/重生、三维度往返、实体/BE/ticks/light、保存退出/重入和修改保留。只修实际缺陷，不借“其它原版功能”无限扩充范围。
7. **待办**：运行所有 Target 的格式、构建、发行、原版/BOP 当前回归；汇总通过、明确不支持、未解释与性能限制。仍有当前 correctness 失败或目标缺实现时不得整体 PASS，完成后交付而非继续寻找新优化。

## 正确性门禁

- 每 Target 至少 72 个代表装饰前样本，覆盖主世界原点/非原点、下界、末地、洞穴、表面、stored-biome/cache，以及本版存在的 Village、Mineshaft、Trial Chambers、Monument、Fortress、End City、Stronghold；包含邻区 start、跨 Chunk pieces、Beardifier/地形适配与无结构样本。
- “原版各类生成”按各版本默认数据包实际可达的生成家族建立有限清单，覆盖树木/植被、矿物、流体/湖、洞穴装饰、表面替换、下界/末地特有装饰及结构与副作用正例；未命中项明确列出，不把抽样证明成所有种子保证，也不随未来上游新增内容无限扩表。默认生成不可达项用源码/数据证明边界，不人工插入副作用充数。
- 对 B 使用同条件原生目标单 writer 控制，完整比较目标方块、biomes、heightmaps、结构、实体、BE/pending NBT、ticks、后处理与状态；逐项核验越界拒绝，不以计数接近代替内容相同。
- 相同输入/顺序/数值条件必须严格一致；差异追到最早输入、读取、随机消费和分支，再双向重现。只沿用已批准的有限原生随机例外，不自动扩大忽略字段或改固定排序。
- 历史运行缺少的读取/RNG 链不能补造，原失败继续保留；新验收以当次完整输入和因果证据为准，不把旧缺链无限转化为必须重建的过去执行，也不据新证据倒填历史 PASS。
- 正常 Vanilla 零侵入；宿主/顺序/并发隔离、重复请求去重、POI 线程归属、预期关闭取消、真实错误 fail-fast 和 session 清理均须回归。
- 保存、重启、重载后 FULL Chunk 不重新生成 Artifact/SPAWN，玩家修改、结构/Overlay/locate 索引、POI 和运行期实体/BE/ticks 保持。
- 每个快路径分别记录支持条件与回退命中；上游或 datapack 改变后，不以假空气、近似高度、master-seed 邻区或异常吞掉冒充等价。

## 性能与停止规则

所有测量由游戏内 probe 自动创建专用世界、发起真实 physical 请求并采样，不要求使用者操作游戏。只读 CLI 前沿不能冒充玩家出生、发送或渲染完成；没有对应自动探针时明确未测。禁止 Computer-Use。只关闭/重启已用命令行、实例目录和启动记录核实属于本线程的游戏，优先正常保存关闭。

默认四逻辑 CPU、低优先级、单个重负载 JVM，Gradle 最多两个 worker。A/B 使用相同资源、种子、轨迹、维度、视距/模拟距离和 JVM；以 8/8 固定负载为主，短测 12/12 与 16/16，不长时间吃满整机 CPU。不修改生产线程数或调度语义来制造收益。

记录 canonical/FULL/send-ready 吞吐、p50/p95、队列/实际并发、CPU、锁、GC/分配、堆、主线程 stall 和最慢请求关键路径。模块计时与 request→ready 分开报告；只能在可靠观测时记录 player-ready/packet。原版与 B 内容差别、各版本性能和受限/正常资源结果分开解释。

每项优化先登记“热点、假设、预期、否证条件、实验次数”；最多两轮实现/实验无因果进展就放弃该假设，不反复换名重试。先一次筛选，有希望才运行至少三组交错配对 A/B；对照不能改变调度或正确性合同。

保留优化需主要端到端指标中位数改善至少约 5%、超出重复波动，并且正确性不退化、p95/内存没有稳定恶化。小于此收益且增加副作用风险时不合入。每轮热点审查只处理当前关键路径前三项，不无限扩充候选；连续两轮都找不到满足门槛的安全候选，即结束该 Target/组合的优化，记录剩余必要成本并转下一门禁。正确性错误不能因收益低而豁免。

遇到上游缺失、需要新增权限或冻结合同冲突时，穷尽安全的范围内检查并记录具体 blocker；不偷偷改合同、不把不支持算通过。每次推进更新当前证据与下一项，不无限增加覆盖组合或扩张功能清单。

## 结果

当前整体未完成。各 Target × 维度 × 门禁的状态、数量和直接证据入口只在顶部矩阵维护；不能把模块通过、历史源码指纹或普通 Vanilla 控制提升为当前完整产品验收。

### 各 Target 当前边界

| Target | 当前结论 | 尚缺门禁 |
| --- | --- | --- |
| 26.3-fabric | 原装饰前／十九项 B、并发／运行期及三维度自然正例保存见顶部矩阵；新增堡垒 BC19 四角色有限验收 PASS，生成算法未改 | 猪灵次序已完成有效双向因果与获批有限规则，严格哈希差异及原失败保留；出生/往返和性能未收口 |
| 26.3-neoforge | 独立装饰前、结构、完整 B、四个持久化目标及三维度运行期/重载通过；28DA 输入普通 FULL 请求九组、逐 owner 一次交接通过 | 自然实体/BE 正例、出生/往返、内容顺序/多 session 并发、世界功能收口；本版性能按两次审查停止，不借用 Fabric 证明 |
| 26.1.2-neoforge | 原生与轻量装饰前、结构 B/脱离式交接及主世界部分并发有对应范围证据；加强普通 Vanilla/Mosaic 三维度自然 tick、实际 BIOMES、实体/流体跨界、熔炉与 fresh/reload 均通过 | 完整生成副作用持久化、出生/重生、维度往返、其余并发与性能收口；运行期测试设施不替代自然生成正例 |
| 1.21.1-neoforge | 当前源码 72 项、模块、三维度单 FULL/reload 与 32 去重通过；状态安全复用候选三维度自然运行期/重载与真实 LIGHT 冷载通过；候选默认关闭 | 候选并发/顺序、出生/往返、性能保留门槛、完整持久化与世界功能 |
| 1.20.1-forge | 状态安全有限环候选的 72 项装饰前、十五项结构六阶段和完整 B/Artifact 交接与三维度 Vanilla/B FULL/保存重载已验，默认仍关闭 | 三维度自然 ticking、并发、出生/世界功能与性能未收口；默认路径原预算失败保留，不宣称可游玩 |

BOP 尚未进入正式组合验收。既有装饰前与末地因果调查只证明对应历史范围；原始失败中缺少完整因果链的部分继续保持未解释，不固定生产排序，也不放宽 B 合同。26.2 与旧存档保持原有语义，不自动获得新 B 能力。

1.20.1 的[完整交接实验](../research/experiment-legacy_artifact_structures.md)对应 2B59B439AF8A15D8AF00CD57FA304317850588E9091BB0B60F71B0F485CDC5DF：十五项结构、60 个独立 JVM/save 与独立 CheckOnly 通过，包含 30 个 owner 关闭后的消费者、真实 BE/pending NBT、实体、ticks、后处理及结构本地 seed 加载。当前[下界/末地原生 FULL](../research/experiment-legacy_native_physical.md)另有实际物理证据，下界的取消恢复证据位于顶部矩阵；它们不代替主世界、自然 ticking、未知模组和性能验收，也不把模块结果提升为完整可游玩结论。

26.1.2 的加强 RuntimeProof 普通 Vanilla/Mosaic 三维度 fresh/reload 共十二个独立 JVM 已通过：全部 49 个 FULL 目标检查实际 BIOMES 输入，fresh 取生成图共 2401 次、reload 取加载图共 441 次，保留 220 个自然 tick、实体跨界、block/fluid callback 与熔炉烧炼检查；重载 canonical/Artifact/SPAWN 均为零、标记保持、in-flight 与绑定清空。[物理 BIOMES 缺口与主世界修复复验](../research/incident-physical_biome_readiness_2612.md)和[错误观察边界的纠正](../research/incident-loading_dependency_observer.md)保留，原始 FAIL 不倒改。生产计划已把原生 BIOMES 及后续依赖纳入精确数据义务；本 Target 的部分 BIOMES 尚未独立证明，保守提供完整 Artifact，不冒用其它 Target 的快路径。三维度 fresh 各捕获 218 个 Artifact，完成整个运行期探针分别约 116.49、24.78、19.78 秒；这是正确性负载而非配对吞吐或客户端测量。加强结果在 integration-production_lifecycle/result/current/26.1.2-neoforge/phase3c2-production 中各自的 runtime-proof-frontier；初始 runtime-proof 证据保留但不替代增强范围。

### 当前未通过项与证据解释

1.20.1 默认 B 的[下界/末地五目标请求](../research/experiment-forge_target_order.md)在 C6E6 输入通过：正序、逆序和五请求共同等待的 Artifact payload、pending/实例化 sidecar、物理投影完全相同，二十四个独立游戏 JVM 的保存重载、五个 local-seed palette 与阶段次数通过。并发组生成七十七个独立依赖，顺序组四十九个，没有同 owner 重复生成；最终结果独立不等于请求工作量独立。C163 的[同目标 32 请求去重](../research/experiment-forge_duplicate_requests.md)保持对应输入。实体/BlockEntity 正例、自然运行期并发、出生/往返与主世界仍待验，不把 FULL 请求共同等待提升为五个 session 同时执行或性能改善。

1.20.1 下界/末地的[装饰后半成品恢复](../research/experiment-forge_partial_recovery.md)已通过：真实原生 INITIALIZE_LIGHT 保存，两个维度共十二个独立 Vanilla/B JVM，恢复时目标 Artifact 为零、SPAWN 一次，最终 FULL 重载均为零生成。B 的全部方块、stored biome、结构与标记保持，冷载八邻区群系与携带输入一致。普通 Vanilla 的后续邻区写入另按实际 section 状态链验证，不放宽 B；原始错误假设与观测缺口的失败保留。完整并发、出生、往返、冷碰撞组件与主世界未完成门禁仍未通过。

1.20.1 探针的[首错保留](../research/incident-forge_probe_failure_reporting.md)已通过当前源码的两个独立游戏负例；原始 FAIL 与停服 stopFailure 分列，不把观察失败、真实生成错误或原始主世界预算失败改记为通过。

1.21.1 有限环、缩锁与状态安全复用候选仍默认关闭。当前 C146 指纹的[三维度自然运行期/save/reload](../research/experiment-legacy_reuse_runtime.md)已通过独立只读检查：49 FULL 前沿与真实 220 tick、实体/流体跨界、熔炉和修改保存都通过。该结果只适用显式候选，不把未复用组合的[原预算失败](../research/experiment-legacy_narrow_lifecycle.md)倒填为通过，也不提升为 Target 或正式性能收口。

1.21.1 的[真实 LIGHT 关闭/冷恢复](../research/experiment-legacy_cold_spawn.md)已证明主动取消只完成一次、in-flight/worker 清空、目标恢复不重建；完整真实异常注入分离、首次出生和维度往返仍另验。普通 Vanilla 与 B 的三维度自然 tick 通过不替代出生验证。无玩家探针的原生实体休眠边界见[调查](../research/incident-empty_level_runtime.md)，不能用历史探针失败猜测生产实体注册失败。

guarded 证据中的三维度 32 请求各汇合到一个真实 LevelChunk，与各自 fresh 单请求的 Artifact/BIOMES/POI/LIGHT/SPAWN/FULL 计数完全一致，in-flight 均零；已由 Check-LegacyConcurrency 按其输入指纹核验独立 JVM 和条件。它证明该范围的同目标去重，不替代状态安全复用候选的并发、多个不同目标或顺序独立。

1.21.1 的 Mosaic 完成 handler 派发到原生主线程队列，且在 future 释放消费者前发布数据；部分 BIOMES 只在已证明环境启用。[下界完成边界控制](../research/incident-legacy_publication_race.md)已证明输入先完成、worldgen 后注册时十二次 holder 完成都在 Server thread、越权零且 FULL 成功；不提升为主世界、自然 tick 或并发全部通过。未启用环候选的主世界 FULL 仍未通过原预算，不把等待栈直接认定为死锁，也不靠延长超时通过。

1.21.1 的[身份生命周期与发行](../research/incident-legacy_identity_packaging.md)、下界/末地 FULL 保存重载及普通 Vanilla 三维度控制各有对应指纹证据，不能提升为当前全部运行期门禁。旧 AC6E 因果目录的 raw 不完整，不再作为可当前复核的完整证明，边界见[目录故障记录](../research/incident-causal_output_variant.md)；当前守卫的四 JVM 因果控制在独立 guarded 目录复验，不回填旧记录。

完整根 spotlessApply/check/verifyRelease 已执行通过，六个发行 JAR 的元数据、必需条目与 probe 排除均通过；[Forge 映射打包修复](../research/incident-legacy_refmap_packaging.md)不改变生成行为。严格合规与 Markdown 已通过，文档收尾再复核。构建不替代各 Target 当前源码的全矩阵、世界功能或性能收口；不作五 Target/BOP 全面兼容或原版级性能承诺。

### 当前性能审查

| H-F163-S 登记项 | 当前约束 |
| --- | --- |
| 热点 | 当前主世界结构 source 求值约 33.63 累计线程秒，结构 CPU 约 29.69%；单位置缓存只保留一份原生输入／出口状态 |
| 假设与实测 | 相同位置、相同原生 leaf 输入覆盖后再次出现；三维度 probe-only 的 2／4／16 状态计量完成。两状态重复 Supplier 墙钟为主世界 6.038 s、下界 1.126 s、末地零，不复用结构或改变原生状态 |
| 当前结论 | 主世界调用重复 22.72% 不等于端到端收益；十六状态只增加少量机会，尚无足够证据值得承担多 carrier／出口恢复风险。不实施生产缓存 |
| 否证 | 没有足够重复、收益上界低、历史模型与原生命中不一致、交接失效或关闭清理不完整；任何候选还须 Oracle 与端到端 ≥5%、p95／内存不退化 |
| 实验预算 | [诊断结束](../research/experiment-fabric_source_state_audit.md)，生产实现迭代 0/2；只开启显式 structureReuseAudit，全部原生求值继续执行，计数吻合、关闭清空。诊断吞吐不计作性能对照或优化保留 |

H-F163-P 的第二次有界审查已完成：两份独立 JFR 的结构保护能力检查与密度图审计各不到 1% CPU；Batch 构造器约 27% 为包含式栈，内部仍有真实 CompactStructureInputs／Beardifier、群系及原生 NoiseChunk 准备，不能当作可删除分配。没有新安全候选，停止当前 Fabric／原版组合追加性能工作；具体计量、未实现理由、输入指纹和单次波动边界均见[审查记录](../research/experiment-fabric_source_state_audit.md)。剩余性能比保持顶部基线，不用关闭诊断单次更快覆盖它，也不宣称已无任何理论优化空间。

当前参考端三维度、三档视距的单对基线及 Vanilla 比值见顶部“兼容与性能计量边界”，不计为优化保留；其它 Target／BOP 仍按各自门禁。H1 的历史三组改善只支持对应输入，不提升当前状态。1.21.1 的共享准备模块筛选计时不是 physical request→ready 或正式配对收益。

H121-R 有限环候选默认关闭。[当前守卫](../research/experiment-legacy_ring_guard.md)只在已证明环境绑定有限 scope，未知组合保持原生调用链；模块与显式候选运行期范围见矩阵。当前单实例配对仍未满足完整性能保留门槛，不把双队列正确性耗时当作正常探索延迟，也不把累计线程阻塞当作墙钟占比。候选机制及筛选证据由[有限环实验](../research/experiment-legacy_finite_rings.md)与[三组配对](../research/experiment-legacy_reuse_pairs.md)保留。

H121-T 保留本版审计过的共享状态保护方式，不减少 source、start、pieces 或密度输入；已覆盖[冷缓存并发与完整结构 B](../research/experiment-legacy_narrow_handoff.md)。单独缩锁不足以在主世界预算内准备好前沿，具体结论保留于[冷并发与前沿实验](../research/experiment-legacy_narrow_runtime.md)；结合状态安全复用的运行期门禁按当前矩阵单独成立。候选默认关闭、未知组合整段锁回退，算法边界见[锁审计](../research/investigation-legacy_template_locks.md)，不以吞吐收益豁免正确性。

1.21.1 当前三组配对中，未复用缩锁 B 在固定 180 秒窗口没有 FULL/send-ready 完成；状态安全复用组合的 FULL/send-ready 吞吐分别为 Vanilla 的 19.05%/14.04%。延迟尾部仍截尾，内存保留门禁未通过；Other Structures 与 Noise/Surface/Biomes 是主要 CPU 成本，Stronghold 已非第一热点。原生冷前沿的短窗口证据见[筛选记录](../research/experiment-legacy_native_frontier.md)，当前配对与资源观察见[完整记录](../research/experiment-legacy_reuse_pairs.md)。不延长观察窗口或把内部 source 数量减少当作优化保留。

| H120-R 登记项 | 当前约束 |
| --- | --- |
| 热点 | 1.20.1 主世界完整原生环搜索占首次 FULL 诊断 97.80% CPU 样本，重复同种子准备导致原 300 秒预算失败 |
| 假设 | 保留原生全部 slot、RNG/fork 和候选命中算法，仅在原生 supplier 的有限作用域跳过不可能进入保守查询范围的 biome search；不新增依赖，不更改其它结构 |
| 预期 | 普通目标的昂贵环查询显著减少，主世界普通 FULL 在原预算内完成；正确性通过后才做同资源端到端筛选及三组配对 |
| 否证 | 任一原生相关正例漏失、结构/密度/群系/地形/B 不等价、随机父流或续流不同、scope 泄漏、查询越域或未知环境未原生回退；性能仍须达到共同保留门槛 |
| 实验预算 | 第 2/2 轮：[状态修正组件](../research/experiment-legacy_ring_tail.md)通过 1536 正例/1000 普通目标及双向真实 leaf/续查，完整搜索 128000→34；当前 72 项、十五项完整 B 与三维度 FULL/保存重载通过。十五项结构六阶段通过；[60/180 秒前沿](../research/experiment-legacy_ring_frontier.md)两 B 均无完整输出，不满足端到端保留门槛；自然运行期待验，默认关闭，不做第三轮或借模块结果保留性能 |

| H120-T 登记项 | 当前约束 |
| --- | --- |
| 热点 | 本版 180 秒前沿，有限环组记录约 4250 线程秒等待共享模板管理器，150 个 worker 在途仍无完整 B；累计阻塞不是墙钟占比 |
| 假设 | 按 1.20.1 的真实缓存与原生静态生成状态缩小保护范围：保留 palette HashMap、pool maxSize 与 Stronghold/Fortress/EndCity 的必要同步，未知配置保持整段锁；不改生成算法 |
| 预期 | 其它结构与地形工作不再被整个 createStructures 监视器串行化，并改善同资源 FULL/send-ready；不能仅凭内部 source 完成数保留 |
| 否证 | 原生结构/原始 NBT、地形/气候续流/B 不等价，冷缓存并发失败、异常泄锁、未知配置未回退或端到端收益不足 |
| 实验预算 | 第 2/2 轮结束：第一轮[冷并发失败](../research/incident-forge_rotation_cache.md)保留；当前缓存完整可见性、异常清理与启动能力边界的[冷组件](../research/experiment-forge_cold_structure_locks.md)、四项 smoke、十五项结构六阶段及[十五项完整 B/交接](../research/experiment-forge_narrow_b.md)通过，输入 1EA1 只读复核 PASS。[四组单实例筛选](../research/experiment-forge_narrow_frontier.md)中共享模板管理器长阻塞消失于采样，但 canonical/FULL/send 仍为零；不满足端到端保留门槛，不做三组配对、第三轮算法或延长窗口。与 H120-R 构成连续两次无合格候选，停止本组合追加优化；候选默认关闭，未完成正确性/运行期门禁不提升 |

| H121-U 登记项 | 当前约束 |
| --- | --- |
| 热点 | 一个 B 准备内重复解析相同 S(C)/配置/source 的 starts 与空结果；缩锁后仍大量进入原生结构和高度计算 |
| 假设 | 生命周期内复用已完成的精确元数据，目标可变图先独占移交，不将整套 session 留到 FULL，不跨 target/seed/world 共享 |
| 预期 | 重复 source 求值显著减少，并使原有冷前沿出现可辨别的端到端收益；不能只用完成更多内部解析通过 |
| 否证 | starts/pieces/references/Beardifier/群系/地形/B 结果不等价、邻区被目标放置污染、资源残留或无主要端到端收益；未知配置必须原生回退 |
| 实验预算 | 第 2/2 轮实现预算结束；[三组配对](../research/experiment-legacy_reuse_pairs.md)的 p50/p95 仍截尾、内存未收口，不满足保留门禁；不增加第三轮缓存算法或延长窗口。query owner 半径 2、source 联集最多 441 坐标、外圈精确回退；冷载与三维度自然运行期已通过，当前源码 72 项通过；候选并发/世界功能仍待验，候选默认关闭 |

当前 C146 输入的[六阶段完整 72 项](../research/experiment-legacy_reuse_matrix.md)与[三维度自然运行期](../research/experiment-legacy_reuse_runtime.md)通过；完整结构 B 的严格证明限定于矩阵所链接的 8332 输入，目标可变图独占、作用域关闭及气候入口/出口续流均有对应证据。未知 biome source 或缺失适配入口继续原生回退。当前尚缺候选并发与世界功能，不用已完成门禁替代缺项，也不因性能未保留而重启第三轮缓存算法。原生组件反例见[缓存状态故障分析](../research/incident-legacy_source_climate_state.md)，所有权约束见[来源审计](../research/investigation-legacy_source_ownership.md)。

26.3 Fabric 的[原生组件反例与状态守卫](../research/incident-fabric_source_climate_state.md)已有双向/重复，当前独立矩阵与完整 B 结论见顶部矩阵。26.3 NeoForge 的[本版原生反例、状态守卫与四项独立预检](../research/incident-neoforge_source_climate_state.md)已通过对应范围，完整矩阵、结构、B、生命周期和性能仍各自验收。26.1.2 尚未完成同等级入口／出口适配；不从同版、代表矩阵或组件推定任意气候状态、datapack 或第三方配置安全。

### 研究与验证入口

逐轮记录已从本账本移至以下调查日期为 2026-10-16 的研究记录；其中“待验”“随后完成”等表述是当时观察，不覆盖本页当前矩阵。研究记录写成后保持原文，结论被采纳时更新本账本或设计文档。

- [多 Target 独立 Oracle 与初始适配调查](../research/investigation-b_multitarget_oracle.md)：装饰前、结构、B 与初始验证过程。
- [多 Target 准备复用实验](../research/experiment-b_multitarget_preparation.md)：H121-S、H1、有限原生随机因果与配对性能过程。
- [多 Target 生命周期接线故障记录](../research/incident-b_multitarget_lifecycle.md)：RuntimeProof、MixinExtras 接线、身份、关闭与物理就绪故障。
- [验证工具](../development/testing-worldgen.md)、[性能验证](../../validations/performance-worldgen/validation.md)与[BOP 验证](../../validations/compatibility-bop/validation.md)：运行方法及各自通过标准。
