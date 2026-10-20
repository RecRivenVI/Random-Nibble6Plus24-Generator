# 1.21.1 真实 LIGHT 冷载与 SPAWN 输入交接

调查日期：2026-10-17。真实 LIGHT 保存、关闭与新 JVM 恢复 FULL 已通过；结果只证明指定目标的冷载生命周期及八来源组件数据，不提升为自然刷怪跨界正例或完整运行期通过。

## 问题

SPAWN 的八来源 biome/collision 输入平时从 canonical preparation 脱离携带，但存档可能在 LIGHT 时关闭。恢复不能依赖上一次 JVM 的 transient snapshot，不能重建目标 Artifact，也不能把真实关闭取消当作 worldgen 失败。

## 方法

采用 1.21.1 NeoForge、主世界、master seed 123456789、目标 (125,-37)、local seed -5161763991829980711。显式开启现有默认关闭候选，四逻辑 CPU、低优先级、单实例；未改变生产数据算法。

探针挂起选定目标真正尚未执行的 SPAWN future，断言实际状态为 LIGHT 且光照正确，正常关闭时只对该 future 完成一次生命周期取消。第二个独立 JVM 读取同一真实存档，经普通 FULL 请求恢复；不改写磁盘状态或复制旧快照。

源码输入指纹为 `0C2991DC8BCDC40A9A4AD31CAB0FE9200C79D2D4D0670993E6AFE4F5CA0DBB73`；固定证据入口（本机证据：`../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/cold-spawn/minecraft-overworld/light_restart_idempotent/`）包含条件、warm-inputs、stage、resume 和原始日志。后续增加的运行期观测器改变 probe 指纹，不回写本次条件。

## 发现

stage/resume 的 PID 分别为 81108/97732，两次均在原生 level close 后记录 in-flight 与 worker 为零。stage 目标 canonical preparation 一次、population 零；收到两次合法关闭通知，仅一次取消完成。resume 实际加载 `minecraft:light`，目标 canonical preparation 零、原版 population 一次，最终为原生 FULL LevelChunk。

八来源 stored-biome hash 为 `abc534cf1c8c9cb2536949bd26f011e7342e7c91a510b453b674c50bd342c893`，collision hash 为 `f92783acca3762e4d1ed1ade7ec8946caea7b193249e932fed6a66e3a28e2ae1`。冷重建与 warm 携带完全一致；恢复时 transient collision snapshot 确实不存在。

本 fixture 的原版 population 自然越界碰撞读取为零。额外八次读取在 population 返回后执行，证据明确标记 `POST_POPULATION_COMPONENT_READS_NOT_NATURAL_BOUNDARY_COVERAGE`，不能用组件命中数冒充自然正例。先前独立前沿的十二次自然读取仍由其原始证据承担。

最初探针把重复 close 通知的第二次 future completion 返回 false 误写成取消失败。该轮已正常保存后的原始 FAIL、日志和世界保留在 light_restart/first_runtime_failure（本机证据：`../../validations/integration-production_lifecycle/result/current/1.21.1-neoforge/phase3c2-production/features-2/development-b/lazy-rings/narrow-locks/cold-spawn/minecraft-overworld/light_restart/first_runtime_failure/`）。修正只使探针完成计数幂等，不改变生产取消规则。编译期的 final-class/interface Mixin cast 与外层两 CPU 限制不含四 CPU 游戏掩码的启动拒绝均发生在游戏前，不归类为游戏崩溃。

## 结论

这个范围的真实冷载交接 PASS：目标数据不重新生成，精确输入可以按需冷恢复，关闭清理完成。49 FULL 前沿、自然 block/entity ticking、其它维度、并发及性能保留门槛仍分别验收；候选不因本组件结果默认启用。
