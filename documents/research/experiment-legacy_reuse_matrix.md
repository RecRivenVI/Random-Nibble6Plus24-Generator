# 1.21.1 状态安全来源复用完整矩阵

调查日期：2026-10-17。当前源码的 72 项六阶段独立 Oracle 与只读源码绑定复核通过；候选仍默认关闭，不提升为性能保留、完整并发或 Target 收口。

## 问题

结构来源复用已经覆盖代表结构和自然运行期，但不能用旧输入的 72 项基线证明当前候选。仅检查 native 与 lightweight 的指纹彼此相等，也不能排除两边同时陈旧。

## 方法

冻结 1.21.1 Target 源码，按现有 72 个 fixture 分别建立原生控制和无关宿主轻量路径；每个角色使用独立 JVM 与 save，共 144 个。四逻辑 CPU、低优先级，不与性能测量重叠。

比较 STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES、NOISE、SURFACE、CARVERS 的完整阶段数据、结构 NBT、stored biome frontier、Beardifier 与原始 density bits；不执行 FEATURES。候选实际启用有限环、窄锁、物理 BIOMES 和状态安全 source reuse。

CheckOnly 默认重新计算当前 Target 的 main/probe、合同源码及 target.properties 指纹，并要求两侧证据匹配该值；归档复核须显式指定历史指纹且只读。运行结束也拒绝源码漂移。不改变生成或比较规则。

## 发现

| 维度 | fixture | 当前源码严格对照 |
| --- | --- | --- |
| 主世界 | 原点 8、非原点 32 | 40/40 PASS |
| 下界 | 16 | 16/16 PASS |
| 末地 | 16 | 16/16 PASS |

输入指纹为 `C146E59F249A7186F3984CF4B97710635D3DE8E75EFD16BE3AC5CB90B5A22FB4`。完整执行与独立 CURRENT_SOURCE CheckOnly 均 PASS；[原始证据](../../validations/conformance-prefeatures/result/source_reuse_narrow_locks_ring_scope_physical_biomes_matrix_source_state/1.21.1-neoforge)保留每个角色输出。

额外传入全零历史指纹时，工具在第一份不匹配证据处明确拒绝，未启动游戏且 fixture 清单 hash 不变。这是故意的复核负例，不是生成失败。原有历史证据不重写、不倒填当前指纹。

## 结论

当前候选的装饰前 72 项门禁通过。source reuse 的气候搜索入口/出口状态和可变结构独占约束仍不可放松；未证明版本继续原生回退。该结果不解决已记录的 p95/内存保留门禁，也不重启已结束的 H121-U 算法预算；后续只补缺失的并发、出生与世界功能门禁。
