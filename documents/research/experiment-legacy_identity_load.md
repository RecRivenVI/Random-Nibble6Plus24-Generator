# 1.20.1 真实世界身份加载与关闭

调查日期：2026-10-17。实际 Forge 普通世界的身份加载、替换失效与关闭十六项，连同既有十七项组件及四十五项种子/格式断言通过独立游戏与 CheckOnly。Mosaic generator 仍未注册，不是 Mosaic production FULL 通过。

## 问题

独立内存状态和 SavedData 编解码测试不能代替真实 MinecraftServer/ServerLevel 生命周期。需要把已适配的 authority 对象接入本版本的加载边界，并证明 profile replacement 和关闭不会留下可用旧身份。

## 方法

核对实际 Forge 47.4.28 patched 字节码：createLevels 先将 overworld 放入 levels，再调用首次 getDataStorage，随后才做初始出生生成；DimensionDataStorage 使用原生 cache/set/get 接口。身份只在这个加载边界验证，在 createLevels 返回时检查三维度归属，查询期间纯内存读取。

本版没有当前编译可用的 MixinExtras WrapMethod，不为复制新版关闭代码增加依赖。按 Forge 原生 runServer 异常/正常退出链，在 HIGHEST 优先级 ServerStoppedEvent 清除 server authority；ServerLevel.close 开始即拒绝继续使用该 level 身份。普通 probe 的 ServerStoppedEvent 在其后观察，不能在刚请求 halt 时提前判 PASS。

可丢弃普通世界 cache 中短暂替换 profile，以真实 SavedData.set 触发失效，再显式从磁盘重验恢复 Vanilla。重验时 profile 文件仍不存在，不把该 cache fixture 当作 Mosaic 存档。原十七项组件与四十五项种子断言不删减。

## 发现

输入指纹 `2F40AAD2F3F966C312FC181438F0B7A9E12B9AA7C265ED97AAE15BACD0390E40`，游戏 PID 108392，四逻辑 CPU、低优先级。实际三维度加载、cache 替换后 fail-closed、显式重验和关闭均通过；停止事件后 server 查询拒绝，三份 level 身份已关闭，探针持有 level 引用清零。所有正常维度保存完成，无新崩溃报告。

结果固定在 [world-load-identity](../../validations/conformance-seed/result/world-load-identity/1.20.1-forge/native-seed.json)，完整日志同目录；独立 CheckOnly 再次通过。原组件证据在 runtime-identity 保留，没有覆盖其旧指纹。

工具从 validation.toml 读取专用端口，支持独立四 CPU 队列，不共享运行目录；结果 FAIL 时先保全世界/日志，不在检查失败证据前回收它。编译期缺少 WrapMethod 的错误不是游戏崩溃，最终使用原生 Forge 事件完成适配。

## 结论

真实普通世界的身份生命周期接线已证明；内存归属、失效与本版启动/关闭边界不再只是模拟组件。Mosaic codec、物化、SPAWN/FULL、Overlay 和玩家生命周期仍需实际接线与独立验收，不因本结果改变可用性声明。
