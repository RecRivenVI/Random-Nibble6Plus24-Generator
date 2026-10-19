# 1.20.1 内存身份状态与 SavedData 准备验证

调查日期：2026-10-17。基础适配已编译并在真实 Forge 注册表生命周期内通过组件检查；尚未注册生成器或接通生产 FULL，不提升为该 Target 可用。

## 问题

本版 SavedData 不使用新版本的 Factory/HolderLookup 签名，生成器采用 Codec 和带 Executor 的阶段入口。生产接线前需要证明身份数据不误用新版 API，并保留 WorldOptions 权威、跨世界隔离与关闭失效。

## 方法

准备本版 fail-closed 生成器、profile SavedData、world-owned runtime state 和生命周期身份加载适配，但不注册 codec 或激活尚未接通的生产路径。版本常量由源码模板展开，不能在 marker 中重复写死 Minecraft 版本。

检查在普通 fresh Forge 世界的 ServerStartedEvent 内执行，四逻辑 CPU、低优先级，随后独立 CheckOnly。使用真实注册表和 NBT Codec，不在未初始化的 JUnit 环境伪造原生注册表；不把组件状态检查称为生产身份加载。

## 发现

输入指纹 `15CCFEDD63D38466A8540D0246C13E517845494E115E045BFFB306DCBAFC91C9` 的[独立结果](../../validations/conformance-seed/result/runtime-identity/1.20.1-forge/native-seed.json)为 PASS，包含原 45 项种子/格式断言和新增 17 项检查。

新检查确认服务端原生维度生成器注册表可用；一万次查询复用同一 context；普通已验证世界没有 Mosaic authority；同 seed 的另一 WorldOptions 对象不能冒充原 authority；替换、跨 world、关闭和新服务器状态彼此隔离；三维度种子域不同且保留主维度原点；本版 save(CompoundTag)/load(CompoundTag) 的 profile NBT 往返一致。

生成器、main 和 probe 编译通过，工具组件 check 通过，实际服务端正常保存关闭，无新增崩溃。最初编译提示模板缺少 Minecraft 版本常量，已改为从 target 事实展开；未改种子或存档合同。验证成功的临时世界已按工具规则移入回收站，初次缺失 Mixin 绑定的历史失败世界没有删除。

## 结论

已证明这批本版 API 和内存状态组件，不证明真实 Mosaic world load、physical Chunk 生命周期、保存 FULL 重载、玩家进入或性能。原生身份/ChunkMap 接线和对应游戏门禁仍需继续完成；生成器保持未注册，普通世界不会进入未完成的生成路径。
