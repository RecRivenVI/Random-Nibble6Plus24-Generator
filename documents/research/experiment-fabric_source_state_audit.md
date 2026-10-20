# Fabric 结构输入历史与局部初始化成本审查

调查日期：2026-10-17。当前 26.3 Fabric 的三维度 source 历史诊断完成；相同原生输入的重复求值存在，但没有形成值得承担额外状态重放与内存风险的性能候选。第二次初始化／能力检查审查同样未找到可安全达到保留门槛的改动；本组合停止追加性能优化，继续尚缺正确性和其它 Target 门禁，不宣称已达到原版速度。

## 问题

当前状态安全缓存只保留一个坐标的一份入口／出口。入口被其它状态覆盖后再出现，可能重复求值；不能仅按 stateMismatches 数量推定可节省成本。另一项待辨别的热点是 LocalTerrainColumns.Batch 构造器：它的包含式 CPU 栈很重，但未必是重复分配或能力检查。

## 方法

H-F163-S 用默认关闭的 probe 观察原生 Supplier.get：仍执行原来求值，不替换结构结果、不修改 RNG 或原生气候状态；只记录 source 坐标及入口 leaf 的身份，模拟最多十六个不同输入的最近访问历史。容量 2／4／16 的结果同时计量，不保存结构 carrier。原生缓存命中不改变不同输入的访问次序；目标独占移交时删除相关诊断历史，关闭时核对 evaluator 次数并释放 token。

最初 WrapMethod 直接声明 package-private StructureInputChunk 导致 probe 编译失败，日志保留（本机证据：`../../build/research/structure-history-audit-compile.log`）。改在原生 Supplier 的公共 Object 返回边界观察，不扩大生产 API；修正编译（本机证据：`../../build/research/structure-history-audit-compile-native-supplier.log`）通过。该失败不是世界生成错误。

三次诊断和一次关闭诊断的主世界控制均独立 JVM／fresh save、四逻辑 CPU、affinity 3840、BelowNormal、8/8、60 秒固定两次移动，串行且无其它游戏重叠。输入指纹均为 `BC6050DA2DB5F6E9B93F7AE8A71969CBA8D6DE478E8E8F56E909861A09300CB7`；算法沿用现状，只增加 probe／工具和轻量计数。PID 分别为主世界 68352、下界 47280、末地 43508、关闭诊断控制 56388，world identity 均不同。

诊断统计全部已关闭作用域的完成求值，包含观察窗口后的关闭尾部。它与固定六十秒 CPU／吞吐窗口不能直接相减或相加；Supplier 墙钟也可能包含锁等待、嵌套任务帮助，不等于可删除的纯 CPU。工具明确写 performanceComparable=false，原版比值汇总拒绝混入开启诊断的结果。

H-F163-P 另用 Java JFR 工具读取已有基线（本机证据：`../../validations/performance-worldgen/result/b_milestone/26.3-fabric/climate_state_frontier/b-v8-s8/jfr-focused.json`）及本轮关闭诊断控制（本机证据：`../../validations/performance-worldgen/result/b_milestone/26.3-fabric/source_state_screen/b-v8-s8/jfr-focused.json`），输出独立 focused 文件，不覆盖原报告。审查 Batch 初始化、密度图审计、结构保护能力检查和 detached biome 原生入口，不实施新缓存。

## 发现

### 相同输入重复量

| 维度 | 完成原生求值 | 两状态重复求值 | 两状态重复 Supplier 墙钟 | 四状态墙钟 | 十六状态墙钟 | 全部 Supplier 墙钟 |
| --- | --- | --- | --- | --- | --- | --- |
| 主世界 | 404471 | 91898（22.72%） | 6.038 s | 6.572 s | 6.591 s | 39.282 s |
| 下界 | 333919 | 51579（15.45%） | 1.126 s | 1.154 s | 1.154 s | 10.011 s |
| 末地 | 211955 | 0 | 0 | 0 | 0 | 1.076 s |

原始诊断：主世界（本机证据：`../../validations/performance-worldgen/result/b_milestone/26.3-fabric/source_state_screen/b-source-audit-v8-s8/source-state-audit.json`）、下界（本机证据：`../../validations/performance-worldgen/result/b_milestone/26.3-fabric/source_state_screen/b-source-audit-the_nether-v8-s8/source-state-audit.json`）、末地（本机证据：`../../validations/performance-worldgen/result/b_milestone/26.3-fabric/source_state_screen/b-source-audit-the_end-v8-s8/source-state-audit.json`）。开／关作用域分别 525／525、675／675、725／725，原生求值计数差均零，清理后保留坐标均零。

主世界两状态重复 Supplier 仅占其 Supplier 墙钟 15.37%；扩大到十六份也只达到 16.78%。同轮准备与装饰合计约 264.43 累计线程秒，重复两状态的约六秒不是 22.72% 的端到端加速。这里没有证明收益严格上界，也没有证明可达 ≥5%：还须额外 carrier 存储、精确出口恢复和原生可变图所有权，不能把机会比例直接作为优化保留。

三维度最终 physical BIOMES in-flight／pending 均零；每次生命周期 profile 文件检查两次、0.252／0.255／0.337 ms，无 Chunk 热路径文件轮询。SPAWN 携带命中分别 357／483／525，没有记录 cold recovery；各次正常保存停止。关闭诊断的控制没有任何 reuseAudit 聚合，文件检查两次 0.297 ms，physical BIOMES 同样清空。

### 包含式初始化栈不等于初始化开销

| 包含式 CPU 栈 | 已有基线 8478 样本 | 关闭诊断控制 10283 样本 |
| --- | --- | --- |
| StructureGenerationLocks.supports | 42（0.50%） | 31（0.30%） |
| LocalDensityTiles.Audit.visit | 50（0.59%） | 70（0.68%） |
| LocalTerrainColumns.Batch 构造器 | 2323（27.40%） | 2813（27.36%） |
| DetachedBiomeInputs.generate | 135（1.59%） | 153（1.49%） |

这些栈互相包含，不能累加。Batch 构造器除能力检查外，会读取已生成群系、解析该位置的精确 CompactStructureInputs／Beardifier，并调用原生 NoiseChunk 和缓存准备；其 27% 不是已证明可删除的构造或分配开销。群系准备和 source 元数据已有目标内复用；密度依赖只首次 resolve，未知图和特殊表面继续精确回退。去掉这些真实输入会触碰尚未建立的新等价证明，不能为了栈比例删除。

26.3 doCreateBiomes 虽未使用传入的 StructureManager，也不能据此删除 preceding starts/references：physical BIOMES 的保存 authority 和后续生成仍需要它们。相较之下，两个轻量能力／密度审计各不到 1%，不足以支持再引入全局或跨 seed 缓存。

### 不将噪声当收益

同一算法关闭诊断的控制 canonical 为 9.34/s、361 FULL、289 send-ready，前一基线为 7.30/s、275 FULL、206 send-ready。未改算法也存在明显单次波动；本次没有新的同输入 Vanilla 配对，不重写[既有 Vanilla 比值](experiment-fabric_current_frontier.md)，不声称这个差距来自诊断或新增优化。开启诊断的吞吐不用于性能保留。

## 结论

H-F163-S 不进入生产多状态缓存，生产实现迭代为 0/2；已有单状态精确输入／出口守卫保留。H-F163-P 不实施能力审计缓存、元数据删减或 NoiseChunk 重写。两次有界审查均没有形成安全、足以进入端到端 ≥5% 验证的候选，按止损规则停止当前 26.3 Fabric／原版组合的追加性能工作；这不是“优化空间已数学穷尽”，也不提升正确性缺项。

当前主要剩余成本仍是结构／精确邻区地形／装饰读取及前沿依赖，既有 8/8 主世界 FULL／send-ready 为 Vanilla 的 52.35%／47.11%，高视距仍明显不足。下界／末地有限需求完成量接近原版，但延迟约两倍，不能宣传原版级性能。下一步补其余 Target 的独立状态安全、生命周期与性能门禁；正式 BOP 仍待五 Target 原版门禁完成。此次没有生产生成算法、合同、存档或 Oracle 比较规则改动。
