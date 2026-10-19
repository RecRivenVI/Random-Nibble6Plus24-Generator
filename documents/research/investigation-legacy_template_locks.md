# 1.21.1 结构生成共享状态与锁边界

调查日期：2026-10-16。主世界前沿的两份线程快照指向完整结构检查持有模板管理器监视器。已按本版字节码提取默认关闭的缩锁候选；独立 Oracle、并发、自然运行期与性能门禁尚未完成，不宣称可默认启用。

## 问题

不能因参考 26.3 已缩锁，就假定 1.21.1 的缓存或静态结构生成器同样安全。也不能用“所有字段都是 final”证明共享对象不可变，或只保护 reset 而放任另一线程改写正在展开的 piece 权重。

## 方法

使用本机 neoforge-21.1.262.jar 的 javap 核对 StructureTemplateManager、StructureTemplate.Palette、StructureTemplatePool、Single/ListPoolElement、JigsawStructure 及原生结构实现、StrongholdPieces、NetherFortressPieces、EndCityPieces。与参考 Target 的保护方式逐项比较，不共享 Minecraft 源码。

候选 H121-T 第一轮只适配结构输入的锁边界。热点证据和原失败见[运行期实验](experiment-legacy_runtime_activity.md)。环候选状态保持不变，不借此修改 placement、Jigsaw/RNG、FEATURES、调度器或线程数。

## 发现

- 1.21.1 的 StructureTemplateManager.structureRepository 与 Palette.cache 都由 Maps.newConcurrentMap 构造；Palette 也没有 26.3 的独立 jigsaws 缓存方法。无需移植新版 palette 缓存锁。
- StructureTemplatePool.maxSize 是普通 int，getMaxSize 在首次查询时遍历元素计算并写回；getRandomTemplate 只索引池，getShuffledTemplates 调用 shuffledCopy。候选仅用池对象监视器保护尺寸缓存，不锁选择随机流或完整 Jigsaw 图。
- StrongholdPieces 的 currentPieces/imposedPiece/totalWeight、NetherFortressPieces 共享 PieceWeight 数组中计数、EndCityPieces 的共享 SectionGenerator 状态不能按 local seed 各加一把锁。候选按原生结构类型使用 JVM 级监视器，覆盖 Structure.generate 的 reset、完整展开与出口；没有重写原生算法。
- 普通 Native 的默认属性关闭时继续调用原操作；显式候选时所有同类原生共享生成状态的调用共同参与监视器，不能让未持锁的消费者与候选竞争。它不创建 Mosaic context 或改变普通世界种子。
- 已知原生结构/元素的能力检查只在 seed-local world owner 创建时完成，fork 继承该结果；未知模组、generator/manager、structure 或 pool element 保留完整模板管理器锁。BOP、Custom Skin Loader 等未证明组合不能因类名相似就开启快路径。
- Reference/input 的结构 NBT、pieces、references、Beardifier、stored palettes、密度及目标方块仍须严格对照。FEATURES 中共享 BlockSelector 的潜在竞争不能由本次 start 生成锁审计自动豁免，需在正例并发门禁检查；尚未以此添加新的忽略规则或未经复现的修复。

## 结论

H121-T 是单一、默认关闭的版本适配候选，属性为 randomnibble6plus24generator.structure.narrowLocks。原生对照不设置该属性；消费者必须记录实际激活的 context，避免“开了参数却一直回退”被误报为验证成功。只允许最多两轮实现/筛选；无同条件端到端收益或出现未解释数据差异则不保留，不继续改 Jigsaw 来追数字。通过独立正确性后才筛选性能，有希望再做三组交错 A/B，并报告 Vanilla 比值。
