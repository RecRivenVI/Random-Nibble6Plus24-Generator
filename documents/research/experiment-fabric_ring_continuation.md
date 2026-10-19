# Fabric 原生环搜索的受控续流对照

调查日期：2026-10-17。结论：两个独立 JVM 的原生环／随机续流与有限真实群系读取全部一致；原生线程局部出口却确实不同。本记录是受控负面证据，不证明所有自然交错、参数表或第三方配置等价，不改变生产 Stronghold。

## 问题

实际 26.3 ChunkGeneratorStructureState 为每个环 slot 调用 RandomSource.fork，再把 112-block biome search supplier 提交到 Util.backgroundExecutor 的 structureRings executor。Mosaic canonical 准备也使用该背景线程池。Lazy scope 保留 slot 和 fork，却跳过不可能命中查询范围的 supplier；既有[气候状态反例](incident-fabric_source_climate_state.md)说明线程局部 RTree 入口／出口不能直接假定无关。

线程池相同或出口对象不同不是方块差异的证明，需要先检查实际原生算法的目标输出与后续读取。既有 5120 个 Stronghold 正例及完整结构／地形 Oracle 不能自动代替这一输入条件的对照。

## 方法

只新增 probe、默认关闭的 Mixin 与验证编排，不改生产环算法、scheduler、线程数、参数表、种子、排序或存档。输入指纹为 2C7EB4A3957BB67040D5C3329385E78A6115AB9FAB539669E7D62F46FC766EDE，范围是参考 Target 的 main、probe、worldgen_contract 与 target.properties，不将其冒称为全部 src 指纹。

原生控制不安装 Mosaic ring scope；候选调用现有生产 ConcentricRingScope。两边均真正执行 getRingPositionsFor，probe 只在实际 forName 返回点提供私有单 worker FIFO executor，这是合法但刻意固定的组件条件，不修改公共线程池。实际原生 fork 调用保持并计数；原生根随机源用完后观察下一次和再下一次 nextLong 返回值。没有自行实现环位置或 preferred-biome 搜索。

使用原版默认主世界参数，种子 0、1、-1、123456789；每个种子两种相反实际群系前缀，再比较原点普通目标与原生第一个真实要塞位置，共十六组。前缀在测试 worker 中通过真实 uncached 查询取得叶节点 token；诊断只在私有 worker 重放同一索引实际到达的入口与两种出口，不将它写进生产，也不将 uncached 查询当作 stored palette 权威。

每组独立检查以完整环／Lazy 出口为入口的 1445 个真实 quart 读取：目标周围 17×17、五个高度。每次读取重置到相应真实出口，避免第一项使状态收敛后掩盖其它输入；核对实际 biome key 与读取后的叶节点身份。代码在首次差异时记录坐标、双方值，并要求交换两种出口后双向复现，否则不通过。两个 fresh JVM/save 的全部病例与读取哈希必须一致，任何缺项或不一致都保留为未通过。

四 CPU、affinity 3840、BelowNormal、单游戏串行，Gradle 两个可见处理器和一个 worker；没有性能队列重叠。组件专用线程在结束时关闭，检查三维度物理加载数没有增加；不构造 ProtoChunk、StructureStart、FEATURES 或物化输入。

## 发现

[受控环结果](../../validations/conformance-prefeatures/result/native_ring_continuation_legal_fifo/26.3-fabric)的两个 PID 为 46208、60004，使用不同 save，当前源码 CheckOnly 通过。每个 JVM 的十六组目标范围位置与根随机续流均一致；普通目标八组实际执行零个 biome supplier，正例八组各执行一个，所有 Lazy 状态仍实际经过 128 次原生 fork 和 executor 选择。

完整控制每个种子／前缀执行一次 128-slot 原生环，再作为两个目标的同入口控制；即每 JVM 八次完整环，而非十六次。候选是十六个独立 structure state，不能把复用的控制结果重复算成独立生成样本或直接换算吞吐收益。

每个 JVM 的 23120 次真实群系读取没有差异；读取之后的叶节点也全部相同。十六组在读取之前的完整环／Lazy 叶节点身份却全部不同。因此这不是“跳过搜索仍保留了原生出口”的证明，而是这些输入下不同出口未改变被测读取的证据。

实际默认注册表的主世界存在一个 concentric set，下界与末地均为零；没有为了凑三维度正例向其它维度注入要塞配置。所有检查使用本版实际 sampler 和 RTree，不用自造气候参数替代这些默认世界输入。

[关闭环探针的气候回归](../../validations/conformance-prefeatures/result/native_climate_state_guarded_ring_gated_control/26.3-fabric)另有两个 fresh JVM/save，PID 80172、46440。每次八项原生气候入口／出口双向测试与三维度状态适配通过，加载数始终为零。原有对称参数的 COUNTEREXAMPLE 和所有旧失败保留，没有被本结果覆盖。

## 结论

本项没有发现真实环位置、根随机续流或有限 native biome 读取差异，不应据此修改 Seed、固定生产排序或增加 NBT／方块豁免。它也不新增优化或性能数字；supplier 数量不是端到端吞吐，现有性能仍待同条件 Vanilla 对照。

该结果不能证明任意 RTree 入口、自然 worker 交错或第三方参数表安全，也没有 StructureStart、stored palette 或最终方块的新对照。完整生成仍使用各自独立 Oracle；不在没有可观察反例时把对象身份差异报告成世界损坏。第三方／datapack 的能力守卫与精确原生回退仍须在对应门禁审计，不从本默认原版组件外推。
