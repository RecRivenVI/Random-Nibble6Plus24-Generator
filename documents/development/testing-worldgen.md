# 世界生成验证工具

`worldgen_validation` 组件保留现有 PowerShell 编排与分析器；必须在游戏中运行的独立探针位于 `src/probe`，只随验证实例加载。产品直接引用的默认关闭验证类继续保留在产品中。

## 运行

各验证的 `validation.md` 提供仓库根目录命令。服务端首次执行前由使用者在本机 `local.toml` 声明 EULA 同意，不从日常实例复制授权文件。

```powershell
.\gradlew.bat check verifyRelease
```

## 数据

验证只使用各自的 `instance/26.2-fabric/`。新结果写入 `result/current/` 固定位置；`result/legacy/` 是不可改写的历史证据，不等于当前测试通过。临时 profiler 文件和世界不进入发行 JAR。
