# 产品候选验收

参考端为 26.3 Fabric。首版交付范围与通过项见[里程碑 2](../project/milestone-productization.md)，性能 PARTIAL 为已知限制；本页仅准备独立验收入口，不启动它，不关闭旧验收实例。

## 准备与启动

已登记 `b-product-server` 与 `b-product-client`，使用新的 `mosaic-b-product` 世界；没有复制地形、玩家、索引或 Overlay。真实生产路径继续生成所有新区，不使用阶段验证属性或 probe。

先在一个终端启动服务端，看到 ready 后在另一个终端启动客户端：

```powershell
.\gradlew.bat :version:26.3-fabric:runBProductServer
.\gradlew.bat :version:26.3-fabric:runBProductClient
```

客户端自动连接 `127.0.0.1:25603`。本机配置为 RecRivenVI、2560×1440、音量 50%、音乐关闭、内存上限 16GB；已登记兼容的 Custom Skin Loader，已登记输入没有匹配 26.3 的 Zume，跳过。服务端保持原生 B 环境，不装这些客户端模组。

首次连接后，在服务端终端执行 `op RecRivenVI`，然后在游戏内执行 `/gamemode creative`。独立服务端的管理员权限与单人存档的作弊开关不同；本入口不预先写入玩家或伪造权限。

## 人工短清单

1. 用创造模式飞向新区，观察硬边界、装饰切断、光照及流体。性能不作为原版级流畅承诺。
2. 执行 `/seed`，应分别看到世界种子与当前位置区块种子。
3. 执行 `/locate structure minecraft:village_plains`、`/locate biome minecraft:plains`、`/locate poi minecraft:home`，均应明确提示暂不支持，而不是给出位置。
4. 修改一个方块，保存退出并重进；修改应保留，新区仍可继续生成。

游戏内画面与交互由使用者判断；后台组件和服务器测试不替代这项人工验收。旧 `b-review` 实例与新实例独立，不在本轮关闭或重置。
