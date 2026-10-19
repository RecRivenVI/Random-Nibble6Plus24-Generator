# 旧版原生票据前沿性能

## 目标

用 1.20.1 Forge 实际原生 PLAYER 票据和 Chunk scheduler，分别测量普通 Vanilla、保守 B、显式有限环 B 与已通过独立正确性门禁的窄锁候选端到端性能；不把离线票据 token 当成真实玩家或网络发送。算法与保存格式不改变。

## 运行

从仓库根目录执行，local.toml 已由使用者接受 EULA 时运行。单个四逻辑 CPU、低优先级游戏，不与正确性队列并行。

```powershell
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline vanilla -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline b_eager -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline b_ring -Dimension overworld -View 8 -Case screen -Offline
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline vanilla -Dimension overworld -View 8 -Case screen -CheckOnly
& components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1 -Pipeline b_narrow -Dimension overworld -View 8 -Case screen -EvidenceLabel rotation_safe -Offline
```

三个维度分别传 Dimension，View 可为 8、12、16。相同组固定 60 秒，第 15/30 秒沿 X 各移动两 Chunk；有界诊断可单独使用 ObserveSeconds 180，不延长请求轨迹或正确性超时。结果固定写入 result/legacy_frontier_cold/1.20.1-forge 下；EvidenceLabel 另隔离结果根目录与世界，不覆盖原失败。screen、pair_a/b/c 各自隔离。成功临时世界移回收站，失败与首次异常保全。

## 通过标准

- 冷起点：票据 token 仅跳过一次 fudgeSpawnLocation，所有维度 updating holders、loaded count 与 Mosaic generation/publication/in-flight/SPAWN/FULL 均为零；计时前没有 Mosaic worker。
- 加载范围使用本版 ChunkMap.isChunkInRange 与实际 DistanceManager；不手工推进生成阶段、不更改调度优先级。
- 分别观察原生 generation FULL 与 FullChunkStatus 三类 future；发送就绪按本版 getTickingChunk，不宣称 packet、客户端画面或出生通过。
- 真实异常、缺文件、新 crash report、资源或源码漂移使验证失败；普通 Vanilla 的 Mosaic 调用计数必须为零。
- 原始时间线、四 CPU 条件、JFR 与截尾请求完整；MEASURED 只表示测量完成，不是生成正确性或优化保留通过。
- 有收益的候选才做至少三组交错配对，并按里程碑审查端到端改善、p95 与内存；不得只凭内部调用下降启用候选。
