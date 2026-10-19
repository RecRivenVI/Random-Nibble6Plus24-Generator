# 1.20.1 原生 Mixin 映射打包边界

调查日期：2026-10-17。完整 check/verifyRelease 暴露 LegacyForge 的重复 refmap 与隐式 AP 映射依赖，项目级配置修复后六个 Target 构建及发行检查通过；没有修改模板或世界生成算法。

## 问题

1.20.1 的 jar 报 `mosaic-worldgen.refmap.json` 重复。消除重复后，reobfJar 又报告读取 compileProbeJava 产物却未声明依赖。不能用全局忽略重复、跳过 remap 或把探针打入产品来绕过。

## 方法

对实际使用的 ModDevGradle 2.0.148 的 MixinExtension 做本地 javap 字节码核对：add(SourceSet, refmap) 为对应 Jar 直接追加 refmap，同时把各已配置源码集的 AP 映射加入统一 extraMappingFiles。项目开发运行另外通过 processResources 提供同一 refmap，因此出现两份来源。

保留开发资源副本，仅对这个明确条目合并重复，并在 Jar 执行前逐字节检查开发副本与 AP 原件一致；不放宽其它资源的重复检查。reobfJar 通过延迟任务配置显式依赖 compileProbeJava，确保已登记的 probe AP 映射存在，但不把探针输出加到产品源码集或 JAR。

## 发现

两份主 refmap 的原始 SHA-256 均为 `4B50492517903AA5EBA3E5A259EA9328481873432FF9716A9452B78C2E6E0950`。直接在 Target 脚本求 tasks.named(reobfJar) 过早：插件尚未注册该任务，会使所有 Target 配置阶段失败，末地普通 Vanilla reload 因而未启动。修正为 matching/configureEach 后重新执行完整场景；原 launcher failure 保留，不当作游戏崩溃。

修复后 check/verifyRelease 成功，六个发行 JAR 全部经过元数据、十五个必需条目和 probe 排除检查。另行直接检查 Forge 发行 ZIP，主 refmap 恰好一个，probe 包及探针 Mixin 资源为零。根 spotlessApply 已执行；后续仅文档更新仍另做严格合规与 Markdown 检查。

## 结论

配置恢复了开发运行与生产打包各自所需的真实 SRG 映射，失败仍保留。此修复不改变结构、地形、存档或注册范围，不提升 1.20.1 的生产 FULL 验收状态。
