# 世界身份生命周期接线验证

## 目标

验证 1.21.1 原生服务器的世界身份 load/replacement/reload/close 接线，及其对三维度严格 B 模块与脱离式交接的零侵入。只证明身份与输入交接边界，不以此宣称生产运行期门禁通过。

## 运行

```powershell
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -CpuAffinity 61440
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -Preparation shared -Handoff -IdentityLifecycle -CpuAffinity 61440 -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -Preparation shared -Handoff -IdentityLifecycle -CheckOnly
```

第一条在尚无 fixture 时只调用原生 ExportOnly 发现入口，然后独立运行四 JVM 朝向因果守卫。第二条在四个 fixture 各运行两个原生、两个无关宿主，共十六 fresh JVM/save；与旧版完整结构队列分开实例、世界、结果和端口。四逻辑 CPU、低优先级，不同时测量性能。亲和性掩码必须在当前机器可用；此验证不改生产线程模型。

工具复用已有 worldgen_validation 的严格 B/Artifact 对照，不复制第二套生成器。只在专用 probe 世界中临时替换自定义身份数据，验证三维度立即拒绝过期 authority、混合身份 fail closed、显式重读恢复普通世界；不改 WorldOptions、地形、生成器或存档合同。默认实例和普通产品不加载该探针。

## 通过标准

所有严格 B 判等、关闭 owner 后的 Artifact/完整 stored SPAWN palette 交接保持通过。每个 JVM 的 identity.json 要求实际三维度、10,000 次内存查询、SavedData replacement 失效、混合 profile 拒绝、显式重读恢复原版且 WorldOptions 对象不变。identity-close.json 在真实 server stop 后要求关闭状态拒绝继续查询；两个文件 PID 必须与该次 B 结果相同。旧证据与新验证结果隔离，不继承其他 Target 的通过状态；任何崩溃、缺失证据或新差异失败。

## 缩锁候选的独立 B 对照

本实例也为默认关闭的 1.21.1 缩锁候选提供独立端口与世界。以下入口不执行身份替换注入，不能替代上面的身份生命周期门禁；结果使用独立 `template_lock` 标签，不覆盖普通或守卫后证据。

```powershell
& components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -RingScope -EvidenceLabel template_lock -CpuAffinity 3840
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -Suite structures -Preparation shared -Handoff -RingScope -NarrowLocks -EvidenceLabel template_lock -CpuAffinity 3840 -Offline
& components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1 -Target 1.21.1-neoforge -ValidationName integration-world_identity -Suite structures -Preparation shared -Handoff -RingScope -NarrowLocks -EvidenceLabel template_lock -CheckOnly
```

每个 fixture 仍需两份 fresh native 与两个无关宿主的完整 B/Artifact 判等、feature seed 序列和可见群系一致。原生不启用缩锁，消费者必须实际激活候选；候选 context 数等于 owner 与 borrowed context 总数，正常退出后绑定为零。未知环境回退成功不能冒充候选已通过。

同目标结构 source 复用候选增加 `-ReuseSources -EvidenceLabel source_state`，其余缩锁/共享/交接条件不变；朝向因果守卫也使用相同标签。消费者必须记录 81×289 次查询、441 个唯一 source、非零命中、原生 RTree 入口不同时的精确重算和命中后的出口重放、至多 441 个驻留载体、目标独占移交、返回前作用域关闭且驻留为零，并检查目标与八邻区的可变 starts/pieces 不共享身份。没有这个气候树的原生 source 不伪造其状态。原生仍不开启候选；该模块通过不提升为性能或默认启用。

1.21.1 的 B 交接同时逐方块/流体核对八份同种子 CARVERS 碰撞数据，含高度区间外、独立组件的修改隔离和 owner 关闭后稳定性；`spawn-collision-before.json` 与 `spawn-collision-after.json` 必须齐全。修复后的独立证据使用 `spawn_packed` 标签，因果控制也用该标签，不覆盖 `spawn_collision` 失败。组件输入不冒充自然世界正例；原生 NaturalSpawner 的实际邻区读取由性能前沿另行记录，冷回退和运行期仍单独验收。
