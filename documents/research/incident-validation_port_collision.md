# 双队列验证的端口冲突

调查日期：2026-10-16。1.21.1 精确 BIOMES 矩阵在完成 40 项后因端口占用停止；没有新的生成内容差异。验证目录独立不等于网络端口独立，后续并行还必须核对 validation.toml 的角色配置。

## 问题

矩阵在 fixture-40 的原生进程启动时出现 Failed to initialize server，未产生该项原生 summary。同期另一槽正在运行 26.1.2 的普通 Vanilla 生命周期验证。需要区分环境启动失败与生成正确性失败，并避免浪费已完成的独立对照。

## 方法

读取失败进程的完整日志、crash report、启动参数、两项验证的实例配置与当前进程列表；确认原进程终止后才处理现场。对既有四十项重新执行完整六阶段数据、结构、density/Beardifier、BIOMES 脱离、进程/存档与源码指纹核验，不只数 PASS 标签。

## 发现

- conformance-prefeatures 与 integration-production_lifecycle 的 server 角色均配置为 25589。前者实际 NeoForge 参数含 --port 25589，后者当时日志也明确绑定同一端口。
- 2026-10-16 16:53:03 的失败日志明确包含 FAILED TO BIND TO PORT 与 java.net.BindException: Address already in use。失败发生在 fixture-40 原生启动、进入目标生成前；没有该项生成对照结论。
- 完成前缀的输入指纹均为 B9CC757105D79A4E782A913610E09B9B280E603216BCA0A2A4B890CA8DB224F3；同指纹四十项完整文件核验通过，不能据此把整套 72 项标为通过。
- 原始日志、crash、未完成世界与当时结果已由续跑任务保全至 conformance-prefeatures/result/first_failure/physical_biomes_matrix/1.21.1-neoforge。原生失败日志 SHA-256 为 A3147F8826286C0F7BD090D611D2E60B3EE0AD02E274AE801D97444FBF5A96CE，原因记录为 ENVIRONMENT_FAILURE_NOT_WORLDGEN_PARITY，不冒充 PASS 或外部中断。
- integration-production_lifecycle 的端口已改为单独的 25592；其它三个可并行角色配置保持原值。新增工具回归要求这四个角色端口互异，三项布局/端口单测与五十二份 PowerShell 解析通过。
- Run-LegacyTerrain 的显式 Resume 仅接受保留了端口绑定失败日志的现场；存在内容 FAIL、源码指纹变化、不完整清单或已完成样本不构成连续前缀时拒绝。续跑复用的是完整核验过的原始独立进程证据，不复用失败世界继续生成；剩余样本仍用 fresh JVM/save。
- 修正端口后，原失败的 fixture-40 与后续 fixture-41、42、43 已分别完成对照，旧启动失败没有被改写。整套矩阵及结构套件仍须全部完成并再次核验。

## 结论

这是验证编排的资源隔离缺陷，不是 BIOMES 或结构算法的失败。后续两队列必须选择实例、世界、端口均不同的角色；同一 validation 的不同 Target 也不能仅因目录不同就直接并行。性能测量仍只保留单实例。

此修正只涉及验证配置、严格续跑与现场保全，不修改玩家生成合同、生产线程模型或通过标准。续跑未完成时保留进行中；不能用完成前缀或环境原因豁免任何内容差异。
