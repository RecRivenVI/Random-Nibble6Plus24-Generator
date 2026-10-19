# 旧版轻量地形独立对照

## 目标

验证 1.20.1 Forge 的轻量结构、生物群系、噪声、表面与洞穴输入，与本版本独立原生世界完全一致。仅保留一个完整目标和八份真实 stored-biome 载体；不运行 FEATURES，不启用未经验证的生产生成器。

## 运行

仅为探针接线改动补独立原生 smoke 时，可用 EvidenceLabel 隔离默认 eager 路径，不启用环或缩锁候选；不覆盖已有失败，也不将四项 smoke 当作完整 72 项。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.20.1-forge -Suite smoke -EvidenceLabel runtime_probe -Offline -CpuAffinity 61440
```

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.20.1-forge -Suite smoke
& components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.20.1-forge -Suite structures
& components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.20.1-forge -Suite matrix
.\gradlew.bat :worldgen_validation:verifyLegacyTerrain
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.20.1-forge -Suite smoke -RingScope -Offline
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1 -Target 1.20.1-forge -Suite smoke -RingScope -CheckOnly
```

工具先运行模板原生验证任务导出启动参数，再串行启动 fresh JVM/save。原生控制使用 S(C)，消费者使用不同宿主种子。固定结果目录为 result/legacy_terrain_ 各套件及本 Target，首次失败、原始 NBT、日志和失败世界保留；成功临时世界移入回收站。仅传递 Forge 必需的 MOD_CLASSES，不复制整个进程环境。

验证使用四逻辑 CPU、低优先级、单实例，Gradle 最多两个 worker。原生 SRG refmap 与探针配置仅属于此项验证，日常运行不加载 probe。

RingScope 是默认关闭候选，独立结果写入 ring_scope_ 对应套件，不覆盖原生基线。smoke 的导出运行另验证四个原生完整环、1,536 个贴近真实 Stronghold 的正例和 1,000 个普通目标；记录父随机 API 的返回值、入口/出口、fork 状态及在副本上的续流。组件同 JVM 对照不代替每个六阶段 fixture 的独立 JVM/save 原生 Oracle。

## 通过标准

- smoke 四项，完整矩阵 72 项（主世界 40、下界 16、末地 16），本版本六类结构及邻区/地形 halo 共 15 项。
- 六个阶段完整原生 Chunk NBT、owner/referenced starts、pieces、junctions、references、九份 stored palette 与可见群系集合严格一致，不新增忽略字段。
- 原生 doFill 实际切片 double raw bits 和 Beardifier 输入逐 bit 相同；不是重新计算噪声冒充原生观测。
- 每个消费者只有一个完整 ProtoChunk、八份 biome-only 载体、289 个 carver 配置源和零 generation holders；无物理宿主修改、无构造器绑定泄漏。
- FEATURES 调用次数为零，真实异常保留并使任务失败；任何缺失证据、新崩溃或未经解释差异均不通过。
- RingScope smoke 须完成 1,536 个正例、1,000 个普通目标与四个完整原生环，零漏检；父随机 API、入口/出口、fork 和续流严格一致，supplier 绑定清空，越域查询及注入异常不被吞掉。组件预算固定 180 秒，超时不计通过；六阶段仍使用独立世界对照，不用组件结果替代。
- `-EvidenceLabel climate_tail` 另验证群系搜索的双向原生入口/出口 leaf 与后续查询：唯一最近记录才允许原生尾查询，等距、重复记录、未知 ParameterList 或无法证明数值安全时保持原生路径；保留完整搜索被省略后的原始组件反例，不将其称为默认世界差异。
