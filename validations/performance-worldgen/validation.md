# 世界生成性能与研究

## 目标

集中保存端到端 profiler、结构隔离、Lazy/Candidate 与 lightweight Oracle 研究；这些模式不是新的生产合同。

这是 Minecraft 26.2 Fabric / 26.3 Fabric 分 Target 验证。工具属于 `worldgen_validation`，独立游戏内入口属于 `src/probe`，产品 Mixin 直接链接的默认关闭 hooks 留在产品中。

## 运行

从仓库根目录执行。先由使用者在 `local.toml` 顶层写入 `eula = true`，不要代替使用者接受 EULA。只启动单个高负载 JVM；所有运行限制为 4 个可见处理器。

```powershell
& .\components\worldgen_validation\scripts\performance-worldgen\Run-LazyVerification.ps1
# 26.3 首个里程碑，分别 fresh 运行；默认全部结构开启，不使用结构禁用模式
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline b
# 同条件比较 B 无复用与会话内结构来源复用；开关仅在 probe 生效
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline b -ReferenceStructures
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline ordering2
& .\components\worldgen_validation\scripts\performance-worldgen\Run-BPerformance.ps1 -Pipeline vanilla
```

其他矩阵与子场景的脚本在同一工具目录。脚本负责隐藏 preset、输入与系统属性，不直接使用日常实例。人工客户端操作仍由使用者完成。

## 通过标准

固定资源与输入轨迹，分别报告 correctness、吞吐、等待与候选数量；D1 历史结果为 PARTIAL，不能以候选减少代替性能成功。

26.3 B 前沿测量使用四逻辑 CPU、低优先级、单 JVM、相同 8/8 与 60 秒轨迹，结果在 `result/b_milestone/26.3-fabric/<pipeline>-v<view>-s<simulation>`。普通 PLAYER ticket 驱动生产请求；不登录假玩家、不发客户端 packet、不修改 scheduler。CLI 跳过初始出生准备，不能作为首次玩家就绪或实际渲染证据。`ordering2` 是保留的 scheduler Ordering 2 路径，不冒充单独的原 B 模块。错误运行不能计入吞吐结果。

按输出 JSON、异常链、资源与副作用断言判断，真实错误不能被吞掉。构建通过不代替 Runtime、Functional 或 Visual 通过。

## 结果

新结果写入固定 `result/` 位置，同一场景覆盖。实例目录为 `instance/<target>/<角色>/`；旧研究运行目录原地保留并忽略。`result/legacy/` 原样保留历史证据，不能据此宣称当前版本通过。
