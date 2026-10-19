# BOP 本地种子隔离调查

调查日期：2026-10-15。范围为 Minecraft 26.3 Fabric、`inputs.toml` 登记的 BOP/TerraBlender 组合及 B 严格切断，不代表其它 Target 或任意地形模组兼容。

## 问题

普通宿主已经按物理种子初始化 TerraBlender 的群系来源；Mosaic 隔离生成器虽有独立 RandomState，却复用了这个来源。TerraBlender 的参数表只初始化一次，末地来源还持有四份带种子的区域数据，重新传递 local seed 不能修复已初始化的共享对象。

## 方法

读取本机 Minecraft 26.3 源码和 TerraBlender/BOP 上游源码。原生控制在全新 JVM/save 使用 S(C)，消费者在另一 JVM/save 使用宿主 17 或 23；不在原生生成后的同一物理世界中做隔离比较。所有运行串行、四逻辑 CPU、低优先级，不操作游戏 GUI。

验证环境、入口与固定结果路径见 [BOP 验证](../../validations/compatibility-bop/validation.md)。完整快照、原始失败、feature 排序和 UUID 数据均保留，没有添加方块忽略规则。

## 发现

修复前，master `123456789`、Overworld `(125,-37)`、local `-5161763991829980711` 的 BIOMES 已不同，TERRAIN 有 7,159 个方块不同。首差为 `(2009,50,-590)` 的 stone/orange_terracotta，说明不是仅表面着色或显示问题。

根上下文现复制原生参数条目或末地来源配置，并独立持有 NoiseGeneratorSettings 的表面规则缓存。通过 TerraBlender 原生初始化入口绑定 S(C)，不重新填充全局表面规则、不重置宿主来源。同种子相关上下文复用这份配置，RandomState 和结构状态仍独立；SPAWN 的备用 resolver 同样隔离。未安装 TerraBlender 时保留原有复制路径，未知接口或来源类型明确失败。

装饰前参考路径与轻量输入路径各九项独立控制通过，覆盖每维度两个实际 BOP stored palette 正例和三个派生种子样本。轻量路径另核对 density raw bits、表面、洞穴、结构输入与 palette 复用清理；异种子并发查询及宿主不变检查通过。

完整 B 的九个原生控制与两个无关宿主得到 16/18 通过。主世界、下界及一个末地 BOP 目标有真实 BOP 方块、tick 或后处理正例；失败集中于 seed 0、End `(112,-512)` 的 `end_reef`，包括 514 个不同方块的比较，没有将其归类为实体随机例外。

该末地目标的三个新原生进程有相同装饰前 hash `3ad1a2999125ccc8a2c844c4f79eb08d768797425d4736ee6242911e643bb5c1`，最终 BOP 方块分别为 925、895、1,026。第 7 步 jagged_sandstone/monolith 的索引、第 9 步群系装饰的索引跨进程改变。TerraBlender 的 `MixinTheEndBiomeSource` 用 `Collectors.toSet()` 合并 possible biomes；26.3 `ChunkGenerator` 把其枚举交给 `FeatureSorter`，原版又把索引用于 `setFeatureSeed`。这三个控制没有建立 isolated context，证明原生输入自身存在不稳定装饰结果。

隐藏生产 B 世界的普通 FULL 请求、测试方块修改、保存并重启通过。重载观察到 Artifact generation/SPAWN 为零，light 正常且修改保留；该项不替代末地装饰等价，也不宣称人工 BOP 游玩或性能通过。

## 结论

本地种子状态污染已修复，三维度装饰前原生兼容基线成立；完整 BOP FEATURES 兼容仍为 PARTIAL。若要保证末地跨 JVM 唯一装饰结果，需要另行决定如何处理上游无序枚举，不能在本轮偷偷固定排序或放宽判等。第三方表面规则保留精确完整回退，本轮没有进行兼容性能优化、移植其它 Target 或接入 C2ME。
