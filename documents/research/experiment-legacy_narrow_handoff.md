# 1.21.1 缩锁候选的完整结构 B 与脱离式交接

调查日期：2026-10-16。默认关闭的 H121-T 第一轮已完成十八项独立结构 B/Artifact 套件及独立 CheckOnly；它证明对应源码的模块输出一致，不替代尚未通过的主世界运行期或正式性能。

## 问题

释放完整模板管理器监视器后，保留原生 start、pieces、Jigsaw、随机消费和严格 B 写入边界，是否还能与独立原生控制一致？

## 方法

使用输入指纹 0D35D887DBC10F0F80F8E90ADAE30CBD033782971F87983E98B0BB0D94423050。每项各运行 native_0、native_1、host_17、host_23，JVM 与 save 均独立；两个原生角色不启用缩锁，消费者共享同一 S(C) 的 world state、实际启用有限环及缩锁，再关闭 owner 并还原 Artifact。完整范围结束后执行独立只读 CheckOnly；原生实体朝向因果控制在同指纹四个 fresh JVM 双向复验。

## 发现

- 完整套件（本机证据：`../../validations/integration-world_identity/result/structures-shared-handoff-ring-scope-narrow-locks-template_lock/1.21.1-neoforge`）为 18 PRODUCED、54 PASS，共 72 个独立 JVM/save；包含七类结构的真实 start/邻区、Village/Trial Chambers 地形适配 halo、Mosaic 原点和已知非原点。
- 完整方块、群系、高度图、结构 NBT/pieces/references、九份装饰前输入、外围原生阶段、feature seed 序列、可见群系和目标副作用均通过既有严格比较。真实 structure placement 与方块写入正例也由工具检查，不以只有 starts 元数据通过。
- 消费者实际缩锁 context 数与 worldStateBuilds/borrowedContexts 相符，结束时绑定与 live context 为零；物理 entities、tick schedulers、POI 未改变。未知组合的保守回退没有被算作候选激活。
- 36 个消费者均验证 owner 关闭后的 Artifact 与八邻区 stored SPAWN palette 交接，完整 pending/instantiated BE、ticks、实体与 post-processing 继续携带；结构加载显式观察 S(C)，并与原生加载控制比较。
- 四 JVM 原生朝向控制（本机证据：`../../validations/integration-world_identity/result/end-yaw-ring-scope-template_lock/1.21.1-neoforge`）双向完整重现通过，只沿用既有版本限定规则。未固定生产实体随机源，未增加任何忽略字段。
- 冷缓存七类结构 42 次并发的范围另见[前沿初筛](experiment-legacy_narrow_runtime.md)。本套件按 fixture/角色串行，不代替不同完整 B target 的并发、实体静态选择器安全或真实玩家行为证明。

## 结论

第一轮缩锁的完整结构 B 模块与脱离式交接在本指纹通过，原生算法未重写；产品快路径仍默认关闭。后续新增的端到端探针不改变本次产品算法，但有新的验证输入指纹，不能倒填本次证据。主世界运行期和性能保留条件仍须分别完成；下界/末地的有限运行期范围见[生命周期实验](experiment-legacy_narrow_lifecycle.md)。
