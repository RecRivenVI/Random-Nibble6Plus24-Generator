# 1.21.1 缩锁候选的冷并发与主世界前沿初筛

调查日期：2026-10-16。H121-T 第一轮冷缓存结构并发通过，主世界自然运行期仍超出原 180 秒预算；候选继续默认关闭。没有改动超时、前沿或生成合同，也不将部分完成计为运行期通过。

## 问题

模板管理器粗锁的移除是否保持完整结构数据，是否足以让 1.21.1 主世界的真实票据前沿在既定预算内完成？此前零 Artifact 的失败见[原生活动窗口实验](experiment-legacy_runtime_activity.md)，候选依据见[本版共享状态审计](investigation-legacy_template_locks.md)。

## 方法

先用新鲜原生控制对照六阶段地形、物理 BIOMES 输入和完整 B/Artifact；再用两个 fresh JVM 分别先运行候选与整段锁，模板缓存初始为空，每次五个任务对七类真实 start 各运行三次。并发结果与另一模式的串行完整 NBT 判等，并检查两个冷顺序输出一致。

主世界使用原有 RuntimeProof 的真实 PLAYER 区域票据、49 FULL 目标前沿和 180 秒总预算；探针不提供伪区块，不驱动 worldgen 阶段，不削减依赖。正确性两队列各限四逻辑 CPU、BelowNormal、独立实例/世界/端口，因此本次计时不是正式性能 A/B，也不能计算相对 Vanilla 吞吐比。

## 发现

- 初批独立地形结构 18 项和完整 B smoke 四项使用输入指纹 C06A261FF5642FF9AF23FD16061409E38223C79399499B6CE026EB419A5E1789，分别有 36、16 个 fresh JVM/save；独立复核通过。它们证明该指纹的候选结果，不能冒充新增探针后的完整 Target 收口。
- 增加并发和运行期激活计数探针后的输入指纹为 0D35D887DBC10F0F80F8E90ADAE30CBD033782971F87983E98B0BB0D94423050；产品缩锁算法未在两次输入间修改。完整结构 B 套件此时仍在执行，不能提前计入通过。
- [冷缓存证据](../../validations/conformance-structure_inputs/result/narrow_lock_concurrency/1.21.1-neoforge)及独立 CheckOnly 通过：两个 JVM 的实际并发均为五，七类结构共 42 次完整 starts/pieces/references 对照一致，物理 Chunk 数不变，FEATURES 为零。它不是独立原生世界 Oracle，也不证明 FEATURES 中所有共享状态安全。
- 第一次冷测试在 Java 启动前因旧版导出没有可选 environment 字段而失败；修正编排读取可选字段，原始文件保存在同验证 result 下的 narrow_lock_concurrency_launcher_failure，不把它记成游戏内失败或倒改证据。
- [主世界候选初筛](../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/runtime-proof-frontier/minecraft-overworld/template_lock)在原 180 秒边界仍为 FAIL。退出前实际 Artifact/publications 各 57、BIOMES 准备/发布各 48、in-flight 64；POI/LIGHT/SPAWN/FULL 均为零，尚未开始运行期设施检查。崩溃链是 RuntimeLifecycleEvidence.advance 主动报告 Runtime ticket/simulation evidence exceeded original bound，不是 POI 越权、OOM 或已证明死锁。
- 该运行约第 153 秒的一份线程快照保存于 ignored build/mosaic-121-narrow-runtime-threads.txt；81 个 Worker-Main 中 6 RUNNABLE、75 WAITING、0 BLOCKED，未观察到等待 StructureTemplateManager 监视器。运行栈仍含 Jigsaw 展开、NoiseChunk.wrap/mapAll 与 CubicSpline.hashCode；等待栈主要是本地地形 future 的 join。这是单份快照，不能换算为 CPU 占比、锁墙钟占比或证明全程没有锁等待。
- 等待中的任务仍有进展：30/60 秒 Artifact 为零，90 秒为一，120 秒为 28，150 秒为 38，最后为 57。不能将“没有在预算内全部完成”简化为所有任务死锁，亦不能将这组正确性负载当成 60 秒探索吞吐。

## 结论

第一轮缩锁已证明有限冷并发的结构一致性，并观察到粗锁等待消失与生成进展，但尚未证明主世界运行期或正式性能达标。继续完成当前完整结构 B、其余维度与普通 Vanilla 对照，再建立本版端到端测量；不直接启用默认快路径、不延长预算，不据单张线程快照顺手改变 Jigsaw、密度算法或调度器。
