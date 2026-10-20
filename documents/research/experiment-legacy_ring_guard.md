# 1.21.1 环搜索能力守卫与 BIOMES 交接

调查日期：2026-10-16。首次有限环搜索筛选后，当前能力守卫和物理 BIOMES 联合 smoke 已在八个独立 native/consumer JVM/save 通过，并另经 CheckOnly。完整矩阵与配对性能尚未通过，优化仍默认关闭。

## 问题

不受支持环境即使执行完整搜索，也不应安装有限 scope 后绕过其它模组的原操作链。首轮组件证明也需要绑定源码指纹，且必须覆盖脱离式 BIOMES，而不仅是完整地形。

## 方法

StructureGenerationContext 仅在显式候选属性开启、能力判定 NONE 时安装 scope；未知模组、BiomeSource、placement 或环 exclusion 直接保持原生未绑定 state。属性由产品常量构造，不改变世界身份。

组件导出前计算源码指纹并传入 JVM，由探针直接写进证据，工具与独立消费者再核对；不事后给旧证据补写指纹。联合模式使用独立 ring_scope_physical_biomes_smoke 目录，保留首轮结果。单实例四 CPU、低优先级执行。

## 发现

- 当前指纹 07159AC5B3512A9DBC523AD26E3380BCEF443AB7953CD1C8DB6546BEAEC3D722。1,536 个环组件正例零漏检，原生槽位 196,608、实际候选搜索 1,536；组件对照口径同[首次筛选](experiment-legacy_finite_rings.md)，不冒充独立物理 Oracle。
- 联合 smoke 四项（本机证据：`../../validations/conformance-prefeatures/result/ring_scope_physical_biomes_smoke/1.21.1-neoforge`）覆盖主世界两个样本、下界和末地，八个 fresh JVM/save；原生六阶段完整 NBT/密度/结构/群系与轻量消费者严格一致。脱离式物理 BIOMES 在 owner 关闭后完整 NBT 与原生 BIOMES 相同，四个消费者 physicalBiomesOwnerClosed 均为真；正常和异常绑定清空。
- 探针运行与独立 CheckOnly 均通过。针对 Target 的 check/verifyRelease、验证工具的二十一个 Python 测试与五十二份 PowerShell 解析通过；这不是根目录完整 check/verifyRelease 收口。
- 首轮 7BA 指纹的十八项结构、四项 B 与 117.9 秒 FULL 筛选不倒填为新指纹的当前完整门禁。新源码尚缺 72 项、结构正例、完整 B、物理运行期与配对性能；未知第三方组合没有本轮实际运行证据。

## 结论

能力守卫和精确 BIOMES 交接的有限 smoke 通过，可以进入同一源码的完整矩阵。保守回退不依赖缓存 authority，旧存档、单 writer 与边界副作用规则均未改；当前仍不默认启用此候选，也不作第三方兼容或性能保留承诺。
