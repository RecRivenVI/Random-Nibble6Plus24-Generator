# 1.21.1 初始刷怪遗漏同种子邻区碰撞输入

调查日期：2026-10-17。重复性能测试暴露了真实 SPAWN 读取缺口；代表输入与一次实际前沿重验已通过，保存中间状态后的冷回退和完整生命周期仍需独立验收。

## 问题

H121-U 的第二组交错性能对照在 02:32:34 崩溃。崩溃报告首因为 `Unproven non-target SPAWN block/height read 134,-41 at minecraft:empty`，调用链为 `NaturalSpawner.spawnMobsForChunkGeneration → noCollision → BlockCollisions → LevelReader.getChunkForCollisions → MosaicPhysicalSpawner.Region.getChunk`。

同一时刻另有 worker 报缺少 SPAWN 父状态。它不能用 catch 或假 parent 掩盖；先修复崩溃报告中的明确读取缺口，再用原负载检查是否仍出现。原报告没有完整的 owner/request 配对，不能据邻区坐标猜定精确 owner 或补造过去的执行顺序。

原始世界、日志与崩溃报告在[第二组失败目录](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/pair_b-duration180/b_reuse/first_failure)保留；该组及未执行余组不计性能通过。

## 方法

审计本 Target 实际加载器补丁 JAR，而不是套用新版本 API。原生 SPAWN 的 direct dependency 为 BIOMES radius 1。`WorldGenRegion.getChunk` 允许读取该范围内已可用的较高状态数据；它并不因为 `required=false` 就返回 null。`LevelReader.getChunkForCollisions` 恰好通过 `EMPTY,false` 请求碰撞读取，所以不能全局吞异常或伪造“可选读取没有区块”。

B 准备已持有同 S(C) 的八份完整 CARVERS 邻区。携带它们的精确方块读取数据，而不是让 SPAWN 再次生成八份地形、读取 physical S(N)，或用默认空气替代。载体只用于这一代 SPAWN，不进入 FULL 后模拟，也不改变 Artifact 或存档格式。

## 发现

### 数据与所有权

候选 `SpawnCollisionSnapshot` 与 stored-biome snapshot 共用 provenance，复制原生 `pack` 返回的调色板条目和自有 long 位存储。序列化 bits 通过本版原生方法取得，读取使用原生 BitStorage；不保留 Chunk、NoiseChunk、session 或 palette resize 回调。

必须保留原生 `ProtoChunk.getBlockState` 的细节：区间外为 VOID_AIR，`hasOnlyAir` section 对外返回 AIR，而不是直接暴露内部 CAVE_AIR/VOID_AIR 条目。方块流体状态由同一个精确 BlockState 取得。CARVERS 来源含 BlockEntity 时明确拒绝这一未审计输入，不静默丢数据。

在物理 holder 发布时一起移交，SPAWN 取用后释放；关闭时清理。只有窄的 `getChunkForCollisions` 接口接收这些数据，未知的邻区通用 Chunk/height 读取仍拒绝。冷载没有临时数据时只对真正读取的来源执行同 S(C) 原生装饰前阶段，不执行 FEATURES/Artifact；此冷路径尚待单独游戏覆盖。

### 测试侧失误与修正

第一次复制隔离组件错误地把原生 `PalettedContainer.copy()` 结果当成可写深拷贝，扩容时触发 ZeroBitStorage 的范围异常；失败在[spawn_collision 冒烟目录](../../validations/integration-world_identity/result/smoke-shared-handoff-ring-scope-narrow-locks-source-reuse-spawn_collision/1.21.1-neoforge)保留。原生调色板复制仍带原来的 resize callback，不能用这个写操作证明隔离；不将该失败冒充新的 worldgen 算法差异。

修正后组件从独立新建的 native ProtoChunk 写入测试点，不修改 canonical 输入；实际数据载体使用自有 packed entries/storage。该组件只证明复制隔离，不充当自然世界正例。

### 已执行结果

输入指纹 `CADAD92C1A83EF1829063E547D95E9CA0085751A4305EBD7A59CBB2FBD17BB75` 的 34 项单元、编译、四 JVM 原生朝向双向重放及[完整 B 四项/十六 JVM](../../validations/integration-world_identity/result/smoke-shared-handoff-ring-scope-narrow-locks-source-reuse-spawn_packed/1.21.1-neoforge)通过。八个宿主消费者共比较 5,275,648 个方块/流体位置，含高度区间外层，均与原生 CARVERS 输入相同；复制隔离和 owner 关闭后 hash 不变均通过。FEATURES 输出、随机流、实体有限验收规则没有放宽。

[四核 8/8、180 秒实际前沿重验](../../validations/performance-worldgen/result/legacy_frontier_cold/1.21.1-neoforge/overworld/v8/collision_screen-duration180/b_reuse)实际触发十二次邻区碰撞读取，冷来源求值为零；完成 212 canonical、106 FULL、66 send-ready，正常保存退出，无新增崩溃，未再观察到缺少 SPAWN parent。主展示目标约 38.76 s 完成 canonical、109.40 s 到 FULL、109.62 s 到 send-ready。它验证命中修复路径，不等于完整生命周期或性能保留通过，也不足以将过去所有父状态错误概括成已证明的同一因果链。

严格合规在本次检查为零失败、零警告，Markdown 与工具测试通过。完整 source-cache 性能配对仍需在修复后的同一源码指纹重新执行；旧源码的单组收益不能拼入新组。

## 结论

全拒绝断言遗漏了原生实际会执行的边界碰撞读取。使用 B 已存在的同种子 CARVERS 数据可在不重新生成邻区、不读取其它种子、不改 FEATURES 或世界格式的前提下满足热路径；单次实际负载已正向命中并正常退出。

仍保持候选和 Target 未收口：冷载回退、保存重载/并发及重复性能门禁分别完成，不能由这份输入证明自动继承。原生 SPAWN 和 FULL 后的物理模拟继续由各自生命周期处理，碰撞载体不会成为存档或运行期 authority。
