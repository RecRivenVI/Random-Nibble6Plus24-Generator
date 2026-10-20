plugins {
    base
}

tasks.register<Exec>("verifyDependencies") {
    group = "verification"
    description = "检查指定 Target 的稳定依赖原生客户端／服务端冒烟摘要。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/smoke-dependencies/Run-Dependencies.ps1",
        "-Target",
        providers.gradleProperty("validationTarget").getOrElse("26.3-fabric"),
        "-CheckOnly",
    )
}

val testMcpAlpha =
    tasks.register<Exec>("testMcpAlpha") {
        group = "verification"
        environment("PYTHONDONTWRITEBYTECODE", "1")
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "src/test/python",
            "-p",
            "test_mcp_alpha.py",
        )
    }

tasks.check { dependsOn(testMcpAlpha) }

tasks.register<Exec>("verifyMcpAlpha") {
    group = "verification"
    description = "只读核对固定 MCP alpha 的纯净 READ 与真实 B 客户端闭环。"
    commandLine("python", "scripts/integration-mcp_alpha/mcp_alpha.py", "check")
}

tasks.register<Exec>("verifyWorldIdentity") {
    group = "verification"
    description = "核验 1.21.1 原生身份生命周期接线与严格 B 交接。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_b/Run-LegacyB.ps1",
        "-Target",
        "1.21.1-neoforge",
        "-ValidationName",
        "integration-world_identity",
        "-Preparation",
        "shared",
        "-Handoff",
        "-IdentityLifecycle",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyLegacyB") {
    group = "verification"
    description = "核验旧版严格 B 的独立原生 smoke 与宿主隔离。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_b/Run-LegacyB.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyLegacyTerrain") {
    group = "verification"
    description = "核验旧版轻量六阶段地形与独立原生 Oracle。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-prefeatures/Run-LegacyTerrain.ps1",
        "-Target",
        "1.20.1-forge",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyNativeStructureInputs") {
    group = "verification"
    description = "核验 1.21.1 原生七类结构、完整紧凑输入与地形适配。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-structure_inputs/Run-StructureInputs.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyNativeSeed") {
    group = "verification"
    description = "核验真实 Forge 生命周期中的冻结种子与存档格式合同。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-seed/Run-NativeSeed.ps1",
        "-CheckOnly",
    )
}

val validateScripts =
    tasks.register<Exec>("validateScripts") {
        group = "verification"
        commandLine("pwsh", "-NoProfile", "-File", "scripts/Check-Scripts.ps1")
        inputs.files(fileTree("scripts") { include("**/*.ps1") })
    }

tasks.check {
    dependsOn(validateScripts)
}

val testBopCausalChecks =
    tasks.register<Exec>("testBopCausalChecks") {
        group = "verification"
        environment("PYTHONDONTWRITEBYTECODE", "1")
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "src/test/python",
            "-p",
            "test_bop_causal.py",
        )
    }

tasks.check { dependsOn(testBopCausalChecks) }

val testPerformanceSummary =
    tasks.register<Exec>("testPerformanceSummary") {
        group = "verification"
        environment("PYTHONDONTWRITEBYTECODE", "1")
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "src/test/python",
            "-p",
            "test_*performance*.py",
        )
    }

tasks.check { dependsOn(testPerformanceSummary) }

val testTargetLayout =
    tasks.register<Exec>("testTargetLayout") {
        group = "verification"
        environment("PYTHONDONTWRITEBYTECODE", "1")
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "src/test/python",
            "-p",
            "test_target_layout.py",
        )
    }

tasks.check { dependsOn(testTargetLayout) }

val testClientPreferences =
    tasks.register<Exec>("testClientPreferences") {
        group = "verification"
        environment("PYTHONDONTWRITEBYTECODE", "1")
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "src/test/python",
            "-p",
            "test_client_preferences.py",
        )
    }

tasks.check { dependsOn(testClientPreferences) }

val testProductionDriverLease =
    tasks.register<Exec>("testProductionDriverLease") {
        group = "verification"
        environment("PYTHONDONTWRITEBYTECODE", "1")
        commandLine(
            "python",
            "-m",
            "unittest",
            "discover",
            "-s",
            "src/test/python",
            "-p",
            "test_production_driver_lease.py",
        )
    }

tasks.check { dependsOn(testProductionDriverLease) }

tasks.register<Exec>("verifyBopCausal") {
    group = "verification"
    description = "核验 TerraBlender 末地自然顺序的双向条件重现；不豁免原严格 B 门槛。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/compatibility-bop/Run-BopCausal.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyBopFeatures") {
    group = "verification"
    description = "核验 BOP 三维度独立原生 B 与两个无关宿主的严格切断结果。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/compatibility-bop/Run-BopFeatures.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyPreFeatures") {
    group = "verification"
    description = "核验 26.3 独立原版装饰前矩阵的环境、进程边界和结果。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-prefeatures/Run-PreFeatures.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyCompactStructures") {
    group = "verification"
    description = "核验 26.3 紧凑结构输入的七类正例、邻区引用和逐 bit 地形适配。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-prefeatures/Run-PreFeatures.ps1",
        "-Suite",
        "structures",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyLightweightTerrain") {
    group = "verification"
    description = "核验 26.3 轻量生物群系、密度、表面和临时洞穴的独立 72 项矩阵。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-prefeatures/Run-PreFeatures.ps1",
        "-Lightweight",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyLightweightStructureTerrain") {
    group = "verification"
    description = "核验轻量地形入口的七类真实结构及邻区地形适配。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-prefeatures/Run-PreFeatures.ps1",
        "-Suite",
        "structures",
        "-Lightweight",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureB") {
    group = "verification"
    description = "核验 B 严格切断的完整数据、顺序、并发与副作用边界。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_b/Run-FeatureB.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyLightweightFeatureB") {
    group = "verification"
    description = "核验 B 按需局部读取与原 B 的完整数据、回退和隔离对照。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_b/Run-FeatureB.ps1",
        "-Lightweight",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureA") {
    group = "verification"
    description = "核验 A 邻区矿脉贡献与 B 的差异、原版算法对照和隔离边界。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_a/Run-FeatureA.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureATrees") {
    group = "verification"
    description = "核验 A 代表树种的稀疏状态、原版随机语义、严格切断与副作用。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_a/Run-FeatureA.ps1",
        "-Trees",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyLightweightFeatureA") {
    group = "verification"
    description = "核验 A 的局部 B 准备、精确高度与原 A/矿脉原版控制一致。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_a/Run-FeatureA.ps1",
        "-Lightweight",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyLightweightFeatureATrees") {
    group = "verification"
    description = "核验局部准备 A 的白桦、附属数据、随机语义与原 A 一致。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_a/Run-FeatureA.ps1",
        "-Trees",
        "-Lightweight",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureAConifers") {
    group = "verification"
    description = "核验 A 的针叶树局部贡献、原版内核、严格切断和两宿主对照。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_a/Run-FeatureA.ps1",
        "-Conifers",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureDesignComparison") {
    group = "verification"
    description = "核验 A/B/C 同输入、轮换次序的内容与成本比较，不代表生产或视觉验收。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_a/Run-FeatureA.ps1",
        "-Compare",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureC") {
    group = "verification"
    description = "核验 C 代表树种贡献记录、确定接收和独立 JVM 重载；不代表物理接入。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCFlow") {
    group = "verification"
    description = "核验 C 完整目标装饰、选择性树木跨界、严格切断及随机/副作用完整对照。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-Flow",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCFrontier") {
    group = "verification"
    description = "核验完整 C 生成与不同派生种子 C 接收基底的有限前沿、顺序和磁盘重放。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-Frontier",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCReplay") {
    group = "verification"
    description = "仅核验固定完整 C 输入的独立接收、保护与跨 JVM 重放，不替代生成等价门禁。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-Replay",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCNativeRepeat") {
    group = "verification"
    description = "核验独立原版实体不确定性证据，不替代 C 完整生成等价门禁。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-NativeRepeat",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCPhysical") {
    group = "verification"
    description = "核验封闭 C 贡献批次的真实物理接收和正常停服重载，不代表完整 C 世界。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-Physical",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCNativeShulker") {
    group = "verification"
    description = "核验独立原版末地城潜影贝随机朝向，不改变 canonical 哈希。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-NativeShulker",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCPhysicalHives") {
    group = "verification"
    description = "核验真实树木蜂巢贡献的封闭物理接收与重载，不代表动态 C 世界。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-PhysicalHives",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCEditProtection") {
    group = "verification"
    description = "核验原生物理修改自动记录、延迟树木接收与独立 JVM 重载。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-EditProtection",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCAppend") {
    group = "verification"
    description = "核验 FULL 区块的增量树木接收、冲突拒绝与独立 JVM 重载。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-Append",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCRecovery") {
    group = "verification"
    description = "核验 C 激活前写盘中断、未提交 LIGHT 恢复与已提交重载。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-Recovery",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCPreLight") {
    group = "verification"
    description = "核验完整 C 有限补丁的原版物理加载、POI、LIGHT 与独立 JVM 重载。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-PreLight",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCAcceptedShulker") {
    group = "verification"
    description = "按独立原版限定字段验收末地城的原 B、按需 B 与完整 C。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-AcceptedShulker",
        "-CheckOnly",
    )
}

tasks.register<Exec>("verifyFeatureCAcceptedFrontier") {
    group = "verification"
    description = "按独立原版证据限定实体字段验收 C 前沿，保留原始 NBT 与严格哈希。"
    commandLine(
        "pwsh",
        "-NoProfile",
        "-File",
        "scripts/conformance-feature_c/Run-FeatureC.ps1",
        "-AcceptedFrontier",
        "-CheckOnly",
    )
}
