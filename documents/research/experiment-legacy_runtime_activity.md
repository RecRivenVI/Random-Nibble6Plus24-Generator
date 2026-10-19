# 1.21.1 原生活动窗口与物理运行期验证

调查日期：2026-10-16。普通 Vanilla 三维度六 JVM、B 下界/末地四 JVM 的自然运行期和重载均通过；B 主世界未在原 180 秒预算内准备好前沿，保持失败，不延长预算或记作通过。

## 问题

修正[无玩家实体休眠的测试条件](incident-empty_level_runtime.md)后，须重新验证真实物理票据、ENTITY_TICKING、跨界实体/流体、BE ticker 和重载，并确认普通 Vanilla 没有进入 Mosaic。单目标 FULL 成功不代表完整 ticking 前沿就绪。

## 方法

输入指纹 3E52B2CE6DE64BB45A2BC309DDBE7175797615A9874B6D1B3E708D5DDCFF6309。仅适配 probe 与工具，没有变更产品生成算法。每个游戏四逻辑 CPU、低优先级，Vanilla 与 B 使用相同原生 PLAYER 区域票据半径 3；先等全部 49 个 FULL 与两侧 ENTITY_TICKING，再在设施安装时重置一次原生 emptyTime，之后运行 220 个自然 tick。不逐阶段驱动、不直接调用私有 tick、不添加强制加载区块。

结果隔离在 runtime-proof-frontier/维度/ticking_activity。下界/末地八 JVM 已通过 Check-LegacyRuntime 的独立只读复核，包括当前指纹、不同 PID、真实 NBT sidecar、原生回调及重载零再生成。主世界普通 Vanilla 的两 JVM 也通过编排断言；B 的失败保留完整现场，不执行不存在的 reload。运行与其它正确性队列曾有重叠，不是正式性能 A/B 或客户端测量。

## 发现

- B [下界](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/runtime-proof-frontier/minecraft-the_nether/ticking_activity)和[末地](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/runtime-proof-frontier/minecraft-the_end/ticking_activity)的 fresh/reload 均 PASS。下界安装设施前 emptyTime 已为 1472，重置后实际获得 220 次实体和熔炉回调，证明原休眠条件确实影响测试，而非用静态推演豁免失败。
- 两维度 fresh 均完成 121 个 Artifact/发布、48 份部分 BIOMES/发布、81 次 POI、49 次 LIGHT/SPAWN/FULL；实际检查所有 49 个 owner 的生成依赖，共 2401 次 BIOMES-or-later 读取。重载改按加载图检查 441 次，Artifact、部分 BIOMES 和 SPAWN 均为零，修改方块保留。自然模拟期间各生成计数不变，in-flight 和作用域清空。
- 掉落物各只有一次实体管理器注册，并按真实物理运动跨入相邻不同 S(C) Chunk；block/fluid callback、下界岩浆/末地水传播和熔炉烧炼均实际发生。设施来自探针，不冒充自然生成副作用覆盖。
- 普通 Vanilla 的主世界、下界、末地共六个 fresh/reload JVM 均完成相同自然检查，实际 MosaicPhysicalLevelState 创建数为零；没有用虚构的新版本 materializer 指标证明零侵入。
- B [主世界首次失败现场](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/runtime-proof-frontier/minecraft-overworld/ticking_activity/first_runtime_failure)在原 180 秒预算耗尽时仍为 Artifact 0、BIOMES 准备 0、in-flight 81，尚未进入设施阶段。这里不是重置计时器后再次停止实体 tick，也不是通过的自然运行期结果。
- 主世界两份只读线程快照保存在 build/mosaic-121-runtime-overworld-threads.txt 和对应 threads-late.txt。首份有 61 个 Worker-Main，其中 38 个等待同一个 StructureTemplateManager monitor；持锁线程正在 Jigsaw 的 getBaseHeight → iterateNoiseColumn → Aquifer/密度计算。后份持锁线程已改变，29 个 worker 仍在等待模板锁，说明至少发生了锁释放/转移，不能直接称为死锁。
- 同一进程的后期堆观测约使用 575 MiB、提交 664 MiB、上限 3 GiB，未出现 OOM；这不等于已经测量 GC 占比。线程数、累积 CPU 时间或阻塞线程数量都不能当作墙钟占比、CPU 热点百分比或正式 Vanilla 比值。

## 结论

下界/末地的有限自然运行期门禁通过，主世界仍失败，Target 没有收口。当前最明确的下一项调查是旧版完整结构输入仍持有的粗粒度模板监视器；只能先按本版实际共享可变状态审计，再提出有界候选，不能直接删除锁、修改 Jigsaw/RNG 或继承 26.3 的安全证明。有限环候选仍默认关闭；正式探索 A/B 与 Vanilla 比值尚未适配完成，不以本轮计时替代。
