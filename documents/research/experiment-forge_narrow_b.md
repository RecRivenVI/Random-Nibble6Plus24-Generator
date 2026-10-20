# 1.20.1 缩锁候选的独立结构与完整 B

调查日期：2026-10-17。H120-T 第二轮的独立结构、地形和完整 B/脱离式交接通过；当前结果不提升为性能保留、完整并发或 physical 生命周期验收。

## 问题

本版缩锁及原生旋转缓存保护是否改变结构输入、地形适配、目标单 writer B 或已关闭 context 后的 Artifact/结构加载输入？

## 方法

冻结输入 1EA12F079FCA02DC86A46521A746B48D6F88F50F8DDC502CF68FB9EC99848BC6。两个独立验证队列分别使用四逻辑 CPU、低优先级、不同实例与端口；性能测量不与它们重叠。消费者显式启用候选并断言能力实际生效，独立原生控制不安装四项候选行为 Mixin。

结构专项保留每 fixture 两个 fresh JVM/save，比较 STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES、NOISE、SURFACE、CARVERS 的完整目标数据、结构 NBT、Beardifier 和密度切片。完整 B 每 fixture 四个 fresh JVM/save，包含两个原生重复及宿主种子 17、23；不以同进程先生成原生再执行消费者作独立证据。

## 发现

- 四项独立 smoke（本机证据：`../../validations/conformance-legacy_terrain/result/narrow_locks_ring_scope_smoke_rotation_safe/1.20.1-forge`）和十五项结构六阶段（本机证据：`../../validations/conformance-legacy_terrain/result/narrow_locks_ring_scope_structures_rotation_safe/1.20.1-forge`）均通过，当前源码 CheckOnly 复核通过。要塞组件的 1536 个正例零漏检，1000 个普通目标的完整搜索为 34/128000，父随机流及气候出口/续查保持。
- 六类实际结构为 Village、Mineshaft、Monument、Fortress、End City、Stronghold，包含邻区 start、Village 地形适配 halo、原点和非原点 blocker；1.20.1 没有 Trial Chambers，不虚构该结构。
- 本轮原生控制与既有有限环原生控制的十五项六阶段、Beardifier、密度文件共 120 份逐字节相同；候选与本轮独立原生控制也严格相同。内部对象/容器遍历不是替代最终数据比较的理由。
- 完整 B/交接（本机证据：`../../validations/conformance-legacy_b/result/structures-handoff-ring-scope-narrow-locks-rotation_safe/1.20.1-forge`）十五项全部通过，共 60 个独立 JVM/save；原始 NBT、目标内状态与副作用、九份精确装饰前输入、越界拒绝、宿主隔离及关闭后的 Artifact/结构加载/SPAWN palette 交接保持原规则。CheckOnly 再次绑定当前源码通过。
- End City 真实产生三个 Shulker，仍只应用已批准的本版构造朝向规则及 UUID 规则；四个原生进程的双向完整重现（本机证据：`../../validations/conformance-legacy_b/result/end-yaw-ring-scope-rotation_safe/1.20.1-forge`）通过。没有加入新的忽略字段、固定生产朝向或改变随机源。
- [冷并发组件](../research/experiment-forge_cold_structure_locks.md)单独通过；它不替代完整 B 的并发门禁。原始第一轮并发失败继续保留，不倒填 PASS。
- spotlessApply、check、verifyRelease、verifyCompliance 严格模式均通过，六个登记 Target 的发行构件排除探针；64 项工具测试、61 个 PowerShell 语法检查、129 篇 Markdown 检查通过，合规零失败、零警告。

## 结论

当前候选在已执行的独立输出范围内与原生控制一致，未改变 B、种子、Ordering 或旧存档合同。候选继续默认关闭；完整 72 项、physical 生命周期和完整 B 并发不能由本次专项提升通过。只有后续单实例端到端筛选与必要的三组配对满足收益、p95、内存门槛时，才考虑保留；否则记录已证明的范围并停止本假设，不进行第三轮同义算法。
