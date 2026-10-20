# 1.20.1 首版游玩暂缓

调查日期：2026-10-20。有限环自动绑定确实减少了无关 biome search，但普通运行期 FULL 前沿在原三百秒内仍未完成；使用者决定首版暂缓 1.20.1 游玩支持。自动启用改动已撤回，源码、构建和失败证据保留，不扩大结构或地形优化。

## 问题

[既有归因](incident-forge_full_preparation.md)指出完整 B 准备放大及重复要塞环求值。需要判断仅把已证明的有限环绑定到真实 local seed 上下文，能否消除 FULL 阻断，而不改变 B 输出、票据前沿或超时预算。

## 方法

临时仅在已验证 Mosaic context 中绑定既有有限环，保持原生 RNG、气候状态尾部和保守范围；未知环境保留原路径。四逻辑 CPU、低优先级，普通 FULL 区域请求及 fresh／reload Vanilla 对照；不延长三百秒、不减少依赖或关闭其它结构。

运行命令：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1 -Dimension overworld -RuntimeProof -EvidenceLabel productization_native_rings -Offline -CpuAffinity 3840
```

原始结果位于 `validations/integration-legacy_physical/result/runtime_current_productization_native_rings/1.20.1-forge/`；入口为 `b/minecraft-overworld/fresh/summary.json`，输入身份为 `DD4E1B63CC56ADD0F4037FE2936B96FB3ACD0763F3957361700DBC06CDA69595`。早期失败没有覆盖。

## 发现

普通 Vanilla fresh／reload 均通过。B 实际打开 5,487 个有限环上下文，考虑 702,208 个 slot，仅执行 1,735 次 biome search；没有研究开关，也没有错误绑定到 physical ring scope。因此本次不是“代码未生效”。

B 在三百秒仍为零完成、物化、LIGHT、SPAWN、FULL，主异常是 `Native physical FULL observation exceeded 300 seconds`。关闭后 in-flight、worker 与 context binding 均归零，保留的 169 次预期取消不伪装成生成完成。

一次只读线程快照显示多个 worker 等待 `CompactStructureInputs` 经共享模板管理器的 monitor；这只是当时的等待栈，不是完整 CPU 比例，也不能据此声称锁是全部耗时。

## 结论

有限环单项接线不足以交付本版运行期 FULL。继续推进将涉及额外的分阶段交接或命中后准备工作，使用者选择首版暂缓。撤回本次自动绑定和对应探针断言，保留原有默认关闭候选；没有改 B、Seed V1、结构放置、存档格式或测试超时，也不把失败重标为通过。
