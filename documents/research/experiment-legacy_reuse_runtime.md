# 1.21.1 状态安全来源复用的三维度运行期

调查日期：2026-10-17。默认关闭的状态安全来源复用候选通过三维度自然运行期及保存重载对照，十二个独立 JVM 经 Check-LegacyRuntime 复核；这不是性能保留或 Target 整体收口。

## 问题

结构输入与完整 B 的模块对照不能替代物理前沿就绪、真实跨区块模拟及保存重载。已有主世界缩锁组曾在原定 180 秒内未就绪，不能借其它维度通过消去这个缺口。

## 方法

输入指纹为 `C146E59F249A7186F3984CF4B97710635D3DE8E75EFD16BE3AC5CB90B5A22FB4`。主世界、下界、末地各运行显式 B 候选及普通 Vanilla，每项 fresh/save/reload 为两个独立 JVM。各游戏限四逻辑 CPU、低优先级，实例与世界按场景隔离；不延长原 180 秒，不修改来源缓存算法或调度器。

由原生区域票据驱动全部 49 个 FULL 与 ENTITY_TICKING 就绪，逐一检查 native generation/loading BIOMES 依赖，再观察至少 220 个自然 game tick。人为设施只用于实体、block/fluid tick 和熔炉运行期机制，不作为自然生成正例。

probe 在原有 preparation observer 的完成边界检查来源 scope 已关闭、驻留零、峰值不超过 441，并计复用命中。fresh 必须有实际复用，reload 不得重新 preparation；普通 Vanilla 的 Mosaic state 创建与路由均须零。

## 发现

| B 场景 | 完整探针墙钟（含自然 tick） | fresh Artifact | 复用命中 | 自然 SPAWN 邻区碰撞读取 |
| --- | --- | --- | --- | --- |
| 主世界 | 120.077 s | 121 | 2,408,083 | 2 |
| 下界 | 23.168 s | 121 | 2,633,620 | 0 |
| 末地 | 17.983 s | 121 | 2,779,006 | 0 |

三维度均有 49 FULL、49 SPAWN 和实际自然实体/流体跨 seed 边界、scheduled callback 与烧炼产物。自然模拟期间 Artifact 与 SPAWN 计数不增长，绑定清空，探针票据正常移除。三个 reload 均为 Artifact/preparation/SPAWN 零，修改方块保留，仍通过同样的自然回调。六个普通 Vanilla fresh/reload 均保持 Mosaic 创建及生成路由零。

证据在 [B 三维度根目录](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/source-reuse/runtime-proof-frontier/)及[普通 Vanilla 根目录](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/vanilla-control/runtime-proof-frontier/)，各维度固定子目录为 source_state。最后独立只读检查输出三维度、十二个不同游戏 PID 和当前输入指纹一致。

末地普通 Vanilla 首次 reload 被另一 Target 的构建配置错误阻止在 Gradle 配置阶段，尚未启动游戏。原始 launcher failure 和已完成的 fresh 结果保留在该场景 first_runtime_failure；修复延迟任务注册后，重新执行这一完整 fresh/reload。它不归类为 Vanilla 或 Mosaic 游戏崩溃，也未改写旧记录。

这些是正确性负载，期间还运行受限构建任务，不能用上述秒数计算配对性能比或宣称吞吐提升。性能仍以[单实例三组配对](experiment-legacy_reuse_pairs.md)为准：FULL/send 为 Vanilla 19.05%/14.04%，p95 截尾与内存仍未收口。

## 结论

本候选的三维度自然运行期/save/reload 门禁 PASS，主世界旧范围的就绪缺口在这一显式候选得到覆盖；旧失败保留。首次玩家出生、维度往返、完整并发/顺序及新源码 72 项仍另验。性能保留门槛未通过，默认关闭不变，H121-U 不开启第三轮缓存算法实验。
