# 1.20.1 原生结构共享状态与锁边界

调查日期：2026-10-17。H120-T 第一轮按本版实际字节码建立默认关闭候选；字段与调用链审计不能代替独立 Oracle、并发与端到端性能验收。

## 问题

有限环消除大量昂贵搜索后，普通票据前沿仍被完整 createStructures 的共享模板管理器监视器限制。哪些共享状态必须继续保护，哪些锁只是覆盖范围过大？

## 方法

使用实际 Forge 47.4.28 patched JAR 与 Java 17 javap，核对 StructureTemplateManager、StructureTemplate.Palette、StructureTemplatePool、ChunkGenerator.tryGenerateStructure 和原生结构实现。只对照其它 Target 的职责，不复制其 MixinExtras 包装或假定缓存类型相同。

## 发现

- repository 在构造器中使用 Maps.newConcurrentMap；本版 Palette.cache 却是 Maps.newHashMap，与 1.21.1 不同。原生 blocks(Block) 只有 Map.computeIfAbsent 访问该缓存，候选在 palette 自身监视器内执行原操作，不替换容器或排序结果。
- pool maxSize 是普通 int，原生 getMaxSize 有两次读取、一次写入；原生元素在固定模板输入下计算相同的整数尺寸，不消费 RNG。候选只对这些字段访问建立同一池监视器的内存边界，保留原生 stream 与 bounding-box 算法；允许冷态重复求值，不锁整个 Jigsaw 图，也不依赖 synthetic lambda 名。
- Stronghold 的 currentPieces/imposedPiece/totalWeight、Fortress 的共享 PieceWeight 计数、End City 的 shipCreated 必须覆盖 reset、完整展开和出口。本版 ChunkGenerator.tryGenerateStructure 的 Structure.generate 调用是窄接入点；按类型分别持有 JVM 级监视器，synchronized 在异常出口也释放，不吞异常。
- getRandomTemplate 只索引原池，getShuffledTemplates 使用原生 shuffledCopy。已知模板与 pool 元素在生成期间只读；尺寸计算不持有池监视器，因此不新增 pool→palette 的嵌套锁顺序。
- 能力检查在 seed-local context 创建时完成。未知 mod、biome source、generator、manager、Structure 或 pool element 保持原有完整管理器锁；没有新增 seed cache 或跨 world authority。默认关闭时普通 Vanilla 继续原生操作。显式候选下，共享原生静态生成状态的正常参与者使用同一类锁，而不是各 seed 加不同锁。
- FEATURES 的共享 BlockSelector 等状态仍须在后续完整 B 并发门禁单独检查；本次 start 锁审计不自动证明它们安全，也未据此修改放块或随机算法。

## 结论

候选只改变已审计共享状态的保护范围，不改变 placement、pieces、Jigsaw、气候查询、B 或旧存档。先执行两种冷缓存顺序和独立六阶段结构对照，再做完整 B/生命周期及单实例性能筛选；仍使用最多两轮预算和统一保留门槛。未完成上述门禁前默认关闭，不从字段审计或内部调用增加推定性能通过。
