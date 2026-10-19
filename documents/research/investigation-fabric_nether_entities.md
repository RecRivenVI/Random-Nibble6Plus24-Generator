# Fabric 下界自然数据与猪灵属性顺序差异

调查日期：2026-10-17。结论：真实下界正例的十个实体、五个箱子完成生产激活与新 JVM 重载；新增堡垒完整 B 对照尚未通过，差异集中在猪灵 attributes 列表换序。仅按 ID 对齐的诊断不能代替原生双向因果证明或新增验收授权。

## 问题

参考版已有主世界／末地自然激活证据，下界不能只用人工熔炉和物品设施充当自然副作用正例。需要从真正的生产输出找到候选，再独立核对内容与生命周期，避免把可加载当成生成等价。

## 方法

只增加 probe、可关闭的 Publication 清点和独立诊断 fixture，不改产品生成、调度、种子、排序、存档或实体验收器。清点只读取真正的物化 Publication：复制实体、BE NBT、真实方块类型、owner、local seed 与 Artifact 指纹，不请求新 Chunk、不创建 BE、不回写 canonical terrain。证据与测试身份、源码和实际 JVM 绑定，关闭后清空观察器与引用。

清点用主种子 123456789、下界目标 (-11,-6)，运行原有票据驱动的 49 FULL、ENTITY_TICKING 和 220 tick，再新 JVM 重载。之后对候选目标另建 fresh Mosaic 世界验证自然激活／保存，并用两份 fresh 原版 local seed 世界和无关宿主的独立 B 消费者比较完整快照。两条正确性队列使用不同实例、世界、端口，各四 CPU、BelowNormal；没有性能测量重叠。源文件全程冻结。

新增 nether-chests 仅由显式诊断选择启用，默认十九项独立 B 和四项自然正例的选择不变。所有原始失败、实体 NBT 与 UUID 保留，不在诊断中修改实体属性或 RNG，不修改 canonical hash 或 NativeEntityAcceptance。

## 发现

### 真实生产清点与生命周期

[清点结果](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/publication-inventory/runtime-proof/minecraft-the_nether/nether_natural_discovery)对应 F8C0EA108236E4409343CB77DDBCD46A56E587F59F5D1500414677C7E82911B0 全 src 输入。新生成 PID 99136，重载 PID 95404。下界实际发布 121 个 owner，唯一含 BE 的候选为 (-10,-10)，local seed 2200905517305458572：五个箱子，原始数据还包含八只 piglin 与两只 piglin_brute。重载发布数为零，引用清理通过。世界累计 B generation 的 156 包含其它维度准备，不能与 121 相除推定重复生成或性能。

[自然激活／重载](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof/minecraft-the_nether/native_nether_activation_nether_chests)对应 92BDEFC7213017CAFF301ED4C6EB78EF8FBED1E7AE7CEB89B04D88146FDE7952 全 src 输入。新生成／重载 PID 64836、62812；真正的十个源实体和五个 BE 均出现在物理生命周期中。实体原始 UUID、类型和唯一对象身份保持，BE 安装的完整 NBT 与原生加载控制相同；各有 220 个自然 tick、49 FULL 和真实 ENTITY_TICKING。重载 B、Artifact、SPAWN 等生成计数为零，测试修改保持，绑定和 in-flight 清空，关闭引用清理通过。

它不是十个实体的全 NBT 运行期冻结证明，也不证明箱子具有服务端 ticker 或猪灵已经走完 AI 全行为。自然数据与原有人工设施分开记录，后者不充当自然生成正例。

### 独立 B 严格失败

[新增完整对照](../../validations/conformance-feature_b/result/nether_natural/independent/26.3-fabric)对应 EBF915ECDEC97C921985C99E517C1B25261CC8DF21EAF90DF2BA2293ACAF33F2 main/probe 输入。原版 native_0 PID 58120 产生基线，native_1 PID 62924 严格比较通过；无关宿主 host_17 PID 112056 失败，工具正确停止，host_23 尚未运行，不将前三份当成完整四角色通过。

两条原版路径实际命中 bastion_remnant placement，十三个 piece 与目标相交，目标有十个实体、五个 BE。host_17 的非实体比较没有差异；八只 piglin 的 follow_range 与 movement_speed 在 attributes 列表中交换位置，造成四十八个未豁免字段路径差异。两个 brute 没有该列表差异；所有 raw UUID 仍保留。

仅作为诊断，将每个 attributes 项按唯一 id 对齐并排除既有根 UUID 后，十个实体的其它字段、类型、base、modifier 数值、操作、成员和列表内容全部逐项相同。此诊断不进入 acceptance，不重排原始 NBT，也不允许 follow_range/random_spawn_bonus 数值变化；原 comparison.accepted=false 与 strictParity=false 保持。

### 尚未闭合的因果链

实际 26.3 AttributeMap 构造器使用 Object2ObjectOpenHashMap，pack 直接按 attributes.values 的迭代顺序生成 Packed 列表；Holder.Reference 没有自定义 hashCode/equals。该源码路径支持“容器次序”假设，但两个原版重复恰好同序，尚没有本 fixture 的不同原生顺序见证与完整双向干预。

因此不能只因源码使用无序 Map 或其它实体曾有同类差异就套用猫的例外。下一步需要记录真实 key/hash/迭代／pack 顺序，得到合法原版两种顺序并双向重现；有其它字段变化仍按最早分叉继续查。未获得证明和必要验收边界之前，堡垒完整 B 保持未通过。

## 结论

下界自然实体／箱子激活、保存与重载已有独立物理证据；清点未引入旁路生成。新发现的实体序列化差异与运行期正常是不同门禁，生命周期通过不能抵消完整内容对照失败。

没有扩大 canonical 或有限实体验收规则，没有选择“最接近”的一轮，没有改变生产排序、Piglin RNG、旧存档或 B 切断合同。当前新增堡垒内容门禁未完成；原十九项与其它 Target 保留各自证据，不由本结果改写。此轮没有性能优化或新的相对 Vanilla 数字。
