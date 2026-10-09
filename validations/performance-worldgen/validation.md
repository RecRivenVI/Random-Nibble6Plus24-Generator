# 世界生成性能与研究

## 目标与范围

集中保存端到端 profiler、结构隔离、Lazy/Candidate 与 lightweight Oracle 研究；这些模式不是新的生产合同。

源码树不附带性能测试存档或采样运行数据。使用 `-PperformanceProbe` 选择客户端运行位置，测试前重新准备独立世界并核对视距/模拟距离。固定 32/32 对照和其它资源配置必须分别记录；已使用的世界不等于 fresh benchmark，不应把归档结果当新测试结果。

这是 Minecraft 26.2 Fabric 单 Target 的验证。与 Vanilla Oracle 的比较属于生成合同符合性，不表示跨 Target/Loader parity。原生 Java 验证入口有一部分由生产 Mixin 显式 gated 调用，继续保留在 `versions/26.2-fabric/src/main`；外部驱动与测试 Mod 程序集中在本项目的 `task/`。

## 从仓库根目录操作

需已有 Java 25、当前 Wrapper/依赖，以及已由用户接受的 `instances/26.2-fabric/server/eula.txt`。不要擅自接受 EULA。先阅读脚本参数；它可能创建、保存或清理自己命名的临时世界。正式用户世界不能用作可丢弃 fixture。

```powershell
Get-Help .\\validations\\performance-worldgen\\task\\Run-StructureIsolation.ps1 -Detailed
.\\validations\\performance-worldgen\\task\\Run-StructureIsolation.ps1
```

脚本是完整入口，内部使用 `:version:26.2-fabric:runServer` 并选择对应验证实例。不要只复制脚本中的半条 Gradle 命令而遗漏 JVM properties、preset 或输入文件。测试 Mod 只在对应显式 Gradle property 下加载；JUnit 仍由根 `test` 任务运行。

同目录其余任务由脚本文件名和参数表达矩阵、重载、采样或分析子场景；保留其原有入口，不要求全部一次运行。性能/压力验证必须限制资源、单个高负载 JVM，禁止 GUI 自动化替用户验收。

## 实例、输出和历史证据

- `instance/server` 为本验证的服务端运行目录；额外 client、研究模式和子世界按任务分别保留，不合并。
- 新输出位于 `instance/output` 下；`instance/evidence` 中保留的原始诊断材料不是运行依赖。
- `result/legacy`（如存在）保留合同/生命周期回归基准及性能对照 JSON，记录的版本、失败、SHA 和输入不改写。它们用于结果比较与诊断，不是当前运行输入；当前 manifest 和路径关联文件应由任务重新生成。
- 实例内容默认 ignored，长期结果不含游戏存档或凭据。构建、目录和运行入口见 [开发与验证](../../documents/development/guide-development.md)。

## 验收

固定资源与输入轨迹，分别报告 correctness、吞吐、等待与候选数量；D1 历史结果为 PARTIAL，不能以候选减少代替性能成功。

按任务输出 JSON、异常链与资源/副作用断言验收。人工/视觉结果只能由用户确认。归档结果仅证明其记录的版本和输入；当前源码的 Runtime / Functional / Visual 状态须由重新运行确认，不能由构建成功代替。
