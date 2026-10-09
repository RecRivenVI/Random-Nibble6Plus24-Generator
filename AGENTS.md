# 仓库目录与命名规范

本文件适用于整个仓库，定义 Minecraft Multi-Target Mod、非 Mod 组件、运行实例、文档和产品验证的目录职责与命名方式。

## 1. 总体结构

```text
Project/
├── versions/<target>/
│   ├── src/
│   ├── build.gradle.kts
│   └── target.properties
├── instances/<target>/
│   ├── client/
│   ├── client-multiplayer/
│   └── server/
├── components/<component>/
├── documents/
│   ├── usage/
│   ├── development/
│   ├── design/
│   ├── project/
│   ├── reference/
│   └── release/
├── validations/<type>-<topic>/
│   ├── task/
│   ├── instance/
│   ├── result/
│   ├── validation.md
│   └── validation-guide.md
├── README.md
├── AGENTS.md
├── instance.properties
├── local.properties
├── gradle/
│   ├── configuration/
│   │   ├── settings.gradle.kts
│   │   ├── build.gradle.kts
│   │   └── src/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .git/
└── .gitignore
```

目录树表示内容的归属和允许的位置，不要求为展示结构而创建空目录。`<target>`、`<component>`、`<type>`、`<topic>` 和 `<number>` 是命名占位符，使用时替换为实际名称或编号。

## 2. Minecraft Target：versions/

`versions/` 保存 Minecraft Multi-Target Mod 的源码与构建配置。每个 `versions/<target>/` 等价于一个 Minecraft Mod 源码根目录，其内部源码、资源、构建配置和代码风格遵循对应生态。

### Target 命名

格式为 `<minecraft>-<loader>`：

- `<minecraft>` 使用 Minecraft: Java Edition 版本号。
- `<loader>` 使用小写加载器名称，仅允许 `neoforge`、`forge`、`fabric`、`quilt`。
- 示例：`1.20.1-forge`、`1.21.1-neoforge`、`26.1.2-neoforge`、`26.2-fabric`、`26.2-neoforge`。

### Target 文件职责

| 位置 | 职责 |
| --- | --- |
| `src/` | 对应生态的原生源码与资源布局 |
| `build.gradle.kts` | Target 插件、依赖、构建与运行适配 |
| `target.properties` | Minecraft、Loader、Java 等 Target Facts |

Gradle project path 使用单数命名空间：

```text
versions/<target>/ → :version:<target>
```

物理目录仍为复数 `versions/`，由根 `settings.gradle.kts` 显式注册并映射到相应的 Gradle project path。

## 3. Minecraft 实例：instances/

`instances/` 保存 Minecraft Multi-Target Mod 的运行实例。`instances/<target>/` 中的 `<target>` 与对应源码 Target 的名称一致。

| 目录 | 职责与内部布局 |
| --- | --- |
| `client/` | 客户端实例；本目录等价于 `.minecraft/`，直接存放 Minecraft 实例数据 |
| `client-multiplayer/` | 多人游戏客户端实例；本目录等价于 `.minecraft/`，直接存放 Minecraft 实例数据 |
| `server/` | 服务端实例；本目录等价于 NeoForge、Forge、Fabric、Quilt 服务端安装目录 |

客户端实例内部不再套一层 `.minecraft/`；服务端保持相应生态的原生安装目录布局。实例目录是运行数据的位置，不是独立的 Mod 源码模块。

## 4. 非 Mod 组件：components/

`components/` 保存非 Minecraft Mod 的产品组件。每个 `components/<component>/` 是对应生态的传统源码根目录，内部结构与代码风格遵循该生态。

### Component 命名

- 按组件职责命名，使用小写英文字母、数字和单词间的连字符 `-`。
- 连字符用于连接单词，不放在名称首尾，也不连续使用。
- 同一项目内组件名不得重复。
- 避免 `common`、`utils`、`misc` 等职责不明确的名称。
- 示例：`protocol-schema`、`runtime-safety`、`companion`、`web-console`、`config-editor`。

参与 Gradle 构建的组件使用单数命名空间：

```text
components/<component>/ → :component:<component>
```

物理目录仍为复数 `components/`。其他生态组件保留自己的原生构建入口，不因位于 `components/` 就必须成为 Gradle 模块。

## 5. 项目文档：documents/

`documents/` 仅使用以下六个一级分类。各分类下直接放文档，不再创建分类子目录；根目录不放普通项目文档，没有内容的分类不要求创建空目录。

| 分类 | 主要职责 | 推荐的 `<type>` |
| --- | --- | --- |
| `usage/` | 产品安装、配置、操作与使用问题处理 | `installation`、`configuration`、`guide`、`troubleshooting`、`compatibility`、`limitation`、`faq` |
| `development/` | 开发环境、构建、协作、测试、调试与移植 | `setup`、`build`、`workflow`、`testing`、`debugging`、`porting`、`contribution`、`compatibility` |
| `design/` | 系统概念、架构、机制、模型、流程、生命周期与安全设计 | `overview`、`concept`、`architecture`、`mechanism`、`model`、`flow`、`lifecycle`、`security` |
| `project/` | 产品范围、提案、计划、状态、里程碑、技术债与阶段记录 | `overview`、`scope`、`proposal`、`plan`、`status`、`milestone`、`debt` |
| `reference/` | 接口、协议、结构、命令、配置、属性、事件、格式与术语的查阅资料 | `api`、`protocol`、`schema`、`command`、`configuration`、`property`、`event`、`format`、`version`、`glossary`、`example` |
| `release/` | 发布说明、变更、升级、迁移、支持与弃用 | `release`、`changelog`、`upgrade`、`migration`、`support`、`deprecation` |

普通文档统一使用 `<type>-<topic>.md`。另有两种专用格式：

```text
documents/design/adr-<number>-<topic>.md
documents/project/phase-<number>-<topic>.md
```

- `adr`：架构或设计决策记录。
- `phase`：阶段目标、实施与历史记录。

### 文档命名要素

- `<type>` 使用单个简短的小写英文词，表示整份文档的主要形式或职责。
- 推荐词只是优先词汇，不构成白名单；没有合适推荐词时，允许根据实际职责选择新的 `<type>`。
- 同一个 `<type>` 可以出现在不同分类中，不要求全局唯一；分类决定主要职责，`<type>` 只在所在分类语境内解释。
- `<topic>` 使用小写英文字母、数字和连字符；不允许首尾连字符或连续连字符，可以由字母或数字开头、结尾。
- `<number>` 使用自然数编号，允许 `0`；可在编号后使用点号连接子编号，区分同编号下的子文档，例如 `1`、`2`、`2.1`、`2.10`。它表示编号层级，不按数学小数合并不同编号。

这些规则约束文档位置和文件名，不规定正文的固定章节或格式。按整份文档的主要职责选择分类与 `<type>`，允许正文同时涉及多个相关主题；不要为了追求分类纯度或覆盖推荐类型而拆分、创建文档。

### 容易混淆的职责边界

| 文档位置 | 主要职责 |
| --- | --- |
| `usage/configuration-*` / `reference/configuration-*` | 前者讲用户如何配置；后者提供配置项的准确、完整定义 |
| `usage/compatibility-*` / `development/compatibility-*` | 前者面向用户说明环境、Mod、硬件等能否正常使用；后者面向开发和集成过程说明兼容要求 |
| `design/overview-*` / `project/overview-*` | 前者说明系统内部如何组成和运行；后者说明产品或项目整体是什么、范围与方向是什么 |
| `development/porting-*` | 源码跨 Minecraft 版本、Loader、平台或 API 的移植 |
| `release/upgrade-*` | 用户或部署方升级版本的说明 |
| `release/migration-*` | 版本变化引起的数据、配置、接口或部署迁移 |
| `project/plan-*` | 已确定方向的实施计划，也可包含尚未实施的内部迁移方案 |

### 标准文件与现有内容

`documents/` 的治理规则不禁止 GitHub、许可证、构建工具或其他生态约定要求放在仓库根目录的标准文件，例如 `README.md`、`LICENSE`、`CONTRIBUTING.md`、`SECURITY.md`、`CODE_OF_CONDUCT.md`。这些文件保留其标准名称和位置，不因文档目录规范而擅自移动，也不要求套用 `<type>-<topic>.md`。

规则允许的位置与类型不等于必须创建的文件清单。规范调整本身不授权创建、拆分、移动现有文档或清理旧目录；实际整理应根据用户明确的任务范围处理。

## 6. 产品验证：validations/

`validations/` 用于产品级验收、回归、集成、兼容、跨 Target 一致性、协议符合性、性能、稳定性、安全性以及长期机器证据。它不等同于普通单元测试、JUnit 或 GameTest；普通源码测试仍属于对应源码和构建体系，不为统一目录而搬入这里。

每项产品验证按 `validations/<type>-<topic>/` 集中组织，将任务、运行实例、结果与说明放在同一个验证项目下。

### 验证项目命名

- `<type>` 表示验证的主要目的，使用单个简短的小写英文词。
- 推荐：`feature`、`regression`、`integration`、`compatibility`、`parity`、`conformance`、`performance`、`stability`、`migration`、`security`。
- 推荐词只是优先词汇，不构成白名单；没有合适推荐词时，允许根据验证的实际目的选择新的 `<type>`。
- `<topic>` 表示被验证的能力、问题或边界，使用与文档主题相同的字符规则。

以下三种验证目的应明确区分，不混用：

| `<type>` | 要证明什么 |
| --- | --- |
| `compatibility` | 不同环境、Mod、组件等能否共同工作 |
| `parity` | 不同 Target / Loader 的行为是否一致 |
| `conformance` | 是否符合明确的协议、Schema 或合同 |

### 验证项目内容

| 位置 | 职责 |
| --- | --- |
| `task/` | 验证程序、断言、输入与辅助工具；不限制内部目录结构 |
| `instance/` | 可选的验证专用实例；保存实例运行内容，以及不值得长期保存的验证结果与机器证据 |
| `result/` | 从 `instance/` 中提取的值得长期保存的验证结果与机器证据；不限制内部目录结构 |
| `validation.md` | 面向开发者和 Agent 的完整测试目标、测试内容、操作说明与验收说明 |
| `validation-guide.md` | 可选的简易版说明，面向一般用户介绍测试目标、测试内容、操作与验收；尽可能简洁易懂，并提供可直接从仓库根目录运行的命令 |

`validation.md` 和 `validation-guide.md` 直接放在验证项目根目录，不放进 `task/`，也不额外套一层 `documents/`。两份说明有不同的读者定位，不要求固定正文模板。

### 验证实例的 Git 策略

`validations/<type>-<topic>/instance/` 默认忽略运行内容，同时允许 Git 遍历子目录；通过 `.gitignore` 的显式 negation 白名单放行指定文件。

不要将父目录整体排除到无法重新包含指定文件。长期结果放入 `result/`，运行数据和临时证据留在默认忽略的 `instance/`。

## 7. 根文件与基础设施

| 文件或目录 | 职责 |
| --- | --- |
| `README.md` | 面向人的模板说明与使用入口 |
| `AGENTS.md` | Coding Agent 的仓库开发规范 |
| `instance.properties` | 仓库共享的实例运行默认值 |
| `local.properties` | 本机工具路径与实例覆盖；Git ignored |
| `gradle/` | Gradle 基础设施 |
| `gradle/configuration/` | 统一 properties 配置插件及其原生测试；作为 included build 显式接入，不是产品组件 |
| `gradle/wrapper/` | 官方 Gradle Wrapper |
| `gradle/wrapper/gradle-wrapper.jar` | Wrapper 启动与发行包下载程序 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 版本、下载地址与校验值 |
| `gradlew` | Unix Gradle Wrapper 入口 |
| `gradlew.bat` | Windows Gradle Wrapper 入口 |
| `build.gradle.kts` | 仓库共同构建协调 |
| `settings.gradle.kts` | 显式注册分层 Gradle 模块及其目录映射 |
| `gradle.properties` | 项目身份、版本与运行环境 Profile |
| `.git/` | Git 元数据 |
| `.gitignore` | 构建输出、本机配置与实例内容的忽略规则 |

## 8. Properties 配置系统

统一实现为 `gradle/configuration/` 中的 `raven.repository-configuration` 插件，根 build 只负责应用和聚合。根扩展 `repositoryConfig` 是 Project/Target/Instance 配置的消费入口；详细 API 与 Loader 接入边界见 [配置指南](documents/development/guide-configuration.md)。不要在每个 Target 再实现一套解析器。

### 四层职责

| 文件 | 唯一负责的事实 |
| --- | --- |
| `gradle.properties` | Project Facts：`mod_id`、`mod_name`、`mod_group`、`mod_version`、`mod_environment`；其他项目事实使用 `mod_*` |
| `versions/<target>/target.properties` | Target Facts：`minecraft_version`、`loader`、`loader_version`、`java_version`，以及真实需要的 mappings / API 等版本 |
| `instance.properties` | Repository Instance Defaults：共享的实例运行属性 |
| `local.properties` | 可选本机 JDK / Launcher 路径，以及最终实例运行覆盖；Git ignored |

同一个事实只保留一个权威来源。项目身份不复制到 Target Facts；实例内存、玩家名、窗口和本机路径不放进 Project/Target Facts。构建脚本与 Loader metadata 消费权威值，不另行硬编码。不要引入 `.env`、其他 local 文件或 per-instance properties 作为平行配置来源。

使用 UTF-8 和 Java properties 语法，重复键报错。本机路径推荐正斜杠；相对路径以仓库根为基准。插件显式读取这四类文件，`-Pmod_*` 等不构成第二套事实覆盖层。根 gradle.properties 允许同时保留 org.gradle.* 原生构建设置，由 Gradle 按其原生优先级消费；它们不属于 Project Facts，不从 repositoryConfig.projectFacts 返回，也不参与 instance Cascade。构建 JVM 内存不等于 Minecraft 实例内存，不复制到实例配置。

### Profile

`mod_environment` 仅允许 `client`、`server`、`both`，表示 Mod 的主要运行环境，不是额外的 distribution 合同。

| Profile | client | client-multiplayer | server | 验证重点 |
| --- | --- | --- | --- | --- |
| client | 部署 | 部署 | 不部署 | Modded Client 连接未安装该 Mod 的服务器 |
| server | 按项目需要 | 不部署 | 部署 | 未安装该 Mod 的客户端连接 Modded Server |
| both | 部署 | 部署 | 部署 | 单人 / Integrated Server、Dedicated Multiplayer、两端一致性 |

插件提供通常的部署建议，不自动复制 JAR 或生成 Loader metadata。真实项目仍要完成对应部署与网络验证。

### 所有实例属性使用同一个 Cascade

每个文件内按以下顺序，从高到低选择第一个存在的键：

```properties
instance.[<target>].<variant>.<property>
instance.[<target>].<property>
instance.<variant>.<property>
instance.<property>
```

Target ID 必须整体放在方括号中。先在 `instance.properties` 求共享默认，再在可选 `local.properties` 独立求本机覆盖；本机有任何匹配键就覆盖共享结果，文件优先级高于 specificity。不得先合并文件再搜索，也不得将不同层级的 extra args 全部拼接。

键存在但值为空仍是显式覆盖。空 extra args 或 `[]` 清除共享追加值；选中空或无效的内存等数值时报错，不静默回退。没有匹配的可选属性时使用生态默认。自定义扁平 runtime property 也复用这条 Cascade。

基础属性为 `memory`、`width`、`height`、`playerName`、`extraJvmArgs`、`extraGameArgs`。内存是游戏 JVM 最大堆，不是 Gradle daemon 内存；宽高是正整数像素，玩家名为 3–16 个 ASCII 字母、数字或下划线。server 不消费窗口与玩家名。

产品验证可在 `task/` 中定义必要的场景输入和固定夹具约束，例如测试身份及隔离工作目录；它们不是第五级实例覆盖。场景身份只保留一个来源供客户端、权限名单和断言共同消费。若已解析的 `playerName` 与场景要求不同，启动前报错；未设置或相同时使用场景身份，避免重复生成参数。不要把普通运行偏好伪装成夹具约束，也不要为保留验证默认行为要求用户改动日常实例配置。

追加参数支持 `=[` 开始、独占一行 `]` 结束的多行列表：每个非空行 trim 首尾后作为完整字面参数，内部空格、反斜杠、Unicode、引号保持，块内不解释注释、转义或续行；兼容旧单行 JSON。空块、[]、空值清除共享 extras。JVM 和游戏参数分别解析，最终顺序为生态参数 + 项目必要参数 + 最终列表，不经 shell 或再次拆分。

分别完成 memory 和 extraJvmArgs 的 Cascade 后，最终列表的唯一合法 `-Xmx` 优先于 memory（包括共享列表覆盖本机 memory）；无 -Xmx 或清空列表则回落 memory。重复/非法 -Xmx 报错。适配器用 `effectiveMemory()` 配置原生堆，只追加 `jvmArgumentsWithoutMaxHeap()`，不能重复发射 -Xmx。游戏 `--width`、`--height`、`--username`、`--gameDir` 仍使用专用设置。

### 本机工具与真实接入

本版支持 `java.<major>.home` 和 `launcher.prism.path`。JDK 路径使用时验证 java/javac 及实际版本；Launcher 路径可查询和校验，不自动启动。缺少 local 文件不阻止构建，也不要求创建空 local 文件。

普通 JavaExec 可显式使用 `JavaExecAdapter.configure(...)`，接入工作目录、堆、参数和可选本机 JDK。无本机 JDK 路径时使用 Gradle toolchain。Loader 专用运行任务优先接入其原生 API，不能假定所有 Loader 都是普通 JavaExec；编译 toolchain 读取 Target 的 `java_version` 配置。

`verifyConfiguration` 明确检查声明值、已注册 Target 和配置过的工具；`printInstanceConfiguration` 查询真实 Target 的最终值与来源，不打印追加参数内容。`check` 聚合插件单元测试和 Gradle TestKit 功能测试。自定义配置的 Provider 读取须保留，以正确跟踪配置缓存中的文件变化与可选文件出现/消失。

本项目只有已注册的 26.2-fabric Target。Java 探针通过只证明配置和普通 JavaExec 接线，不证明游戏、部署或人工验收通过；实际 Loom 接线还需独立任务模型验证。测试临时工程不注册到正式项目，不创建假产品模块。

## 9. 本项目事实与行为边界

- 当前唯一真实 Target：`versions/26.2-fabric`，Gradle 地址 `:version:26.2-fabric`。没有非 Mod 组件或其它已实现 Target；不要创建占位模块。
- 项目身份保持根 `gradle.properties` 为权威；Target 依赖与 Java 版本保持 `target.properties` 为权威。未经授权不升级 Minecraft、Loader、Loom、Gradle 或依赖。
- 原生 JUnit 与测试夹具留在 Target 的 `src/test/java`；产品验证程序、脚本和测试 Mod 资源在 `validations/*/task`，由 Target 的原有 test source set 显式接入。生产 Mixin 直接链接的 gated 验证 hooks 保留在 Target，避免改变 JAR 装载与运行行为。
- 日常实例只有现有 client/server。验证专项配置不能冒充 client-multiplayer，也不能合并为一个共享运行实例。
- Seed Algorithm V1、WorldOptions.seed() 权威、Feature Ordering V1、Mosaic format V2、9-writer FEATURES 与 Structure Overlay 为冻结产品合同，不得未经授权改变。
- Repair C 的历史验收为 PASS；D1 候选裁剪的历史结论为 correctness 通过、性能 PARTIAL。保存已有工作不代表升级验收状态；不要自动进入 D2、V3、WorldPreset 或 Preview。
- 普通 Vanilla 保持原路径；Mosaic 身份缺失、损坏和真正生成错误继续 fail closed。预期关闭取消、POI 主线程归属等已确认修复不得退化。
- 保留的产品证据不得改写 SHA、时间、失败或签名；归档 PASS 不能代替当前源码验证。
- 分别报告 Build、Runtime、Functional/Visual。没有运行产品不能宣称游戏/服务器/视觉 PASS。不得使用 GUI 自动化替代用户人工验收。
- 测试使用受限并发，单个高负载 JVM，保留用户 CPU 资源；不得为了测试更改生产线程模型或调度。
- 仅操作当前 Git 根。未经明确授权不创建提交、推送、分支或 tag；不丢弃已有未提交工作。
- 没有实际需求不加 CI、通用框架或共享层；构建与原生测试不等于自动目录治理。
