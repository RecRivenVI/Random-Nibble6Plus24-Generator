# 1.20.1 冷结构缩锁与原生旋转组件

调查日期：2026-10-17。H120-T 第二轮通过两种冷启动顺序及原生旋转返回值、异常清理组件；仍不等于独立完整 B、运行期或性能保留通过。

## 问题

修复第一轮的原生方向缓存提前发布后，两个并行结构任务能否保留结构元数据，并在真实异常后清除初始化状态？候选关闭时，行为注入是否真正不安装？

## 方法

使用 Forge 47.4.28、Java 17、四逻辑 CPU、低优先级，两次独立 fresh JVM/save；每次从空模板 repository 和 141 个未初始化 pool 尺寸开始。候选先行与原生整段锁先行各运行一次，六类真实结构各重复三个并行任务，与另一模式的串行原生结果比较。实际并发上限为二，不把组件的两线程当作生产并发设置。

输入指纹为 1EA12F079FCA02DC86A46521A746B48D6F88F50F8DDC502CF68FB9EC99848BC6；两次进程号分别为 80720、106100。原始结果、条件和模板冷态检查见固定组件证据（本机证据：`../../validations/conformance-structure_inputs/result/narrow_lock_concurrency_rotation_safe/1.20.1-forge`）。工具的 CheckOnly 在相同源码下再次通过。

## 发现

- 两种顺序共 36 次并发结构比较通过；StructureStart 原始 NBT、pieces、references 与 bounding boxes 相同，六份投影哈希跨 JVM 一致，物理加载 Chunk 数未改变，没有执行 FEATURES。
- 每个 JVM 另外从空旋转缓存比较全部 48 个 OctahedralGroup 的六个 Direction 和十二个 FrontAndTop 返回值，两个线程执行 64 轮。原生 API 对某些组合返回的 null 也逐项相同，不替换方向算法或填默认方向。
- 候选只保护完整原生初始化的可见性；拥有初始化作用域的线程重入原生 rotate，其余线程等待同一个枚举对象。没有新增种子、排序、RNG 消费或旋转数据表。
- 在原生初始化入口注入异常时，异常继续报告；初始化绑定清零，另一线程随后能恢复相同的原生返回值，没有遗留锁或线程局部状态。
- 关闭 startup 属性或发现未知模组时不安装四项行为 Mixin；context 同时要求实际能力标记及已审计原生配置，不能仅用可变属性假定注入已安装。四项独立六阶段 smoke（本机证据：`../../validations/conformance-legacy_terrain/result/narrow_locks_ring_scope_smoke_rotation_safe/1.20.1-forge`）通过，原生控制检查未安装能力标记。
- 第一轮[冷并发真实失败](../research/incident-forge_rotation_cache.md)继续保留，没有倒填 PASS；本轮证明也没有把共享状态竞争解释为可接受的自然随机差异。

## 结论

该组件证明本版冷缓存初始化和已选结构的缩锁边界可继续进入独立完整 B 与性能门禁。候选默认关闭；第三方模组、完整 FEATURES 并发、physical FULL/save/reload 和端到端收益尚不能由这份组件证据推出。H120-T 已用完两轮算法预算，不在后续失败时继续新增第三轮同义缩锁实现。
