# Fabric 双队列验证的端口碰撞

调查日期：2026-10-17。结论：两次中断均为验证启动参数共享造成的监听失败，没有得到相应角色的生成结果；不能算完整 B 通过，也不是已证明的世界生成差异。

## 问题

26.3 Fabric 装饰前与独立 B 在不同实例、世界和四 CPU 掩码中并行，但两项验证的原生服务端端口都是 25589。第一次 B 批次完成十四份结果后，village-edge 的 host_17 启动失败。

## 方法

保留失败实例、原始日志、崩溃报告与已有结果；只读检查导出的 launcher.json、Loom 配置和本版 Main、DedicatedServer 的字节码。验证工具另建标签，不覆盖旧目录；不改 Minecraft 或产品源码、fixture、随机规则及比较标准。

## 发现

第一次失败日志（本机证据：`../../validations/conformance-feature_b/result/climate_state/independent/26.3-fabric/village-edge-host_17.log`）记录 14:43:27 监听 127.0.0.1:25589，随后 FAILED TO BIND TO PORT、BindException 和 Failed to initialize server。进程 97976 使用 affinity 61440、BelowNormal。该批次只有十四份 summary：四份 PRODUCED、十份 PASS，缺失当前角色和其余 fixture；不提升套件状态。日志 SHA-256 为 6A5225BD00E62AF2FDC7AC01D04B152D8FF572956C6635EDABD1042FF51D68EA。

导出的 gameArguments 已包含 DLI 追加的参数：前部 --port 0 后仍有 --port 25589。仅检查第一个参数不能证明实际监听端口。Loom 的共享 launch.cfg 还会随任务重写，不能当作两个队列的独立配置。

第二次失败日志（本机证据：`../../validations/conformance-feature_b/result/climate_state_ports/independent/26.3-fabric/export.log`）记录 14:49:56 同样的监听失败；本次已使用私有 DLI 配置，但没有规范化导出参数中的第二个端口，尚无 fixture summary。日志 SHA-256 为 AA04CBB1FF7EF67DAC5B39B086B49E907ACBF71CDF7DB33C69EF7719AA026873。

本版 Main 将命令行端口传给 DedicatedServer.setPort；DedicatedServer 只有 getPort 小于零时才用 server.properties 的值，零是合法的临时监听端口。因此不是 Minecraft 拒绝 port 0，而是实际收到的最后一个值仍为 25589。

修正后的验证编排复制原生配置至自身实例，只规范服务端端口；同时把导出游戏参数中的每个明确端口统一为零，其余原生参数不变。每次启动记录 PID、四 CPU 掩码、低优先级、世界与配置哈希、实际游戏参数；CheckOnly 还要求启动日志实际为 port 0。未知参数布局或配置改变直接失败，不猜测。

新条件批次（本机证据：`../../validations/conformance-feature_b/result/climate_state_launch_ports/independent/26.3-fabric`）的原点 native_0、native_1 在 14:51:26、14:51:41 明确记录监听 127.0.0.1:0，两个原版与两个无关宿主均已完成。完整套件结论由里程碑的当前状态维护，不从这四份结果外推。

## 结论

验证队列隔离必须覆盖最终有效配置和参数，而不只是目录、CPU 与第一个 --port。两份原失败保持未完成，后续独立批次不回填历史。修正仅属于工具，装饰前及完整 B 仍绑定原来的 ACCC5307CA415A2B35A1C0A9D8BED6C0074958667CBC66B43CBD856A8773D492 源码输入；没有改变生成合同或调度语义。
