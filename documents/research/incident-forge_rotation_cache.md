# 1.20.1 冷结构并发中的旋转缓存提前发布

调查日期：2026-10-17。H120-T 第一轮真实冷缓存并发失败，候选未通过、继续默认关闭；本记录保留失败，不把顺序 Oracle 通过当成线程安全证明。

## 问题

恢复产品 Mixin 的正式启动配置后，两个并发结构任务在原生 Jigsaw 方块旋转中读到空 Direction，是否为新的结构算法差异？

## 方法

使用当前输入 ED1826663BFD2DE5A3B5C1FDCEC111B902C30CD071C1FA5DCE3F15CFF73FE0AE 的独立冷缓存 JVM，四逻辑 CPU、低优先级，真实六类 start 任务重复生成。检查原始 crash report、完整异常链及 Forge 47.4.28 的 OctahedralGroup.rotate 字节码；未捕获后继续、未填补默认方向，也未改动原始结果。

## 发现

真实失败在候选先行结果（本机证据：`../../validations/conformance-structure_inputs/result/narrow_lock_concurrency/1.20.1-forge/candidate_first`）：初始模板 repository 为空、141 个 pool 尺寸缓存未初始化，产品与探针 Mixin 均已加载。异常链为 JigsawPlacement→SinglePoolElement.getShuffledJigsawBlocks→StructureTemplate.filterBlocks→JigsawBlock.rotate→OctahedralGroup.rotate(FrontAndTop)→FrontAndTop.lookupKey，空 Direction 触发 NullPointerException。

原生 rotate(Direction) 在字节码偏移 13 把新建的空 EnumMap 写入 rotatedDirections，偏移 105/112 才在循环中填充六个方向；另一线程在偏移 1 看到非空后跳过初始化，偏移 125/129 可以读到尚不存在的键。这里没有随机抽取、种子或高度计算，缺口是共享惰性缓存的初始化可见性。不能把这个生产并发缺陷归为可豁免的原版自然波动。

较早一次失败仅由旧启动参数缺少产品 Mixin 导致 StructurePlacementAccessor 类型转换错误；完整证据与原始世界独立保全于缺失 bootstrap 的目录（本机证据：`../../validations/conformance-structure_inputs/result/narrow_lock_concurrency_missing_bootstrap/1.20.1-forge`）。正式 prepareValidationConformanceStructureInputsServerRun 刷新参数后，工具增加产品/probe 配置检查；该启动失败不代替本次真实并发结果。

## 结论

第一轮同步范围仍不完整。下一轮只可为原生方向缓存增加初始化完整可见性保护，继续调用原生旋转算法，并覆盖并发与异常清理；不得替换方向表、返回默认值或改动原生 RNG。最多第二轮预算，不以多次改名或延长等待逃避停止规则。顺序六阶段套件仅证明对应顺序范围，不能推翻本失败；FEATURES 与性能门禁仍待验。
