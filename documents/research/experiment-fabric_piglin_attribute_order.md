# Fabric 猪灵属性序列化顺序的双向因果实验

调查日期：2026-10-17。结论：26.3 原版自身可合法产生两种猪灵属性列表次序；有效双向干预严格复现了原始失败两侧的完整 UUID-only 快照。使用者批准有限的按属性 ID 对齐后，新增堡垒四角色独立 B 对照通过；生产排序和原始哈希没有改变。

## 问题

[原始调查](investigation-fabric_nether_entities.md)的 nether-chests 在原版重复通过、host_17 严格失败后停止，不能仅凭无序 Map 或按 ID 对齐的诊断放行。需要证明顺序来自上游合法执行，并排除属性值、RNG、地形或结构变化。

fixture 为主种子 123456789、下界 (-10,-10)，local seed 2200905517305458572。真实堡垒十三个 piece 与目标相交，放置十实体、五箱子，并有六个流体 tick；没有手工插入数据。

## 方法

只增加显式 probe 和工具。LivingEntity 保存时包装原版 AttributeMap.pack 调用，先执行原方法，再观察实体位置、实际 Map 类型、Holder 身份哈希、fastutil 掩码和槽位、values iterator、Packed 顺序及最终原始 NBT。探针不更换 Map、不修改属性、不调用 worldgen RNG、不改变生产生成或旧存档。

预先固定四份普通原版新 JVM/save 捕获；先得到两种实际原生顺序，再重放已捕获的顺序带。重放仅排列原方法返回的同一组 Packed 对象，唯一 ID 集合及成员数必须完全相同；完整 UUID-only 目标快照、原始实体的其它字段、引用结构、地形、biomes 和 feature seed trace 全部比较。

判据要求两个方向都发生真正的八实体顺序干预。写回相同顺序的 no-op 只证明路径可运行，不作为该方向的因果证明。四份自然捕获和四份重放全部保留，不按最终哈希挑选最接近的一轮。游戏实例逐个运行，各四 CPU、BelowNormal；没有性能实验、GUI 操作或其它重负载队列重叠。

## 发现

### 上游调用与自然见证

实际 26.3 字节码为 LivingEntity.addAdditionalSaveData → AttributeMap.pack → attributes.values().iterator → AttributeInstance.pack → Packed.LIST_CODEC → ValueOutput。原版 AttributeMap 使用 Object2ObjectOpenHashMap；Holder.Reference 没有自定义 hashCode/equals。fastutil 8.5.18 的 MapIterator 从高索引向低索引扫描；本案例两键落在不同槽，没有碰撞或修改期间遍历。

schema-2 因果汇总（本机证据：`../../validations/conformance-feature_b/result/piglin_order_iterator-effective-causality.json`）绑定 main/probe 输入 62BC2BDE3833508BCE83ABAE2406C310006A35AA75E66E387CBBD8417D17EB65。四捕获都逐项证明原版 iterator、pack 与最终 NBT 顺序一致，observed 等于 used，没有排序干预。

| 捕获 | 目标 JVM PID | 原版属性顺序 | 身份哈希对应槽位 |
| --- | --- | --- | --- |
| a | 100744 | movement_speed → follow_range | 14 → 5 |
| b | 40816 | movement_speed → follow_range | 28 → 24 |
| c | 46640 | follow_range → movement_speed | 16 → 6 |
| d | 25292 | movement_speed → follow_range | 原始 trace 保留 |

a 的两个身份哈希分别为 1104693947、115715241；c 为 1364242148、636370090。实测普通 Object 哈希和 identityHashCode 相等，没有特殊 JVM hashCode 标志或人为替换 key。

两种完整 UUID-only 快照为：

- movement_speed 在前：9803c58183289e4f2f8b8d4a54175cbe7d7112434bcf4dce9b9aac1266ad0843，精确等于原始 host_17 的失败哈希。
- follow_range 在前：d29816432e390bcccd5b4b76788e14dab4ab7f16c71681111b3afe88f8a4f583，精确等于原始两份 native 的哈希。

这不是 world seed RNG 的不同：两属性 base、完整 modifier 数值/操作、所有其它实体成员与非实体结果相同；70 次 feature-seed 调用及序列 -4777320093227134762 一致。仅在序列化边界重排同一 Packed 列表，不消费随机流。

### 有效双向干预

| 重放 | 目标 JVM PID | 真正改变顺序的猪灵数 | 完整 UUID-only 快照 |
| --- | --- | --- | --- |
| a_with_b | 88820 | 0，no-op 不算该方向证明 | 严格匹配 c |
| b_with_a | 61172 | 8，follow_range → movement_speed 在前 | 严格匹配 a |
| a_with_b_2 | 108652 | 8，movement_speed → follow_range 在前 | 严格匹配 c |
| a_with_b_3 | 115924 | 8，同上 | 严格匹配 c |

八份生成均使用不同 JVM/save，原始实体、结构和快照保存。每次重放的完整 strictParity=true，所有其它字段继续一致。最早的 schema-1 汇总缺少“干预必须有效”的检查，保留但不作为完成证据；schema-2 工具明确拒绝单纯 no-op，后续门禁只引用 effective 汇总。

第一轮观测探针误用了 map.values().stream，原版却使用 iterator，导致观测器自身报告 Native values iteration/pack differs。失败输入 22782A40FCB83E5B1FFA8EFDFBDCA01B256674E4E3DEE6FC43CE3245C7278612、PID 99364 和原始 crash/world 保留；修正为同一种迭代器后重跑，不把此失败归因于产品。

### 获批规则与独立 B 重验

使用者在取得上述证据后明确批准有限例外。规则只在显式 nether-chests fixture 对 minecraft:piglin 开启，必须恰含 follow_range、movement_speed 两个唯一属性 ID；只对齐整项，不忽略数值、modifiers 或其它字段。默认规则、brute、其它实体/Target/模组和未知属性集不扩张。三个新增单元测试覆盖换序、值/修饰符/生命差异、brute、未知/缺失/重复属性，连同原八项共十一项通过。

新四角色结果（本机证据：`../../validations/conformance-feature_b/result/nether_natural_accepted/independent/26.3-fabric`）绑定 BC19FA3F0B0EAFD5EEC5326A8B326C46054F0A71684F31BBAE537299CE09D2E1 main/probe 输入。运行前与 CheckOnly 均显式按历史输入复核 schema-2 因果证据，不将其伪装成新增验收器后的新实验。

| 角色 | 目标 JVM PID | 结果 | UUID-only strictParity |
| --- | --- | --- | --- |
| native_0 | 80544 | PRODUCED | 基线 |
| native_1 | 85400 | PASS，有限验收 | false，仍保留列表顺序差异 |
| host_17 | 105912 | PASS，有限验收 | false，仍保留列表顺序差异 |
| host_23 | 84368 | PASS | true |

各有十实体、五 BE、六流体 tick、十三个相交 piece，实际一个 writer，feature seed trace 和原生引用结构相同；两个无关物理宿主未被修改，四份绑定均为零。新结果没有加载属性顺序干预 Mixin；生产与控制仍自由使用自己的原版容器次序。原始 nether_natural 的 FAIL 与缺 host_23 保持原状，没有倒填为 PASS。

## 结论

此差异已从未解释收口为合法 JVM 容器序列化次序波动，新增堡垒内容对照在使用者批准的有限验收下通过。它不是结构漏失、宿主种子改变 RNG 或方块差异，也不授权泛化成“整个 attributes 可忽略”。canonical 哈希依旧只排除既有 UUID，原始 NBT、比较差异与原版行为都保留。

本轮只改验证与文档，没有新的生产性能优化或可靠相对 Vanilla 性能比值。既有自然实体/BE 激活与重载证据保留其原输入范围；出生/往返、五 Target 收口和 BOP 仍按里程碑继续，不由本实验宣称全部完成。
