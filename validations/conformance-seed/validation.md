# 原生种子与存档合同验证

## 目标

在真实 Forge 注册表与服务端生命周期内，检查 Seed V1 的 18 个冻结向量、五项原生随机调用链、区块坐标采样，以及 format V2 / Ordering 1 和 2 的编码、默认值与 fail-closed 行为。此项不证明地形或 B FEATURES 已移植，不伪造注册表来运行普通 JUnit。

可选的 `PhysicalPlan` 模式单独核验 1.20.1 的生产依赖元数据：从本版 `ChunkStatus.getRange` 推导闭包，与真实 `ChunkMap.getDependencyStatus` 私有方法的独立队列展开比较。只证明三组坐标 × 十二种状态的依赖与数据义务，不推进任何 physical Mosaic Chunk，也不声明生产 FULL 已接通。

可选的 `RuntimeIdentity` 模式在实际 Forge 注册表初始化后验证内存 authority 的归属、替换、关闭和原生 SavedData 编解码，不把这些组件检查提升为 Mosaic 世界加载或 FULL 验收。

`WorldLoadIdentity` 另验实际 MinecraftServer/三维度身份加载钩子、原生 SavedData.set 触发失效、显式读盘重验和 ServerStoppedEvent 之后的关闭状态。只在可丢弃普通 Vanilla 世界的 cache 中注入再移除 profile，确认重验时文件仍不存在；不注册 Mosaic generator，也不宣称 production FULL 已接通。原 17 项组件与 45 项种子断言照常执行，结果单独存入 world-load-identity，不覆盖旧结果。

## 运行

在仓库根目录执行；需已在本机配置接受 EULA。工具串行启动单个四核、低优先级服务端，并检查本次证据和新增崩溃报告。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-seed/Run-NativeSeed.ps1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-seed/Run-NativeSeed.ps1 -PhysicalPlan -EvidenceLabel binding
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-seed/Run-NativeSeed.ps1 -RuntimeIdentity
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-seed/Run-NativeSeed.ps1 -WorldLoadIdentity -CpuAffinity 61440 -Offline
```

只检查已有结果：

```powershell
.\gradlew.bat :worldgen_validation:verifyNativeSeed
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-seed/Run-NativeSeed.ps1 -PhysicalPlan -EvidenceLabel binding -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-seed/Run-NativeSeed.ps1 -RuntimeIdentity -CheckOnly
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-seed/Run-NativeSeed.ps1 -WorldLoadIdentity -CheckOnly
```

## 通过标准

- 实际加载产品和 probe，在 ServerStartedEvent 中执行全部原断言，status 为 PASS。
- 18 个冻结种子向量、原生调用续链与绝对区块原点一致；默认 Ordering 1 不变，Ordering 2 必须显式选择；未知版本和缺字段拒绝加载。
- 所有 expected/actual 保留在 result/current/1.20.1-forge/native-seed.json；真实错误导致任务失败，服务端正常保存关闭，无新增崩溃报告。
- 游戏内种子通过不提升完整生成、运行期生命周期或性能验收状态。
- 可选依赖模式的 36 项实际原生比较全部为真，含正负非原点，计划不可修改；对应当前源码指纹，单独输出 result/native-plan-binding/1.20.1-forge/native-plan.json。普通种子断言数量与既有结果路径不变。无绑定的初次失败保留在 result/native-plan，不覆盖或计为通过。
- 可选身份状态模式在独立 fresh 游戏执行原 45 项种子断言与 17 项内存/SavedData/原生维度注册表检查；单独输出 result/runtime-identity/1.20.1-forge/native-seed.json，源码指纹匹配，scope 明确不是生产世界加载。失效、同 seed 的不同 WorldOptions、跨 world 和关闭后的访问必须拒绝；三维度种子域保持分离。
- 实际世界身份模式必须另有 16 项 load/replacement/native close 断言：中间结果只能为 PENDING_SHUTDOWN，停止事件之后才可 PASS；三份已加载 ServerLevel 全部标记关闭，server authority 查询拒绝、探针持有的 level 引用清零，显式重验时 profile 文件仍不存在。只证明普通世界的真实身份钩子，不替代 Mosaic 生成。
