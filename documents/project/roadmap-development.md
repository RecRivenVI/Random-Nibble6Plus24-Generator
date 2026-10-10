# 开发状态与路线图

当前参考 Target 为 Minecraft 26.3 Fabric，26.2 Target 与原有存档继续保留。新版构建、自动回归、72 项独立原生对照与人工进入、探索、保存退出和重进验收通过，结果见[升级计划](plan-target_upgrade.md)。本轮到此停止，不自动进入新功能或 D2。

## 当前状态

真实 Mosaic 生产生命周期已建立：隔离生成、Artifact、物化、POI、光照、SPAWN、FULL 与物理运行期交互。隐藏单人存档可以探索、保存退出与重载；`/seed` 可查看世界和区块种子，结构 Overlay 与 `/locate structure` 只处理已生成区域。

Repair A/B/C 的历史成果包括身份验证热路径、SPAWN 数据复用、Lazy Stronghold、POI 主线程归属与预期关闭取消。D1 安全候选裁剪的正确性通过，性能验收仍是 PARTIAL；人工探索仍未达到原版生成速度。

模板迁移与分支调整已经完成，开发分支为 `develop`。26.3 的人工生命周期复验已完成，但探索性能未作为本次受限资源测试的验收项；历史性能结果也不能代替新版本采样。

## 优先级

1. 此次版本与依赖升级已完成；后续持续核对 Minecraft 最新正式版与最新 Loader，测试版仅适用于 Loader。
2. 等待使用者决定后续工作；推荐先重新采样 26.3 的真实探索性能，包含新增物理生物群系邻区的工作量，再评估 D2 的结构模板 / Jigsaw 锁竞争工作，不根据旧采样直接实施。
3. 核心性能与生命周期达到可接受水平后，单独设计正式创建世界入口。
4. 未生成区域结构搜索、Preview 与 V3 属于后续独立范围，没有自动进入的授权。

## 不变边界

Seed Algorithm V1、Feature Ordering V1、Mosaic format V2、9-writer FEATURES 与当前 Structure Overlay 语义保持冻结。Minecraft 版本升级不自动批准新的生成合同、General Lazy Structures 或 target-only FEATURES。

## 验收

分别记录静态检查、普通 Vanilla 对照、Mosaic 运行与使用者人工体验。涉及游戏的验收不使用 GUI 自动化代替操作；测试保留用户 CPU 资源，单个高负载 JVM、受限并发。

旧版本证据原样保留。新版本使用独立测试实例与世界，不把用户已有存档作为迁移试验场。
