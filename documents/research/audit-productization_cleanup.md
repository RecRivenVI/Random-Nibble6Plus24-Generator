# 产品化代码与验证边界整理

调查日期：2026-10-20。首版范围按使用者决定收敛为 26.3 Fabric／NeoForge；本记录说明代码整理的取舍与删除原因，不把构建通过等同于其余 Target 可玩。当前交付范围只由[里程碑 2](../project/milestone-productization.md)定义。

## 问题

现代 Target 的产品源码包含独立阶段验证入口；普通服务器启动与原生状态推进也反复检查这些仅开发使用的属性。六端还分别维护相同的格式／算法版本判定。清理必须保留产品真实调用的诊断、旧地图合同和各版 Oracle，不把 Minecraft 类型机械共享。

## 方法

从生产调用处区分独立入口与被产品实际引用的代码；只将独立入口、捕获 Mixin 和游戏验证迁入各 Target 的 probe。保留原生 RNG、Minecraft codec、生命周期、写入边界及本版本 API；仅把不引用游戏的版本规则收进 `worldgen_contract`。

使用普通原版／B 的真实票据、自然 tick、fresh 与独立 JVM reload 对照验证清理后的路径，再执行全仓格式、check、发行与严格合规。过时研究原文按 D-10 保留，由证据索引提供入口，不执行与规范冲突的研究正文替换。

## 发现

26.3 Fabric／NeoForge 各 20 个、26.1.2 共 23 个独立验证类迁到各自的 `probe.verify.legacy`；三端的 `ChunkStepMixin` 也从产品配置迁至独立 probe 配置。它们仍可用于对应开发验证，不进入发行包。产品 Mixin 直接引用的诊断按 X-06 保留，不通过修改调用者强行搬走。

以下文件移入回收站，不是保留两套产品实现：

| 文件／范围 | 原因 |
| --- | --- |
| 两个 26.3 端的 `NativeVanillaControlHarness.java` | 其旧独立 SURFACE／CARVERS 捕获在 26.3 必然抛出不支持；本版 TERRAIN 与完整 B Oracle 另有有效入口 |
| 两个 26.3 端的 `Phase2AVerification.java` | 26.3 已没有独立 SURFACE 阶段，保留不能执行的入口会误导验证 |
| 两个 26.3 端的 `Phase2BVerification.java` | 26.3 已没有独立 CARVERS 阶段，同上；26.2 的有效旧 Oracle 不移除 |
| 26.1.2 probe 的 `PreparationAccessEvidence.java` | 临时全局数量归因已完成；它对 ForkJoin 帮助执行采用错误的非嵌套假定，不能成为长期隔离断言 |
| 26.1.2 probe 的 `PreparationAccessMixin.java` | 只服务上述临时归因，随观察器移除 |
| 26.1.2 probe 的 `PreparationChunkAccessMixin.java` | 同上，相关开关、注册与启动参数同时移除；失败证据不删除 |

全局加载数量误判的生产修正及未通过的冷末地玩家路径见[独立归因](incident-neoforge_player_cold_frontier.md)。A、C、BOP 代码与证据保留且默认不参与交付路径；没有删除参考实现、改随机例外或迁移任何地图。

直接 Gradle 验证的固定端口和启动文件发生过交叉运行冲突；工具改为在修改实例前获取独占运行租约，释放置于 finally。长期测试验证第二个运行在改文件或启动 JVM 前被拒绝。这不改变生产调度、游戏线程数或 Oracle 双队列。

## 结论

`spotlessApply` 后的 `check verifyRelease verifyCompliance --strict` 通过；六端发行包的探针排除核对通过，现代三端另行检查也未包含 probe 或产品 `ChunkStepMixin`。六端 JUnit 结果为 385 项、失败／错误／跳过均为零；工具测试、脚本解析与 Markdown 链接检查由根 check 一并执行。严格合规为零失败、零警告；旧 Forge 探针仍有原生过时 API 编译警告，不将它写作整个构建零警告。

本记录只证明上述清理边界。两个 26.3 端的游戏与有限世界证据在[索引](../reference/registry-b_evidence.md)，另外三端仍按使用者决定暂缓首版游玩支持。原始失败未被重写或以构建通过覆盖。
