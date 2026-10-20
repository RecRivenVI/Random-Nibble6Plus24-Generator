# 依赖更新记录

本文件记录 2026-10-20 的稳定依赖更新；Minecraft、模板 2.0.2、生成合同和 Oracle 夹具不变。五端构建、单测与原生进世界冒烟均已完成，不代替后续 B 正确性及吞吐门禁。

## 2026-10-20

| 依赖／Target | 更新前 | 选定稳定版 | 来源 |
| --- | --- | --- | --- |
| NeoForge 26.3 | 26.3.0.83 | 26.3.0.94 | [官方 Maven 元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) |
| NeoForge 26.1.2 | 26.1.2.121 | 26.1.2.122 | [官方 Maven 元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) |
| NeoForge 1.21.1 | 21.1.262 | 21.1.264 | [官方 Maven 元数据](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) |
| Forge 1.20.1 | 47.4.28 | 47.4.28，保持 | [官方版本列表](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) |
| Fabric Loader | 0.19.5 | 0.19.5，保持；0.20.0 未标稳定 | [官方 Loader 元数据](https://meta.fabricmc.net/v2/versions/loader) |
| 26.3 Registry Sync | 8.0.2+1ca9f6365d | 8.0.3+1ca9f6365d | [Fabric API 0.164.0+26.3 官方 POM](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.164.0+26.3/fabric-api-0.164.0+26.3.pom) |
| Custom Skin Loader | 15.1 | 保持，同一发行 | [Modrinth](https://modrinth.com/mod/customskinloader/version/yyYvYfZI) |
| Zume | 1.2.2 | 保持，同一发行；26.x 无对应版本 | [Modrinth](https://modrinth.com/mod/zume/version/Rh2ImJ86) |

本轮按使用者最新指令只选稳定版，不采用测试版。产品继续只依赖 Registry Sync，不新增整个 Fabric API。BOP／TerraBlender／GlitchCore、其完整 API 以及固定 MCP alpha 都属于验证输入，保留 inputs.toml 中对应 Oracle／自动化快照，不混入本轮产品依赖升级。BOP 完整收口延期；工具版本随冻结模板保持，不修改受保护模板文件。

五端 `spotlessApply check verifyRelease` 和严格合规已通过，配置缓存成功保存；CI 对应的本地 `checkRepository --continue` 也通过。五端均完成普通独立服务端原生四十 tick 与正常保存停服，再以独立 JVM 的原生 Quick Play 实际进入同版单人世界，读取真实 LevelChunk、运行二十玩家 tick，正常保存关闭、退出码零、没有新 crash report。各次游戏均四逻辑 CPU、低优先级，没有 Computer-Use。

摘要固定在 `validations/smoke-dependencies/result/<Target>/summary.json`，绑定 Target 事实、产品 JAR、两个游戏 PID、真实 tick／区块与命令；五端 CheckOnly 均通过。成功实例与 stdout／stderr 已移入回收站，只留摘要；首次启动失败原日志仍保留。前几端使用的 simulationDistance=2 被原生客户端按其合法范围恢复默认值，实际进世界证明仍有效，但不作任何性能对照；工具后续改为合法最小值 5。

冒烟启动曾把旧式 `--mixin` 参数传入新版 NeoForge，被 Minecraft 参数解析拒绝；失败日志保留，现代加载器改用原生元数据登记，1.20.1 使用原生 Forge tick 事件，不改变产品生成路径。历史研究文档中未上传的结果链接经授权仅改为保留原路径的文字，CI 按 W-03 使用 `checkRepository`；完整检查仍在本机执行。云端未重新推送验证。

依赖发生回归时先定位，当轮无法修复则只回退该项更新。已有结果不因无关文件指纹改变整体作废，交付时再对冻结候选做完整受影响矩阵。
