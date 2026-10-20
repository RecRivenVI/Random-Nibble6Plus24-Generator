# 26.3 结构密度探针的最高层覆盖

调查日期：2026-10-17。26.3 的结构密度探针少采集最高一层，却声明完整高度样本数。本次只修验证读取、计数和负例检查，不修改生产结构／地形算法、生成合同或世界存档；受影响结构门禁须用新证据重验。

## 问题

实际 26.3 NeoForge patched LevelHeightAccessor.getMaxY 字节码返回 minY + height - 1，最高层包含在有效范围内。参考 Fabric 已确认相同边界。两端 PreFeaturesVerifier.captureBeardifier 和 CompactStructureFixtures 的密度扫描仍用 y < getMaxY，少一层；报告 samples 却按 256 × height 计算。

## 方法

直接核对本项目 26.3.0.83 制品的 javap 输出、探针循环，以及已有原生样本的 JSON 和二进制长度。不得补零、回写旧样本或把本次问题归为生产生成差异。

修正两端验证代码为包含最高层；在原生读取循环中累计真实样本数，结束时断言等于 256 × height。新 report 的 coverageVersion 为 2，包含 minY、maxYInclusive、height、sampleBytes、实际 samples 与原始 payload SHA-256。结构 CheckOnly 对两侧分别核验完整高度、四字节 float 长度和原始 hash，再做独立原生／消费者比较。

单元回归以编码夹具检查完整样本、少掉整层、少样本、错误边界、旧版本和错误 hash。编码夹具不是游戏 Oracle。新的结构验证仍分别启动原生 S(C) 和 unrelated-host 消费者，两个 Target 各用独立实例与四 CPU 队列；验证配置端口为 0，避免模板附加的固定端口覆盖原生命令。

## 发现

原生村庄样本（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state/26.3-neoforge/village_plains-start/native/beardifier.json`）声明 98304 样本，但二进制（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state/26.3-neoforge/village_plains-start/native/beardifier.bin`）只有 392192 字节，即 98048 个 float。差值正好 256 个，等于一层；应有长度为 393216 字节。旧报告中的 sha256 是对 payload 的十六进制文本再哈希，新 coverage 2 明确使用原始字节哈希，不混用两种定义。

旧 NeoForge 十八项编排在已完成九项后主动停止，保留为 WITHDRAWN_PROBE_COVERAGE，不计完整门禁通过，也不推定生成失败。只停止已核实归属的编排进程 108108，当前子 JVM 86628 获准正常保存退出；确认它已结束后才修改本 Target 源码。未改写旧目录的状态、哈希或缺失数据。旧数据被新检查拒绝（本机证据：`../../build/research/beardifier-coverage-old-rejection.log`）已记录。

编译和四十项工具回归（本机证据：`../../build/research/beardifier-coverage-compile.log`）通过。两端新结构证据目录分别为 NeoForge（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_full_height/26.3-neoforge`） 与 Fabric（本机证据：`../../validations/conformance-prefeatures/result/structure_reuse_structures_climate_state_full_height/26.3-fabric`），本文写成时仍在运行，不计通过。

装饰前 72 项不启用这个 structures 专用采集分支，其完整目标方块、群系、密度／表面／洞穴比较不由本缺口推翻；十九项完整 B 和正常运行重载也不使用该密度报告。其证据仍限定原输入与范围，不能提升为已证明完整高度的直接 Beardifier 输入对照。运行期 schema-2 的 NeoForge physicalBiomeChecks 是物理 BIOMES 依赖就绪检查，不是全部 palette 值扫描，不能借字段名宣传成全群系内容对照。

## 结论

这是验证覆盖和报告准确性问题，不是已定位的方块或结构输出错误。新检查必须拒绝有此缺口的证据；两端完整高度结构对照完成后才能恢复该项通过结论。生产 main 编译保持 UP-TO-DATE，未改变 Seed V1、Ordering1/2、format V2、B 切断、旧存档或原生算法。

不修改其它 Minecraft 版本的边界语义，不从本次 26.3 修正推定旧版也应使用包含式 getMaxY。其它保存／投影边界仍按各自门禁审计，不靠这份结构探针证明它们已正确。
