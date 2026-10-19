# Fabric 状态安全结构来源的独立原版对照

调查日期：2026-10-17。结论：26.3 Fabric 气候入口／出口状态修正的完整装饰前矩阵、结构专项及独立 B 均通过；这是有限默认原版 fixture 的正确性证据，不是性能保留或 Target 收口。

## 问题

[原生组件反例](incident-fabric_source_climate_state.md)证明，按 source 坐标缓存结构结果而不保留原生气候查询状态，不能保证与真实调用等价。需要独立游戏确认状态守卫没有改变地形、结构或完整 B，而不是只检查缓存命中计数。

## 方法

冻结参考 Target 的 main、probe 与 worldgen_contract 源码，输入为 ACCC5307CA415A2B35A1C0A9D8BED6C0074958667CBC66B43CBD856A8773D492。装饰前每项使用两个独立 JVM/save：fresh 普通原版 S(C) 与无关宿主的轻量消费者；FEATURES 为零。完整 B 每项另有两份 fresh native 重复和宿主 17、23 的消费者。没有复用旧世界或旧生成结果。

正确性最多两队列，各四逻辑 CPU、BelowNormal，使用独立实例、世界和端口；不与性能测量重叠。完整 B 的端口编排问题另记[故障分析](incident-fabric_validation_ports.md)，原失败不计通过，新批次单独命名。所有通过结果均再次使用当前源码 CheckOnly，不用旧输入指纹重新生成。

## 发现

| 门禁 | 实际结果 | 证据 |
| --- | --- | --- |
| 装饰前矩阵 | 72/72：主世界 40、下界 16、末地 16；144 个 fixture JVM/save | [matrix](../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-fabric) |
| 七类结构专项 | 18/18；36 个 fixture JVM/save | [structures](../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state/26.3-fabric) |
| 独立完整 B | 19/19：主世界 14、下界 2、末地 3；76 个 fixture JVM/save | [independent](../../validations/conformance-feature_b/result/climate_state_launch_ports/independent/26.3-fabric) |

上述合计 256 个 fixture JVM/save，不包括空世界导出、组件预检或失败启动。结构专项覆盖 Village、Mineshaft、Trial Chambers、Ocean Monument、Nether Fortress、End City、Stronghold；包含邻区 start、两种 halo 及原点／原 blocker。完整 starts、pieces、references、地形适配输入、stored biomes、密度内部切片、表面与洞穴仍按同条件原版严格对照，不按对象数量或视觉近似判等。

矩阵中每项 source 查询为 2890、唯一坐标 361。状态入口不同则调用原生求值，不能复用旧结果；同入口命中后恢复原生出口。汇总如下：

| 维度 | source 查询 | 实际求值 | 入口不匹配重算 | 气候出口重放 |
| --- | --- | --- | --- | --- |
| 主世界 | 115600 | 35140 | 20700 | 80460 |
| 下界 | 46240 | 7861 | 2085 | 38379 |
| 末地 | 46240 | 5776 | 0 | 0 |

末地原生源没有这棵 MultiNoise 查询树，零重放是对应分支而非缺失接线。72 项矩阵与 18 项专项结束时作用域均 closed、resident 为零。这些计数只说明实际工作量，不换算为 CPU 或端到端提速。

完整 B 原版生产者合计有 6 个实体、8 个方块实体、2 项待加载方块实体 NBT、1280 个 block ticks、292 个 fluid ticks、1856 个后处理位置；消费者继续完整比较目标内数据、结构 NBT、可见群系及随机调用序列。每项实际 writer 为一、并发 writer 为一，宿主未改变、binding 为零。保留 raw NBT 和原始严格视图，仍只使用既有且经原版证明的有限实体验收，没有新忽略字段。

## 结论

本次状态安全来源复用通过默认原版有限矩阵的独立对照；两次端口失败不能由新结果改记为通过。Seed V1、Ordering1/2、format V2、B 越界切断、原版命中后的结构算法与已有存档均未改变。

这不证明任意参数树、datapack、第三方 Mixin、宿主预热／线程交错、环搜索续流或完整物理生命周期安全。未知环境的来源复用必须精确回退；其它 Target 须按自身实际原生入口分别适配和验证。三维度运行／重载、顺序／并发、世界功能与性能状态由里程碑当前矩阵维护。

本批次没有性能测量，也没有相对 Vanilla 的新吞吐或 send-ready 分位数；历史 H1 的 9.16% 不能充当这个输入的保留依据。
