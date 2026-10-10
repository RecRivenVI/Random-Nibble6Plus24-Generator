# 世界生成验证工具

`worldgen_validation` 组件保留现有 PowerShell 编排与分析器；必须在游戏中运行的独立探针位于 `src/probe`，只随验证实例加载。产品直接引用的默认关闭验证类继续保留在产品中。

## 运行

各验证的 `validation.md` 提供仓库根目录命令。服务端首次执行前由使用者在本机 `local.toml` 声明 EULA 同意，不从日常实例复制授权文件。

```powershell
.\gradlew.bat check verifyRelease
```

## 数据

验证只使用各自的 `instance/<target>/`。脚本默认仍选择 26.2；验证配置登记了 26.3 时，可以显式选择：

```powershell
$env:MOSAIC_VALIDATION_TARGET = '26.3-fabric'
```

新结果写入 `result/current/<target>/` 固定位置，不在版本之间复用原生证据；`result/legacy/` 是不可改写的历史证据，不等于当前测试通过。临时 profiler 文件和世界不进入发行 JAR。26.2 的 SURFACE/CARVERS golden 与实验性轻量研究探针只在原 Target 运行；26.3 的合并 TERRAIN 及 FEATURES 必须使用同版本的原生对照。
