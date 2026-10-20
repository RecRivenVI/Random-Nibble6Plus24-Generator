# 1.20.1 原生装饰后半成品恢复

调查日期：2026-10-17。默认 B 的下界与末地已通过真实 INITIALIZE_LIGHT 保存、新 JVM FULL 恢复及再次重载，共十二个独立游戏 JVM；不代表首次出生、维度往返、碰撞输入或性能收口。

## 问题

完整 FULL 保存重载不能证明中断在装饰与刷怪之间的存档可以恢复。SPAWN 使用的同种子 stored-biome 快照只在内存中携带，冷恢复必须证明原生回退不会改变该输入，也不能重新生成目标 Artifact 或覆盖修改。

普通 Vanilla 的 FEATURES 结果还可能被随后执行的邻区 writer 修改；因此不能直接把普通部分生成进度当作稳定 canonical FEATURES。

## 方法

使用[物理交接验证](../../validations/integration-legacy_physical/validation.md)的 PartialPreLight、section_causal 条件。源码与输入指纹为 DB8BFDE6D9DD8D1FD02A767527CC7920E0990661BADE2A1898A4106D7E1E4810；未启用有限环、缩锁或其它性能候选。

每个维度分别建立普通 Vanilla 与隐藏 B 存档，每侧使用三个独立 JVM。fresh 根据本版 ChunkLevel.byStatus 取得实际票据强度，只普通请求 INITIALIZE_LIGHT；原生生成并保存 ProtoChunk，不请求 FULL 后手工降级，不编辑磁盘状态。resume 先直接读取实际 ChunkMap 磁盘 NBT 确认状态与快照，再普通请求 FULL；reload 从已经保存的 FULL 再次启动。

方块、群系单元通过本版原生 codec 解码后逐项比较，结构完整 NBT 严格比较；测试金块独立标记玩家修改。B 冷载得到的八邻区 stored-biome 快照逐项对照 fresh 携带输入。新依赖 Chunk 允许各自生成，目标 Artifact 计数单列。

Vanilla 的实际磁盘加载注册目标 section 身份；只在原生 applyBiomeDecoration 期间观察 LevelChunkSection.setBlockState 返回的旧状态和原始新状态。覆盖 WorldGenRegion 与 OreFeature/BulkSectionAccess 的直接写入，不增加或替换放块调用，不改变随机数或执行顺序。每个位置的全部实际状态转换必须构成完整首尾链，未被记录解释的变化仍失败；不把回调到达顺序伪装成原生执行顺序。此规则仅解释普通 Vanilla 的新增邻区写入，B 和最终 FULL 重载仍要求全部实际单元不变。

正确性最多两个游戏 JVM 并行，各四逻辑 CPU、低优先级，实例与端口独立；性能不在此负载中测量。运行中 Target 与合同源码冻结。

## 发现

### 恢复结果

证据固定在 partial_pre_light_section_causal/1.20.1-forge（本机证据：`../../validations/integration-legacy_physical/result/partial_pre_light_section_causal/1.20.1-forge`）。两维度各六个独立 JVM，执行后均已通过当前指纹 CheckOnly。

| B 维度 | local seed | fresh 目标生成 / SPAWN | resume 目标生成 / SPAWN | FULL reload 生成 / SPAWN | 冷载群系逐项比较 |
| --- | --- | --- | --- | --- | --- |
| 下界 | -8648803285809077725 | 1 / 0 | 0 / 1 | 0 / 0 | 一次、相同 |
| 末地 | -5783970183263702423 | 1 / 0 | 0 / 1 | 0 / 0 | 一次、相同 |

共同 master seed 为 123456789，目标为 (125,-37)。B fresh 各生成并物化 25 个实际依赖，目标停在 INITIALIZE_LIGHT，LIGHT/SPAWN/FULL 均为零；resume 各生成另外 24 个新依赖并完成目标 LIGHT/SPAWN/FULL，目标本身没有重生成。最终 FULL reload 的 canonical、publication 与 SPAWN 均为零。

每个 resume/reload 实际观察到 16 个磁盘 section 身份，目标修改、方块、群系与结构保持；关闭后 worker、in-flight、generation binding 和探针 writer scope 均为零。普通 Vanilla 的 Mosaic canonical/物化计数为零。成功的临时世界由验证工具移入回收站，证据保留。

### 原始失败与观测边界

输入 9054 的首次 PartialPreLight 控制，Vanilla fresh 通过，resume 报目标投影变化；原始 FAIL 与世界保存在 partial_pre_light（本机证据：`../../validations/integration-legacy_physical/result/partial_pre_light/1.20.1-forge/vanilla/minecraft-the_nether`）及对应 first_failure 中。磁盘输入相同，推进 FULL 后有 143 个真实方块变化，包括诡异疣块、藤蔓、砂砾和岩浆块；不能因此判定 Mosaic 重载重生成。

输入 9AC0 的 WorldGenRegion 放块观察记录了 138 次实际写入，仍不能解释砂砾等变化，原始 FAIL 保留在 partial_pre_light_causal（本机证据：`../../validations/integration-legacy_physical/result/partial_pre_light_causal/1.20.1-forge/vanilla/minecraft-the_nether`）。本版 OreFeature 使用 BulkSectionAccess 直接调用 LevelChunkSection.setBlockState，绕过 WorldGenRegion.setBlock。

当前 section 观察条件下，Vanilla 下界 resume 的 180 次实际邻区写入完整解释最终所有方块变化，群系与结构未变；末地本次无新增目标写入。Vanilla 最终 FULL 重载与自身 resume 保存状态严格一致。B 两维度没有任何目标投影变化，不采用 Vanilla 邻区解释来放宽 B 合同。两份失败没有改写为 PASS，也未扩大实体 canonical 例外。

### 装饰前回归

当前 DB8B 的四项六阶段原生/轻量对照（本机证据：`../../validations/conformance-legacy_terrain/result/legacy_terrain_smoke_partial_section_probe/1.20.1-forge`）全部通过及 CheckOnly，覆盖主世界 origin/non-origin、下界和末地；不替代正式 72 项。9AC0 的同类四项也已独立完成；9054 编排中仅完成两项后进程与句柄均不在场，该目录保留为不完整，不计整套通过或世界生成失败，不以其余运行补写原指纹。

## 结论

本版默认 B 的这两个半成品保存恢复门禁已闭合；没有生产算法、存档 authority、SPAWN 随机规则或生成合同变更。普通 Vanilla 的后续邻区装饰必须与 B 的严格切断分开验收。

主世界原 300 秒未完成 FULL 的证据仍未通过，本项没有重跑或提高预算。初次出生、维度往返、完整并发与冷碰撞组件仍须分别验证；正确性负载耗时不作为性能改善或相对 Vanilla 吞吐比值。
