# BOP 本地种子隔离

## 目标

在 Minecraft 26.3 Fabric、Biomes O' Plenty、TerraBlender 与 GlitchCore 的固定组合下，使用独立 JVM、全新存档与不同宿主种子验证装饰前原生结果及 B 严格切断合同。不使用日常游玩存档，不启用 C2ME，不移植其它 Target。

## 运行

按 `inputs.toml` 登记的发布版本取得验证输入，在本机 `local.toml` 的 `[inputs]` 登记路径，并明确接受 EULA。一次只运行一个四逻辑 CPU、低优先级 Minecraft JVM。

末地因果套件复用首次装饰前 smoke 生成的 launcher；完整三顺序复现还读取 `result/causal_end/natural_witness_fca/trace.json` 这份已记录自然顺序。它只提供未改写上游枚举的见证，不作为旧读取日志的严格对照。缺少任一输入时先采集相应自然见证，不手造排列或参考方块。检查已有结果可直接执行 `verifyBopCausal`，不启动游戏。

```powershell
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Bop -Suite smoke
& components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1 -Bop -Suite smoke -Lightweight -StructureReuse -HostSeed 23
& components/worldgen_validation/scripts/compatibility-bop/Run-BopFeatures.ps1
.\gradlew.bat :worldgen_validation:verifyBopFeatures
& components/worldgen_validation/scripts/compatibility-bop/Run-BopFeatures.ps1 -OnlyFixture bop-the_end-1 -NativeRepeats
& components/worldgen_validation/scripts/compatibility-bop/Run-BopCausal.ps1
.\gradlew.bat :worldgen_validation:verifyBopCausal
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
& components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1 -Bop -FeatureOrderingVersion 2 -DevelopmentB -VerifyReload -Mutate
```

## 通过标准

- 原生 producer 使用 S(C)，consumer 使用不同宿主种子；两者的 JVM 与存档独立。
- 装饰前共九项：每维度两个实际命中 BOP stored palette 的样本，另有三个实际派生种子回归。参考路径与轻量地形路径分别在无关宿主 17、23 上比较原生输入；另检查种子诊断网格、异种子并发与宿主不变。
- B 原生控制从普通 physical scheduler 取得真实 TERRAIN 输入，再在 B 的全部写入防护下调用目标一次原生装饰入口；周围八个 writer 不执行 FEATURES。九个全新原生控制分别与两个无关宿主比较完整目标快照、feature-visible biome 与拒绝计数，不要求与九 writer 原版装饰结果一致。
- 三维度的 STRUCTURE_STARTS、STRUCTURE_REFERENCES、BIOMES、TERRAIN 检查点完整判等；保留结构 NBT、stored palette 与目标方块，不以直接采样替代 palette。
- 隐藏生产 B 世界用正常 Chunk 请求推进到 FULL，保存并重启后不重新生成 Artifact/SPAWN，保留测试修改；SPAWN 的备用解析器也使用本地种子上下文。
- 普通 Vanilla、B 的写入边界与旧 Ordering 1/2 语义保持；无跨种子状态污染，正常及异常结束后隔离绑定清空。
- 缺失输入、缺失证据与任何未批准差异均失败；本验证不代表其它版本或任意第三方生成器兼容。
- 末地因果验证单独写入 `result/causal_end/`：同一输入顺序下，独立原生与两个无关宿主的完整快照、有序读取/写入、随机 API 返回值、随机状态及续流一致；双方自然顺序须分别等于未改写的 TerraBlender 枚举和原版 FeatureSorter 输出。诊断只重放已记录的顺序，不提供参考方块、不修改种子或生产排序。原始 `feature_b/` 全部文件校验不变，因果通过不自动修改原严格 B 门槛。

## 当前限制

完整 B 的严格门槛仍未通过：末地 `end_reef` 在三个独立普通原生 JVM 中，装饰前完整 hash 一致，但第 7、9 步的 feature index 顺序不同，最终 BOP 方块数量分别为 925、895、1,026。TerraBlender 的末地 `collectPossibleBiomes` 合并无序集合，原版 `FeatureSorter` 使用其枚举顺序建立索引，而索引又参与装饰 seed。原始完整快照、排序和失败结果保留在 `result/feature_b/26.3-fabric/bop-the_end-1/`。

`-NativeRepeats` 只采集并检查独立重复的环境与装饰前条件，不把不同的装饰结果判为 PASS。没有修改上游排序、扩大实体例外或忽略任何方块差异；完整 B 核验任务继续对此失败。
