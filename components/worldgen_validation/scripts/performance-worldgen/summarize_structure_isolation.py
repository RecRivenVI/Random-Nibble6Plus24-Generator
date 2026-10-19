"""Read-only reduction of a bounded, disposable structure-isolation workload."""
import csv
import json
import re
import statistics
import sys
from pathlib import Path

root = Path(sys.argv[1])
data = json.loads((root / 'checkpoint-timeline.json').read_text(encoding='utf-8'))
jfr = json.loads((root / 'jfr-analysis.json').read_text(encoding='utf-8'))
conditions = json.loads((root / 'conditions.json').read_text(encoding='utf-8'))
with (root / 'jfr-analysis.json.events.tsv').open(encoding='utf-8') as stream:
    events = list(csv.DictReader(stream, delimiter='\t'))
for event in events:
    event['nanos'] = int(event['nanos'])
events.sort(key=lambda event: event['nanos'])
start = next(event['nanos'] for event in events if event['phase'].startswith('WORKLOAD_START_'))
end = next(event['nanos'] for event in events if event['phase'] == 'WORKLOAD_END')
seconds = (end - start) / 1e9
dimension = conditions.get('dimension', 'minecraft:overworld')
if dimension not in ('minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'):
    raise RuntimeError('Unsupported measured dimension: ' + dimension)
chunks = [chunk for chunk in data['chunks'] if chunk['dimension'] == dimension]
if not chunks:
    raise RuntimeError('No actual chunks in measured dimension: ' + dimension)
for chunk in data['chunks']:
    if chunk['dimension'] != dimension and any(start <= chunk['firstNanos'].get(phase, end+1) <= end
                                              for phase in ('PREPARE_END', 'STAGE_FULL_END')):
        raise RuntimeError('Generation in an unintended dimension: ' + chunk['key'])
events = [event for event in events if event['key'] == 'WORKLOAD' or event['key'].startswith(dimension + ':')]
samples = [sample for sample in data['samples'] if start <= sample['seconds'] * 1e9 <= end]

def count(phase):
    return sum(start <= chunk['firstNanos'].get(phase, end + 1) <= end for chunk in chunks)

if conditions.get('pipeline') == 'vanilla' and any(
        chunk.get('mosaic', False) or 'PREPARE_BEGIN' in chunk['firstNanos']
        for chunk in data['chunks']):
    raise RuntimeError('Vanilla control entered Mosaic generation')

def stats(values):
    if not values:
        return None
    values = sorted(values)
    return dict(count=len(values), mean=statistics.mean(values), median=statistics.median(values),
                p90=values[min(len(values)-1, int(len(values)*.9))],
                p95=values[min(len(values)-1, int(len(values)*.95))], maximum=max(values))

prepare = [(chunk['firstNanos']['PREPARE_END'] - chunk['firstNanos']['PREPARE_BEGIN']) / 1e9
           for chunk in chunks if 'PREPARE_BEGIN' in chunk['firstNanos']
           and start <= chunk['firstNanos'].get('PREPARE_END', end + 1) <= end]
visible_latency = [(chunk['firstNanos']['READY_TO_SEND'] - chunk['firstNanos']['VIEW_DEMAND']) / 1e9
                   for chunk in chunks if 'VIEW_DEMAND' in chunk['firstNanos']
                   and start <= chunk['firstNanos'].get('READY_TO_SEND', end + 1) <= end
                   and chunk['firstNanos']['READY_TO_SEND'] >= chunk['firstNanos']['VIEW_DEMAND']]
initial = next(chunk for chunk in chunks if chunk['key'] == f"{dimension}:{conditions.get('x',125)},{conditions.get('z',-37)}")
initial_origin = initial['firstNanos'].get('VIEW_DEMAND', initial['firstNanos'].get('PHYSICAL_REQUEST'))
initial_timeline = {phase: (stamp-initial_origin)/1e9 for phase,stamp in initial['firstNanos'].items()
                    if initial_origin is not None and stamp <= end and phase in (
                        'VIEW_DEMAND', 'PHYSICAL_REQUEST', 'PLAN_BEGIN', 'ARTIFACT_REQUEST',
                        'PREPARE_BEGIN', 'CANONICAL_BEGIN', 'CANONICAL_END',
                        'ARTIFACT_CAPTURE_BEGIN', 'ARTIFACT_CAPTURE_END', 'REHYDRATE_BEGIN',
                        'REHYDRATE_END', 'PREPARE_END', 'POI_BEGIN', 'POI_END',
                        'STAGE_INITIALIZE_LIGHT_BEGIN', 'STAGE_INITIALIZE_LIGHT_END',
                        'STAGE_LIGHT_BEGIN', 'STAGE_LIGHT_END', 'STAGE_SPAWN_BEGIN',
                        'STAGE_SPAWN_END', 'STAGE_FULL_END', 'PROMOTE_FULL_READY',
                        'BLOCK_TICKING_READY', 'ENTITY_TICKING_READY', 'READY_TO_SEND')}
player_proxy = initial['firstNanos'].get('ENTITY_TICKING_READY')
player_proxy = (player_proxy-start)/1e9 if player_proxy is not None and player_proxy<=end else None
intervals = sorted((max(start, chunk['firstNanos']['PREPARE_BEGIN']), min(end, chunk['firstNanos'].get('PREPARE_END', end)))
                   for chunk in chunks if chunk['firstNanos'].get('PREPARE_BEGIN',end+1)<=end)
busy_ns=0
left=right=None
for a,b in intervals:
    if b<=a: continue
    if left is None: left,right=a,b
    elif a<=right: right=max(right,b)
    else: busy_ns+=right-left;left,right=a,b
if left is not None: busy_ns+=right-left
wanted=set();admitted=set();ready=set();peak_admission_queue=0
coverage=[];thresholds=[10,20,30,45,55];cursor=0
center=(conditions.get('x',125),conditions.get('z',-37))
for event in events:
    if event['nanos']>end:break
    t=(event['nanos']-start)/1e9
    while cursor<len(thresholds) and t>=thresholds[cursor]:
        radius=conditions['view']
        viewport={f"{dimension}:{x},{z}" for z in range(center[1]-radius,center[1]+radius+1)
                  for x in range(center[0]-radius,center[0]+radius+1)}
        coverage.append(dict(seconds=thresholds[cursor], wanted=len(wanted), ready=len(wanted & ready),
                             fraction=len(wanted & ready)/len(wanted) if wanted else None,
                             currentViewport=len(viewport),currentViewportReady=len(viewport & ready),
                             currentViewportFraction=len(viewport & ready)/len(viewport)))
        cursor+=1
    key=event['key'];phase=event['phase']
    if phase=='VIEW_DEMAND':wanted.add(key)
    elif phase=='VIEW_WITHDRAWN':wanted.discard(key)
    elif phase=='TICKET_ADD' and event['detail'].startswith('minecraft:player_loading@'):admitted.add(key)
    elif phase=='TICKET_REMOVE' and event['detail'].startswith('minecraft:player_loading@'):admitted.discard(key)
    elif phase=='READY_TO_SEND':ready.add(key)
    elif phase=='FULL_STATUS_INACCESSIBLE':ready.discard(key)
    if phase.startswith('TICKET_VIEWER_MOVE_') or phase.startswith('TICKET_VIEWER_ENTER_'):
        match=re.search(r'\[(-?\d+),\s*(-?\d+)\]',phase)
        if match:center=(int(match[1]),int(match[2]))
    peak_admission_queue=max(peak_admission_queue,len(wanted-admitted))
thread_samples=[sample for sample in samples if sample.get('serverCpuNanos',-1)>=0]
thread_cpu=None
if len(thread_samples)>1:
    a,b=thread_samples[0],thread_samples[-1]
    thread_cpu=(b['serverCpuNanos']-a['serverCpuNanos'])/1e9/(b['seconds']-a['seconds'])*100
process_samples = [sample for sample in samples if 'processCpuNanos' in sample]
process_cores = None
if len(process_samples) > 1:
    a,b = process_samples[0],process_samples[-1]
    if b['seconds'] > a['seconds']:
        process_cores = (b['processCpuNanos']-a['processCpuNanos'])/1e9/(b['seconds']-a['seconds'])
cpu_loads = [sample['processCpuLoad'] for sample in samples if sample['processCpuLoad'] >= 0]
log=(root/'console.log').read_text(encoding='utf-8-sig',errors='replace')
before_end=log.split('MOSAIC PROFILE MARKER WORKLOAD_END')[0]
lag=[int(value) for value in re.findall(r'Running (\d+)ms or \d+ ticks behind',before_end)]
if any(word in before_end for word in ('Disabled ring','Disabled structure','Encountered an unexpected exception','Caught exception in thread','A single server tick took')):
    raise RuntimeError('Workload failed before measurement end: '+str(root))
exp=data['structureExperiment'];mode=conditions.get('structures', 'baseline' if conditions.get('pipeline') in ('b', 'ordering2', 'vanilla') else None)
if mode is None:
    raise RuntimeError('Missing structure experiment mode')
if mode in ('none','no-stronghold') and (exp.get('ringPreparations',0) or exp.get('ringPlacementEvaluations',0)):
    raise RuntimeError('Rings were not disabled')
if mode in ('none','no-other') and exp.get('otherPlacementEvaluations',0):
    raise RuntimeError('Other structures were not disabled')
if mode=='none' and any(key.startswith('startAttempts.') for key in exp):
    raise RuntimeError('Structure generation was not disabled')
buckets=jfr['exclusiveCpuBuckets'];cpu_count=jfr['cpuSamples']
result=dict(label=root.name,conditions=conditions,windowSeconds=seconds,canonical=count('PREPARE_END'),
            javaRuntime=data.get('javaRuntime'),javaVendor=data.get('javaVendor'),
            availableProcessors=data.get('availableProcessors'),
            canonicalPerSecond=count('PREPARE_END')/seconds,
            busyCanonicalPerSecond=count('PREPARE_END')/(busy_ns/1e9) if busy_ns else None,
            canonicalBusySeconds=busy_ns/1e9,FULL=count('STAGE_FULL_END'),sendReady=count('READY_TO_SEND'),
            entityTicking=count('ENTITY_TICKING_READY'),viewDemand=count('VIEW_DEMAND'),
            averageTargetLatency=stats(prepare),visibleReadyLatency=stats(visible_latency),
            initialCenterTimelineSeconds=initial_timeline,
            initialCenterEntityReadyProxySeconds=player_proxy,
            processCpuPercent=statistics.mean(cpu_loads)*100 if cpu_loads else None,
            processCoreEquivalents=process_cores,
            processCpuBudgetPercent=process_cores/conditions['cpu']*100 if process_cores is not None and conditions.get('cpu',0)>0 else None,
            serverThreadOneCorePercent=thread_cpu,maxLoggedServerLagMs=max(lag) if lag else None,
            structureCpuPercent=(buckets.get('strongholdRings',0)+buckets.get('otherStructure',0))/cpu_count*100,
            strongholdCpuPercent=buckets.get('strongholdRings',0)/cpu_count*100,
            cpuBucketsPercent={k:v/cpu_count*100 for k,v in buckets.items()},cpuSamples=cpu_count,
            peakAdmissionQueue=peak_admission_queue,peakExecutorQueue=max(s['queuedPrepare'] for s in samples),
            peakActiveSessions=max(s['activePrepare'] for s in samples),coverage=coverage,
            heapPeak=max(s['heapUsed'] for s in samples),gcPauseMs=jfr['gcPauseMs'],
            maxGcPauseMs=jfr['maxGcPauseMs'],experiment=exp,
            moves=sum(e['phase'].startswith('TICKET_VIEWER_MOVE_') for e in events))
(root/'isolation-summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({k:result[k] for k in ('label','canonicalPerSecond','FULL','sendReady','structureCpuPercent','strongholdCpuPercent','coverage')}))
