import csv
import json
import statistics
import sys
from pathlib import Path
from collections import Counter, defaultdict

root = Path(sys.argv[1])
data = json.loads((root / 'timeline.json').read_text(encoding='utf-8'))
events_path = root / 'jfr-analysis.json.events.tsv'
events = []
if events_path.exists():
    with events_path.open(encoding='utf-8') as f:
        events = list(csv.DictReader(f, delimiter='\t'))
    for e in events:
        e['nanos'] = int(e['nanos'])
    events.sort(key=lambda e: e['nanos'])
markers = [e for e in events if e['key'] == 'WORKLOAD']
start = next((e['nanos'] for e in markers if e['phase'].startswith('WORKLOAD_START')),
             min((e['nanos'] for e in events), default=0))
end = next((e['nanos'] for e in markers if e['phase'] == 'WORKLOAD_END'), data['uptimeNanos'])
window = (end-start)/1e9
chunks = data['chunks']
bykey = {c['key']: c for c in chunks}
ticket_types = Counter()
player_admits = {}
wanted=set()
admitted=set()
peak_admission_queue=0
for e in events:
    if e['nanos'] > end:
        continue
    if e['phase']=='VIEW_DEMAND':wanted.add(e['key'])
    if e['phase']=='VIEW_WITHDRAWN':wanted.discard(e['key'])
    if e['phase'] == 'TICKET_ADD':
        kind = e['detail'].split('@')[0]
        ticket_types[kind] += 1
        if kind == 'minecraft:player_loading':
            player_admits.setdefault(e['key'], e['nanos'])
            admitted.add(e['key'])
    if e['phase']=='TICKET_REMOVE' and e['detail'].startswith('minecraft:player_loading@'):
        admitted.discard(e['key'])
    peak_admission_queue=max(peak_admission_queue,len(wanted-admitted))

def stats(values):
    if not values:
        return {'n': 0}
    values = sorted(values)
    return dict(n=len(values), mean=statistics.mean(values), median=statistics.median(values),
                p90=values[min(len(values)-1, int(len(values)*0.9))], max=max(values))

def phase_count(phase):
    return sum(phase in c['firstNanos'] and c['firstNanos'][phase]<=end for c in chunks)

def duration(c, a, b):
    t=c['firstNanos']
    return (t[b]-t[a])/1e9 if a in t and b in t else None

ready = []
for c in chunks:
    t=c['firstNanos']
    if 'READY_TO_SEND' not in t or t['READY_TO_SEND'] > end:
        continue
    origin = 'VIEW_DEMAND' if 'VIEW_DEMAND' in t else 'PUBLIC_REQUEST' if 'PUBLIC_REQUEST' in t else 'PHYSICAL_REQUEST'
    if origin not in t:
        continue
    row = dict(key=c['key'], localSeed=c['localSeed'], requestOrigin=origin,
               readySeconds=max(0,(t['READY_TO_SEND']-t[origin])/1e9))
    if 'PREPARE_BEGIN' in t and 'PREPARE_END' in t:
        row['beforePrepare']=(t['PREPARE_BEGIN']-t[origin])/1e9
        row['prepare']=(t['PREPARE_END']-t['PREPARE_BEGIN'])/1e9
        row['afterPrepare']=(t['READY_TO_SEND']-t['PREPARE_END'])/1e9
        points=[('request', t[origin]), ('prepareBegin', t['PREPARE_BEGIN']),
                ('canonicalEnd',t.get('CANONICAL_END',t['PREPARE_END'])), ('prepareEnd',t['PREPARE_END']),
                ('initializeBegin',t.get('STAGE_INITIALIZE_LIGHT_BEGIN',t['PREPARE_END'])),
                ('initializeEnd',t.get('STAGE_INITIALIZE_LIGHT_END',t['PREPARE_END'])),
                ('lightBegin',t.get('STAGE_LIGHT_BEGIN',t['PREPARE_END'])),
                ('lightEnd',t.get('STAGE_LIGHT_END',t['PREPARE_END'])),
                ('spawnBegin',t.get('STAGE_SPAWN_BEGIN',t['PREPARE_END'])),
                ('spawnEnd',t.get('STAGE_SPAWN_END',t['PREPARE_END'])),
                ('fullEnd',t.get('STAGE_FULL_END',t['PREPARE_END'])),('ready',t['READY_TO_SEND'])]
        row['segments']={f'{a[0]}->{b[0]}':(b[1]-a[1])/1e9 for a,b in zip(points, points[1:])}
    packet=t.get('PACKET_ENQUEUE_END')
    if packet:
        row['packetEnqueuedSeconds']=(packet-t[origin])/1e9
        row['readyToPacket']=(packet-t['READY_TO_SEND'])/1e9
    # Report the last FULL prerequisite in Vanilla's 3x3 block-ticking neighborhood.
    neighbors=[]
    for dz in range(-1,2):
        for dx in range(-1,2):
            n=bykey.get(f"{c['dimension']}:{c['x']+dx},{c['z']+dz}")
            if n and 'STAGE_FULL_END' in n['firstNanos']:
                neighbors.append((n['firstNanos']['STAGE_FULL_END'],n['key']))
    if neighbors:
        latest=max(neighbors)
        row['lastNeighborFull']=dict(key=latest[1], secondsAfterRequest=(latest[0]-t[origin])/1e9,
                                    readyGap=(t['READY_TO_SEND']-latest[0])/1e9)
    ready.append(row)
ready.sort(key=lambda r:r['readySeconds'], reverse=True)
samples=[s for s in data.get('samples',[]) if start<=s['seconds']*1e9<=end]
active_demands=[c for c in chunks if c['key'] in wanted]
queue=[c for c in active_demands if c['key'] not in admitted]
canon_done=[c for c in chunks if 'PREPARE_END' in c['firstNanos'] and c['firstNanos']['PREPARE_END']<=end]
admission_waits=[(player_admits[c['key']]-c['firstNanos']['VIEW_DEMAND'])/1e9 for c in chunks
                 if c['key'] in player_admits and 'VIEW_DEMAND' in c['firstNanos']]
result=dict(windowSeconds=window, integrated=data['integrated'],
    markers=[dict(seconds=(e['nanos']-start)/1e9,phase=e['phase']) for e in markers],
    uniquePhysicalPositions=len(chunks), actualHolderPositions=phase_count('HOLDER_LEVEL'), viewDemand=phase_count('VIEW_DEMAND'),
    playerLoadingAdmitted=len(player_admits), activeViewDemandsAtEnd=len(active_demands),
    waitingBeforePlayerTicketAtEnd=len(queue), withdrawn=phase_count('VIEW_WITHDRAWN'),
    peakPlayerAdmissionQueue=peak_admission_queue,
    ticketAdds=dict(ticket_types), canonicalRequested=phase_count('ARTIFACT_REQUEST'),
    canonicalCompleted=len(canon_done), canonicalPerSecond=len(canon_done)/window if window else 0,
    full=phase_count('STAGE_FULL_END'),blockTicking=phase_count('BLOCK_TICKING_READY'),
    entityTicking=phase_count('ENTITY_TICKING_READY'),readyToSend=len(ready),packets=phase_count('PACKET_ENQUEUE_END'),
    artifactRepeats=[dict(key=c['key'],count=c['counts']['PREPARE_BEGIN']) for c in chunks if c['counts'].get('PREPARE_BEGIN',0)>1],
    admissionWaitSeconds=stats(admission_waits),
    prepareSeconds=stats([duration(c,'PREPARE_BEGIN','PREPARE_END') for c in canon_done]),
    requestedToReadySeconds=stats([r['readySeconds'] for r in ready]),
    peakActivePrepare=max((s['activePrepare'] for s in samples),default=0),
    peakExecutorQueue=max((s['queuedPrepare'] for s in samples),default=0),
    averageCpuLoad=statistics.mean([s['processCpuLoad'] for s in samples if s['processCpuLoad']>=0]) if samples else None,
    sampledHeapPeak=max((s['heapUsed'] for s in samples),default=0),
    aggregates=data.get('aggregates',{}),slowReadyChunks=ready[:15],
    slowPacketChunks=sorted([r for r in ready if 'packetEnqueuedSeconds' in r],
                           key=lambda r:r['packetEnqueuedSeconds'],reverse=True)[:15])
(root/'summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k not in ('slowReadyChunks','slowPacketChunks','aggregates')},indent=2))
