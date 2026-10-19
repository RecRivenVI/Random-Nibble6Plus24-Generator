# 1.21.1 身份类型重复打包修复

调查日期：2026-10-16。发行检查发现 Target 与产品组件重复定义身份类型；移除重复源码、统一内部接口调用后，受影响 Target 检查与发行校验通过，身份生命周期和真实下界/末地 FULL 重载得到复验。主世界长等待仍未解决，不据此宣布 Target 收口。

## 问题

1.21.1 jar 任务报 MosaicIdentityValidationException.class 重复。开发模块能启动不代表发行 JAR 正确；设置 duplicatesStrategy 忽略会掩盖同名接口方法不同的问题。

## 方法

比较 Target 与 worldgen_contract 的完整源码及同路径类清单。只发现两个重复类型：MosaicIdentityValidationException 的父类和两个构造器语义相同；MosaicProfileChangeSource 的两个回调语义相同，但 Target 使用 mosaic$ 方法前缀，组件使用完整模组前缀。

保留已有产品组件为唯一来源，机械修改 1.21.1 的 DimensionDataIdentityMixin 与 MosaicWorldIdentity 调用名；将两份 Target 重复源码移至回收站，不改模板或忽略重复类。没有改变存储键、NBT、身份失效条件、Seed、装饰或生成算法。

## 发现

- 新增长期布局测试，拒绝 Target main 重复定义已打包产品组件的 Java 类型。全部二十项 Python 工具测试与五十二份 PowerShell 语法检查通过。
- 1.21.1、26.1.2 及验证工具的定向 check 通过；两个 Target 的 verifyRelease 均确认元数据、十五个必需条目和 probe 排除正确。1.21.1 十九项单测实际重新执行；26.1.2 此次为缓存命中，其五项已在物理计划修改后执行通过。
- 当前严格合规零失败、零警告，Markdown 和相对链接检查通过；这不替代整库最终收口，额外根 check/verifyRelease 的中断另在账本注明。
- 新源码指纹 AC6E0A412C6ACA4820F5767F6873E7F20047113A23D0BAAAB75C10BB441DD016 的身份生命周期、完整 B、脱离式 Artifact/SPAWN 交接四项，十六个独立 JVM/save，执行后另经 CheckOnly 通过。原生潜影贝构造随机性的四 JVM 双向因果重跑通过；不改变生产随机源或例外范围。
- 当前源码的普通 Vanilla 三维度 FULL/修改/save/restart/reload 共六个 JVM 通过，Mosaic Artifact/SPAWN 工作为零。控制结果按维度隔离在 integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/vanilla-control/physical-handoff。
- 下界强制晚注册场景 JVM 68380，输入先完成后由 worldgen 线程注册，十二次原生 holder 完成均归属 Server thread，越权零；最终真实 FULL、光照、49 次群系就绪检查、in-flight 零。结果在 features-2/development-b/completion-order/physical-handoff/minecraft-the_nether。
- 去重后重新执行的下界 JVM 95320/89624 与末地 JVM 100548/8352，普通 FULL/save/reload 通过。fresh 各二十五个 Artifact、二十四个 detached BIOMES；reload 两类准备与 SPAWN 都为零，标记保持。证据在 features-2/development-b/physical-handoff 的各维度目录。
- 上述 FULL 仍不是 BLOCK_TICKING/ENTITY_TICKING 或自然实体/BE/tick 激活验收；不能继承 26.1.2 或参考 Target 的 RuntimeProof。主世界当前普通请求仍触及 180 秒，已有失败保持未通过。

## 结论

同名 Java 类型不能靠开发 classpath 优先级或忽略重复项决定发行行为。模板既有产品组件保持唯一来源，Target 只适配 Minecraft 生命周期调用，真实 JVM 回归确认内部接口改名后身份与重载仍正常。

已回收且可恢复的源码为 1.21.1-neoforge 的 src/main/java 包内 worldgen/identity/MosaicIdentityValidationException.java 与 MosaicProfileChangeSource.java；组件原件保留。当前阶段不提交、不推送、不改旧存档或生成合同，继续以账本当前门禁为准。
