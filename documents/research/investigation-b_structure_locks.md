# B 结构锁范围审计与对照

调查日期：2026-10-15。Minecraft 26.3 Fabric 的标准结构输入可在保持 B 结果的前提下移出整段模板管理器锁；本次四逻辑 CPU 的吞吐仅小幅变化，不证明已经解决探索性能。

## 问题

真实客户端在 32/32 新区生成时，一次线程快照有 27 个生成线程中的 23 个等待同一 StructureTemplateManager monitor，持锁线程正在展开 Jigsaw。需要区分共享定义缓存与本次生成自己的随机、pieces、碰撞和高度计算，不能直接删除所有保护。

## 方法

本地 26.3 `minecraft-common.jar` 的 SHA-256 为 `04f39c439ae296e7eb125962e6c2c1165839321ff9b390823c95a824ee501a38`。反编译实际模板管理器、模板、模板池、pool elements、Jigsaw 与原版结构生成器；仅对已审计的原版实现开启短锁入口。

先捕获未改锁代码的 fresh B 基线，再执行相同的四逻辑 CPU、affinity 3840、低优先级、单 Minecraft JVM、8/8、60 秒固定 PLAYER ticket 前沿。没有关闭结构、修改调度器或修改地形算法。输出位于被忽略的 `versions/26.3-fabric/build/research/b-template-lock/before/` 与 `after/`。

最终代码重新运行 18 项独立普通原版/轻量结构对照，包含七类 start、邻区投影、地形适配 halo 与 Mosaic seed 回归。两个独立宿主分别先做 21 项五线程结构来源对照，再运行完整 B 读取/结果/随机/顺序/并发检查，以及各 19 项 detached Artifact 交接。

## 发现

- StructureTemplateManager 的 repository 是 ConcurrentHashMap，完成加载后由原版 computeIfAbsent 发布；不需要以该 manager 的 monitor 串行化整个 Jigsaw 图。
- Palette 的过滤缓存是普通 HashMap，cachedJigsaws 也在首次读取时写入；必须以同一个 palette monitor 保护两个入口。getJigsaws 的旋转结果是每次新建的列表，原版 shuffle 与 selection-priority 排序留在锁外。
- 原版 pool 的 maxSize 是懒写入的普通 int，按该 pool 同步。只有固定原版 pool/element 类型使用该短锁；未知实现不新增可能相互嵌套的 pool 锁，B 同时回退整段管理器锁。
- StrongholdPieces 有静态权重、currentPieces、imposedPiece 与 totalWeight；Fortress 的静态权重数组内包含可变 placeCount；EndCity 的静态生成器包含 shipCreated。三类分别用 JVM 范围的独立锁保护原版 Structure.generate 的 reset 和完整展开，不能只锁 reset，也不能以服务器实例锁保护 JVM 静态状态。
- 标准 Jigsaw 的 Placer、pending pieces、碰撞 shape、随机数与高度查询属于本次生成，留在缓存锁外。未审计结构类、pool 子类及嵌套自定义 element 保留保守路径。现有 ring futures 的锁外等待没有改变。

标准缓存和静态状态的所有消费者参与同步，包括原版生成和重载；没有把 Vanilla 请求改成 B 请求。普通世界的 canonical 请求、Artifact capture 与 Mosaic production plan 均为零。

| 同条件 60 秒 | 整段锁基线 | 缩小锁范围 |
| --- | --- | --- |
| canonical 完成 | 354 | 367 |
| canonical/s | 5.90 | 6.12 |
| FULL 完成 | 159 | 158 |
| 发送就绪 | 110 | 104 |
| 准备平均墙钟时间 | 772 ms | 697 ms |
| 管理器锁等待累计 | 12.98 s | 未记录到该 monitor 的等待事件 |
| GC pause 累计 | 2.89 s | 2.76 s |

锁等待按所有线程累加，不是玩家单次等待。JFR 只记录超过其阈值的事件，未记录不等于所有短锁开销为零。只有一组 fresh A/B；约 3.7% 的吞吐变化不作为显著加速结论，FULL/send-ready 没有改善。没有实际登录的 CLI 前沿也不等于客户端首次进入或画面就绪。

同一分类器的 CPU 采样中，局部地形/噪声/表面查询约占 74% 到 75%，结构约占 11% 到 12%，LIGHT 不到 1%。结构 CPU 比例没有降低并不代表锁更重：本轮消除等待，没有减少原版命中后的实际计算。不能据此优化地形或扩大本次范围。

## 结论

最终七类结构/邻区/halo 的 18/18 独立对照通过，含完整结构 NBT、references、逐 bit Beardifier 与原版地形数据。两个宿主的冷缓存结构对照、完整 B、detached 交接和异常清理通过，不新增快照忽略字段。

B 三维度 FULL、同目标 32 次并发仅一个目标 Artifact、修改后保存重启通过。旧 Ordering 1/2 世界保存重载通过，已保存 FULL 的 Artifact/SPAWN 再生成均为零，玩家修改保留。普通世界前沿运行通过，214 项参考 Target 单元测试及两个 Target 发行检查通过，严格合规失败/警告均为零。

工程格式检查沿用已记录的根 spotlessMisc 运行目录遍历排除，不修改模板来绕过；Java、资源、Markdown 与其余检查执行。本次不修改地形、生成合同、旧存档身份或模板文件。不限 CPU 的真实客户端改善仍需使用者对新区飞行作人工判断，不以四 CPU 对照或启动菜单代替验收。
