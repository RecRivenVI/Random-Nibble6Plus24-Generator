# 26.3 完整高度结构 Oracle 重验

调查日期：2026-10-17。26.3 Fabric 与 NeoForge 各自完成十八项完整高度结构对照，共七十二个独立游戏 JVM／存档；两端均通过。另复核 NeoForge 状态守卫输入的七十二项装饰前、十九项完整 B 与三维度运行重载；不把不同指纹改标为同一轮。

## 问题

[结构密度覆盖调查](incident-beardifier_height_coverage.md)指出，两端验证循环漏掉 getMaxY 包含的最高一层。旧证据保留，但不能继续证明完整高度的直接 Beardifier 输入等价。修正不得改变生产地形、结构算法或以补零修补历史结果。

该调查记录关于“验证配置端口为 0”和“full_height 仍在运行”的两条现场判断不成立，本文更正：模板 2.0.2 的实例配置端口只接受 1–65535，两个启动尝试实际在 Gradle 配置时失败，游戏尚未开始。原始失败分别在 NeoForge export（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_full_height/26.3-neoforge/export.log`） 与 Fabric export（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_full_height/26.3-fabric/export.log`），不计生成失败或 Oracle 通过，不改写旧记录。

## 方法

验证配置保留合法端口 25589，项目工具从本验证此前实际导出的 launcher.json 复制原生启动配置到自己的实例目录。Fabric 使用独占 DLI 配置，并覆盖导出参数中已识别的端口；NeoForge 使用独占原生参数文件。只把监听端口设为 0，不改模板、Loader 或 JAR，不猜测未知入口。每次重新启动 fresh JVM／save，独立重新导出并生成 fixture；既有 launcher 只提供启动参数，不提供 Oracle 数据。

两条正确性队列分别使用四逻辑 CPU、BelowNormal，掩码为 61440 与 3840；实例、世界和自动分配的端口独立。每个角色记录 PID、资源限制、世界、私有参数文件及其 SHA-256，CheckOnly 同时核对真实绑定日志。没有与性能测量重叠。

完整高度 report 为 coverageVersion 2，原生循环逐次计数，包含最高层，检查实际 payload 长度、四字节 float、完整边界和原始字节 SHA-256。两侧再严格比较完整结构 NBT、pieces／junctions／references、投影、BIOMES、TERRAIN、密度／表面／洞穴内部结果；FEATURES 执行数必须为零。编码负例仅用于测试检查器，不冒充世界生成数据。

## 发现

| Target | 完整高度结果 | 独立 JVM／save | 实际密度样本总数（两角色） | 本次源码指纹 |
| --- | --- | --- | --- | --- |
| 26.3-fabric | 18/18 PASS（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-fabric`） | 36／36 | 3276800 | 79C2C17E9D9A8A09CABF4540B4E77672FA48811F4EE4B981803E0BEE5809333C |
| 26.3-neoforge | 18/18 PASS（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_complete_height/26.3-neoforge`） | 36／36 | 3276800 | 7172EA749A0F0F9FFA4F57A2BE4B3C9C6424480EB22CD7A16C487820B83A5073 |

每端包含 Village、Mineshaft、Trial Chambers、Ocean Monument、Fortress、End City、Stronghold 的 start／邻区正例、两个地形适配 halo，以及实际 Mosaic origin／non-origin。每个指定原生 start 都有 pieces，halo 有非零原生密度样本。村庄样本实际为 98304 个 float、393216 字节；原始密度 hash 两侧一致，不再仅信任声明样本数。

两端正常完成并退出，随后 spotlessApply 没有改变这两份输入，当前源码 CheckOnly 仍通过。完整构建日志（本机证据：`../../build/research/modern-full-height-final-gates-stdout.log`）记录 spotlessApply、check、verifyRelease、verifyCompliance --strict 全部成功，3 分 28 秒、217 个任务，其中 39 个执行；六个 Target 发行 JAR 均检查探针排除。严格合规为零失败、零警告，158 份 Markdown 检查通过。Gradle 未来版本弃用提示与 JOML Unsafe 提示保留，不冒充合规警告。

独占参数复制已从两个验证入口提取到同一个工具函数，没有改变已验证 B 的启动语义；工具回归包含两种加载器的实际参数复制、源文件不变及外部路径拒绝，目标布局工具四十三项通过。原始旧密度证据被新检查拒绝，失败日志保留。

### NeoForge 状态守卫的独立范围

[状态守卫调查](incident-neoforge_source_climate_state.md)对应输入 75875AB13693D240BB2738FAD7BD6E0AAAA8858C7F958CC57B9910CA25E38BE7。本轮以该显式历史指纹只读重验，不改其证据或世界：

- 装饰前矩阵（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_matrix_climate_state/26.3-neoforge`）：72/72，主世界 40、下界 16、末地 16；FEATURES 为零。
- 完整 B（本机证据：`../../validations/conformance-feature_b/result/climate_state_full/independent/26.3-neoforge`）：19/19，76 个 fresh JVM／save，两次原生与无关宿主 17／23；自然数据包括 8 BE、2 pending BE、6 实体、1280 block ticks、292 fluid ticks、1856 后处理位置。
- 运行重载（本机证据：`../../build/research/neoforge-climate-runtime-recheck.log`）：三维度各有 Vanilla／B 的 fresh 与 reload，共十二 JVM；49 FULL 物理前沿、ENTITY_TICKING、220 tick、真实跨边界实体／流体、熔炉及保存标记通过，已保存 FULL 不重建 Artifact／SPAWN，Vanilla 不进入 Mosaic。

这里的 NeoForge physicalBiomeChecks 只证明 BIOMES 依赖就绪，不证明逐值扫描全部 owner palette；自然生成正例与运行期测试设施也分别计量。最高层修正仅影响 structures 专用 probe 分支，不能据其新指纹倒填以上不同输入，但也不据此否定它们未受影响的门禁。

## 结论

两端完整高度结构门禁恢复为限定范围 PASS，未改变 Seed V1、Ordering1/2、format V2、B 切断、已有存档或原生结构／地形算法。没有新增 canonical 忽略规则，也不使用近似密度。

本轮没有性能收益结论。NeoForge 当前状态守卫下的三维度／三档端到端性能仍需单实例重测，不能复用守卫之前的比值；出生往返、并发及其它 Target／BOP 继续按里程碑分别验收。MCP 仅参考 Fabric 接入范围已复核，不从本次服务端 Oracle 推定其它 MCP Target 或玩家功能通过。
