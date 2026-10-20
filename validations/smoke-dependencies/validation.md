# 稳定依赖更新冒烟

## 目标

验证五个主验收 Target 在当前加载器和产品下可以正常建立普通原版世界、进入单人世界、正常保存并关闭。只证明依赖集成，不代替 B Oracle、Mosaic 玩家门禁或性能测量。全程使用原生服务器和 Quick Play，不使用 GUI 自动化、不改用户存档、不装额外模组。

## 运行

从仓库根目录逐端执行；工具先准备专用普通服务器世界，正常停服后复制到同验证的客户端，原版 Quick Play 进入，探针观察真实玩家和收到的区块后正常关闭。

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/smoke-dependencies/Run-Dependencies.ps1 -Target 26.3-neoforge
```

Target 可替换为本验证登记的其它目标。每次只运行一个游戏 JVM、四逻辑 CPU、低优先级。结果固定在 result/Target；成功只留摘要，失败保留日志。准备不覆盖已有世界或结果；CheckOnly 只读检查。

其他线程占用客户端时可加 `-ServerOnly` 先完成独立服务端；随后执行同一命令会沿用已保存的服务端证明，只继续客户端阶段，不重复生成已通过的服务端世界。

## 通过标准

- 加载当前产品和探针，没有世界生成或加载器错误；正常服务器 tick 至少四十次后正常保存停服。
- 原生单人 Quick Play 进入，真实客户端玩家至少二十 tick，当前位置已收到非空原生 LevelChunk；随后客户端正常保存关闭，进程退出码为零，无新 crash report。
- 摘要绑定 Target 事实、产品 JAR 与本次游戏 PID，两个角色均四逻辑 CPU。不存在 Computer-Use、假区块、跳过原生世界生命周期或修改生成合同。
