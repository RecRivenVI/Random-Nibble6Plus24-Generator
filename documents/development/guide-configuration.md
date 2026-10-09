# 配置系统接入指南

配置实现位于 `gradle/configuration/`，通过根 settings 的 included build 加载本地插件 `raven.repository-configuration`。它是构建基础设施，不是 Minecraft Target 或产品组件。根 build 只应用插件、`base` 并聚合配置测试。

## 配置权威与文件格式

| 文件 | 职责 | 本版支持 |
| --- | --- | --- |
| `gradle.properties` | Project Facts | 必填 `mod_id`、`mod_name`、`mod_group`、`mod_version`、`mod_environment`；其他项目事实使用 `mod_*` |
| `versions/<target>/target.properties` | Target Facts | 必填 `minecraft_version`、`loader`、`loader_version`、`java_version`；其他 Target 事实使用小写字母、数字与下划线 |
| `instance.properties` | 共享运行默认值 | 四级 `instance.*`，支持基础属性与额外的扁平运行属性 |
| `local.properties` | 本机工具路径与实例覆盖 | 可选；四级 `instance.*`、`java.<major>.home`、`launcher.prism.path` |

显式读取上述文件作为权威，不通过 `-Pmod_version=...` 或用户级 Gradle 配置另建产品事实覆盖层。根 gradle.properties 可保留 org.gradle.* 原生 JVM/性能选项，由 Gradle 按原生优先级读取；解析器将它们与 Project Facts 分离，不从 repositoryConfig.projectFacts 返回，也不参与实例 Cascade。org.gradle.jvmargs 控制构建 JVM，不作为 Minecraft 实例内存或追加参数。

文件采用 UTF-8、Java properties 语法；支持注释、转义与续行，重复键报错。Windows 路径推荐使用 `/`，例如 `C:/Tools/jdk-21`，避免 properties 与 JSON 双重反斜杠转义。相对本机路径相对于仓库根。

Target 文件中的版本和 Loader 必须与目录 ID 一致。Project Facts 不能放进 Target/local/instance 文件，Target Facts 和实例内存等不能混在一起；不引入 `.env`、其他 local 文件或 per-instance 配置权威。

产品验证的固定身份和隔离工作目录属于 `task/` 的场景约束，不增加第五级 Cascade。身份由同一场景输入供客户端、权限名单和断言消费；若解析出的 `playerName` 已设置且不同，启动前报错。未设置或相同时保留场景身份并只生成一次参数，不要求改变日常配置。普通运行偏好仍走统一 Cascade，不能用场景约束绕过覆盖规则。

## 四级 Cascade 与两个文件

对于每个 `(target, variant, property)`，在每个文件内按以下顺序选择第一个存在的键：

```properties
instance.[<target>].<variant>.<property>
instance.[<target>].<property>
instance.<variant>.<property>
instance.<property>
```

先在 `instance.properties` 得到共享默认值，再在 `local.properties` 独立求值；只要本机存在匹配键，就使用本机值。不能先合并文件再按具体程度搜索。

例如仓库的 `instance.[26.2-neoforge].client.memory=12G` 会被本机的 `instance.client.memory=8G` 覆盖为 `8G`。Target ID 带点号，必须整体放在方括号中。

所有属性共用这条解析路径。键存在且值为空仍算显式覆盖；空 extra args 清除共享追加参数，空内存等无效数值报错，不偷偷回退。

## 基础运行属性

| 属性 | 含义 |
| --- | --- |
| `memory` | 游戏 JVM 最大堆，正整数加 `K` / `M` / `G`，不配置 Gradle daemon |
| `width`、`height` | 客户端窗口尺寸，正整数像素 |
| `playerName` | 客户端开发名，3–16 个 ASCII 字母、数字或下划线 |
| `extraJvmArgs` | 多行参数列表，兼容单行 JSON；追加 JVM 参数 |
| `extraGameArgs` | 多行参数列表，兼容单行 JSON；追加游戏/服务端参数 |

没有匹配的可选属性时保留生态默认。server 不消费 width/height/playerName；两个客户端分别解析自己的玩家名。默认值以根 `instance.properties` 为准。

```properties
instance.memory=4G
instance.extraJvmArgs=[
    -Xms1G
    -Xmx8G
    -Ddemo=hello world
    -ea
]
instance.extraGameArgs=[
    --demo
    two words
]
```

多行块每个非空行是一个完整参数，去掉首尾空白但保留行内空格、反斜杠和引号。开始行的值只有 `[`，结束行只有 `]`，不需要逗号或包裹引号；注释放在块外，块内不解释注释、转义或续行。不经过 shell、环境变量替换或再次按空格拆分。空字段、`[]` 和空块都表示不追加。兼容原单行 JSON 数组，仍可表达空字符串参数；其转义继续遵循 properties 和 JSON 两层语法。选择最终列表后只追加一次，不合并不同作用域或文件的候选列表。

最终顺序：生态原有参数 → 项目必要参数 → 选中的实例追加参数。列表中的 `-Xmx` 优先于选中的 `instance.memory`；无 `-Xmx` 时使用 memory。先完成各自的文件/作用域 Cascade，再应用此规则，因此共享列表中的 `-Xmx` 也优先于本机 memory；本机空列表可清除共享参数并恢复 memory。重复或无效 `-Xmx` 报错。`-Xms` 等其他参数交给 JVM 解释，不保证任意 GC 参数组合兼容所有 Java 版本。游戏 `--width`、`--height`、`--username`、`--gameDir` 仍使用专门属性和目录模型。

## Profile 与本机工具

`mod_environment` 只允许 `client`、`server`、`both`。`deployment(variant)` 返回通常的部署建议：

| Profile | client | client-multiplayer | server |
| --- | --- | --- | --- |
| client | INSTALL | INSTALL | ABSENT |
| server | PROJECT_DEFINED | ABSENT | INSTALL |
| both | INSTALL | INSTALL | INSTALL |

这不是自动 Mod 部署器，也不是双方必须安装的网络合同。client 项目仍应验证装 Mod 的客户端连接未装该 Mod 的服务器；server 项目反向验证；both 覆盖单人、Dedicated Multiplayer 与两端一致性。

`java.<major>.home` 指向对应 JDK 根目录，使用时检查 `bin/java`、`bin/javac` 和 `release` 中的 Java 版本。没有本机路径时，JavaExec 适配器使用 Gradle 原生 toolchain 选择 Target 所需版本。`launcher.prism.path` 提供可查询的 Launcher 可执行文件路径，显式配置校验检查其存在；插件不启动 Launcher。

这些配置不更换已经启动的 Gradle JVM，也不安装工具。Target 的编译 toolchain 仍应从 `java_version` 配置在原生 Java/Loader DSL 中；本机路径可以通过 `javaHome(major)` 供需要该路径的工具接入使用。

## Target 的 Kotlin DSL 接入

本项目根 settings 显式注册唯一 Target `:version:26.2-fabric` 并映射到 `versions/26.2-fabric`。不扫描或注册测试假工程；不要复制测试 fixture 的模块作为产品 Target。

Target 构建脚本可以读取根扩展：

```kotlin
import raven.gradle.RepositoryConfiguration

val config = rootProject.extensions.getByType<RepositoryConfiguration>()
val target = project.name
val projectFacts = config.projectFacts
val targetFacts = config.targetFacts(target)
val environment = config.environment
val javaVersion = config.javaVersion(target)

// 在对应 Loader 的原生运行配置中使用这些值。
val client = config.resolveInstance(target, "client")
// client.memory(), width(), height(), playerName(), extraJvmArgs(), extraGameArgs()
// config.value(target, "client", "memory") 可查询最终值的 file/key/raw 来源。
```

Project group/version 已提供给根项目与正式 Target。Loader metadata、依赖、编译 toolchain 和运行 DSL 从这里消费事实，不在根 build 塞入 Loader-specific 分支。

Fabric 适配用 `effectiveMemory()` 设置 JavaExec 最大堆，仅把 `jvmArgumentsWithoutMaxHeap()` 传给尾部 provider。`memory()` 与完整 `extraJvmArgs()` 只保留解析/诊断含义，不能同时直接发射两份最大堆参数。模型检查同时断言最终恰有一个显式 -Xmx（如果配置了最大堆）。

对于普通 JavaExec，提供可选的真实运行适配器：

```kotlin
import raven.gradle.JavaExecAdapter

// 在已有 JavaExec 任务的配置块内调用一次；主类与 classpath 仍由原生构建负责。
JavaExecAdapter.configure(
    this, config, target, "client",
    listOf("-Dproject.option=required"),
    emptyList()
)
```

它在任务执行前解析选中值、按需创建 `instances/<target>/<variant>/`，设置工作目录、最大堆和本机 JDK，追加项目参数与实例参数。普通 client 参数为 `--width`、`--height`、`--username`，server 不追加这些字段。原任务不得重复提供这些托管选项；重复接入也会报错。

Loader 可能在执行时自行改写参数、选择进程或有专用 run DSL，因此不能假定所有 Loader 任务都能直接套用 JavaExec 适配器。真实 Loader 接入应优先使用其原生配置 API，并验证最终进程。验证项目的专用 instance 路径仍由验证任务负责；没有额外的场景配置层。

## 检查与可见性

```powershell
.\gradlew.bat verifyConfiguration
.\gradlew.bat check
```

`verifyConfiguration` 显式检查事实职责、所有声明的基础运行值、已注册 Target 的四级解析及配置过的本机工具。`resolveInstance` 只校验实际选中的运行值；普通根 help/tasks 不要求 local 文件或 Minecraft 实例存在。

真实 Target 注册后可以查询最终结果及来源：

```powershell
.\gradlew.bat printInstanceConfiguration --target=26.2-fabric --variant=client
```

查询不会启动程序或创建实例，不打印 extra args 的具体值。`verifyConfiguration` 在本项目应报告一个已注册 Target。

`check` 同时运行配置库单元测试与 Gradle TestKit 功能测试。功能测试使用临时工程和独立 Java 参数探针，不是 Minecraft 测试。可用 `-PtestFixturesDirectory=<仓库外目录>` 指定临时工程位置；目录应预先存在。测试输入保留便于复核，不执行永久删除。

可额外传入 `-PalternateJdkHome=<另一个版本的JDK根目录>`，验证实例 JDK 与 Gradle JVM 不同的真实启动场景；未提供时只跳过这一项额外测试，其余测试照常执行。本机 JDK 的 executable 在配置任务时接入，让 Gradle 正确推导匹配的 Java launcher，不能在任务执行时才替换成另一版本。

配置文件通过 Gradle Provider 读取，配置缓存会感知内容变化及 local 文件的出现/消失。修改配置后无需清除整个 Gradle 缓存。

## 本项目 Fabric 1.17.20 接线与边界

Project 身份值保持：Random Nibble6Plus24 Generator / randomnibble6plus24generator / io.github.recrivenvi / 0.0.0-dev，作者 RecRivenVI。`mod_environment=both` 对应原 Fabric metadata 的 `*`；只提供部署建议，不自动部署 JAR，也不改变网络合同。

键名收敛为 `mod_name`（原 mod_display_name）、`mod_group`（原 maven_group）、`mod_archive_base_name`（原 artifact_base_name）；Target `loader_version`（原 fabric_loader_version）仍为 0.19.3，新增 `loader=fabric`。Minecraft 26.2、Loom 1.17.20、Java 25、JUnit 5.14.4 全保持。根 `org.gradle.jvmargs=-Xmx1G`、parallel=true、configuration-cache=false 保留为 Gradle 原生设置。

没有既定共享游戏堆、窗口或日常玩家名，所以本项目不填 4G/DevGuest 等模板示例；`instance.properties` 仅声明空追加参数。未选中的设置保留生态默认。没有 local.properties 也可构建，不创建必须的本机路径文件。

只有已有 `runClient` / `runServer`：没有新增 client-multiplayer 运行任务，尽管公共模型可解析该 variant。Loom 原生 runDirectory 保持日常及验证隔离路径；原生 programArguments 追加客户端字段和最终 game extras，源集与主类/classpath不变。JVM extras 由排在 Loom 原生 provider 后的项目 provider 追加，`mosaic.repositoryRoot` 必需参数先于 extras，且不允许被 extras 覆盖。游戏堆和JDK在 JavaExec任务配置时接入；不是套用会覆盖验证工作目录的普通 JavaExecAdapter。

验证项目的目录由 `validationProject` 或对应已有测试属性选择，仍在 `validations/<项目>/instance`，不是第五级配置。SPAWN客户端fixture的 `-Username`（默认 SpawnVerifier）保持单一场景输入，传入 `spawnFixturePlayer` 后统一生成一次 `--username` 并用于加入断言。若配置 playerName 与其不一致则配置阶段报错；未配置或相同时保留场景身份。不能用 `--args` 再给专用窗口/身份/目录参数制造竞争。

```powershell
.\gradlew.bat :version:26.2-fabric:verifyFabricRunConfiguration
```

此任务读取真实 Loom 参数提供器、JDK与工作目录，写入 Target build/configuration/loom-model.json；不启动Minecraft。配置库的真实Java进程探针不等于Loader/游戏运行验收。IDE独立生成启动配置不是本项目已验证入口；通过Gradle run任务启动才能保证项目尾部JVM provider生效。

`verifyConfiguration`、根 `check/build` 严格检查全部声明值，客户端设置无效时会失败。Target 独立 `build/test` 和仅 server 消费不应因此解析无关客户端字段；可使用模型检查的 `-PrunModelVariant=server` 单独验证服务器。不要用全仓校验失败推断服务器错误消费了客户端设置。
