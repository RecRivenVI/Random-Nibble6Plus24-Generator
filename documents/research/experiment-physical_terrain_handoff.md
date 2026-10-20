# 物理部分地形与私有副本交接

调查日期：2026-10-20。Fabric 部分 TERRAIN 与私有副本复用的三组配对，平均请求等待改善中位数约 5.07%；外部负载波动明显，只作为集成候选，不代表相对 Vanilla 性能目标或完整交付门禁通过。

## 问题

原生 FULL 请求需要只到 BIOMES、只到 TERRAIN 的物理输入。全部做成完整 B 会提前装饰远处依赖；改为真正部分阶段后，又观察到同一位置先生成 TERRAIN、后重算 B 目标地形。需要避免实现重复，不改变真实物理依赖或 B 的同种子邻区读取。

## 方法

26.3 原生 ChunkPyramid、ChunkStep 和 ServerChunkCache 字节码确认：FEATURES 直接需要半径一 TERRAIN，LIGHT 实际需要 INITIALIZE_LIGHT 邻区。保留原生状态需求，不改票据、holder、调度器或线程数量。

部分准备使用原生地形入口、stored biome 和紧凑结构输入，停止在请求阶段，不执行 FEATURES 或创建 Artifact。后续 B 只复制已完成目标 TERRAIN 的私有 section、heightmap、tick、entity、BE 和后处理数据；starts／references 从新隔离结构上下文获得，避免共享可被放置修改的 pieces。原物理输入不被装饰修改，关闭后的 session 不保留；升级、混合与未验证模组环境继续精确回退。

独立 Oracle 覆盖主世界原点、非原点、下界和末地水晶：普通 Vanilla 两份 fresh JVM、两个无关宿主各一份，共十六份；另外同样四场景经压缩 NBT 写盘、原生解析和加载，再完整对照一次。既有有限实体规则不扩大。

## 发现

### 正确性与票据

四场景私有副本对照、四场景磁盘输入对照均通过，包括完整目标、feature seed 流、可见群系、结构元数据和物理源未变。磁盘组 inputHash 为 `BC57D1BF300E62C04F63CB40545F27125448FC1079EDE59BFA2957CAEC083AE0`，入口为 `validations/conformance-feature_b/result/retained_terrain_disk_reload/independent_smoke/26.3-fabric`。包含实体、流体 tick 和后处理正例，不含 BE／方块 tick 正例，不能替代最终结构矩阵。

部分 TERRAIN 三维度四角色保存重载通过：部分请求不捕获 Artifact，部分重载不重建输入，升级 FULL 每目标只捕获一次，FULL 重载不重复 Artifact／SPAWN。独立冷区 FULL 的 Artifact 义务二十五降到九，不等于同比性能收益。

二十秒票据诊断中，2,137 次票据观察全部在服务端线程。86 个 BIOMES owner、74 个 TERRAIN owner 的原生需求仍为各自阶段，没有已知 FULL 需求。不能猜测随后升级、提前做 FULL。入口为性能目录的 `native_ticket_demand_scope/b-duration20-v8-s8`，不是六十秒吞吐样本。

### 性能

六次运行 inputHash 为 `14E5913A98D79340F69790C36B913500E07BD1C5719E18FAE6B012C0957F02C5`。固定四逻辑 CPU、低优先级、8/8、六十秒、相同种子与有限移动，本线程单实例；其它线程可运行，记录整机负载。

| 配对 | 平均请求等待秒 reference→candidate | FULL | send-ready | 整机 CPU 均值 reference→candidate |
| --- | --- | --- | --- | --- |
| 筛选 | 41.395→39.295 | 340→357 | 270→283 | 57.38%→59.45% |
| 反向 | 41.160→36.523 | 341→396 | 271→318 | 53.80%→54.29% |
| 正向 | 38.659→38.017 | 361→359 | 289→287 | 46.21%→63.95% |

平均等待包含同窗全部 357 个需求，未完成请求按观察下界计入，不伪造完成延迟。三组改善为 5.07%、11.27%、1.66%；共同完成目标的条件 p95 为 58.933→55.356、58.309→51.201、54.593→54.406 秒。未完成数 87→74、86→66、68→70，第三组没有吞吐改善。

采样分配量除以 FULL 约下降 1%～4.5%，但分子含未完成工作，不是逐目标精确分配。GC 后堆中位数 reference 为 343／357／364 MB，candidate 为 307／308／325 MB（十进制近似）；未观察持续驻留增大，不能据采样证明绝对零变化。

归并结果为 `validations/performance-worldgen/result/b_milestone/26.3-fabric/terrain-paired.json`；标签为 `retained_terrain_handoff_screen`、`retained_terrain_handoff_reverse`、`retained_terrain_handoff_forward`。没有同源新 Vanilla 对照，不能与旧数字相除宣称八成门槛通过。

## 结论

精确交接消除部分重复地形计算，未修改 B 内容或物理邻区权威，但收益有限、有机器噪声。保留到集成候选，仍须冻结后的完整受影响 Oracle、两个 Target 与三个视距正式 Vanilla 对照；对象和 Artifact 数减少不是可玩性证明。

NeoForge 部分 BIOMES 单独筛选改善约 3.89%，加原有窄锁入口后约 0.51%，均不足独立保留。组合交接需单独证明该端序列化、生命周期与端到端收益，不能机械引用 Fabric 结论。
