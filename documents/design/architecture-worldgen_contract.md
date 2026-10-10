# 世界生成合同组件

`worldgen_contract` 保存不引用 Minecraft 或 Fabric 的生命周期、取消、隔离模式、计数与错误类型。两个 Target 使用同一份源码；种子算法、生成顺序与存档合同没有因此变化。

## 职责与接口

组件保留原 Java 包与公开接口，只承载独立于游戏版本的值类型与生命周期边界。游戏对象、区块缓存、生成器、结构及 Mixin 仍由各 Target 独立实现。

## 构建

组件登记为 `product`，使用 `java-library`。模板从已登记 Target 推导 Java 要求，并将组件类合入每个发行 JAR；不另行发布 Maven 构件。

`check` 与 `verifyRelease` 分别检查组件构建及发行 JAR 中的类。运行验证继续通过产品调用这些接口，不新增生成路径。
