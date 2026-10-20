# 1.20.1 完整结构 Artifact 交接对照

调查日期：2026-10-16。十五项完整结构套件已完成，独立 CheckOnly 复核通过；结果支持脱离式 Artifact 和八邻区 stored SPAWN palette 交接，不支持尚未接线的生产 FULL 或可玩性声明。

## 问题

四项 smoke 中没有足够的结构加载与 BlockEntity 正例。需要在真实结构、邻区 start 和跨区块投影中证明：关闭隔离 owner 后仍能完整还原目标，并且结构 loader 使用 S(C)，不是无关宿主 seed。

## 方法

输入指纹 2B59B439AF8A15D8AF00CD57FA304317850588E9091BB0B60F71B0F485CDC5DF，运行 structures-handoff-artifact（本机证据：`../../validations/conformance-legacy_b/result/structures-handoff-artifact/1.20.1-forge`）。每 fixture 两个独立原生世界、两个无关宿主 17/23，共 60 个独立 JVM/save；每实例四逻辑 CPU、低优先级，与另一独立正确性队列并行，不作为性能实验。

覆盖本版本实际存在的村庄、矿井、海底神殿、下界要塞、末地城、要塞及各邻区场景，再覆盖原点、非原点和末地柱。没有为 1.20.1 伪造 Trial Chambers。生成端仍独立准备原生 B 输入，没有把其它版本的轻量优化视为已经移植。完成后单独以同参数加 CheckOnly 再读证据，不启动游戏、不改写结果。

## 发现

- 15 个 PRODUCED、45 个 PASS，输入指纹一致。30 个宿主消费者全部在 owner 关闭后通过 Artifact 往返、stored SPAWN palettes、误用拒绝与物理宿主隔离断言。
- 每个宿主的十五项合计有 4 个实体、2 个 BlockEntity 位置、793 个 block ticks、1896 个 fluid ticks、14720 个后处理位置。已实例化 BE 和 pending NBT 各有 2 个，属于同坐标共存，不能加成 4 个不同 BE。
- 两个宿主各有 6 次真实 structure-start load；村庄、矿井、神殿、下界要塞、末地城和要塞 start 都观察到 seed 0，等于各 fixture 的 localSeed，不等于宿主 17 或 23。邻区投影没有 owner start 时不虚报 loader 调用。
- Artifact 本进程往返保留完整 raw entity NBT，包括 UUID 与朝向。独立进程 B 对照仅使用已批准且在本版本重新双向证明的有限原生实体规则；因果 replay 使用完整 UUID-only 比较，不把它冒充全部 B parity。
- 两个消费者都核对脱离后的八份 stored palettes；不持有旧 session、NoiseChunk 或 RandomState。真实 ticks、BE、实体和后处理数据均由原生生成取得，不是手工构造的生成覆盖。

## 结论

完整结构 Artifact/群系快照交接门禁通过，补足 smoke 缺少的结构加载和 BE 正例。未接生产 generator codec、POI/LIGHT/SPAWN/FULL 与保存重载，不据此宣称 1.20.1 可以进入 Mosaic 世界。当前传输仍拒绝未验证的模组扩展和特殊 ProtoChunk；第三方兼容与性能必须独立验证。
