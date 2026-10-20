# NeoForge 结构来源缓存的气候状态守卫

调查日期：2026-10-17。26.3 NeoForge 已独立复现坐标缓存的原生气候入口／出口反例，并通过本加载器的状态守卫组件、四项装饰前和四项完整 B 预检。完整矩阵、结构套件、十九项 B、生命周期和新性能仍未完成，不能沿用 Fabric 或历史 NeoForge 的结果提升为通过。

## 问题

ScopedStructureSources 只按 source 坐标保存 starts／空结果。但当前 NeoForge patched Minecraft 的 Climate.RTree 同样持有 ThreadLocal lastResult。实际 ParameterList 的公共 List 构造器委托到私有 List,int 构造器，后者调用 RTree.create(List,int)。这个事实来自本项目使用的 26.3.0.83 制品字节码，不从另一个 Loader 推定。

源结果的正确性除了 seed／world／位置，还依赖原生入口叶节点；跳过求值也可能丢失应留下的出口及下一次查询结果。合成参数的反例不能直接证明默认世界已经出现某处方块错误，但足以否定无条件的坐标缓存前提。

## 方法

原生组件使用 NeoForge 自己加载的 Climate.ParameterList.findValue，包含 19-child 与重建 2-child 索引、相反查询前缀，共八项入口／出口双向控制。前缀通过原生调用到达，反射只读取实际叶节点和调用包内缓存入口，不反射改写原生状态、不重写气候算法。每组在两个 fresh JVM/save 重复，四 CPU、低优先级，组件前后物理加载数必须相同。

状态守卫绑定实际 ParameterList/RTree 的 ThreadLocal，不把 leaf 转成近似值；相同 source 和同一真实入口身份才命中，并恢复保存的出口。入口不同则继续调用原生 evaluator。作用域还约束 seed、world、BiomeSource、设置、查询范围和线程；未知模组集合、BiomeSource／参数列表子类或缺少桥接时，生产 factory 返回不使用缓存的精确路径。目标放置前继续独占移交，关闭清空结果与临时引用。

世界级预检继续使用同版 fresh 原生 S(C) 与两个无关宿主；不修改比较规则或忽略字段。装饰前观察完整 stored biomes、结构／Beardifier、密度、表面、洞穴及原始内部切片；完整 B 只运行目标 writer、严格切断，核对目标内副作用和 physical host 未变化。

## 发现

### 原生反例与守卫

原始输入 `AC201FD1D5DC51203CDC8B47A389C75D397CEEDAA811D423513A6EE23886B75D` 的两个独立组件（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state_neoforge_owned_module/26.3-neoforge`）均为 COUNTEREXAMPLE：不同入口时 cachedValue 与 directSameInputValue 不同；同入口命中跳过原生调用后，exitLost 为 true，下一次结果也不同。八项在两个 JVM 完全重复，不能改记为通过。

修正输入 `75875AB13693D240BB2738FAD7BD6E0AAAA8858C7F958CC57B9910CA25E38BE7` 的两个 guarded 组件（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state_guarded_neoforge_native_ctor/26.3-neoforge`）为 PID 70644／70092。八项全部通过，主世界／下界实际桥接到气候树、末地为无树路径，物理加载前后均零。组件不替代自然调度、完整世界或第三方数据包证明。

### 预检

四项装饰前（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_smoke_climate_state/26.3-neoforge`）已执行并 CheckOnly：主世界原点、(125,-37)、下界、末地均 PASS，FEATURES 执行零。原点／非原点实际入口失配重算 1221／326 次，命中和精确出口恢复 1308／2203 次；关闭后 resident 为零。不是缓存始终未启用而偶然通过。

完整 B 重跑预检（本机证据：`../../validations/conformance-feature_b/result/climate_state_retry/independent_smoke/26.3-neoforge`）的四项共十六个独立 JVM/save 全部通过：原生重复与 host 17／23 一致，writer 只有目标一个，绑定清空，宿主物理数据未变化。实际正例包含一个实体、六个流体 tick、398 个后处理位置；该套没有 BE 或 block tick 正例，仍由完整结构／B 套件补足。

两项预检均绑定上述 7587 输入。组件与四项通过不提升正在进行的72 项矩阵（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-neoforge`）和十九项完整 B（本机证据：`../../validations/conformance-feature_b/result/climate_state_full/independent/26.3-neoforge`），也不替代结构、保存重载、并发与性能门禁。

### 工具和失败边界

首次 probe 与 main 使用同一个 worldgen.structure 包，触发 FML 模块分包错误，游戏未开始；原始日志保留（本机证据：`../../validations/conformance-prefeatures/result/native_climate_state_neoforge_original/26.3-neoforge/first/console.log`）。probe 已迁到独立 probe.verify 包，仅在组件中反射访问包内入口，没有扩大产品 API；既有分包回归继续约束全部 FML Target。

首次完整 B 预检（本机证据：`../../validations/conformance-feature_b/result/climate_state/independent_smoke/26.3-neoforge`）在 nether/native_1 启动阶段失去运行句柄，随后核实 runner 与记录的 PID 都已不存在，既没有该角色完整 stdout／summary，也没有整组终态。标为 INTERRUPTED，不计通过或生成失败；原因未由现有证据确定，不猜测为 worldgen bug。结果与半成品实例原地保留，重跑使用独立标签 climate_state_retry，没有覆盖。

新 NeoForge B 标签运行读取模板实际导出的 DevLaunch 参数，复制到本验证实例自己的 native-args 文件，只把唯一已识别的端口改为 0，保留原始文件。每个角色记录 PID、四 CPU affinity、优先级、参数哈希、世界和请求端口；CheckOnly 同时核对真实绑定日志与独立 JVM/save。未知入口、外部／链接路径或不唯一端口直接拒绝，不猜测。两队使用独立验证目录，未同性能测量重叠。

## 结论

26.3 NeoForge 的坐标缓存前提缺口已有本版原生反例和限定修复证据，尚不能称为 Target 收口。当前没有改变 Seed V1、Ordering1/2、format V2、结构放置算法、B 切断或已有 FULL 存档；不重新解释或回填历史失败。

当前源码继续冻结以执行完整 Oracle。自然宿主／并发／生命周期、三维度当前性能及止损各自验收；26.1.2 尚未继承适配，BOP 也未被证明。未知环境的回退是安全边界，不是已完成第三方兼容。以后是否有性能收益必须来自单实例同条件端到端对照，不能用双队列预检耗时或命中数冒充。
