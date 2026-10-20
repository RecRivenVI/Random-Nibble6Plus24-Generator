# 产品化后的 8 视距回归抽查

调查日期：2026-10-20。整理后的 B 保持已接受的性能 PARTIAL；本次单配对没有出现超出既有冷区延迟范围的突变，但不能据此证明统计上无退化或接近原版速度。不展开新的性能优化。

## 问题

生产／验证边界与共享合同逻辑整理后，需要一次端到端抽查，而不是用格式、编译或组件计时证明游戏吞吐。使用者已经接受原版 B 的性能限制，不再进行三档、多轮优化筛选。

## 方法

26.3 Fabric，四逻辑 CPU、低优先级、单个测试游戏 JVM，主世界 master seed 123456789，8/8，六十秒，两次相同固定前沿移动。普通原版与 B 各使用一个 fresh 世界；测量期间不运行构建、准备任务或其它正确性测试，用户旧验收服务端不由本线程关闭。

```powershell
& components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target 26.3-fabric -Pipeline vanilla -View 8 -Simulation 8 -Seconds 60 -EvidenceLabel productization_delivery
& components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1 -Target 26.3-fabric -Pipeline b -View 8 -Simulation 8 -Seconds 60 -EvidenceLabel productization_delivery
& components/worldgen_validation/scripts/performance-worldgen/Analyze-BPerformance.ps1 -Target 26.3-fabric -Pipelines @('vanilla', 'b') -View 8 -Simulation 8 -Seconds 60 -EvidenceLabel productization_delivery
python components/worldgen_validation/scripts/performance-worldgen/compare_native_frontier.py validations/performance-worldgen/result/b_milestone/26.3-fabric/productization_delivery --dimension overworld
```

原始条件、轨迹与归并为 `validations/performance-worldgen/result/b_milestone/26.3-fabric/productization_delivery/`，输入身份只在该目录的 conditions 中定义。提前运行的 `productization_screen` 与约十秒实例准备任务重叠，原始记录保留但不作为隔离性能对照；仅重做这一次无重叠配对。

## 发现

| 指标 | Vanilla | B | B／Vanilla |
| --- | --- | --- | --- |
| 观察窗 FULL | 525 | 393 | 吞吐 74.84% |
| 观察窗发送就绪 | 437 | 315 | 吞吐 72.07% |
| 最终视野需求发送就绪 p50 | 6.559 秒 | 27.638 秒 | 421.35% |
| 最终视野需求发送就绪 p95 | 11.513 秒 | 51.314 秒 | 445.69% |
| 平均占用 CPU 核等价量 | 0.885 | 3.158 | 不当作吞吐比 |

B 的 canonical 为约 7.98／秒；普通原版没有 Mosaic canonical 阶段，不给它虚构同名分母。最终 289 个视野请求均在窗口内就绪，B 第五十五秒就绪率约 97.58%；这不表示持续飞行即时跟上。发送就绪是服务端状态，不是实际包送达或画面呈现，也不是玩家首次加入计时。

B 的 CPU 样本为其它结构约 35.11%、Stronghold/ring 约 2.37%、装饰约 27.42%、噪声／表面／群系约 23.49%。独立 local seed、精确同种子输入与邻区 physical readiness 仍有额外代价；只报告这次观察，不把样本比例当成安全可删除的工作。两组没有记录到 server lag 警告，归并值为 null，不将缺测写成零等待。

## 结论

相对[三组既有交付成本](investigation-native_b_delivery_cost.md)，本次冷区 p50／p95 仍在既有约四至五倍的区间。单配对发送吞吐低于既有多组均值，不能从一次采样推定优化收益或证明统计等价；本轮也没有新增生成或调度优化。产品按性能 PARTIAL 交付，继续优化不是本里程碑的剩余任务。
