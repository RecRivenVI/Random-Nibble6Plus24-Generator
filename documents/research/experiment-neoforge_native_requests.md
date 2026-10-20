# NeoForge 普通 FULL 请求与逐目标交接

调查日期：2026-10-18。26.3 NeoForge 的三维度重复请求、相邻不同种子 pair 和九区块 patch 共九组通过。这里只证明普通 physical FULL 请求及逐目标一次交接，不提升为内容顺序独立、自然副作用激活、玩家进入或性能验收。

## 问题

[当前状态守卫](incident-neoforge_source_climate_state.md)及已有运行重载证据没有逐一观察普通请求返回对象和每个请求 owner 的捕获／发布次数。Fabric 的通过不能替代本加载器的实际运行，也不能把 ChunkStatus.FULL 与 ticking／玩家就绪混为一谈。

## 方法

保留本版 Phase3C2ProductionVerification 原有请求，probe 仅包装其实际 CompletableFuture.join 返回值、结果写入与方法退出。没有新增或替换 future、请求、ticket、调度、物理写入或生产超时；默认关闭，仅显式 requestProof 验证启用。原生异常继续传播，finally 清空观察器的区块和 server 引用。

每个维度分别创建三个 fresh save，主种子 123456789，目标 (125,-37)：三十二个同目标普通 getChunkFuture(FULL) 请求、东侧相邻 pair、按 Z/X 排列的 3×3 patch。九个独立 JVM/save 串行运行，各限四逻辑 CPU、affinity 3840、BelowNormal；Gradle 两逻辑 CPU、一个 worker。观察器使用 300 秒失败边界，不改变原生完成条件，不在超时后伪造完成。

输入为 28DAAE1C924816E2B77B7DD7F9E329B9A795E93EE3E6022B0516C5E401D4084F，覆盖本 Target 全部 src、worldgen_contract 源码与 target.properties。生产 main 未因本观察器修改。旧 7587 运行重载、7172 结构与性能、5B7A 历史筛选各保留自己的指纹，不重标为本轮输入。

结果旁的 requests.json 只在原方法成功返回、观察状态清空后 CREATE_NEW 写入；记录 raw JSON 的 SHA-256，不改写原始结果。工具检查实际九个 PID/save/runIdentity、条件与当前源码、返回对象身份、owner 位置／local seed、逐目标一次捕获／发布、绑定及引用清理。单元回归覆盖原始结果改变、版本不符、观察次数错误和未清理引用的拒绝，不以单元样例冒充游戏证据。

## 发现

原始结果（本机证据：`../../validations/integration-production_lifecycle/result/current/26.3-neoforge/phase3c2-production/features-2/development-b/request-proof`）位于各维度的 native_joins；额外的单项 smoke 独立放在 native_joins_smoke，不算入九组。

| 维度 | 32 次同目标 PID | 相邻 pair PID | 3×3 patch PID |
| --- | --- | --- | --- |
| 主世界 | 68636 | 64116 | 73000 |
| 下界 | 77360 | 36360 | 103024 |
| 末地 | 55020 | 29932 | 45696 |

九组共 129 次实际 join、36 份请求 owner 记录，返回位置、LevelChunk/FULL 与 lightCorrect 均通过。每维度分别得到一个、两个、九个唯一对象；三十二次同目标返回同一对象。每个请求 owner 的 Artifact 捕获与物化发布各一次；pair 的 local seed 不同，同坐标在不同请求布局中的 local seed 一致。

GenerationContextRegistry 与 SPAWN 绑定均为零，观察器退出后的区块引用为零、server 引用为空，原生方法正常返回。原输出的全局 in-flight 为零，lifecycleDuplicates 为空。九份控制台日志均记录 BUILD SUCCESSFUL、Stopping server 和 Closing FML Loader；没有新的请求超时、worldgen failure 或崩溃。

全请求窗口 Artifact 捕获／发布分别为 49、56、81，包含原生 physical 依赖，不是目标重复生成次数。physicalBiomeChecks 分别为 49、98、441，只证明 BIOMES readiness 检查，不是群系 palette 全内容比较。developmentB.generations 含开服阶段，不与观察窗口的 Artifact 计数混用，也不从本正确性运行推算吞吐。

当前源码只读复核命令：

```powershell
pwsh -NoProfile -File components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricRequests.ps1 -Target 26.3-neoforge -EvidenceLabel native_joins -CheckOnly -CpuAffinity 3840
```

脚本名称保留既有入口，已按 Target 分别读取 Fabric 内嵌证明或 NeoForge sidecar；未把 Fabric JSON 当成本版结果。九组全部通过 CURRENT_ALL_SRC 复核，原始结果及 SHA 不变。

## 结论

补齐 26.3 NeoForge 三维度普通 FULL 的重复请求去重与相邻／区域布局门禁，没有修改生成算法、冻结合同、旧存档或生产调度器。probe 不进入发行 JAR。

本轮没有逐目标最终内容哈希的顺序对照、不同目标五 session 同时执行证明、自然实体／BE 激活、保存重载、玩家出生或数据包发送证明；这些仍保持独立缺项，不因九组通过而提升为 Target 或里程碑完成。按使用者要求，本轮收尾后暂停任务与持续目标，不启动下一门禁。
