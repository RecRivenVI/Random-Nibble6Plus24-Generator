# Fabric 群系验证漏读最顶层四格高度

调查日期：2026-10-17。结论：这是新增运行期探针的覆盖缺陷，不是已证明的生产生成错误；原批次不具备全量群系门禁资格。产品代码没有改变。

## 问题

全 owner 运行期探针首次增加 stored biome 扫描时，将 26.3 的 getMaxY 当成了不包含上界。全部列的最顶层 quart 没有读取，而首版检查器只检查读数为正，未能拒绝这种不完整覆盖。

## 方法

读取实际 26.3 common JAR 的 LevelHeightAccessor、LevelReader、ChunkAccess 与 holder 字节码；核对原始读数，不重写 PASS 字段或哈希。加强检查器后，让已启动游戏正常保存退出，再以验证失败终止串行链；没有强杀或修改生产地形。修正只涉及当前 Target 的 RuntimeLifecycleEvidence 和工具。

## 发现

原生 getMaxY 的字节码为 getMinY + getHeight - 1，包含最高有效方块。LevelReader.getNoiseBiome 先以 BIOMES、create=false 查询 physical Chunk，存在时读取其 palette，缺失才进入 uncached fallback。验证必须确认这条真实路径，而不是直接调用 BiomeSource 采样。

原循环以 y 小于 floorDiv(getMaxY,4) 结束，主世界每 Chunk 只读 1520 格而非 1536，下界／末地只读 1008 格而非 1024。每个 Chunk 恰少十六格，即最顶层 4×4 的 quart 平面。

原批次 [all_owner_biomes](../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/vanilla-control/runtime-proof/minecraft-overworld/all_owner_biomes)共留下九份结果：主世界和下界 Vanilla/B fresh/reload 各四份，以及末地 Vanilla fresh 一份。raw status 均为 PASS，但新增覆盖门禁拒绝它们；没有末地重载或 B 结果，不计完整套件通过。原始条件指纹为 81ABB06D5390214A627FCB34B169AB19B88A398DC288561FB2FCFE5FFFA89295。原结果、日志与未完成世界保留。

修正后包括 floorDiv(getMaxY,4) 本身，并独立累计实际 section 数。每个 section 有 64 个 biome cells，消费者要求 physicalBiomeCells 等于 physicalBiomeSections × 64，且 biomeQuartCoverageVersion 为 1；不再以正计数充当全量覆盖。旧批次在显式原输入只读复核时被特定的 Incomplete physical biome quart coverage 拒绝，原 schema-1 证据则仍能以自身输入和明确范围复核。

修正前后产品 main/client、worldgen_contract 与 Target facts 的摘要均为 EFF0A7E9087FDEBDB9E1FA5E9A17DFEBA1771FAA7FD074EBB77DA8CC79EFBBD2；除 RuntimeLifecycleEvidence 外本 Target 全部 src、worldgen_contract 与 facts 的摘要均为 61106BF7881D4289E321A13C51AEF856D135E8920535A1DD1C245D055B14AED0。验证专属 Mixin 由 unchanged ValidationMixinPlugin 按 validation.name 选择，运行期组不在装饰前或独立 B 中启用。

修正后的全部 src 条件指纹为 CF39EA0E23251629AE8BFA8C0A8F985640158B9DB7606B3504EB6518FBCBE379；main/probe 条件指纹为 B072BA8B3820F46D95DCFBD71E1DC89D6CD2A564FACAAE0E93E8B5007C71BF84。它们不同于原有 72/18/19 和 schema-1 的完整输入，不把旧记录改成这些新指纹。

## 结论

按受影响门禁只重跑运行期，而非重跑未改变产品和未启用本探针的装饰前／B 入口。新条件为 all_quart_biomes，旧批次保持不完整；是否通过由当前里程碑维护，不能由编译成功外推。

本次旁查还发现部分 Overlay／locator 边界使用 getMaxY - 1；它们未在本次修改，列入已有有限世界功能的最高层边界验收，不从本探针的修复宣称这些路径已正确。A/C 暂缓，不修改它们的代码。
