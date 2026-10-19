# 1.21.1 守卫后物理 FULL 与重载

调查日期：2026-10-16。当前守卫的三维度 B 与普通 Vanilla FULL/save/reload 共十二个独立 JVM/save 均通过；这是双队列正确性负载，不是正式吞吐或客户端性能测量。

## 问题

装饰前矩阵、结构输入与完整 B 通过后，仍须证明当前守卫能把真实物理依赖推进到 FULL，且重载不重新 canonical generation。不能把首次候选的物理结果回填到补强后的源码。

## 方法

输入指纹 07159AC5B3512A9DBC523AD26E3380BCEF443AB7953CD1C8DB6546BEAEC3D722。以 master 123456789、目标 (125,-37) 分别在主世界/下界/末地运行普通 ticket-backed FULL 请求，然后保存并以新 JVM 重载；测试修改一个方块检验是否保留。普通 Vanilla 使用相同 master、维度与目标，Mosaic 仍按本身规则使用各物理目标 S(C)。

场景固定为 guarded，同时隔离世界名称与结果目录，启动前由任务记录源码哈希和参数，不事后修改证据。每个游戏四逻辑 CPU、低优先级，与独立的 1.20.1 正确性队列并行；没有性能 profiler。沿用原 180 秒就绪预算，不提高 timeout、不逐阶段驱动、不改变 scheduler。

## 发现

- B 的 [主世界](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/physical-handoff/minecraft-overworld/lazy-rings/guarded)、[下界](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/physical-handoff/minecraft-the_nether/lazy-rings/guarded)、[末地](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/physical-handoff/minecraft-the_end/lazy-rings/guarded)六个 fresh/reload JVM 均 PASS，取得真实、光照正确的 LevelChunk。
- 每维度 fresh 均为 25 个 Artifact/发布、24 份部分 BIOMES/发布，实际检查 49 份生成群系数据，in-flight 为零。各有 POI 9 次及 Mosaic LIGHT/SPAWN/FULL 计数各 1；没有用空 palette、统一 master seed 邻区或未生成 placeholder。
- 三维度重载 Artifact、部分 BIOMES 准备与 SPAWN 均为零，读取 loading pyramid 的 9 份群系依赖，测试方块保持，in-flight 为零。光照加载计数允许按原版加载路径执行，不误称所有 lifecycle 回调都为零。
- [普通 Vanilla 主世界](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/vanilla-control/physical-handoff/minecraft-overworld/guarded)、[下界](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/vanilla-control/physical-handoff/minecraft-the_nether/guarded)、[末地](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/vanilla-control/physical-handoff/minecraft-the_end/guarded)另六个 JVM 均 PASS；Mosaic Artifact/SPAWN 计数为零，普通原版 SPAWN 本身没有被禁用。
- B 的单 FULL 就绪时间为主世界 109.183 s、下界 11.686 s、末地 13.671 s；重载分别约 239、289、178 ms。这是 2/2 正确性探针，且与另一 JVM 并行，不能据此计算正式 Vanilla 性能比或宣称达到正常探索吞吐。
- 结果仅证明 ChunkStatus.FULL 与对应 FullChunkStatus.FULL；没有证明 BLOCK_TICKING/ENTITY_TICKING、自然实体/BE/tick 激活、完整字段持久化、出生/重生或网络发送。并发去重和正式配对性能另验。

## 结论

守卫后真实物理 FULL/save/reload 的有限门禁通过，未发现再生成 Artifact 或覆盖测试修改。有限环候选仍默认关闭，性能保留须等单实例同条件 A/B 与 Vanilla 比值；其它生命周期范围不能从本结果推定通过。
