# Fabric 状态修正后的三维度自然运行与重载

调查日期：2026-10-17。结论：状态安全结构来源的当前参考版通过三维度 Vanilla/B 区域 ticking 与保存重载；未证明玩家出生、维度往返、客户端发包、所有 owner 的群系前沿或性能保留。

## 问题

[独立原版矩阵](experiment-fabric_state_oracle.md)通过后，需要确认普通 physical FULL 请求、原生票据与 tick 循环仍可完成，并且新 JVM 重载不再次生成已保存 B Chunk。不能由组件、完整 B 快照或旧输入的运行结果替代。

## 方法

冻结 26.3 Fabric 全部 src 与 worldgen_contract；指纹为 3B48A6A92E70F74FC343DDAF9F1A8BE905E2C935C08ACB059B5CFCCDD7E4E090。此输入集合含 test/client，与装饰前及完整 B 的 main/probe 指纹 ACCC5307CA415A2B35A1C0A9D8BED6C0074958667CBC66B43CBD856A8773D492 不同；没有改写任一记录。

每维度创建一个普通 Vanilla 和一个真实 B 专用世界，均使用 masterSeed 123456789、目标 (125,-37)，分别执行 fresh 与新 JVM reload，共十二个游戏进程、六个世界。B 的各 physical Chunk 使用自身 S(C)，原版组仅作生命周期／零侵入控制，不把它的 master-seed 地形当成 B 的内容 Oracle。

只运行一个游戏实例，四逻辑 CPU、BelowNormal，不与性能测量或其它游戏重叠。票据和正常 tick 循环驱动 49 个 FULL 与两侧实体 ticking；在明确标记的空中区域安装运行期测试方块、掉落物与熔炉，不直接调用 tick callback 或私有 promotion。正常保存退出后新进程重载并重复运行。结束后复核六份条件指纹、十二个独立 PID、实际状态与零重生成计数。

## 发现

| 维度 | B fresh / reload PID | Vanilla fresh / reload PID | 结果 |
| --- | --- | --- | --- |
| 主世界 | 106612 / 96532 | 23188 / 109688 | 全部 PASS |
| 下界 | 97596 / 58628 | 82980 / 91468 | 全部 PASS |
| 末地 | 91036 / 102548 | 72180 / 89492 | 全部 PASS |

每个进程均完成 220 个自然 game tick。掉落物曾跨 physical seam、实体管理器仅有一份对应 UUID，block/fluid callback 为正，水或下界岩浆真实传播，熔炉完成烧炼；两侧仍为实际 LevelChunk、光照正确。探针移除票据、恢复测试方块、清除实体后 generation/spawn binding 与 in-flight 均为零。

B 三维度的 fresh 前沿各记录 156 次 B 生成，而实际要求 FULL 为 49；不能把它们误写为“只生成 49 个 canonical target”，也不能把这些正确性测试时间当作吞吐。原生依赖的额外工作仍存在，性能须由固定前沿和普通原版单独测量。

六次重载均保留测试修改；B 的 generation、Artifact、发布、POI、INITIALIZE_LIGHT、LIGHT、SPAWN 与 FULL 生成计数为零，无生命周期重复。普通 Vanilla 的 Mosaic generation、Artifact、POI 与生产计划计数均为零。

B 的初次主目标 physicalBiomeChecks 为 49，重载为零。Fabric schema 1 没有逐一审计全部 49 个 owner 的 generation/loading BIOMES；它不能被写成 NeoForge schema 2 的全 owner 证明。运行期设施也不替代自然生成的实体／方块实体正例持久化。

证据分别位于 B runtime-proof（本机证据：`../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/features-2/development-b/runtime-proof`） 与 Vanilla runtime-proof（本机证据：`../../validations/integration-production_lifecycle/result/current/26.3-fabric/phase3c2-production/vanilla-control/runtime-proof`） 下各维度的 climate_state。只读复核入口及通过范围见[验证说明](../../validations/integration-production_lifecycle/validation.md)。

## 结论

当前状态修正没有破坏已验证的区域 ticking、跨 seed 物理互动、保存／重载或普通原版路径；原生错误没有被新增 catch 或默认值掩盖。没有修改 B 合同、Seed V1、Ordering1/2、format V2、旧存档或 Structure Overlay。

仍须分别完成宿主预热／线程与顺序、环搜索续流、全部 owner 的群系前沿、自然生成正例持久化和有限世界功能门禁。十二次正确性运行没有 JFR 或性能对照，不能给出当前输入相对 Vanilla 的吞吐或 send-ready p50/p95；历史性能保留结论不转移到这个输入。
