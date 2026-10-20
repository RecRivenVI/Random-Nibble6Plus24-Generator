# NeoForge 部分阶段组合交接

调查日期：2026-10-20。精确部分 BIOMES／TERRAIN、窄结构入口与私有地形副本的组合，三组受限交错对照平均请求等待改善中位数约 10.08%；这是旧 B 与候选的比较，不代表相对 Vanilla 目标通过。

## 问题

NeoForge 原路径把只需装饰前状态的物理依赖提前生成完整 B。单独部分 BIOMES 及缩锁筛选不足保留阈值，需要分别验证完整组合的内容、生命周期与性能，不能引用 Fabric 结果作为本端证明。

## 方法

沿用原生 ChunkStatus 需求，只完成请求的部分阶段；进入 B 时复制已存在 TERRAIN 的私有工作状态，结构仍使用新隔离上下文。未知模组环境准确回退，不改票据、调度器、随机流或生成合同。

四场景使用两份 fresh Native 和两个无关宿主，宿主输入经过压缩 NBT 写盘、原生解析重载，再完整对照 B。三维度分别验证普通部分请求、并发升级、部分重载和 FULL 重载。

性能固定四逻辑 CPU、低优先级、8/8、六十秒与相同有限移动；本线程单实例，三组按 reference→candidate、candidate→reference、reference→candidate 交错。reference 关闭物理部分阶段，candidate 启用整个组合；没有用单项开关结果冒充组合收益。

## 发现

四场景十六份独立 JVM 磁盘对照通过，原始输入不被装饰修改；实体、流体 tick 与后处理有正例，BE／方块 tick 正例不在这四场景中。inputHash 为 `E513DB04A5068FE42F69FB97714515FAD7FEE19B0268592491BDFB961F39090B`，入口为 `validations/conformance-feature_b/result/retained_terrain_disk_current_loader/independent_smoke/26.3-neoforge`。历史启动器引用旧 Loader 的失败原样保留，新运行显式选择当前原生启动元数据。

三维度四角色部分 TERRAIN 交接通过：部分请求无 Artifact，十六路升级每目标只捕获一次；部分重载不重建输入，FULL 重载不重新生成 Artifact／SPAWN。入口为生产生命周期的 `partial-terrain/retained_terrain_handoff`。

| 配对 | 平均请求等待秒 reference→candidate | FULL | send-ready | 整机 CPU 均值 reference→candidate |
| --- | --- | --- | --- | --- |
| 正向一 | 44.109→42.056 | 261→313 | 194→243 | 52.11%→54.82% |
| 反向 | 43.095→38.753 | 327→359 | 256→287 | 42.02%→47.27% |
| 正向二 | 45.374→38.875 | 263→346 | 199→274 | 64.51%→60.55% |

同窗全部 357 个需求都计入平均等待，未完成项按观察下界计入；三组改善为 4.66%、10.08%、14.32%。共同完成目标的条件 p95 为 57.122→52.996、56.363→52.646、58.304→47.135 秒；未完成需求 163→114、101→70、158→83。不能把条件 p95 当作所有需求的完成延迟。

每 FULL 的采样分配量约 150→127、142→133、150→131 MB；GC 后中位堆约 715→452、698→474、653→438 MB。采样分子含未完成工作，不能声称逐目标精确分配量。候选少做完整 canonical 任务而完成更多 FULL，canonical 次数下降不是吞吐退化。

六次运行 inputHash 为 `BE07ABF4A52BF05200B9FE0F24DAB2575708DB084C86F95CC26D8BECB9687967`。归并入口为 `validations/performance-worldgen/result/b_milestone/26.3-neoforge/biomes-paired.json`，标签为 `terrain_bundle_first`、`terrain_bundle_reverse`、`terrain_bundle_forward`。

## 结论

组合候选达到约 5% 的筛选阈值，p95 与内存未见退化，保留到最终集成。单独部分 BIOMES 的低收益结论不变；完整结构／B 矩阵及同源 Vanilla 三视距比较仍待最终冻结验证。
