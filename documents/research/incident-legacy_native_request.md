# 1.20.1 原生物理请求与关闭预算故障

调查日期：2026-10-17。实际普通 Vanilla FULL/保存重载通过；Mosaic 主世界尚未通过。旧版 Mixin 初始化缺失已修复，启动探针同步阻塞已改正；保守原生结构准备仍超出原预算，关闭排空也未通过，不延长窗口或移植未经证明的快路径。

## 问题

1.20.1 Forge 的 B/Artifact 独立 Oracle 不能代替真实物理调度交接。本次接入普通 getChunkFuture(FULL) 后，先遇到身份字段为 null，再遇到启动回调同步等待触发看门狗，异步请求对照随后又暴露真实准备成本和关闭等待。

## 方法

依据本版 ChunkMap、ChunkStatus、ServerChunkCache、ChunkSerializer 的实际字节码接线，保留原生依赖调度、光照和 ProtoChunk→LevelChunk 转换。只有已校验 Mosaic B 的装饰前阶段、物化/POI 和本地种子人口生成接入本版适配；普通 Vanilla 不进入这些生成 hooks。

[独立验证](../../validations/integration-legacy_physical/validation.md)使用四逻辑 CPU、低优先级、原生看门狗和固定 300 秒观察预算，普通 Vanilla 与 B 分别新建世界，再各以新 JVM 重载；跳过启动出生准备，不以此证明玩家出生。输入固定为 master 123456789、主世界 Chunk (125,-37)、local -5161763991829980711。

比较 Mixin 导出的实际 MinecraftServer 字节码，而不是只阅读注入源码；对 B 取得线程栈及一段 20 秒 JFR，未同时运行另一个性能实例。原始故障和测试世界均保留，不修改历史结果。

## 发现

### 身份初始化

实际加载的 MinecraftServer 在新增第二个 Mixin 字段初始化后没有写入 identity 字段，而另一个集合初始化仍存在。编译后的 Mixin 本身含有两次赋值，因此不能用编译通过排除注入问题。改为构造器 RETURN 显式初始化每个实例的 owner，ServerLevel 的两个临时 owner 采用同一明确边界；没有静态跨世界缓存。

修复后的普通 Vanilla 新建与新 JVM 重载均完成 FULL。另一个独立原生游戏通过 16 项真实身份加载/替换/关闭、17 项上下文归属和 45 项种子/格式检查，证据在 physical_owner（本机证据：`../../validations/conformance-seed/result/world-load-identity-physical_owner/1.20.1-forge`）。该身份结果属于其记录的输入指纹，不替代 B FULL 或后续探针源码的当前输入复核。

原始身份故障在 Vanilla first_failure（本机证据：`../../validations/integration-legacy_physical/result/first_failure/1.20.1-forge/vanilla/minecraft-overworld`）；实际导出 class 留在该验证实例的 .mixin.out，不进入发行构件或文档证据目录。

### 启动探针与看门狗

本版公开 ServerChunkCache.getChunkFuture 在服务端线程调用时会执行 managedBlock。探针在 ServerStartedEvent 直接调用，B 计算超过一分钟后触发原生看门狗；异常栈精确落在 NativePhysicalProbe.started。此轮保留于 B first_failure（本机证据：`../../validations/integration-legacy_physical/result/first_failure/1.20.1-forge/b/minecraft-overworld`）。

探针改为从调用线程提交同一公开接口，再由服务端 tick 观察原生 future。未手工调用内部阶段、关闭看门狗、缩小依赖或延长预算。普通 Vanilla 对照仍使用完全相同的请求方式。

### 异步请求后的实际未通过项

输入 08716C5CC8A98BCDFBC875360121AA7282627395807AE592BF985A393DBE2501 的普通 Vanilla 主世界新建与重载分别约 1.98 秒和 0.67 秒完成；B 在 300 秒内未达到 FULL，探针记录 FAIL 并请求原生停服。当前主世界原始目录（本机证据：`../../validations/integration-legacy_physical/result/current/1.20.1-forge/b/minecraft-overworld`）保留请求、异常、JFR 和关闭线程栈，不补造 FULL 或 send-ready 比值。

20.081 秒诊断窗口有 1,860 个 CPU 样本，其中 1,819 个（97.80%）在原生 Stronghold ring biome search 调用链。GC 暂停累计约 6.01 毫秒；这不是完整探索 A/B，也不能据此报告吞吐或最大 CPU 利用率。1.20.1 当前紧凑输入仍使用未优化的原生环准备，不能继承其它 Target 的 Lazy 正确性或性能结论。

07:00:35 开始原生停止，随后仍在 stopServer→waitUntilNextTick 等待；后台结构/地形 worker 尚未排空。07:02:01 在保留关闭线程栈后结束唯一核实属于本验证的 JVM（PID 4088）。这是受控终止未完成的测试进程，不是又一次 JVM fatal、正常停服或存档重载通过。世界保留，未作为可游玩存档交付。

## 结论

普通 Vanilla 零侵入控制与实例身份修复有实际运行证据；Mosaic 主世界物理 FULL 和关闭门禁继续未通过。应先闭合本版后台准备的生命周期终止边界，再依据有限假设独立证明结构准备快路径；不得用延长超时、关闭看门狗、假邻区或未经证明的跨版优化通过。

本记录不推定下界/末地、出生、自然 ticking、并发、BOP 或性能收口状态；各项最新结论只在[当前里程碑](../project/milestone-features.md)维护。
