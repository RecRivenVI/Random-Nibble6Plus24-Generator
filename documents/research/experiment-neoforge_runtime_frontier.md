# 26.3 NeoForge 原生运行期前沿

调查日期：2026-10-17。三维度普通 Vanilla/B 的 fresh/save/reload 共十二个独立 JVM 通过源码绑定的运行期门禁与只读复核。仅证明本范围的自然模拟与重载，不提升为首次玩家出生、维度往返、并发或性能收口。

## 问题

本 Target 的物化和持久化证据不足以证明真实票据推进、ENTITY_TICKING、跨区块实体/流体及 BlockEntity ticker。不能把 Fabric 或其它 Minecraft 版本的结果当作本加载器运行证据。

## 方法

对本机实际 `minecraft-patched-26.3.0.83.jar` 核对 ServerChunkCache 的加载/模拟票据、ServerLevel 的 block/fluid/entity callback、熔炉 ServerLevel ticker 和 holder 的状态接口。独立 probe 源码使用该 Target 原生 API，不共享 Minecraft 相关源码，也不手动驱动 generation/promotion/tick callback。

区域票据产生 49 个 FULL，目标及相邻 Chunk 必须当前为 ENTITY_TICKING、真实物理 LevelChunk 且光照正确。每个 owner 按原生 generation/loading pyramid 核查需要 BIOMES 的实际数据，再观察 220 个自然 tick、实体和流体跨 seed 边界、scheduled callback 与真实烧炼产物。测试设施明确不是自然生成正例，结束恢复设施并移除票据。

输入指纹 `B9EDFD499766306E5B7C3CBCFFE070825E06F50365429E02EC25C2C3890E2D08`；四逻辑 CPU、低优先级，实例、世界、端口独立。另一个正确性队列使用不同四 CPU 运行 1.21.1；本组不是性能测量。

## 发现

| B 场景 | 完整探针墙钟（含自然 tick） | fresh Artifact | BIOMES 检查 | reload Artifact/SPAWN |
| --- | --- | --- | --- | --- |
| 主世界 | 23.585 s | 232 | 2401 | 0 / 0 |
| 下界 | 14.833 s | 232 | 2401 | 0 / 0 |
| 末地 | 12.683 s | 232 | 2401 | 0 / 0 |

所有 reload 各检查 441 个 loading 依赖，修改方块保留，实体注册唯一，所有自然回调成功，generation/spawn 绑定及 in-flight 为零。普通 Vanilla 的 Artifact、计划、生成器路由与 B 生成计数均为零。Check-NeoRuntime 核对当前输入、十二个不同 PID、六份不同 run identity 和全部场景字段后 PASS。

证据分别在 [B runtime-proof-frontier](../../validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/features-2/development-b/runtime-proof-frontier/)与[普通 Vanilla 控制](../../validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/vanilla-control/runtime-proof-frontier/)，各维度子目录为 native_frontier。

首次移植编译发现本 Target 没有 Fabric 的 PhysicalBiomePreparation，删除对不存在模块的计数引用，不伪造零计数。首轮无标签场景的游戏完成，但独立复核因没有 conditions 指纹文件而拒绝，原始无标签数据保留；没有事后补写指纹，而是另建固定 native_frontier 场景完整重跑。Windows OSHI Perflib 009 的可选系统报告告警之后游戏继续正常运行，不归类为生成崩溃，也未修改系统注册表。

## 结论

本 Target 的三维度自然运行期/save/reload 门禁 PASS。尚未适配的部分 BIOMES 性能入口保持保守完整 Artifact 路径，不能由其它 Target 推定可用；232 个 Artifact 是实际数据义务的成本记录，不是吞吐数字。性能仍待同条件单实例 Vanilla 配对，当前不提供虚构比值。
