"""Read saved Oracle/probe/JFR evidence; never opens or changes a world."""
import json
import re
import statistics
import sys
from pathlib import Path

root = Path(sys.argv[1])

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def children_count(nbt):
    # Count compounds at the top level of the native Children list, respecting strings.
    text = nbt[nbt.index('Children:[') + len('Children:'):]
    depth = 0
    quote = None
    escaped = False
    count = 0
    for char in text:
        if quote:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'"):
            quote = char
        elif char in '[{':
            if depth == 1 and char == '{':
                count += 1
            depth += 1
        elif char in ']}':
            depth -= 1
            if depth == 0:
                return count
    raise ValueError('Unterminated native Children list')

out = {'scope': 'Research only; no production activation; no expanded canonical ignore list'}
out['validationNotes'] = [
    'Final acceptance rows are limited-candidate-verified and limited-lightweight-verified. Earlier failed diagnostic reports are retained, not deleted.',
    'Unmodified Oracle also changes HashMap-derived Beardifier list order across JVMs. Every geometry/junction field and multiplicity is still compared; order differences require native density raw-double equality at every voxel and interpolation boundary. No production input is sorted.',
    'End City Shulker Rotation also differs between unmodified Oracle processes. Full entity parity is not claimed; entity NBT is retained and no new field is ignored.',
    'Performance labels containing limited use 4 visible CPUs, affinity 0xF00, BelowNormal, 2 Gradle workers and one instance. Older unrestricted windows are separate evidence and must not be pooled.',
    'All performance reductions end at WORKLOAD_END. Existing cancellation exceptions during disposable-world shutdown are outside that window and were not repaired.',
    'Allocation per completed target is a whole-JVM sampled proxy including in-flight work, not exact per-target accounting.',
]
out['rings'] = read(root / 'lazy-ring-audit/report.json')['rings']
matrices = {}
for name in ('oracle-stage-exact', 'candidate-stage-exact', 'lightweight-stage-exact',
             'limited-oracle-final', 'limited-candidate-final', 'limited-lightweight-final',
             'limited-candidate-verified', 'limited-lightweight-verified',
             'oracle-strongholds', 'candidate-strongholds', 'lightweight-strongholds'):
    path = root / ('lazy-' + name) / 'report.json'
    if not path.exists():
        continue
    report = read(path)
    rows = report['fixtures']
    totals = {}
    for row in rows:
        for key, value in row.get('metrics', {}).items():
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                totals[key] = totals.get(key, 0) + value
    matrices[name] = {
        'status': report.get('status'), 'fixtures': len(rows), 'metrics': totals,
        'availableProcessors': report.get('availableProcessors'),
        'meanGenerationMs': statistics.mean(r['generationMs'] for r in rows) if rows and 'generationMs' in rows[0] else None,
        'meanStructureStageMs': totals.get('structureStageNanos', 0) / max(1, len(rows)) / 1e6,
        'meanNativeStructureEvaluations': statistics.mean(r['nativeStructureEvaluations'] for r in rows) if rows and 'nativeStructureEvaluations' in rows[0] else None,
        'entityDifferences': [r['id'] for r in rows if r.get('fullFeaturesIncludingEntitiesMatch') is False],
        'emptyTargetReferences': sum(r.get('targetReferenceTypes') == 0 for r in rows),
        'targetReferencedStarts': sum(r.get('targetRelevance',{}).get('referencedStarts',0) for r in rows),
        'targetReferencedPieces': sum(r.get('targetRelevance',{}).get('referencedPieces',0) for r in rows),
        'targetIntersectingPieces': sum(r.get('targetRelevance',{}).get('piecesWithHorizontalTargetIntersection',0) for r in rows),
        'nativeMapOrderDifferences': sum(len(r.get('beardComparison',{}).get('nativeMapOrderDifferentChunks',[])) for r in rows),
        'rawDensitySamplesCompared': sum(r.get('beardComparison',{}).get('rawDoubleSamplesCompared',0) for r in rows),
    }
out['matrices'] = matrices
focused = {}
for name, structure in (('village','village_'),('mineshaft','mineshaft'),('trial','trial_chambers'),
                        ('trial-adaptation','trial_chambers'),('monument','monument'),('fortress','fortress'),
                        ('end-city','end_city'),('stronghold-start','stronghold'),('stronghold-neighbor','stronghold')):
    row = read(root / 'lazy-candidate-stage-exact' / (name + '.json'))
    focus = {key: children_count(nbt) for key, nbt in row['preStructures'].items()
             if ('/minecraft:' + structure) in key and 'Children:[' in nbt}
    focused[name] = {'target': row['chunk'], 'seed': row['seed'], 'startsAndPieceCounts': focus,
                     'targetBeardRigidCount': row['preBeardInputs'].get(row['chunk'], '').count('Rigid['),
                     'targetReferenceTypes': row['targetReferenceTypes']}
out['focusedStructures'] = focused
perf = []
for path in sorted(root.glob('lazy-perf-*/isolation-summary.json')):
    row = read(path)
    jfr = read(path.parent / 'jfr-analysis.json')
    timeline = read(path.parent / 'checkpoint-timeline.json')
    row['allocationWeightedBytesTotal'] = jfr.get('allocationWeightedBytesTotal')
    row['allocationMiBPerCompletedProxy'] = (jfr['allocationWeightedBytesTotal'] / 1024**2 / row['canonical']) if row['canonical'] else None
    row['lazyMetricsCompletedContextsOnly'] = timeline.get('lazyStructureMetrics', {})
    perf.append(row)
out['performance'] = perf
path = root / 'lazy-study-summary.json'
path.write_text(json.dumps(out, indent=2), encoding='utf-8')
print(json.dumps({'matrices': matrices, 'focusedStructures': focused,
                  'performance': [{k:r[k] for k in ('label','canonicalPerSecond','FULL','sendReady','structureCpuPercent','strongholdCpuPercent','allocationMiBPerCompletedProxy')} for r in perf]}, indent=2))
