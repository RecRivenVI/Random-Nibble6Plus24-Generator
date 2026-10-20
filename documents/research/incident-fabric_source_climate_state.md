# 26.3 Fabric 结构来源缓存的气候续流

调查日期：2026-10-17。原生组件双向反例证明，结构来源缓存不能只按坐标复用，也必须保留原生气候查询的入口与出口状态。参考版已补状态校验、精确续流和未审计环境的原生回退；组件及四项独立装饰前预检通过。完整 72 项、结构、完整 B、生命周期和性能仍待重验，本文不提升它们的当前状态。

## 问题

26.3 Fabric 的 ScopedStructureSources 在一个 B 准备内复用相同 source 坐标的 starts 和空结果。原版 Climate.RTree 却持有 ThreadLocal lastResult：入口叶节点可以影响相同距离的选择，真正求值后还会留下出口，影响下一次查询。只保存结果、跳过原生调用，不足以证明相同输入及续流等价。

## 方法

审计本机实际 26.3 Minecraft JAR 的 ParameterList、RTree 和 MultiNoiseBiomeSource 字节码。用[气候状态组件审计](../../validations/conformance-prefeatures/validation.md)在两个 fresh JVM/save 中执行原生 Climate.ParameterList.findValue，气候参数采用合法的两组对称值，值为 cold/warm 字符串。查询前缀由真正原生调用到达，不反射设置叶节点、不改数值路径或 RNG。

分别使用原生 19-child 和重建 2-child 索引，两个相反前缀，每种索引/方向检查两项：不同入口时缓存是否错误复用；相同入口时命中是否保留原生出口及下一次查询。调用实际 ScopedStructureSources，而不是复制其算法作替代 Oracle。反射仅在 probe 中读取 index、lastResult 的实际身份；整个组件过程不生成物理地形。

修正后同样运行两个独立 JVM/save；此外核对三维度的实际 BiomeSource 状态适配，以及所有两种索引的构造接线。随后按新条件 climate_state，以 fresh 原版 S(C) 与 unrelated-host lightweight 分别运行四项装饰前比较，全部数据和内部切片仍严格判等。所有运行串行、四逻辑 CPU、低优先级，不与性能实验重叠。

## 发现

### 原生双向反例

原始输入 2E00BC01E26AEE2EB0EC8832CAA59E5DBE44990F3DDC1A5631F36D238B5AE8E4 的两个组件控制（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state/26.3-fabric`）分别为进程 45036、36336。每个进程八项反例完全相同，状态保持 COUNTEREXAMPLE，不改为生成通过；加载的物理区块始终为零。

不同入口时，相同中点查询可以合法返回 cold 或 warm；坐标命中却仍返回上一次条件的值。相同入口时，即使假定被缓存输出可复用，跳过原生调用会保留旧入口而不是出口，接着相同的中点查询仍产生不同值。反方向重复得到完全对称的结果。这不是精度、种子流或实体 UUID 问题。

该参数表不是默认群系表，组件也没有生成真实 StructureStart。它证明坐标缓存的普遍前提不成立，不证明默认存档已经发生某处方块或结构差异，也不授权增加任何 canonical 例外。

### 版本接线与失败保留

初次守卫运行 A3BF4FE85528AF50FEC48F8CFA755A3A118F80EA2A7CE25F2A6AD8CA1A41E376 在guarded（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state_guarded/26.3-fabric/first/console.log`）失败，原因是 Native dimension search state unavailable。诊断输入 80FEC4FA55F4ED62B0235FE85F58221A508CC37C2FE33FDA2B49F3465C0E8C2F 的bridge 日志（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state_guarded_bridge/26.3-fabric/first/console.log`）确认实际为原生 MultiNoiseBiomeSource/ParameterList，Owner 接口已加入，但 cursor 为 null。

实际加载类的字节码转储与 SHA（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state_guarded_bridge/26.3-fabric/first/native-bytecode.txt`）显示处理器已合入、私有构造器中没有对应调用。26.3 的公共 List 构造器委托到私有 List,int 构造器，后者调用 RTree.create(List,int)；不能沿用旧版单参数工厂或不限定重载的选择。适配改为精确的私有构造器描述符，仍返回原始 RTree，不改变气候查询算法。

两个失败的原始日志、crash report 与存档保留，没有以重跑覆盖。适配缺失时生产 factory 返回无复用的精确原生路径；探针则明确失败，不能拿静默 fallback 冒充已验证命中。

### 状态安全修正与当前证据

新输入 ACCC5307CA415A2B35A1C0A9D8BED6C0074958667CBC66B43CBD856A8773D492 的两个 guarded 控制（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state_guarded_native_ctor/26.3-fabric`）为进程 14676、14556。全部八项与三维度状态接线通过，并通过当前源码 CheckOnly；物理加载数前后仍为零。

缓存条目包含真实入口叶节点身份、来源输出和出口身份。同坐标但入口不同时重新执行原版 evaluator；同入口命中时返回已保存结果并恢复精确原生出口。作用域继续限制 world、seed、配置、BiomeSource 身份、查询范围和线程，目标可变结构图先独占移交，关闭清除条目与临时引用。真实 evaluator 异常不缓存、不吞掉，也不回滚其部分原生状态以伪造成功。

Native 参数列表需精确类、实际状态桥接和已审计环境；未知模组、BiomeSource、参数列表子类或缺失适配时，正常 B 交接不启用来源复用，继续同一精确 evaluator。普通 Vanilla 不调用 Mosaic 的缓存命中/续流；构造观察只保存原始索引的状态引用，不替换返回值。第三方模组的实际基线仍待对应阶段验证。

四项独立预检（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_smoke_climate_state/26.3-fabric`）覆盖主世界 origin、(125,-37)、下界与末地，原版生产者和轻量消费者使用不同 JVM/save/host seed，FEATURES 为零；结构、全部 stored biome、地形、密度、表面、洞穴和内部数据一致。每项实际 queries 为 2890、unique source 为 361，作用域关闭后 resident 为零。

| fixture | 原生入口不同而重算 | 实际缓存命中 | 实际 evaluator 次数 | 原生出口恢复 |
| --- | --- | --- | --- | --- |
| fixture-03 主世界 origin | 1221 | 1308 | 1582 | 1308 |
| fixture-22 主世界非原点 | 326 | 2203 | 687 | 2203 |
| fixture-40 下界 | 190 | 2339 | 551 | 2339 |
| fixture-56 末地 | 0 | 2529 | 361 | 0 |

这说明真实流程中入口确实变化，但不将重算次数解释为已发现同数量的方块错误。末地没有 MultiNoise RTree 状态，出口恢复为零。新增单元回归验证入口键、出口恢复、线程边界、异常不缓存与未知模组守卫；参考版全部 234 项测试通过。

## 结论

来源缓存必须把原生隐式输入和续流纳入等价条件，而不只是记录 source 坐标与 seed。参考版该缺口的组件修复和有限独立预检已成立；不修改 Seed V1、Ordering1/2、format V2、FEATURES 严格切断或已有 FULL 存档，不回填历史证据。

新增状态守卫会使原先的部分命中改为精确重算。本轮未测吞吐，不以命中数量或预检时间宣称性能保留；旧 H1 数字不能代替新输入的收益门禁。完整 72、七类结构/邻区/地形适配、完整 B、宿主/线程/顺序独立、环搜索续流和生命周期仍须各自收口。26.3 NeoForge 与 26.1.2 尚未继承本适配，不从同版本或已有代表矩阵推定它们已通过。
