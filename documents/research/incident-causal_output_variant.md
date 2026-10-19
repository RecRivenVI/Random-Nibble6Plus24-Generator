# 因果探针的目录参数遗漏

调查日期：2026-10-16。新增环搜索验证目录时，工具最初只改变自己的输出路径，没有把 variant 传给游戏进程；首个角色仍写入既有 end-yaw 目录。已补齐参数传递并在独立目录完整重跑，不把旧目录伪装为原指纹的完整证据。

## 问题

新增 RingScope 分流的 native_a 进程正常退出，但工具找不到预期 summary。仅改工具路径不足以隔离游戏内证据。

## 方法

核对启动参数、NativeFeatureVerifier 的 variant 默认值、两个目录内 summary 的 inputHash，以及初始 baseline/first_failure。修正工具始终把同一个 evidenceVariant 显式传给游戏，并添加静态回归；不改游戏算法、随机流或验收规则。

## 发现

- 原启动参数只有 suite=end-yaw，没有 variant。游戏默认写入 integration-world_identity/result/end-yaw/1.21.1-neoforge/end_city-start/native_a，新工具却读取 end-yaw-ring-scope，因缺文件失败。
- 这一次将旧目录 native_a 的部分原始文件写成新输入指纹 7BA70184B3F8FB59FD7DFC16A4C877FFC7884D614DDE47565B57A1AC36CAB8EB；不能再把该目录当作原 AC6E 指纹的完整四角色证据。原聚合说明与其它角色不是恢复缺失 raw 的依据，不手工重建或倒填。baseline_initial 与 first_failure 中 9B610E 原始证据仍保留。
- 修正后四个独立 fresh JVM/save 完整执行 native_a、native_b、native_a_with_b、native_b_with_a，双向重现通过，证据位于 [end-yaw-ring-scope](../../validations/integration-world_identity/result/end-yaw-ring-scope/1.21.1-neoforge)。这是原生实体构造随机性的组件因果控制，不是 B 或完整地形通过。
- 工具现在显式传递 variant=$evidenceVariant，目录与游戏内命名只有一个来源；Python 回归检查两端都使用该变量。新增验证路径不再依赖 Java 默认目录。

## 结论

路径隔离必须核对启动参数与实际产物，不能只看脚本变量。新增目录的首次运行失败保留为工具失败；旧因果目录不再计入可当前复核的原指纹完整证明。后续使用新目录的独立四角色证据，不改写历史失败，也不据此扩大任何实体忽略规则。
