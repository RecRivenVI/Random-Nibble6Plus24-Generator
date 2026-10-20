# 项目规范

本文件补充根目录的[仓库规范](../AGENTS.md)，对整个仓库生效。

## 项目信息

| 项 | 值 |
| --- | --- |
| 产品显示名 | Random Nibble6Plus24 Generator |
| 模组 ID | `randomnibble6plus24generator` |
| Java 包 | `io.github.recrivenvi.randomnibble6plus24generator` |
| 入口类 | `RandomNibble6Plus24Generator` |
| 业务 | 使用每区块派生种子的隔离生成与物化，构建真实 Mosaic 世界 |
| 文档主语言 | 简体中文 |
| 首页语言 | 简体中文 |
| 参考 Target | `26.3-fabric` |

## Target

| Target | 加载器版本 | Java |
| --- | --- | --- |
| `1.20.1-forge` | 47.4.28 | 17 |
| `1.21.1-neoforge` | 21.1.264 | 21 |
| `26.1.2-neoforge` | 26.1.2.122 | 25 |
| `26.2-fabric` | 0.19.5 | 25 |
| `26.3-neoforge` | 26.3.0.94 | 25 |
| `26.3-fabric` | 0.19.5 | 25 |

## 补充规则

- **X-01** [判断] Seed Algorithm V1、WorldOptions.seed() 权威、Feature Ordering V1、Mosaic format V2、9-writer FEATURES 与 Structure Overlay 是冻结产品合同，未经授权不得改变。
- **X-10** [判断] 26.3 的新测试地图可显式采用 Feature Ordering 2：仅目标 writer 执行 FEATURES，越界生成写入丢弃，邻区读取保留同种子 Pre-FEATURES 数据；不迁移既有 Ordering 1 世界，不以此试验宣称原版 FEATURES parity。
- **X-02** [判断] 历史验收只证明对应版本和范围，不自动授权当前里程碑以外的工作；保存代码不提升未验证能力的验收状态。
- **X-03** [判断] 普通 Vanilla 保持原路径，身份缺失、损坏和真正生成错误保持 fail closed；保留预期关闭取消和 POI 主线程归属修复。
- **X-04** [判断] 不使用 GUI 自动化代替人工验收，分别报告构建、运行与人工观察；没有运行产品不宣称游戏通过。
- **X-05** [判断] 测试保留用户 CPU 资源，不为测试改动生产线程模型。正确性对照最多同时两个 JVM，各限四逻辑 CPU、低优先级，实例、世界和端口独立；性能测量只运行单个高负载 JVM，不与正确性队列重叠。
- **X-06** [判断] 产品 Mixin 直接引用的默认关闭验证类保留在产品源码，独立开发入口属于 probe；日常运行不加载 probe。
- **X-07** [判断] 不改写既有证据的 SHA、时间、失败或签名，历史证据不能代替当前验证。
- **X-08** [判断] 未经明确授权不提交、推送、创建分支或 tag，不丢弃已有工作。
- **X-09** [判断] 开发按里程碑组织，当前范围与完成条件以 [FEATURES 里程碑](project/milestone-features.md) 为权威；不沿用旧目标、路线图或自动新增后续里程碑。版本变更、生产切换与其它产品功能需另行授权，不从历史安排推断授权。
- **X-11** [判断] 当前授权五 Target 的稳定依赖更新、两个 26.3 Target 的 B 原版正确性与主世界性能收口，以及 1.20.1 主世界 FULL 缺陷归因；BOP、其余三个 Target 的完整收口及 A/C 暂缓。具体验收与停止条件只定义在当前里程碑，不自动扩张到 C2ME、全局未生成区域 locate 或新玩法。
- **X-12** [判断] 自动测试客户端 1920×1080、全局静音；人工验收 2560×1440、全局音量 50%、音乐关闭，玩家名 RecRivenVI。启动前用客户端准备工具设置；人工实例只装 Modrinth 声明兼容的 Custom Skin Loader 与 Zume，无匹配版本跳过；不进入产品或原版自动对照。
