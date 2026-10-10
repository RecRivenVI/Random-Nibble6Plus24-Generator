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
| 参考 Target | `26.2-fabric` |

## Target

| Target | 加载器版本 | Java |
| --- | --- | --- |
| `26.2-fabric` | 0.19.5 | 25 |

## 补充规则

- **X-01** [判断] Seed Algorithm V1、WorldOptions.seed() 权威、Feature Ordering V1、Mosaic format V2、9-writer FEATURES 与 Structure Overlay 是冻结产品合同，未经授权不得改变。
- **X-02** [判断] Repair C 的历史验收为 PASS，D1 的历史结论为 correctness 通过、性能 PARTIAL；保存代码不升级验收状态，不自动进入 D2、V3、WorldPreset 或 Preview。
- **X-03** [判断] 普通 Vanilla 保持原路径，身份缺失、损坏和真正生成错误保持 fail closed；保留预期关闭取消和 POI 主线程归属修复。
- **X-04** [判断] 不使用 GUI 自动化代替人工验收，分别报告构建、运行与人工观察；没有运行产品不宣称游戏通过。
- **X-05** [判断] 测试使用受限并发、单个高负载 JVM，并保留用户 CPU 资源，不为测试改动生产线程模型。
- **X-06** [判断] 产品 Mixin 直接引用的默认关闭验证类保留在产品源码，独立开发入口属于 probe；日常运行不加载 probe。
- **X-07** [判断] 不改写既有证据的 SHA、时间、失败或签名，历史证据不能代替当前验证。
- **X-08** [判断] 未经明确授权不提交、推送、创建分支或 tag，不丢弃已有工作。
