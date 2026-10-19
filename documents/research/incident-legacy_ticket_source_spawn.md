# 1.21.1 性能票据 token 意外触发出生搜索

调查日期：2026-10-16。初版性能探针在构造离线票据 token 时触发了真实出生区生成，默认 B 因同步等待超过看门狗预算退出；这是探针负载错误，不是已完成的 60 秒前沿性能结果，也不据此宣称正常玩家出生已通过或失败。

## 问题

不能把 26.3 的离线 ServerPlayer 构造行为直接当作 1.21.1 的等价入口。初版尚未开始计时和 JFR 就进入世界生成，污染原版基线，并使默认 B 无法开始正式负载。

## 方法

保留旧 legacy_frontier 的日志、世界、崩溃报告与 summary；按本机 neoforge-21.1.262.jar 的签名和实际堆栈核对 ServerPlayer 构造器、adjustSpawnLocation、PlayerRespawnLogic。修正仅位于 probe，普通产品 JAR 不包含该代码，不改变任何正常玩家构造或生成器。

## 发现

- 初版默认 B 的 23:38:02 看门狗栈为 ServerPlayer.<init> → adjustSpawnLocation → PlayerRespawnLogic.getOverworldRespawnPos → Level.getChunk → ServerChunkCache.getChunk/managedBlock。负载开始标记尚未输出，不得填成“B 前沿吞吐为零”。
- 初版 Vanilla 虽完成窗口，但 token 创建前后没有再次核验冷状态，存在同类无关出生区输入；其原始 MEASURED 文件保持原样，不能作为新冷负载的控制。
- 新 probe 的线程局部作用域只包住离线 token 的构造，并仅在该构造器调用 adjustSpawnLocation 时返回其输入位置。恰好一次命中后立刻释放，再核验三维度已加载 Chunk 为零且没有 Mosaic worker 状态，才开始票据/JFR 窗口。真实玩家、其它验证及属性关闭时仍调用原生操作。
- 修正结果使用固定 legacy_frontier_cold 证据域和 schema 2，检查 coldAfterToken 与 tokenRespawnSkips；不修改旧 schema 或覆盖原失败，不假装做了实际登录/发送/渲染。
- 另有两个测量工具问题：PowerShell 单元素字符串被 splat 为字符，导致 Gradle Task '-' 失败，完整启动错误保存在 legacy_launcher_failure；已有 JFR 分析器强制读取 elapsedNanos，不认识本版仅有时间戳的窗口标记。现在保留旧事件的原始字段，无字段时明确用实际 JFR 窗口时间戳，并输出该选择，不补造原始事件。
- 新冷 Vanilla 窗口已执行并复核，输入指纹 0C6D8DDF4C89215BEA0F86FBA850732580F31DAF83D90440587DDED3FA209A42；默认 B、有限环和缩锁的冷对照此时尚未结束，不能提前给出收益或保留决定。

## 结论

离线票据 token 不是实际玩家；构造时不能悄悄预生成出生区域。后续仅用 schema 2 的冷前沿与独立同条件控制衡量端到端成本，正常玩家出生仍属另外的世界功能门禁。未完成请求保留截尾，任何原生等待、真实异常或新看门狗仍按实际证据处理，不通过修改生产 scheduler 或扩大超时修正测量。
