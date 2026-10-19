# 1.20.1 下界与末地原生 FULL 交接

调查日期：2026-10-17。下界与末地各四个独立 Forge JVM 的普通 Vanilla/B FULL、保存及重载通过；只证明 ChunkStatus.FULL 交接范围，不提升为出生、自然 ticking、完整结构运行语义或性能收口。

## 问题

独立 B/Artifact 对照不能证明物理 scheduler 已能消费结果，需要在本版普通 getChunkFuture(FULL) 和真实保存/重启中验证。主世界的预算与关闭失败保留于[故障记录](incident-legacy_native_request.md)，不由另两个维度的通过抵消。

## 方法

使用[原生物理验证](../../validations/integration-legacy_physical/validation.md)，四逻辑 CPU、低优先级，维度顺序执行。每个维度分别新建普通 Vanilla 和隐藏 B 世界，各以新的 JVM 重载同一 save；所有请求都走本版公开 FULL 接口，不手动调用生成阶段。

输入指纹为 08716C5CC8A98BCDFBC875360121AA7282627395807AE592BF985A393DBE2501。目标均为 (125,-37)，master 123456789；B 下界 local -8648803285809077725，末地 local -5783970183263702423。两个 Vanilla 世界用 master seed，它们是零侵入生命周期控制，不是与 B 的 local-seed 地形内容 Oracle。

新 B 保存前从原生 holder 只读取得全部实际依赖，对照其独立 Artifact 的 stored-biome palette；不为断言另发请求、不接受只有 BIOMES 标签的空数据。保存前放置一个明确测试标记，重载按原始 NBT 比较结构及全部目标群系数据。通过后回收验证世界，保留原始报告、结构和群系证据。

## 发现

| 维度 | Vanilla fresh / reload PID | B fresh / reload PID | B 首次 FULL | B 重载 FULL |
| --- | --- | --- | --- | --- |
| 下界 | 115732 / 43152 | 116684 / 103276 | 23.580 s | 0.678 s |
| 末地 | 74612 / 100160 | 114792 / 112408 | 21.850 s | 0.724 s |

八个 PID 独立，报告角色与 save 均由工具严格复核。证据分别在[下界](../../validations/integration-legacy_physical/result/current/1.20.1-forge/b/minecraft-the_nether)和[末地](../../validations/integration-legacy_physical/result/current/1.20.1-forge/b/minecraft-the_end)，相应普通控制位于同级 vanilla 目录。

每个 B 首次请求产生原生依赖要求的 49 个 Artifact/物化/POI，三者计数一致；目标生成恰好一次，原生 LIGHT、SPAWN、FULL 各一次。全部 49 个 BIOMES+ owner 的 50,176 个 quart cell 与其 canonical Artifact 相同；没有用空 palette 代替较低阶段的物理输入。这里只有一个 FULL，不把 49 次 Artifact 计为 49 个 gameplay-ready Chunk。

重载时 canonical、Artifact、物化、POI 和 SPAWN 均为零，原生 LIGHT/FULL 加载回调仍可执行。目标 1,024 个 quart cell、标记方块、结构原始 NBT 和光照正确标志保持。四个普通 Vanilla 运行均未进入 Mosaic canonical/物化/SPAWN 路径。

所有八个实例正常停止，无新的 crash report。B 关闭时 worker、in-flight 和临时绑定均为零；本组没有未完成任务，所以不能替代关闭取消回归。SPAWN 回调执行也不等于取得人口实体正例；真实自然 tick、BlockEntity、跨边界流体/实体和完整结构刷怪仍需分别验证。

这些是单目标生命周期计时，不是固定探索 A/B、吞吐或 send-ready 分位数，不能用它们计算正式相对 Vanilla 性能比。

## 结论

1.20.1 的下界与末地已取得本版真实 FULL/保存重载证据，普通 Vanilla 保持生成路径不变。主世界、未完成任务关闭、出生/重生、维度往返、并发/顺序及运行期结构语义仍未因此通过。

验证结束重新计算输入指纹，运行中没有改动该 Target 源码；最新门禁和缺项统一在[里程碑矩阵](../project/milestone-features.md)维护。
