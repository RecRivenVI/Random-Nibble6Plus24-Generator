# Fabric 状态安全结构来源的完整 B 顺序对照

调查日期：2026-10-17。结论：参考 Target 的十八项模块对照、逆序、五线程并发、两个宿主及物理前沿变化通过；没有新增实体例外，不代替 production 请求或性能收口。

## 问题

[状态安全来源修正](incident-fabric_source_climate_state.md)已有独立原版装饰前与完整 B 证据，但仍需验证预热、线程和请求顺序。已有按需验证器对末地城只比较非实体状态和实体数量，不能据此证明其余实体字段正确。

## 方法

只增强 LightweightFeatureBVerifier 和验证工具，不改产品。当前 main/probe 输入 D6C8040BF1D561EA2D249BF8D6B93453E1E438407AEDD55197726B6A5AFF0B2B，全 src 输入 71F7AEB14286B90C8959A9D3AF1174A4D2112C9DCBD3052B67B24030F88F87CE；产品摘要仍为 EFF0A7E9087FDEBDB9E1FA5E9A17DFEBA1771FAA7FD074EBB77DA8CC79EFBBD2。运行期探针文件仍为 388F7A6221072A8AB134444B1CE416C883A2144B5AD87A238FE69AE959F2E7E8，不重跑未受影响的区域运行门禁，也不改写其 CF39 原输入。

入口 Run-StructureB 只使用本验证的原生启动配置引导，在新的 JVM/save 重新导出；各阶段绑定源码、PID、宿主种子、实例与私有配置。单游戏四逻辑 CPU、affinity 3840、BelowNormal，每个进程上限 300 秒；不与性能测试或其它游戏队列重叠。成功世界由任务移入回收站，已有条件目录拒绝覆盖，失败世界和原始数据保留。

每个宿主先执行七类冷模板结构的五线程对照，再执行十八项完整原 B/按需 B，逐次核对实际邻区读取、结构、随机序列和全部目标数据。十二项代表先逆序、再五线程并发，非原点另核对物理前沿 generated/mutated。第二宿主读取第一宿主的四十四份完整快照及原始实体 NBT 作交叉比较。

所有完整比较复用既有 NativeEntityAcceptance。UUID-only 原始快照仍原样保存；末地城只沿用已批准且有界的潜影贝朝向例外，其余字段、实体顺序和数量严格比较。没有固定随机值、增添忽略字段或把整个实体列表排除。两条模块路径在同一 JVM 的比较不冒充独立原版生产证据；独立控制仍由原 ACCC 输入的十九项 fresh native 对照承担。

## 发现

原始结果（本机证据：`../../validations/conformance-feature_b/result/climate_state_order/structure_reuse/26.3-fabric`）中，导出 PID 78116，宿主 17 为 21700，宿主 23 为 105768；三者均使用独立 fresh save 和 port 0。两个宿主均通过十八项串行、二十四项逆序/并发、两种物理前沿条件，峰值活跃目标五个，bindings 为零。十八项包括主世界十三项、下界两项、末地三项；完整结构包含七类及邻区 start。

| 每宿主门禁 | 实际结果 |
| --- | --- |
| 原 B 与按需 B 完整目标 | 44 份比较通过，其中 18 份串行 |
| 逆序/并发与串行结果 | 24 份通过 |
| 物理前沿生成/修改 | 2 份通过，物理数据不变 |
| 两宿主完整结果 | 44 份交叉比较通过 |
| 冷模板结构 | 7 类、21 项五线程比较通过 |
| 注入失败与作用域关闭 | 真正异常传播，来源与群系 resident 为零 |

两个宿主的十八项串行来源计数相同：queries 42194、evaluations 10912、stateReplays 30195、stateMismatches 4538。入口状态不同时确实重新求值，并没有把这些 miss 当作复用命中；关闭后 resident 均为零。这些是验证计数，不是无偏性能对照。

每宿主串行目标自然含五个实体、三个 BlockEntity、1112 个 block tick、284 个 fluid tick 和 1550 个后处理位置；pending NBT 为零，不能用本套件宣称其正例覆盖。保存原始实体与完整字段比较后，末地城跨宿主差异仅为已有 UUID 和 Rotation[0]；strictParity 保持 false，有限验收 accepted 为 true，unexpectedDifferences 为空，位置、类型、pitch 和其它数据没有被豁免。

两次 CheckOnly 工具错误均未改变游戏结果：严格 PowerShell 读取 Gson 省略的 null 成员，以及读取空拒绝计数 map 的属性集合。工具分别显式处理可空成员和空集合，非空差异仍失败；保留首次运行和两次复核失败日志，不重启游戏或回填数据。修正后的当前源码只读复核通过，输入错误时拒绝复用。

## 结论

补齐参考 Target 模块层的完整实体、宿主预热、逆序、并发和物理前沿门禁；产品、B、Seed V1、Ordering1/2、format V2 和旧存档均未改。其它 Target 不继承这份证明，未知模组/能力仍须精确回退。

尚缺 production holder 的更多请求布局、自然生成正例激活/持久化、出生/往返、环搜索异步续流与当前输入的性能收口。本实验没有玩家、网络发送或吞吐对照，不能给出相对 Vanilla 的性能比，也不宣称目标已完成。
