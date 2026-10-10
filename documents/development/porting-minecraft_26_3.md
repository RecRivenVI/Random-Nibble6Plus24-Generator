# Minecraft 26.3 移植

26.3 Fabric 是当前参考 Target，已通过构建、原生对照、运行回归与人工进入、探索、保存退出和重进验收。26.2 仍保留原实现、存档实例与 golden；不把游戏版本升级包装成旧版本逐块一致。

## 原版 API 变化

| 边界 | 26.3 处理 |
| --- | --- |
| NOISE、SURFACE、CARVERS | 合并为 TERRAIN，调用原版 `NoiseBasedChunkGenerator.buildTerrain`，不自行重写三个算法 |
| `RandomState` | 按新版参数顺序构造；洞穴阶段直接使用该对象的本地种子 |
| 生物群系 | 使用新版 resolver；已生成区块的 palette 仍是权威，uncached fallback 来自本地 `RandomState` |
| `WorldGenRegion` 缓存 | 原版重新映射 holder cache 为 Chunk cache；映射后传递隔离绑定，构造时种子与 resolver 均本地化 |
| 高度 provider 上下文 | 新增的 `WorldGenerationContext.of` 不再经 physical level 取 generator；绑定隔离区域时直接用本地 generator 构造原版上下文，普通世界保持原路径 |
| CarvingMask | 原版仅在 TERRAIN 内临时存在，不再保存到 ProtoChunk 或存档；snapshot 不制造不存在的持久字段 |
| Structure placement | 通用配置移至 `AbstractSpreadingStructurePlacement`；候选与 Stronghold 仍调用原版算法 |
| Structure 查询 | 按新版坐标参数接入原有 Overlay，不改变已生成区域语义 |
| SPAWN | 新版原版刷怪读取区域环境属性；保留本地种子与已携带生物群系输入 |
| 运行期环境属性 | 物理依赖中要求 BIOMES 的位置必须发布真实 canonical 区块，不能以未生成 palette 的 pass-through future 交接；已存在的 structure-only holder 在后续 BIOMES 交接时补齐物化 |

## 验证边界

旧 SURFACE/CARVERS 验证只适用于 26.2。26.3 使用 TERRAIN 前沿及 stable FEATURES 的独立原生证据，检查相同种子和顺序；不能把三个旧阶段简单别名为 TERRAIN 后宣称旧 golden 通过。

26.2 的 FEATURES 与物化 golden 也只属于原 Target。26.3 的 FEATURES 重复性检查比较本版本输出，独立原生证据负责跨路径对照；含旧 hash 的物化 golden 矩阵明确拒绝在 26.3 执行，单项物化及生产生命周期验证仍可运行。不会改写旧 hash 或把新版输出冒充旧 golden。

两版单元测试、普通 Vanilla 运行与 Mosaic 原生对照分别记录。用户已有存档不作为新版本移植试验场，人工客户端验收不使用 GUI 自动化。

人工测试发现，运行期刷怪的环境属性查询会读取物理 BIOMES 邻区。26.3 冷区 LIGHT 计划因此需要物化 Vanilla 依赖中全部具备 biome 数据的 7×7 位置，而非只物化光照的 3×3。这会增加冷区边缘的实际生成工作，不是性能优化；structure-only 外围仍不物化，每个 target 的隔离 9-writer 合同不变。不用默认 palette、physical master-seed fallback 或跳过刷怪来规避异常。

## 不变合同

Seed V1、Feature Ordering V1、Mosaic format V2、9-writer FEATURES、Structure Overlay 与已生成区域 `/locate` 不变。TERRAIN 只替换新版原版阶段入口；物化、POI、光照、SPAWN、FULL 继续使用真实 physical Chunk 生命周期。

26.2 的实验性轻量 resolver 探针未迁入 26.3，不删除其原 Target 源码、研究结果或历史证据，也不借此次移植实现 D2/V3。
