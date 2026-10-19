# 1.21.1 无玩家运行期探针的实体休眠

调查日期：2026-10-16。下界 B 的自然运行期测试因掉落物未移动而失败；实际字节码与保存数据表明，无玩家世界的实体休眠窗口已在冷生成期间耗尽。此记录不把失败改为通过，也不据此修改产品 ticking 行为。

## 问题

RuntimeProof 已等到 49 个真实 FULL、两侧 ENTITY_TICKING 及生成空闲，再安装实体、流体、方块 tick 和熔炉设施。普通 Vanilla 的首次生成与重载通过，但 B 在 220 个 game tick 后报告掉落物没有跨界。必须区分注册错误、错误物理邻区与测试世界本身已经停止实体 ticking。

## 方法

保留 [ticking_lane 的首次失败现场](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/runtime-proof-frontier/minecraft-the_nether/ticking_lane/first_runtime_failure)，包括条件、FAIL、完整世界和 crash report。输入指纹为 283D4406003B6763C27A6C2CE1790EB8DA4B667288BAAC2B1742AEDD2A88FCCF，游戏 PID 81156，每实例四逻辑 CPU、低优先级，与另一独立正确性队列并行；不是性能 A/B。

用本机 neoforge-21.1.262.jar 的 javap 核对 ServerLevel.tick/resetEmptyTime，读取归档世界 DIM-1/entities/r.3.-2.mca 的原始 item NBT。读取不写回存档，不给实体补 tick 或改变运动。

## 发现

- 全部 121 个 Artifact 已发布，48 份 BIOMES 准备已发布，POI 81 次，LIGHT/SPAWN/FULL 各 49 次，in-flight 为零。目标与相邻 holder 已满足真实 ENTITY_TICKING。失败不是尚未完成 FULL 的冒充通过。
- 原生 ServerLevel.tick 在玩家列表非空或 ForcedChunkManager.hasForcedChunks 为真时重置 emptyTime；否则在 emptyTime 达到 300 后跳过实体及 BlockEntity ticking。普通 PLAYER 区域票据推进 holder，不等于玩家存在或强制区块集合非空。
- 失败前冷前沿已超过 60 秒。原始掉落物 NBT 为 Age=0、Pos=[2015.5,137.5,-585.5]、Motion=[0.3,0,0]、Health=5、OnGround=0，与初始位置和速度一致。实体已保存、未死亡，没有证据支持“被岩浆烧毁”或“越界卸载”的猜测。
- 普通 Vanilla 准备得快，探针的 220 tick 自然模拟在原生 300 tick 活跃窗口内完成；B 准备较慢，使没有玩家的相同设施遇到了不同活动条件。FULL/ENTITY_TICKING 就绪本身不承诺无玩家世界永远模拟实体。

## 结论

诊断指向无人探针的活动条件缺口，不是把物理运行期重新路由到虚拟世界的理由。探针在设施安装边界只调用一次原生 resetEmptyTime，记录之前的值；之后仍完全由服务器自然 tick 运行，220 tick 小于原生窗口。Vanilla/B 使用相同条件，不循环重置、不直接调用实体/BE tick、不添加永久 forceload、不修改产品代码。该调整尚须重新执行；原始失败继续保留为 FAIL，不能由静态归因自动豁免。
