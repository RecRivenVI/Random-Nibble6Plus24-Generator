"""Keep FML modules and bundled product classes unambiguous."""

from pathlib import Path
import hashlib
import importlib.util
import json
import re
import subprocess
import tempfile
import tomllib
import unittest


ROOT = Path(__file__).resolve().parents[5]


def packages(source):
    found = set()
    for path in source.rglob("*.java"):
        match = re.search(r"^package\s+([\w.]+)\s*;", path.read_text(encoding="utf-8"), re.MULTILINE)
        if match:
            found.add(match.group(1))
    return found


class TargetLayoutTests(unittest.TestCase):
    def test_26_3_structure_probes_include_the_native_top_y(self):
        for target, package in (("26.3-fabric", "worldgen/verify"),
                                ("26.3-neoforge", "probe/verify")):
            base = ROOT / "versions" / target / "src/probe/java/io/github/recrivenvi/randomnibble6plus24generator" / package
            probe = (base / "PreFeaturesVerifier.java").read_text(encoding="utf-8")
            fixtures = (base / "CompactStructureFixtures.java").read_text(encoding="utf-8")
            with self.subTest(target=target):
                self.assertIn("y <= run.level.getMaxY()", probe)
                self.assertIn("y <= level.getMaxY()", fixtures)
                self.assertIn('summary.addProperty("coverageVersion", 2)', probe)
                self.assertIn("samples != 16 * 16 * run.level.getHeight()", probe)
                self.assertIn('summary.addProperty("sha256", sha256(payload))', probe)

    def test_beardifier_header_cannot_hide_a_missing_top_layer(self):
        path = ROOT / "components/worldgen_validation/scripts/conformance-prefeatures/check_beardifier_coverage.py"
        spec = importlib.util.spec_from_file_location("beard_coverage", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        data = bytes(16 * 256 * 4)
        report = {"coverageVersion": 2, "sampleBytes": 4, "minY": -16,
                  "maxYInclusive": -1, "height": 16, "samples": 16 * 256,
                  "sha256": hashlib.sha256(data).hexdigest()}
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            def write(value, payload):
                (directory / "beardifier.json").write_text(json.dumps(value), encoding="utf-8")
                (directory / "beardifier.bin").write_bytes(payload)
            write(report, data)
            self.assertEqual(4096, module.check(directory))
            for mutation, payload in (({}, data[:-1024]), ({"samples": 4095}, data),
                                      ({"maxYInclusive": 0}, data), ({"coverageVersion": 1}, data),
                                      ({"sha256": "0" * 64}, data)):
                with self.subTest(mutation=mutation, size=len(payload)), self.assertRaises(ValueError):
                    write(dict(report, **mutation), payload)
                    module.check(directory)

    def test_neoforge_source_reuse_preserves_native_climate_state_and_unknown_fallback(self):
        base = ROOT / "versions/26.3-neoforge/src/main/java/io/github/recrivenvi/randomnibble6plus24generator"
        cache = (base / "worldgen/structure/ScopedStructureSources.java").read_text(encoding="utf-8")
        state = (base / "worldgen/structure/NativeBiomeSearchState.java").read_text(encoding="utf-8")
        wiring = (base / "mixin/ClimateIndexAccess.java").read_text(encoding="utf-8")
        caller = (base / "worldgen/features/LightweightFeatureBaseline.java").read_text(encoding="utf-8")
        self.assertIn("existing.input() == input", cache)
        self.assertIn("biomeState.replay(existing.output())", cache)
        self.assertIn("biomeSource != context.generator().getBiomeSource()", cache)
        self.assertIn("ModList.get().getMods().stream()", cache)
        self.assertIn("NativeBiomeSearchState.from(context.generator().getBiomeSource()) == null", cache)
        self.assertIn("return null;", cache)
        self.assertIn("type != MultiNoiseBiomeSource.class", state)
        self.assertIn("parameters.getClass() != Climate.ParameterList.class", state)
        self.assertIn('method = "<init>(Ljava/util/List;I)V"', wiring)
        self.assertIn("ScopedStructureSources.create(context)", caller)
        self.assertNotIn("new ScopedStructureSources(context)", caller)

    def test_scoped_neoforge_oracle_uses_owned_ephemeral_native_arguments(self):
        script = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1").read_text(encoding="utf-8")
        helper = (ROOT / "components/worldgen_validation/scripts/Native-ServerLaunch.ps1").read_text(encoding="utf-8")
        self.assertIn("NEOFORGE_ARGUMENT_FILE", helper)
        self.assertIn("versions/$validationTarget/build/moddev", helper)
        self.assertIn("Foreign or linked NeoForge native arguments", helper)
        self.assertIn("if($ports -ne 1)", helper)
        self.assertIn("$arguments=@('@'+$privateNativeConfig)", script)
        self.assertIn("Scoped NeoForge proof requires the actual native argument-file launcher", script)
        self.assertIn("$execution.nativeConfigSha256 -cne $nativeConfig.ownedSha256", script)
        self.assertIn("Starting Minecraft server on", script)

    def test_nether_natural_positive_has_independent_native_fixture_without_changing_default_suite(self):
        base = ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator"
        probe = (base / "worldgen/verify/IndependentFeatureBVerifier.java").read_text(encoding="utf-8")
        tool = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1").read_text(encoding="utf-8")
        natural = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1").read_text(encoding="utf-8")
        self.assertIn('Boolean.getBoolean("featureb.naturalNether")', probe)
        self.assertIn('"nether-chests", "the_nether"', probe)
        self.assertIn("Real Nether structure/BlockEntity positive was not hit", probe)
        self.assertIn("-Dfeatureb.naturalNether=true", tool)
        self.assertIn("if($Fixture -ceq 'nether_chests')", natural)

    def test_publication_inventory_is_passive_and_separate_from_activation_proof(self):
        base = ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator"
        probe = (base / "probe/verify/PublicationInventory.java").read_text(encoding="utf-8")
        tool = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1").read_text(encoding="utf-8")
        self.assertIn("setVerificationObserver(PublicationInventory::capture)", probe)
        self.assertIn("publication.prepared()", probe)
        self.assertIn("getBlockEntityNbtForSaving", probe)
        self.assertIn("getWorldGenSettings().options().seed()", probe)
        self.assertIn("PASSIVE_PUBLISHED_INPUT_DISCOVERY_NOT_POSITIVE_ACTIVATION_PROOF", probe)
        self.assertIn("rows.clear()", probe)
        self.assertNotIn("getChunkFuture(", probe)
        self.assertNotIn("setBlock(", probe)
        self.assertNotIn("addFreshEntity(", probe)
        self.assertNotIn("newBlockEntity(", probe)
        self.assertIn("Preserve publication inventory evidence/world; never overwrite it", tool)
        self.assertIn("Publication discovery/reload boundary was not observed", tool)

    def test_ring_continuation_is_a_gated_native_component_not_a_production_executor(self):
        base = ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator"
        probe = (base / "worldgen/structure/RingContinuationProbe.java").read_text(encoding="utf-8")
        mixin = (base / "prefeaturestest/mixin/RingContinuationMixin.java").read_text(encoding="utf-8")
        plugin = (base / "probe/ValidationMixinPlugin.java").read_text(encoding="utf-8")
        tool = (ROOT / "components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1").read_text(encoding="utf-8")
        self.assertIn("state.getRingPositionsFor(placement)", probe)
        self.assertIn("frame.forks == 128 && frame.executorSelections == 128", probe)
        self.assertIn("frame.root.nextLong()", probe)
        self.assertIn("climate.replay(eager)", probe)
        self.assertIn("climate.replay(lazy)", probe)
        self.assertIn("ACTIVE.remove()", probe)
        self.assertIn("worker.awaitTermination", probe)
        self.assertIn("BOUNDED_NATIVE_RING_AND_CONTINUATION_NOT_WORLDGEN_PARITY", probe)
        self.assertIn("original.call(instance)", mixin)
        self.assertIn('endsWith("RingContinuationMixin")', plugin)
        self.assertIn('".prefeatures.ringAudit"', plugin)
        self.assertIn("Native ring component exposed an unresolved difference", tool)
        self.assertIn("Native ring output/RNG/continuation check incomplete", tool)
        self.assertFalse((ROOT / "versions/26.3-fabric/src/main/java/io/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/RingContinuationProbe.java").exists())

    def test_natural_runtime_observes_published_input_and_actual_native_boundaries(self):
        base = ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator"
        probe = (base / "probe/verify/NaturalRuntimeEvidence.java").read_text(encoding="utf-8")
        self.assertIn("setVerificationObserver(NaturalRuntimeEvidence::capture)", probe)
        self.assertIn("prepared.chunk()", probe)
        self.assertIn('write("natural-source-raw.nbt", root)', probe)
        self.assertIn("BlockEntity.loadStatic(", probe)
        self.assertIn('nbt.getStringOr("id", "").equals("DUMMY")', probe)
        self.assertIn(".newBlockEntity(pos, state)", probe)
        self.assertIn("control.hasLevel()", probe)
        self.assertIn("sourceFailure = failure", probe)
        runtime = (base / "probe/verify/RuntimeLifecycleEvidence.java").read_text(encoding="utf-8")
        self.assertIn("NaturalRuntimeEvidence.checkFailure();", runtime)
        self.assertIn("chunk.getBlockEntities().get(pos)", probe)
        self.assertIn('reloadSaved.get(be.getBlockPos())', probe)
        self.assertIn('write("natural-saved-"', probe)
        self.assertIn("expected.equals(actual)", probe)
        self.assertIn("Natural End Crystal ticked twice", probe)
        self.assertNotIn("addFreshEntity(", probe)
        self.assertNotIn("setBlock(", probe)
        self.assertNotIn("generateForHandoff(", probe)
        end = (base / "productiontest/mixin/NaturalEndCrystalTickMixin.java").read_text(encoding="utf-8")
        self.assertIn('method = "tick", at = @At("TAIL")', end)
        saved = (base / "productiontest/mixin/NaturalChunkSaveMixin.java").read_text(encoding="utf-8")
        self.assertIn('method = "copyOf", at = @At("RETURN")', saved)
        tool = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricNatural.ps1").read_text(encoding="utf-8")
        self.assertIn("$conditions.inputHash -cne $hash", tool)
        self.assertIn("Saved natural FULL repeated", tool)
        self.assertIn("Natural fixture selection is incomplete", tool)
        self.assertIn("Natural pending BE positive was not hit", tool)

    def test_fabric_request_proof_observes_real_full_identity_and_exact_once_owners(self):
        probe = (ROOT / "versions/26.3-fabric/src/main/java/io/github/recrivenvi/randomnibble6plus24generator/worldgen/verify/Phase3C2ProductionVerification.java").read_text(encoding="utf-8")
        driver = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1").read_text(encoding="utf-8")
        checker = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-FabricRequests.ps1").read_text(encoding="utf-8")
        self.assertIn('Boolean.getBoolean(PREFIX + "requestProof")', probe)
        self.assertIn("new java.util.IdentityHashMap<LevelChunk, Boolean>()", probe)
        self.assertIn("captures != 1 || publications != 1", probe)
        self.assertIn("identities.size() != positions.size()", probe)
        self.assertIn("getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true)", probe)
        self.assertIn("300-second observation bound", probe)
        self.assertIn("Preserve scoped production request evidence/world; never overwrite it", driver)
        self.assertIn("Source changed during ordinary request proof", driver)
        self.assertIn("$proof.inputHash -cne $hash", checker)
        self.assertIn("$proof.logicalProcessors -ne 4", checker)
        self.assertIn("$proof.distinctChunkObjects -ne $expected.Count", checker)
        self.assertIn("!$worlds.Add([string]$proof.save)", checker)
        self.assertIn("ORDINARY_FULL_REQUESTS_NOT_TICKING_OR_PLAYER_ENTRY", checker)
        self.assertIn("Historical source identity is read-only", checker)

    def test_neoforge_request_probe_observes_joins_without_replacing_futures_or_raw_results(self):
        base = ROOT / "versions/26.3-neoforge/src/probe"
        prefix = "java/io/github/recrivenvi/randomnibble6plus24generator/"
        probe = (base / (prefix + "probe/verify/OrdinaryRequestEvidence.java")).read_text(encoding="utf-8")
        mixin = (base / (prefix + "productiontest/mixin/NativeRequestEvidenceMixin.java")).read_text(encoding="utf-8")
        plugin = (base / (prefix + "probe/ValidationMixinPlugin.java")).read_text(encoding="utf-8")
        self.assertIn("Object value = original.call(future)", mixin)
        self.assertIn("return value;", mixin)
        self.assertIn("OrdinaryRequestEvidence.close(returned)", mixin)
        self.assertIn("boolean done = complete.getAsBoolean()", mixin)
        self.assertIn("if (!done) OrdinaryRequestEvidence.checkDeadline()", mixin)
        self.assertIn("return done;", mixin)
        self.assertIn("300-second observation bound", probe)
        self.assertIn("require = 1", mixin)
        self.assertIn("allow = 1", mixin)
        self.assertIn('ModConstants.MOD_ID + ".phase3c2.production.requestProof"', plugin)
        self.assertIn("captures != 1 || publications != 1", probe)
        self.assertIn("identities.size() != positions.size()", probe)
        self.assertIn("StandardOpenOption.CREATE_NEW", probe)
        self.assertIn('frame.output + ".requests.json"', probe)
        self.assertIn("Files.readAllBytes(frame.output)", probe)
        self.assertLess(probe.index("ACTIVE.remove()"), probe.index("if (!bodyReturned"))
        self.assertLess(probe.index("frame.server = null"), probe.index("Files.writeString"))
        for forbidden in ("getChunkFuture(", "addRegionTicket(", "setBlock(", ".complete(", ".join("):
            self.assertNotIn(forbidden, probe)
        for forbidden in ("catch (", ".thenApply(", ".whenComplete("):
            self.assertNotIn(forbidden, mixin)

    def test_native_request_sidecar_reader_rejects_stale_raw_and_uncleared_observers(self):
        reader = ROOT / "components/worldgen_validation/scripts/Native-RequestEvidence.ps1"
        with tempfile.TemporaryDirectory(prefix="mosaic-request-sidecar-") as temporary:
            raw = Path(temporary) / "raw.json"
            raw.write_text('{"returnedFullChunks":32}\n', encoding="utf-8", newline="\n")
            expected = {"sidecarVersion": 1, "rawResult": str(raw),
                        "rawResultSha256": hashlib.sha256(raw.read_bytes()).hexdigest().upper(),
                        "bodyReturned": True, "serverThread": True, "retainedChunks": 0,
                        "retainedServer": False, "observedJoins": 32}
            sidecar = Path(str(raw) + ".requests.json")
            cases = [None, ("rawResultSha256", "0" * 64), ("bodyReturned", False),
                     ("serverThread", False), ("retainedChunks", 1), ("retainedServer", True),
                     ("observedJoins", 31), ("sidecarVersion", 0)]
            for mutation in cases:
                data = dict(expected)
                if mutation:
                    data[mutation[0]] = mutation[1]
                sidecar.write_text(json.dumps(data), encoding="utf-8")
                code = (f". '{reader}'; $r=Get-Content -LiteralPath '{raw}' -Raw|ConvertFrom-Json; "
                        f"try {{[void](Read-NativeRequestSidecar -RawPath '{raw}' -RawResult $r)}} "
                        "catch {[Console]::Error.WriteLine($_.Exception.ToString()); exit 3}")
                run = subprocess.run(["pwsh", "-NoProfile", "-Command", code], capture_output=True, text=True, timeout=30)
                self.assertEqual(run.returncode, 3 if mutation else 0, run.stderr)

    def test_structure_b_order_gate_binds_source_and_complete_entities(self):
        script = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-StructureB.ps1").read_text(encoding="utf-8")
        probe = (ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/worldgen/verify/LightweightFeatureBVerifier.java").read_text(encoding="utf-8")
        self.assertIn("Historical source identity is read-only", script)
        self.assertIn("$conditions.inputHash -cne $inputHash", script)
        self.assertIn("$summary.entityAcceptanceVersion -ne 1", script)
        self.assertIn("Preserve scoped B evidence", script)
        self.assertIn("-Dvalidation.result=$resultBase", script)
        self.assertIn("$arguments[$i+1]='0'", script)
        self.assertIn("$child.ProcessorAffinity=[IntPtr]$CpuAffinity", script)
        self.assertIn("Source changed during structure B validation", script)
        self.assertIn("No complete structure B PASS; save retained", script)
        self.assertIn("raw results and save retained", script)
        self.assertIn("$comparison.PSObject.Properties['nonEntityDifference']", script)
        self.assertIn("$null -ne $nonEntity.Value", script)
        self.assertIn("NativeEntityAcceptance.compare(", probe)
        self.assertIn('id.equals("end-city")', probe)
        self.assertIn('root.resolveSibling("host_0")', probe)
        self.assertIn('writeRaw(output.resolve("entities.nbt")', probe)
        self.assertIn('second.resolve("cross-host.json")', probe)
        self.assertNotIn("expected.entityCount() != actual.entityCount()", probe)

    def test_fabric_runtime_recheck_does_not_borrow_neoforge_schema_or_stale_source(self):
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Check-FabricRuntime.ps1").read_text(encoding="utf-8")
        self.assertIn("$conditions.inputHash -cne $hash", script)
        self.assertIn("$proof.schema -ne $ExpectedRuntimeSchema", script)
        self.assertIn("Schema 1 is historical and requires its explicit source input", script)
        self.assertIn("$proof.physicalBiomeOwners -ne 49", script)
        self.assertIn("$proof.physicalBiomeCells -ne $proof.physicalBiomeSections*64", script)
        self.assertIn("Owner stored biome hashes changed across save/reload", script)
        self.assertIn("$proof.simulationTicks -lt 220", script)
        self.assertIn("$pids.Add([long]$proof.pid)", script)
        self.assertIn("Saved FULL repeated $field", script)
        self.assertIn("not player entry/packets or an independent per-owner terrain Oracle", script)
        self.assertNotIn("Start-Process", script)
        self.assertNotIn("WriteAllText", script)
        self.assertNotIn("gradlew.bat", script)

    def test_fabric_biome_probe_includes_native_top_quart_and_uses_noncreating_lookup(self):
        path = ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/verify/RuntimeLifecycleEvidence.java"
        probe = path.read_text(encoding="utf-8")
        self.assertIn("y <= Math.floorDiv(physical.getMaxY(), 4)", probe)
        self.assertIn("physicalBiomeSections += physical.getSections().length", probe)
        self.assertIn('"biomeQuartCoverageVersion", 1', probe)
        self.assertIn("stored.equals(level.getNoiseBiome(qx, y, qz))", probe)
        self.assertIn("ChunkStatus.BIOMES, false", probe)
        self.assertIn("ChunkPyramid.LOADING_PYRAMID", probe)

    def test_current_performance_labels_do_not_replace_prior_source_evidence(self):
        script = (ROOT / "components/worldgen_validation/scripts/performance-worldgen/Run-BPerformance.ps1").read_text(encoding="utf-8")
        self.assertIn("$validationTarget/$EvidenceLabel/$label-v$View-s$Simulation", script)
        self.assertIn("$worldName+='-'+$EvidenceLabel", script)
        self.assertIn("Preserve scoped performance evidence and world; never overwrite it", script)
        self.assertLess(script.index("Preserve scoped performance evidence"), script.index("$process.PriorityClass = 'BelowNormal'"))
        self.assertIn("$conditions.inputHash = $inputHash", script)
        self.assertIn("if((Get-PerformanceInputHash) -cne $inputHash)", script)

    def test_independent_b_labels_preserve_old_evidence_and_use_owned_native_server_launches(self):
        script = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1").read_text(encoding="utf-8")
        self.assertIn("-Dvalidation.result=$resultBase", script)
        self.assertIn("$defaultResultBase", script)
        self.assertIn("Invoke-NativeCommand -Launch $bootstrap -World $exportWorld", script)
        self.assertIn("$bootstrap.gameArguments[$portIndex+1] -cne '0'", script)
        self.assertIn("$child.ProcessorAffinity = [IntPtr]$CpuAffinity", script)
        self.assertIn("$execution.pid -ne $row.pid", script)
        self.assertIn("$privateNativeConfig=New-OwnedNativeConfig $bootstrap", script)
        self.assertIn("$argument.StartsWith('-Dfabric.dli.config=')", script)
        self.assertIn("$start.ArgumentList.Add(\"-Dfabric.dli.config=$privateNativeConfig\")", script)
        self.assertIn("$execution.nativeConfigSha256 -cne $nativeConfig.ownedSha256", script)
        self.assertIn("FAILED TO BIND TO PORT", script)
        helper = (ROOT / "components/worldgen_validation/scripts/Native-ServerLaunch.ps1").read_text(encoding="utf-8")
        self.assertIn("../Native-ServerLaunch.ps1", script)
        self.assertIn("$ports -gt 1", helper)
        self.assertIn("$arguments[$i+1]='0';$ports++", script)
        self.assertIn("gameArguments=$arguments", script)
        self.assertIn("Target source changed during independent B validation", script)
        self.assertIn("if($ExpectedInputHash -and !$CheckOnly)", script)
        self.assertIn("never overwrite", script)

    def test_prefeatures_owned_launcher_never_uses_fixed_port_or_foreign_instance(self):
        script = (ROOT / "components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1").read_text(encoding="utf-8")
        for fragment in ("[string]$BootstrapLauncher", "../Native-ServerLaunch.ps1",
                         "Foreign or linked launcher bootstrap", "Unsafe or foreign pre-FEATURES launcher bootstrap",
                         "Scoped native bootstrap cannot fall back", "ownedNativePort = [bool]$privateNativeConfig",
                         "$execution.pid -ne $proof.pid", "$execution.affinity -ne $selection.cpuAffinity",
                         "$execution.nativeConfigSha256 -cne $nativeConfig.ownedSha256",
                         "Pre-FEATURES server did not use its isolated ephemeral port",
                         "$gameArguments[$i + 1] = '0'", "$argument.StartsWith('-Dfabric.dli.config=')"):
            self.assertIn(fragment, script)
        config = tomllib.loads((ROOT / "validations/conformance-prefeatures/validation.toml").read_text(encoding="utf-8"))
        self.assertGreater(config["instances"]["server"]["port"], 0)

    def test_owned_native_server_config_copies_both_loader_ports_and_rejects_foreign_files(self):
        helper = ROOT / "components/worldgen_validation/scripts/Native-ServerLaunch.ps1"
        with tempfile.TemporaryDirectory(prefix="mosaic-native-config-") as temporary:
            repo = Path(temporary)
            server = repo / "server"
            result = repo / "result"
            server.mkdir()
            result.mkdir()
            dli = repo / ".gradle/launch.cfg"
            args = repo / "versions/26.3-neoforge/build/moddev/args.txt"
            dli.parent.mkdir(parents=True)
            args.parent.mkdir(parents=True)
            originals = {
                dli: "commonProperties\n\tkeep=yes\nserverArgs\n\t--port\n\t25589\n",
                args: "net.neoforged.fml.startup.Server\n--port\n25589\n--nogui\n",
            }
            for path, value in originals.items():
                path.write_text(value, encoding="utf-8", newline="\n")

            def literal(value):
                return "'" + str(value).replace("'", "''") + "'"

            command = f"""
$ErrorActionPreference='Stop'
$validationRepository={literal(repo)};$validationServer={literal(server)};$resultRoot={literal(result)}
. {literal(helper)}
$Target='26.3-fabric';$validationTarget=$Target;$EvidenceLabel='fabric'
$launch=[pscustomobject]@{{jvmArguments=@('-Dfabric.dli.config='+{literal(dli)})}}
[void](New-OwnedNativeConfig $launch)
$Target='26.3-neoforge';$validationTarget=$Target;$EvidenceLabel='neoforge'
$launch=[pscustomobject]@{{main='net.neoforged.devlaunch.Main';gameArguments=@('@'+{literal(args)})}}
[void](New-OwnedNativeConfig $launch)
$launch.gameArguments=@('@'+{literal(dli)})
$rejected=$false
try{{[void](New-OwnedNativeConfig $launch)}}catch{{if($_.Exception.Message -cne 'Foreign or linked NeoForge native arguments'){{throw}};$rejected=$true}}
if(!$rejected){{throw 'Foreign native launch accepted'}}
"""
            run = subprocess.run(["pwsh", "-NoProfile", "-Command", command], capture_output=True, text=True, timeout=30)
            self.assertEqual(run.returncode, 0, run.stderr)
            self.assertEqual((server / "native-dli-fabric.cfg").read_text(encoding="utf-8"), originals[dli].replace("25589", "0"))
            self.assertEqual((server / "native-args-neoforge.txt").read_text(encoding="utf-8"), originals[args].replace("25589", "0"))
            for path, value in originals.items():
                self.assertEqual(path.read_text(encoding="utf-8"), value)

    def test_native_runtime_historical_check_is_explicit_read_only_not_source_relabelling(self):
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Check-NeoRuntime.ps1").read_text(encoding="utf-8")
        self.assertIn("[string]$ExpectedInputHash", script)
        self.assertIn("INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT", script)
        self.assertIn("$conditions.inputHash -cne $hash", script)
        self.assertIn("$proof.ticketFullFrontier -ne 49", script)
        for mutation in ("WriteAllText", "Start-Process", "gradlew.bat", "Set-Content"):
            self.assertNotIn(mutation, script)

    def test_fabric_native_climate_audit_is_a_scoped_counterexample_not_parity_acceptance(self):
        base = ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/worldgen"
        probe = (base / "structure/ClimateContinuationProbe.java").read_text(encoding="utf-8")
        self.assertIn("parameters.findValue(target(0))", probe)
        self.assertIn("new NativeBiomeSearchState(cursor)", probe)
        self.assertIn('"NATIVE_CLIMATE_CACHE_COMPONENT_NOT_STRUCTURE_ORACLE"', probe)
        self.assertIn('"GUARDED_COMPONENT_PASS"', probe)
        self.assertIn('"exitLost"', probe)
        self.assertNotIn("setBlock(", probe)
        self.assertNotIn("cursor.set(", probe)
        script = (ROOT / "components/worldgen_validation/scripts/conformance-prefeatures/Run-FabricClimateState.ps1").read_text(encoding="utf-8")
        self.assertIn("if($ExpectedInputHash -and !$CheckOnly)", script)
        self.assertIn("never overwrite", script)
        self.assertIn("-XX:ActiveProcessorCount=4", script)
        self.assertIn("NOT worldgen parity PASS", script)

    def test_fabric_oracle_source_scope_preserves_history_and_freezes_inputs(self):
        path = ROOT / "components/worldgen_validation/scripts/conformance-prefeatures/Run-PreFeatures.ps1"
        script = path.read_text(encoding="utf-8")
        self.assertIn("$native.environment.inputSha256 -cne $inputHash", script)
        self.assertIn("if ($ExpectedInputHash -and !$CheckOnly)", script)
        self.assertIn("Target source changed during independent Oracle validation", script)
        self.assertIn("$worldSuffix", script)
        self.assertIn("never overwrite", script)

    def test_forge_multiple_owners_only_issue_public_requests_with_lifetime_tickets(self):
        base = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production"
        targets = (base / "NativePhysicalTargets.java").read_text(encoding="utf-8")
        for fragment in ("getChunkFuture(pos.x,pos.z,ChunkStatus.FULL,true)", "addRegionTicket(", "removeRegionTicket(",
                         '"FIVE_NATIVE_FULL_TARGETS_NOT_TICKING_OR_PLAYER_ENTRY"', '"requestedOrder"', '"completionOrder"',
                         "pendingBlockEntitiesCopy()", "instantiatedBlockEntityPositionsCopy()"):
            self.assertIn(re.sub(r"\s+", "", fragment), re.sub(r"\s+", "", targets))
        self.assertNotIn(".generate(", targets)
        self.assertNotIn(".setStatus(", targets)
        self.assertNotIn("newFixedThreadPool", targets)
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Check-ForgeTargetOrder.ps1").read_text(encoding="utf-8")
        self.assertIn("'artifact.nbt','pending.nbt','instantiated.nbt','physical.nbt'", script)
        self.assertIn("-CheckOnly", script)

    def test_multi_owner_biome_proof_uses_held_full_not_expired_generation_dependencies(self):
        base = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production"
        targets = (base / "NativePhysicalTargets.java").read_text(encoding="utf-8")
        self.assertIn("static Map<ChunkPos, ChunkAccess> completedChunks()", targets)
        self.assertIn("ready(future)", targets)
        proof = (base / "NativePhysicalProbe.java").read_text(encoding="utf-8")
        self.assertIn("NativePhysicalTargets.completedChunks()", proof)
        self.assertIn("actual = heldTargets.get(pos)", proof)
        self.assertIn("sections = saved.getList(key, Tag.TAG_COMPOUND)", proof)
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1").read_text(encoding="utf-8")
        self.assertIn("if($pipeline -eq 'b' -and $proof.physicalBiomeOwners -ne 5)", script)
        self.assertIn("if($TargetOrder){5}", script)
        self.assertIn("if($EvidenceLabel){$variant+='_'+$EvidenceLabel}", script)

    def test_forge_request_burst_observes_pending_native_futures_without_an_executor(self):
        base = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production"
        request = (base / "NativePhysicalRequests.java").read_text(encoding="utf-8")
        self.assertIn("getChunkFuture(pos.x, pos.z, status, true)", request)
        self.assertIn("SAME_TARGET_PENDING_REQUESTS_NOT_PARALLEL_SESSION_PROOF", request)
        self.assertIn('"allIssuedBeforeFirstCompletion"', request)
        self.assertIn('"samePhysicalIdentity"', request)
        self.assertNotIn("Executors.", request)
        self.assertNotIn("managedBlock(", request)
        self.assertNotIn("Thread.sleep(", request)
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Check-ForgeRequests.ps1").read_text(encoding="utf-8")
        self.assertIn("beforeSave.$metric", script)
        self.assertIn("fresh/root-artifact.nbt", script)
        self.assertIn("-CheckOnly", script)

    def test_forge_probe_preserves_primary_error_separately_from_stop_error(self):
        base = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production"
        proof = (base / "NativePhysicalProbe.java").read_text(encoding="utf-8")
        self.assertIn('proof.has("failure") ? "stopFailure" : "failure"', proof)
        self.assertIn('proof.has("failure") ? "additionalFailure" : "failure"', proof)
        self.assertIn('PROBE_OBSERVATION_FAILURE_NOT_GENERATION_FAILURE', proof)
        failed = proof.split("private static void failed(", 1)[1].split("private static void require(", 1)[0]
        self.assertLess(failed.index('proof.addProperty("status", "FAIL")'), failed.index("NativeRuntimeProbe.close()"))
        stopped = proof.split("public static void stopped(", 1)[1].split("private static void failed(", 1)[0]
        self.assertLess(stopped.index('proof.add("afterStop"'), stopped.index('"PENDING_NATIVE_STOP"'))
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1").read_text(encoding="utf-8")
        self.assertIn("$expectedStatus=if($FailureProof){'FAIL'}else{'PASS'}", script)
        self.assertIn("$roles=if($FailureProof){@('fresh')}elseif($PartialPreLight){@('fresh','resume','reload')}", script)
        self.assertIn("$row.stopFailure -cne", script)
        self.assertIn("EXPECTED_PROBE_FAILURE_RETAINED", script)

    def test_forge_partial_checkpoint_uses_native_ticket_save_and_cold_palette_comparison(self):
        base = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production"
        probe = (base / "NativePhysicalProbe.java").read_text(encoding="utf-8")
        self.assertIn("ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(requestedStatus)", " ".join(probe.split()))
        self.assertIn("position, ticketRadius, position", probe)
        partial = (base / "PartialPreLightProbe.java").read_text(encoding="utf-8")
        for fragment in ("chunkMap.read(pos).join()", "ChunkSerializer.write(level, chunk)",
                         'phase.equals("resume") ? ChunkStatus.INITIALIZE_LIGHT : ChunkStatus.FULL',
                         '"rootPopulationCalls"', '"coldBiomeComparisons"', '"warm-spawn-biomes.nbt"',
                         '"NATIVE_PRE_LIGHT_SAVE_RESUME_NOT_PLAYER_ENTRY"'):
            self.assertIn(fragment, " ".join(partial.split()))
        self.assertNotIn("setStatus(", partial)
        self.assertNotIn("ChunkSerializer.read(", partial)
        replay = (base / "PartialIncomingProof.java").read_text(encoding="utf-8")
        self.assertRegex(replay, r"stateCodec\s*\.parse")
        self.assertRegex(replay, r"biomeCodec\s*\.parse")
        self.assertIn("Native transition trail invented an edge", replay)
        self.assertIn("Mosaic or FULL reload received new worldgen writes", replay)
        hook = (base / "mixin/PartialIncomingWriteMixin.java").read_text(encoding="utf-8")
        self.assertIn("@Mixin(LevelChunkSection.class)", hook)
        self.assertIn('@At("RETURN")', hook)
        self.assertNotIn("@Redirect", hook)
        self.assertNotIn("setBlockState(", partial)
        self.assertIn('"partialLoadedSections"', partial)

    def test_forge_runtime_observes_native_promotion_not_manual_simulation(self):
        base = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production"
        proof = (base / "NativeRuntimeProbe.java").read_text(encoding="utf-8")
        for fragment in ("addRegionTicket(TicketType.PLAYER, owner, 3, owner)",
                         "getFullChunkFuture()", "getTickingChunkFuture()", "getEntityTickingChunkFuture()",
                         "MosaicPhysicalGenerationPlan.derive", "220", "300_000_000_000L",
                         "baseline.equals(state.metrics())", "REGIONAL_TICKING_NOT_PLAYER_ENTRY_OR_PACKET"):
            self.assertIn(fragment, proof)
        self.assertNotIn(".serverTick(", proof)
        self.assertNotIn("marker.tick()", proof)
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1").read_text(encoding="utf-8")
        self.assertIn("$variant='runtime_'+$variant", script)
        self.assertIn("$runtime.entityRegistrations -ne 1", script)
        self.assertIn("$runtime.allCallbacksServerThread", script)
        self.assertIn("$expectedBiomeOwners=if($TargetOrder){5}elseif($RuntimeProof){49}else{1}", script)

    def test_forge_frontier_uses_native_either_and_cold_holder_boundary(self):
        base = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe"
        probe = (base / "performance/LegacyPerformanceProbe.java").read_text(encoding="utf-8")
        self.assertIn("requireCold(server)", probe)
        self.assertIn('result.addProperty("target", "1.20.1-forge")', probe)
        self.assertIn("metrics.inFlight() != 0", probe)
        self.assertIn("preWindowWorkers.sum() != 0", probe)
        self.assertIn("ChunkMap.isChunkInRange", probe)
        self.assertIn("getFutureIfPresentUnchecked(ChunkStatus.FULL)", probe)
        self.assertIn("getTickingChunk()", probe)
        self.assertIn("result.left().isPresent()", probe)
        self.assertNotIn("ChunkResult", probe)
        self.assertNotIn("ClientInformation", probe)
        self.assertNotIn("ChunkTrackingView", probe)

    def test_forge_frontier_observer_keeps_native_executor_and_future(self):
        mixins = ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/structure/mixin"
        worker = (mixins / "PerformancePhysicalMixin.java").read_text(encoding="utf-8")
        self.assertIn("executor.execute(", worker)
        self.assertIn("LegacyPerformanceProbe.worker(", worker)
        self.assertIn("ci.getReturnValue()", worker)
        self.assertIn(".whenComplete(", worker)
        self.assertNotIn("setReturnValue", worker)
        token = (mixins / "PerformanceTicketSourceMixin.java").read_text(encoding="utf-8")
        self.assertIn('method = "fudgeSpawnLocation"', token)
        self.assertIn("constructingTicketSource()", token)

    def test_forge_frontier_is_separate_probe_only_validation(self):
        configuration = tomllib.loads((ROOT / "validations/performance-legacy_frontier/validation.toml").read_text(encoding="utf-8"))
        self.assertEqual(configuration["targets"], ["1.20.1-forge"])
        self.assertEqual(configuration["instances"]["server"]["game-args"],
                         ["--mixin", "mosaic-structure-input-test.mixins.json"])
        script = (ROOT / "components/worldgen_validation/scripts/performance-worldgen/Run-ForgePerformance.ps1").read_text(encoding="utf-8")
        self.assertIn("Four available logical CPUs required", script)
        self.assertIn("runValidationPerformanceLegacyFrontierServer", script)
        self.assertIn("if($Pipeline -eq 'b_narrow')", script)
        self.assertIn("$row.narrowContexts -ne $row.contexts", script)
        self.assertIn("if($EvidenceLabel)", script)
        self.assertNotIn("reuseSources=true", script)
        self.assertNotIn("max-tick-time", script)
        self.assertIn("if($ExpectedInputHash -and !$CheckOnly)", script)
        self.assertIn("INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT", script)

    def test_forge_optional_locks_require_startup_capability_and_known_environment(self):
        base = ROOT / "versions/1.20.1-forge/src/main/java/io/github/recrivenvi/randomnibble6plus24generator"
        plugin = (base / "mixin/plugin/StructureFastpathPlugin.java").read_text(encoding="utf-8")
        self.assertIn('Boolean.getBoolean(ModConstants.MOD_ID + ".structure.narrowLocks")', plugin)
        self.assertIn("FMLLoader.getLoadingModList()", plugin)
        self.assertIn("if (mods == null) return false", plugin)
        self.assertIn("StructureLockEnvironment.knownMod", plugin)
        guard = (base / "worldgen/structure/StructureGenerationLocks.java").read_text(encoding="utf-8")
        self.assertIn("instanceof NativeStructureSynchronization", guard)
        self.assertIn("supportsPool", guard)
        injection = (base / "mixin/StructureGenerationMixin.java").read_text(encoding="utf-8")
        self.assertRegex(injection, r"implements\s+[\w.\s]*NativeStructureSynchronization")

    def test_forge_rotation_guard_preserves_native_algorithm_and_exception_cleanup(self):
        base = ROOT / "versions/1.20.1-forge/src"
        product = base / "main/java/io/github/recrivenvi/randomnibble6plus24generator"
        mixin = (product / "mixin/StructureRotationCacheMixin.java").read_text(encoding="utf-8")
        self.assertIn("require = 3", mixin)
        self.assertIn("allow = 3", mixin)
        self.assertIn("StructureGenerationLocks.rotationOwner() == owner", mixin)
        self.assertIn("synchronized (owner)", mixin)
        self.assertIn("owner.rotate(requested)", mixin)
        self.assertIn("finally", mixin)
        self.assertIn("StructureGenerationLocks.endRotation(previous)", mixin)
        self.assertNotIn("catch", mixin)
        proof = (base / "probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/structure/NativeRotationVerifier.java").read_text(encoding="utf-8")
        for fragment in ("OctahedralGroup.values()", "FrontAndTop.values()", "InjectedFailure", "activeRotationInitializers()"):
            self.assertIn(fragment, proof)

    def test_forge_cold_proof_validates_mixin_bootstrap_and_native_failure(self):
        script = (ROOT / "components/worldgen_validation/scripts/conformance-structure_inputs/Run-NarrowStructureLocks.ps1").read_text(encoding="utf-8")
        self.assertIn("product/probe Mixin bootstrap is missing", script)
        self.assertIn("$row.rotationCache.nativeFailureReported", script)
        self.assertIn("$row.rotationCache.recovered", script)
        self.assertIn("$row.rotationCache.activeInitializers -ne 0", script)
        self.assertIn("$row.coldPoolSizes -le 0", script)

    def test_native_lifecycle_failure_and_recovery_are_not_false_full_acceptance(self):
        scripts = ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle"
        lifecycle = (scripts / "Run-ForgeLifecycle.ps1").read_text(encoding="utf-8")
        self.assertIn("NATIVE_CLOSE_AND_FAILURE_NOT_FULL", lifecycle)
        self.assertIn("EXPECTED_FAILURE_OBSERVED", lifecycle)
        self.assertIn("$proof.contextsOpened -le 0", lifecycle)
        self.assertIn("$proof.retainedFailureIsExpected", lifecycle)
        self.assertIn("$proof.failure -or !$proof.nativeFutureFailureObserved", lifecycle)
        self.assertIn("$ancestor.ParentProcessId -eq $ownerPid", lifecycle)
        self.assertIn("EXPECTED_CLOSE_EXCEEDED_FIXED_20_SECONDS", lifecycle)
        self.assertNotIn("max-tick-time", lifecycle)
        probe = (ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production/NativeLifecycleProbe.java").read_text(encoding="utf-8")
        self.assertIn('proof.addProperty("status", "OBSERVED_NATIVE_FAILURE")', probe)
        self.assertIn('&& !proof.has("failure")', probe)
        self.assertIn("failedStage.get().isCompletedExceptionally()", probe)
        self.assertIn("failedStage.get().join()", probe)
        self.assertIn("NATIVE_GENERATION_STAGE_FUTURE", lifecycle)
        full = (scripts / "Run-ForgePhysical.ps1").read_text(encoding="utf-8")
        self.assertIn("'recovery_shutdown'", full)
        self.assertIn("$stop.save -cne $lastSave", full)
        self.assertIn("$stop.afterStop.generations -ne 0", full)
        self.assertIn("Run the matching clean shutdown with KeepWorld before recovery", full)

    def test_legacy_physical_proof_keeps_native_request_and_current_source_bound(self):
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-ForgePhysical.ps1").read_text(encoding="utf-8")
        self.assertIn("$hash=Get-InputHash", script)
        self.assertIn("if((Get-InputHash) -cne $hash)", script)
        self.assertNotIn("max-tick-time", script)
        self.assertIn("$proof.physicalBiomeChecks -le 0", script)
        probe = (ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production/NativePhysicalProbe.java").read_text(encoding="utf-8")
        self.assertIn("NativePhysicalRequests.submit(level, position, requestedStatus)", probe)
        requests = (ROOT / "versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator/probe/production/NativePhysicalRequests.java").read_text(encoding="utf-8")
        self.assertIn("CompletableFuture.supplyAsync(", requests)
        self.assertIn("300_000_000_000L", probe)
        self.assertIn("verifyPhysicalBiomes(chunk, reload)", probe)

    def test_legacy_check_only_binds_current_or_explicit_historical_source(self):
        script = (ROOT / "components/worldgen_validation/scripts/conformance-prefeatures/Run-LegacyTerrain.ps1").read_text(encoding="utf-8")
        self.assertIn('if ($ExpectedInputHash -and !$CheckOnly)', script)
        self.assertIn('else{Get-SourceFingerprint}', script)
        self.assertIn('Check-Evidence -CurrentHash $selectedHash', script)
        self.assertIn('INPUT_SCOPE: EXPLICIT_HISTORICAL_INPUT', script)
        self.assertIn('INPUT_SCOPE: CURRENT_SOURCE', script)
        self.assertIn("Target source changed while its oracle matrix was running", script)

    def test_physical_proof_labels_separate_world_and_output(self):
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1").read_text(encoding="utf-8")
        self.assertIn('if ($EvidenceLabel) { $worldName += "-$EvidenceLabel" }', script)
        self.assertIn('if ($EvidenceLabel) { $resultRoot = Join-Path $resultRoot $EvidenceLabel }', script)
        self.assertIn('"conditions-$Mode-$ChunkX-$ChunkZ.json"', script)
        self.assertIn('inputHash = Get-ProductionInputHash', script)
        self.assertIn('HashData([Text.Encoding]::UTF8.GetBytes($body))', script)
        self.assertIn('phase3c2.production.output=$($output.Replace', script)
        self.assertLess(script.index('if ($PreserveFailureOnly) {'), script.index('inputHash = Get-ProductionInputHash'))

    def test_causal_scope_variant_is_forwarded_to_the_game(self):
        script = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-ShulkerYawCausality.ps1").read_text(encoding="utf-8")
        self.assertIn('"result/$evidenceVariant/$target"', script)
        self.assertIn('"-D$($prefix)variant=$evidenceVariant"', script)
        self.assertIn("$evidenceVariant='end-yaw-ring-scope'", script)
        self.assertIn("if($EvidenceLabel){$evidenceVariant+='-'+$EvidenceLabel}", script)
        caller = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-LegacyB.ps1").read_text(encoding="utf-8")
        calls = [line for line in caller.splitlines() if "Run-ShulkerYawCausality.ps1" in line]
        self.assertEqual(2, len(calls))
        for call in calls:
            self.assertIn("-RingScope:$RingScope", call)
            self.assertIn("-EvidenceLabel $EvidenceLabel", call)

    def test_piglin_attribute_tape_and_approved_exception_remain_explicit(self):
        probe = ROOT / "versions/26.3-fabric/src/probe"
        trace = (probe / "java/io/github/recrivenvi/randomnibble6plus24generator/worldgen/verify/PiglinAttributeOrderTrace.java").read_text(encoding="utf-8")
        plugin = (probe / "java/io/github/recrivenvi/randomnibble6plus24generator/probe/ValidationMixinPlugin.java").read_text(encoding="utf-8")
        self.assertIn('Boolean.getBoolean("featureb.attributeOrder")', plugin)
        self.assertIn('System.identityHashCode(holder)', trace)
        self.assertIn('iteration.equals(nativeOrder)', trace)
        self.assertIn('row.get("usedOrder").equals(ids)', trace)
        self.assertIn('comparison.strictParity()', trace)
        self.assertIn('wanted.stream().map(byId::get)', trace)
        self.assertNotIn('setBaseValue(', trace)
        self.assertNotIn('setSeed(', trace)
        acceptance = (probe / "java/io/github/recrivenvi/randomnibble6plus24generator/worldgen/verify/NativeEntityAcceptance.java").read_text(encoding="utf-8")
        self.assertRegex(acceptance, r'piglinAttributeOrder\s*&&\s*entity\.getStringOr\("id", ""\)\.equals\("minecraft:piglin"\)')
        self.assertIn('Outside evidenced piglin attribute members', acceptance)
        script = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-IndependentB.ps1").read_text(encoding="utf-8")
        self.assertIn('NOT independent B parity acceptance', script)
        self.assertIn("if($AttributeReplaySource -and !$AttributeCaptureOnly)", script)
        causality = (ROOT / "components/worldgen_validation/scripts/conformance-feature_b/Run-PiglinOrderCausality.ps1").read_text(encoding="utf-8")
        self.assertIn("$_.changedEntities -eq 8", causality)
        self.assertIn("a no-op replay is not causal proof", causality)
        self.assertIn("foreach($suffix in @('a','b','c','d'))", causality)

    def test_player_probe_uses_native_preparation_and_explicit_transport_scope(self):
        probe = ROOT / "versions/26.3-fabric/src/probe/java/io/github/recrivenvi/randomnibble6plus24generator"
        source = (probe / "probe/verify/PlayerLifecycleEvidence.java").read_text(encoding="utf-8")
        self.assertIn('new PrepareSpawnTask(server, new NameAndId(profile))', source)
        self.assertRegex(source, r'prepare\.spawnPlayer\(\s*connection')
        self.assertIn('PERFORM_RESPAWN', source)
        self.assertIn('new EmbeddedChannel', source)
        self.assertIn('Connection.configureInMemoryPipeline', source)
        self.assertIn('NOT_CLIENT_HANDSHAKE_OR_RENDER', source)
        self.assertNotIn('new ServerPlayer(', source)
        self.assertNotIn('new ProtoChunk(', source)
        self.assertNotIn('getChunkFuture(', source)
        self.assertNotIn('postProcessGeneration(', source)
        plugin = (probe / "probe/ValidationMixinPlugin.java").read_text(encoding="utf-8")
        self.assertIn('.phase3c2.production.playerLifecycle', plugin)
        script = (ROOT / "components/worldgen_validation/scripts/integration-production_lifecycle/Run-Phase3C2Production.ps1").read_text(encoding="utf-8")
        self.assertIn("Preserve native player lifecycle evidence/world; never overwrite it", script)
        self.assertIn("-or $PlayerLifecycle", script)

    def test_targets_do_not_redeclare_bundled_product_classes(self):
        components = ROOT / "components/worldgen_contract/src/main/java"
        bundled = {path.relative_to(components) for path in components.rglob("*.java")}
        self.assertTrue(bundled)
        for target in (ROOT / "versions").iterdir():
            source = target / "src/main/java"
            local = {path.relative_to(source) for path in source.rglob("*.java")}
            with self.subTest(target=target.name):
                self.assertFalse(bundled & local, "Duplicate bundled class definitions")

    def test_parallel_correctness_roles_have_disjoint_ports(self):
        ports = {}
        for name in ("conformance-prefeatures", "conformance-legacy_b", "conformance-seed",
                     "integration-world_identity", "integration-production_lifecycle", "integration-legacy_physical"):
            path = ROOT / "validations" / name / "validation.toml"
            config = tomllib.loads(path.read_text(encoding="utf-8"))
            port = config["instances"]["server"]["port"]
            with self.subTest(validation=name):
                self.assertNotIn(port, ports, f"Parallel validation port collision: {ports}")
            ports[port] = name

    def test_mixin_packages_do_not_own_regular_probe_helpers(self):
        for target in (ROOT / "versions").iterdir():
            source = target / "src/probe"
            for config in (source / "resources").rglob("*.mixins.json"):
                data = json.loads(config.read_text(encoding="utf-8"))
                namespace = data.get("package")
                if not namespace:
                    continue
                for path in (source / "java").rglob("*.java"):
                    content = path.read_text(encoding="utf-8")
                    match = re.search(r"^package\s+([\w.]+)\s*;", content, re.MULTILINE)
                    if match and (match.group(1) == namespace or match.group(1).startswith(namespace + ".")):
                        with self.subTest(config=str(config), source=str(path)):
                            self.assertRegex(content, r"@Mixin\s*\(", "Regular helper in loader-owned mixin package")

    def test_fml_product_and_probe_have_disjoint_packages(self):
        targets = [path for path in (ROOT / "versions").iterdir()
                   if path.name.endswith(("-neoforge", "-forge"))]
        self.assertTrue(targets)
        for target in targets:
            with self.subTest(target=target.name):
                overlap = packages(target / "src/main") & packages(target / "src/probe")
                self.assertFalse(overlap, "FML split packages: " + ", ".join(sorted(overlap)))

    def test_root_formatter_keeps_template_scope_with_early_exclusions(self):
        template = (ROOT / "gradle/conventions/platform/src/main/java/io/github/recrivenvi/conventions/platform/Formatting.java").read_text(encoding="utf-8")
        template = template[template.index("static void root("):]
        literals = template[template.index("List.of("):template.index('"documents/**/*.md"') + len('"documents/**/*.md"')]
        expected = set(re.findall(r'"([^"]+)"', literals))
        build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        actual = set(re.findall(r'"([^"]+)"', re.search(r'include\(\s*(.*?)\s*\)', build, re.DOTALL).group(1)))
        self.assertEqual(expected, actual, "Root formatting must not silently omit a template text input")
        self.assertIn("ComponentConventions.TEXT_EXTENSIONS", build)
        self.assertIn('"validations/**/*.${extension}"', build)
        excluded = set(re.findall(r'"([^"]+)"', re.search(r'exclude\(\s*(.*?)\s*\)', build, re.DOTALL).group(1)))
        required = set(re.findall(r'"([^"]+)"', re.search(r'misc.targetExclude\((.*?)\);', template, re.DOTALL).group(1)))
        self.assertTrue(required <= excluded)
        wanted_directories = {pattern.split("/")[0] for pattern in expected if "/" in pattern} | {"validations"}
        for pattern in excluded - required:
            self.assertTrue(pattern.endswith("/**"))
            self.assertNotIn(pattern[:-3], wanted_directories, "Early pruning cannot remove a wanted directory")
        self.assertRegex(build, r'format\("misc"\)\s*\{\s*target\(repositoryText\)\s*\}')


if __name__ == "__main__":
    unittest.main()
