# 独立原生结构输入对照

## 目标

为 1.21.1 NeoForge 和 1.20.1 Forge 各自的紧凑结构输入建立独立基线。普通原生 world 的 STRUCTURE_REFERENCES 与 unrelated-host / same-local-seed 输入分别运行在 fresh JVM/save，比较完整 owner starts、references、实际引用的 starts/pieces/junction NBT，以及整个目标范围的 Beardifier double raw bits。只替代结构元数据载体，所有 placement、候选命中后的 Jigsaw 与 pieces 仍走本版原版算法。

此项不证明 B FEATURES、完整地形、持久化或性能，不把 metadata 载体当作生成好的邻区地形，也不将 26.3 的 Lazy Stronghold/缩锁结论套到本版。

## 运行

在仓库根目录执行；需本机已接受 EULA。工具按原生 Gradle 运行导出的实际命令，串行启动 fresh 四核、低优先级世界。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-structure_inputs/Run-StructureInputs.ps1
pwsh -NoProfile -File components/worldgen_validation/scripts/conformance-structure_inputs/Run-StructureInputs.ps1 -Target 1.20.1-forge
```

已有证据核验：

```powershell
.\gradlew.bat :worldgen_validation:verifyNativeStructureInputs
```

1.21.1 的默认关闭缩锁候选另有冷缓存并发检查。先完成本版 `-RingScope -PhysicalBiomes -NarrowLocks -Suite structures` 的独立地形输入导出，再执行：

```powershell
& components/worldgen_validation/scripts/conformance-structure_inputs/Run-NarrowStructureLocks.ps1 -CpuAffinity 61440
& components/worldgen_validation/scripts/conformance-structure_inputs/Run-NarrowStructureLocks.ps1 -CheckOnly
```

该检查用两个 fresh JVM 分别先执行候选和保守整段锁；每个 JVM 在模板缓存尚空时以五个任务对七类真实 start 各重复三次，再与另一模式逐字段比较。最多四逻辑 CPU、低优先级，不是吞吐测量；不替代独立原生地形 Oracle、FEATURES 并发或物理生命周期证明。

1.20.1 独立适配本版 HashMap palette 缓存与原生尺寸缓存字段保护，不复用新版方法包装；使用同一工具显式传 Target。每个进程以两个任务对本版六类结构各重复三次，另检查原生 pool 尺寸缓存尚未初始化；结果独立存入本 Target，合计 36 次并发判等。

```powershell
& components/worldgen_validation/scripts/conformance-structure_inputs/Run-NarrowStructureLocks.ps1 -Target 1.20.1-forge -EvidenceLabel rotation_safe -CpuAffinity 61440
& components/worldgen_validation/scripts/conformance-structure_inputs/Run-NarrowStructureLocks.ps1 -Target 1.20.1-forge -EvidenceLabel rotation_safe -CheckOnly
```

## 通过标准

- 1.21.1 原生发现七类结构 start/邻区正例、两个 terrain-only halo 和两个冻结派生种子，共 18 项；1.20.1 没有试炼密室，六类结构、一个村庄 halo 及两个派生种子共 15 项。所有指定 start 均真实有效，halo 必须实际产生非零地形适配。
- 每项 native 与 compact 进程/save 独立，local seed 相同而 consumer host seed 不同；环境、输入指纹、完整结构 NBT 和 Beardifier 原始文件逐字节一致。
- 所有 source 仅保存元数据；原生引用半径动态核对为八，不读取 physical master-seed terrain 或 physical region NBT；FEATURES 为零。
- 成功世界进入回收站；失败证据和世界保留，新增崩溃报告即失败。未通过内容不能因编译或位置发现成功而提升验收状态。
- 冷缓存缩锁检查要求两个不同 PID、相同当前输入指纹、七类非空结构、合计 42 次实际并发判等、完整 starts/pieces/references 一致，且物理已加载 Chunk 数不变、没有执行 FEATURES；实际激活守卫必须与指定模式一致。
- 1.20.1 为六类、合计 36 次；另按实际 48 个 OctahedralGroup、六个 Direction 与全部 FrontAndTop 比较原生串行和候选并发返回值，包括原生不支持的方位组合返回 null。只在任务屏障后清空/恢复诊断缓存；注入原生初始化异常必须报告，之后可恢复、初始化作用域为零。此组件不增加任何 B 快照忽略字段。
