# 1.20.1 物理验证首错被停服摘要覆盖

调查日期：2026-10-17。已修正探针错误归档，当前源码的两个真实游戏负例均保留首错与停服次生错误，清理完成；不代表主世界 FULL 已通过，也不代替真实生成异常隔离测试。

## 问题

[原生运行期实验](experiment-forge_native_runtime.md)的默认 B 主世界在原 300 秒预算内未完成 FULL。console 中首错为 Native physical FULL observation exceeded 300 seconds，停服后 summary 的 failure 却被 Native stop followed an incomplete/failed request 覆盖，降低了归因准确性。

## 方法

只修改 NativePhysicalProbe 与验证工具，不改生产任务或取消处理。首次失败先保存 failure；停服清理照常执行并记录计数，停服异常单列 stopFailure，重复运行错误单列 additionalFailure。清理错误不能把首错改成次生错误，也不能把失败改为 PASS。

用[物理交接验证](../../validations/integration-legacy_physical/validation.md)的 FailureProof，在实际普通 Chunk 请求之后显式注入观察异常。普通 Vanilla 与隐藏 B 分别使用独立 fresh JVM 和存档，四逻辑 CPU、低优先级；无新增 crash report，原始 summary 保留 FAIL，工具只确认预期探针失败及清理已被准确记录。

## 发现

当前输入 DB8BFDE6D9DD8D1FD02A767527CC7920E0990661BADE2A1898A4106D7E1E4810，证据在 observation_failure_section_probe（本机证据：`../../validations/integration-legacy_physical/result/observation_failure_section_probe/1.20.1-forge`），运行与当前源码 CheckOnly 均完成。

| 控制 | 独立 PID | 原始状态 | 首错 | 次生错误 | 关闭后 in-flight / worker / binding |
| --- | --- | --- | --- | --- | --- |
| Vanilla | 11476 | FAIL | 注入观察异常 | 未完成请求的停服检查 | 0 / 0 / 0 |
| B | 44692 | FAIL | 注入观察异常 | 未完成请求的停服检查 | 0 / 0 / 0 |

两侧 failure 精确为 java.lang.IllegalStateException: Injected native physical observation failure，stopFailure 精确为 java.lang.IllegalStateException: Native stop followed an incomplete/failed request。较早 8A1D 条件的两个负例另行保留，不改写为当前指纹。

原始主世界 PID 53504 的结果仍为输入 4578 的 FAIL，console 与被覆盖的旧摘要原样保留。新负例并没有重跑或提升它，也不证明生产 generation exception 会被此探针故障替代。

## 结论

首错归档缺陷已修复，错误证据可以区分根因与清理阶段的后续错误。真实生成异常、意外取消和生命周期取消的行为没有修改，仍由独立的 NativeLifecycleProbe 门禁判断。成功结束负例后，工具仅将自建临时世界移入回收站，原始证据与历史失败保留。
