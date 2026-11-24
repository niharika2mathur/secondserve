import { Fragment, useEffect, useMemo, useRef, useState } from 'react';
import { api, bytes, num } from '../api.js';
import NetworkGraph, { GraphLegend } from '../NetworkGraph.jsx';
import { Badge, Button, Empty, ErrorBox, Field, Loading, Stat, useLoad, useToast } from '../ui.jsx';

export default function Lab({ go }) {
  const [tab, setTab] = useState('ek');
  return (
    <>
      <div className="page-head">
        <div><h1>Algorithm lab</h1><p>Look inside the two algorithms implemented from scratch in Java: the Edmonds-Karp maximum flow engine and the Bloom filter used for duplicate screening.</p></div>
        <div className="tabs" role="tablist"><button className={tab === 'ek' ? 'on' : ''} onClick={() => setTab('ek')}>Edmonds-Karp</button><button className={tab === 'bf' ? 'on' : ''} onClick={() => setTab('bf')}>Bloom filter</button></div>
      </div>
      {tab === 'ek' ? <EdmondsKarpLab go={go} /> : <BloomLab />}
    </>
  );
}

/* ------------------------------ Edmonds-Karp ------------------------------ */
function EdmondsKarpLab({ go }) {
  const { data: a, error, loading, reload } = useLoad(() => api('/algorithms/network-analysis'));
  const [k, setK] = useState(null);
  const [all, setAll] = useState(false);
  const [busy, setBusy] = useState(false);
  const notify = useToast();
  useEffect(() => { if (a) setK(a.steps.length); }, [a]);

  const view = useMemo(() => {
    if (!a || k == null) return null;
    const batches = new Set(a.arcs.filter((x) => x.from === 'Source').map((x) => x.to));
    const nodes = a.nodes.map((n, i) => ({ id: n, label: n, sub: '', type: i === 0 ? 'source' : i === a.nodes.length - 1 ? 'sink' : batches.has(n) ? 'batch' : 'ngo', status: 'OK' }));
    const step = k > 0 ? a.steps[k - 1] : null;
    const flows = step ? step.flows : a.arcs.map(() => 0);
    const onPath = new Set(step ? step.pathArcs : []);
    const cut = new Set(k === a.steps.length ? a.cutArcs : []);
    const edges = a.arcs.map((x, i) => ({
      from: x.from, to: x.to, capacity: x.capacity, flow: flows[i],
      state: onPath.has(i) ? 'PATH' : cut.has(i) ? 'CUT' : flows[i] > 0 ? 'FLOW' : 'IDLE',
      label: flows[i] > 0 || onPath.has(i) || cut.has(i) ? (x.capacity == null ? `${flows[i]}` : `${flows[i]}/${x.capacity}`) : '',
    }));
    return { nodes, edges, flows, step, onPath };
  }, [a, k]);

  const runNow = async () => {
    setBusy(true);
    try { const d = await api('/dashboard'); await api('/allocation/run', { method: 'POST', body: { planningTime: d.suggestedPlanningTime } }); notify('Allocation run'); reload(); }
    catch (e) { notify(e.message, 'err'); } finally { setBusy(false); }
  };

  if (loading && !a) return <Loading />;
  if (error?.status === 404) return <div className="card"><Empty title="No allocation has been run yet">The lab shows the trace of the most recent run.<div style={{ marginTop: 14 }}><Button variant="primary" busy={busy} onClick={runNow}>Run allocation now</Button></div></Empty></div>;
  if (error) return <ErrorBox error={error} />;
  if (!view) return <Loading />;

  const fmtCap = (c) => (c == null ? '\u221e' : c);
  const step = view.step;
  return (
    <div className="stack">
      <div className="grid g4">
        <Stat k="Maximum flow" v={num(a.maxFlow)} tone="good" /><Stat k="Augmenting paths" v={a.augmentations} s={`${a.bfsRuns} BFS passes`} />
        <Stat k="Network size" v={`${a.nodeCount} nodes`} s={`${a.edgeCount} edges (feasible routes only)`} /><Stat k="Min-cut capacity" v={num(a.cutCapacity)} s="equals max flow" />
      </div>

      <div className="card">
        <div className="card-head"><div><h2>Step through the augmenting paths</h2><p>Step 0 is the original network. Each step is a BFS-found shortest path from source to sink with spare (residual) capacity. The last step shows the minimum cut in orange.</p></div>
          <div className="row"><Button size="sm" disabled={k <= 0} onClick={() => setK(k - 1)}>&larr; Previous</Button><b className="mono">step {k} / {a.steps.length}</b><Button size="sm" disabled={k >= a.steps.length} onClick={() => setK(k + 1)}>Next &rarr;</Button></div></div>
        <input type="range" min="0" max={a.steps.length} value={k} onChange={(e) => setK(Number(e.target.value))} style={{ width: '100%', marginBottom: 12 }} aria-label="Augmentation step" />
        <NetworkGraph nodes={view.nodes} edges={view.edges} showUnavailable={false} />
        <div className="legend" style={{ marginTop: 10 }}><span><i style={{ borderColor: '#2b5f8a' }} />Current augmenting path</span><span><i style={{ borderColor: '#1f6b48' }} />Flow so far</span><span><i style={{ borderColor: '#cfd5cb' }} />Unused</span><span><i style={{ borderColor: '#c2570c' }} />Min-cut edge</span></div>
      </div>

      <div className="grid g2">
        <div className="card">
          <h2 style={{ marginBottom: 12 }}>{step ? `Augmentation ${step.number}` : 'Original graph'}</h2>
          {step ? (
            <>
              <div className="path">{step.path.map((n, i) => <span key={i} style={{ display: 'contents' }}><span className="n">{n}</span>{i < step.path.length - 1 && <span className="a">{step.reverse[i] ? '\u21a9 (reverse edge)' : '\u2192'}</span>}</span>)}</div>
              <dl className="kv" style={{ marginTop: 14 }}><dt>Bottleneck capacity</dt><dd>{step.bottleneck} meals (smallest residual capacity on the path)</dd><dt>Flow added</dt><dd>+{step.bottleneck}</dd><dt>Total flow after step</dt><dd>{step.totalFlow}</dd></dl>
              {step.reverse.some(Boolean) && <div className="notice info" style={{ marginTop: 12 }}>This path uses a reverse edge: it cancels part of an earlier assignment so those meals can go elsewhere. A greedy allocator cannot do this.</div>}
            </>
          ) : <p className="muted">Source edges carry batch supply, sink edges carry NGO intake capacity, and the middle edges (only feasible routes) are unbounded (&infin;). All flows start at 0.</p>}
          {k === a.steps.length && (
            <div style={{ marginTop: 14 }}><h3>Final state</h3>
              <p className="muted" style={{ marginTop: 4 }}>The last BFS cannot reach the sink. The {a.sourceSide.length} nodes it can reach form the source side S of a minimum cut: {a.sourceSide.join(', ')}.</p>
              <p style={{ marginTop: 6 }}>Cut edges: {a.cutArcs.length ? a.cutArcs.map((i) => `${a.arcs[i].from}\u2192${a.arcs[i].to} (${a.arcs[i].capacity})`).join(', ') : 'none'}. Total capacity {a.cutCapacity} = max flow {a.maxFlow}.</p></div>
          )}
        </div>
        <div className="card">
          <h2 style={{ marginBottom: 10 }}>Augmentation log</h2>
          <div className="steps">{a.steps.map((s) => <button key={s.number} className={k === s.number ? 'on' : ''} onClick={() => setK(s.number)}><b>#{s.number}</b> +{s.bottleneck}, total {s.totalFlow}<div className="muted" style={{ fontSize: 12.5 }}>{s.path.join(' \u2192 ')}</div></button>)}</div>
          {a.truncated && <p className="muted" style={{ marginTop: 8 }}>Trace truncated to the first {a.steps.length} paths.</p>}
        </div>
      </div>

      <div className="card flush scroll">
        <div style={{ padding: '18px 20px 0' }} className="card-head"><div><h2>Residual graph {step ? `after step ${k}` : '(initial)'}</h2><p>Residual forward = capacity &minus; flow. Residual backward = flow (what could be undone).</p></div>
          <label className="row muted"><input type="checkbox" checked={all} onChange={(e) => setAll(e.target.checked)} /> show every edge</label></div>
        <table><thead><tr><th>Edge</th><th className="num">Capacity</th><th className="num">Flow</th><th className="num">Residual forward</th><th className="num">Residual backward</th></tr></thead>
          <tbody>{a.arcs.map((x, i) => ({ x, i, f: view.flows[i] })).filter(({ f, i }) => all || f > 0 || view.onPath.has(i)).map(({ x, i, f }) => (
            <tr key={i} style={view.onPath.has(i) ? { background: '#e6eef5' } : undefined}><td>{x.from} &rarr; {x.to}</td><td className="num">{fmtCap(x.capacity)}</td><td className="num">{f}</td><td className="num">{x.capacity == null ? '\u221e' : x.capacity - f}</td><td className="num">{f}</td></tr>))}</tbody></table>
      </div>

      <div className="card">
        <h2 style={{ marginBottom: 10 }}>Complexity</h2>
        <dl className="kv">{Object.entries(a.complexity).map(([key, v]) => <Fragment key={key}><dt>{key}</dt><dd>{v}</dd></Fragment>)}</dl>
        <p className="muted" style={{ marginTop: 10 }}>Correctness: when no augmenting path exists, the nodes reachable from the source define a cut whose capacity equals the current flow (max-flow min-cut theorem), so the flow is maximum for this network.</p>
      </div>
    </div>
  );
}

/* ------------------------------ Bloom filter ------------------------------ */
function BitGrid({ bits, size, highlight = [], cols = 120 }) {
  const ref = useRef(null);
  const cell = 5;
  const rows = Math.ceil(size / cols);
  useEffect(() => {
    const c = ref.current; if (!c) return;
    const ctx = c.getContext('2d');
    ctx.clearRect(0, 0, c.width, c.height);
    ctx.fillStyle = '#e9ede6';
    for (let i = 0; i < size; i++) ctx.fillRect((i % cols) * cell, Math.floor(i / cols) * cell, cell - 1, cell - 1);
    ctx.fillStyle = '#1f6b48';
    for (const i of bits) ctx.fillRect((i % cols) * cell, Math.floor(i / cols) * cell, cell - 1, cell - 1);
    ctx.strokeStyle = '#c2570c'; ctx.lineWidth = 2;
    for (const i of highlight) ctx.strokeRect((i % cols) * cell - 1, Math.floor(i / cols) * cell - 1, cell + 1, cell + 1);
  }, [bits, size, highlight, cols]);
  return <div className="scroll"><canvas ref={ref} width={cols * cell} height={rows * cell + 2} role="img" aria-label={`Bit array of ${size} bits, ${bits.length} set`} /></div>;
}

function BloomLab() {
  const { data: s, error, loading, reload } = useLoad(() => api('/algorithms/bloom-stats'));
  const [id, setId] = useState('BATCH-101');
  const [check, setCheck] = useState(null);
  const [checkErr, setCheckErr] = useState(null);
  const [demo, setDemo] = useState({ expectedInsertions: 50, falsePositiveRate: 0.1, inserts: 200, queries: 2000 });
  const [demoRes, setDemoRes] = useState(null);
  const [demoErr, setDemoErr] = useState(null);
  const [busy, setBusy] = useState(false);

  const doCheck = async (e) => { e.preventDefault(); setCheckErr(null); try { setCheck(await api('/algorithms/bloom-check', { method: 'POST', body: { id } })); } catch (x) { setCheckErr(x); setCheck(null); } };
  const doDemo = async (e) => { e.preventDefault(); setBusy(true); setDemoErr(null); try { setDemoRes(await api('/algorithms/bloom-demo', { method: 'POST', body: demo })); } catch (x) { setDemoErr(x); } finally { setBusy(false); } };
  const dset = (k) => (e) => setDemo({ ...demo, [k]: e.target.value });

  if (loading && !s) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  const outcomeTone = { NEW: 'green', DUPLICATE: 'red', FALSE_POSITIVE_ACCEPTED: 'amber' };
  return (
    <div className="stack">
      <div className="grid g4">
        <Stat k="Bit array (m)" v={num(s.bitSize)} s={`${bytes(s.memoryBytes)} of memory`} /><Stat k="Hash functions (k)" v={s.hashCount} s="double hashing" />
        <Stat k="Fill ratio" v={(s.fillRatio * 100).toFixed(1) + '%'} s={`${num(s.setBits)} bits set, ${s.insertions} IDs`} /><Stat k="Expected FP probability now" v={(s.expectedFppNow * 100).toFixed(4) + '%'} s={`configured for ${(s.targetFpp * 100).toFixed(1)}% at ${s.expectedInsertions} IDs`} />
      </div>
      <div className="card">
        <div className="card-head"><div><h2>Live bit array</h2><p>Green cells are 1-bits set by the registered batch IDs. Orange outlines mark the k positions probed by the ID you check below.</p></div><Button size="sm" onClick={reload}>Refresh</Button></div>
        <BitGrid bits={s.setBitIndexes} size={s.bitSize} highlight={check?.positions || []} />
      </div>
      <div className="grid g2">
        <div className="card">
          <h2 style={{ marginBottom: 6 }}>Membership query</h2>
          <p className="muted" style={{ marginBottom: 12 }}>Read-only probe: nothing is inserted or counted. A "possibly present" answer is always verified exactly.</p>
          <form onSubmit={doCheck} className="row" style={{ alignItems: 'flex-end' }}><Field label="Batch ID"><input value={id} onChange={(e) => setId(e.target.value)} /></Field><Button variant="primary">Check</Button></form>
          <div style={{ marginTop: 12 }}><ErrorBox error={checkErr} />
            {check && <div className={'notice ' + (check.outcome === 'NEW' ? 'ok' : check.outcome === 'DUPLICATE' ? 'err' : 'warn')}>
              <b>{check.outcome === 'NEW' ? 'Definitely not present' : check.outcome === 'DUPLICATE' ? 'Duplicate confirmed' : 'False positive'}</b> &mdash; {check.explanation}
              <div className="mono" style={{ marginTop: 6 }}>bit positions: {check.positions.join(', ')}</div></div>}</div>
        </div>
        <div className="card">
          <h2 style={{ marginBottom: 10 }}>Screening statistics (this session)</h2>
          <dl className="kv"><dt>IDs screened</dt><dd>{s.screened}</dd><dt>Definitely new</dt><dd>{s.definitelyNew} (exact lookup skipped)</dd><dt>Possibly present</dt><dd>{s.possiblyPresent} &rarr; exact lookup each time</dd>
            <dt>Confirmed duplicates</dt><dd>{s.confirmedDuplicates} (rejected)</dd><dt>False positives</dt><dd>{s.falsePositives} (accepted)</dd><dt>Observed FP rate</dt><dd>{(s.observedFpRate * 100).toFixed(2)}%</dd></dl>
          {s.recent.length > 0 && <div style={{ marginTop: 12 }}>{s.recent.slice(0, 6).map((r, i) => <div key={i} className="row muted" style={{ gap: 8 }}><Badge tone={outcomeTone[r.outcome]}>{r.outcome.replace(/_/g, ' ').toLowerCase()}</Badge><span className="mono">{r.id}</span></div>)}</div>}
        </div>
      </div>
      <div className="card flush scroll">
        <div style={{ padding: '18px 20px 0' }}><h2>Inserted IDs and their bit positions</h2></div>
        <table style={{ marginTop: 10 }}><thead><tr><th>Batch ID</th><th>Bit positions (k = {s.hashCount})</th></tr></thead><tbody>{s.items.map((it) => <tr key={it.id}><td><b>{it.id}</b></td><td className="mono">{it.positions.join(', ')}</td></tr>)}</tbody></table>
      </div>
      <div className="card">
        <h2 style={{ marginBottom: 6 }}>False-positive sandbox</h2>
        <p className="muted" style={{ marginBottom: 12 }}>Builds a separate filter, inserts known IDs, then queries IDs that were never inserted. Every "possibly present" answer for those is a genuine false positive. Shrink the filter or insert more than expected to watch the rate climb.</p>
        <form onSubmit={doDemo}>
          <div className="grid g4"><Field label="Expected insertions"><input type="number" value={demo.expectedInsertions} onChange={dset('expectedInsertions')} /></Field><Field label="Target FP rate (0-1)"><input value={demo.falsePositiveRate} onChange={dset('falsePositiveRate')} /></Field>
            <Field label="IDs inserted"><input type="number" value={demo.inserts} onChange={dset('inserts')} /></Field><Field label="Never-inserted queries"><input type="number" value={demo.queries} onChange={dset('queries')} /></Field></div>
          <div className="row" style={{ marginTop: 12 }}><Button variant="primary" busy={busy}>Run experiment</Button></div>
        </form>
        <div style={{ marginTop: 12 }}><ErrorBox error={demoErr} /></div>
        {demoRes && (
          <div className="grid g2" style={{ marginTop: 12 }}>
            <dl className="kv"><dt>Filter</dt><dd>{demoRes.bitSize} bits, {demoRes.hashCount} hashes, {bytes(demoRes.memoryBytes)}</dd><dt>False positives</dt><dd>{demoRes.falsePositives} of {demoRes.queries} queries</dd><dt>Observed rate</dt><dd>{(demoRes.observedFpRate * 100).toFixed(2)}%</dd><dt>Theoretical rate</dt><dd>{(demoRes.theoreticalFpRate * 100).toFixed(2)}%</dd><dt>Examples</dt><dd className="mono">{demoRes.sampleFalsePositives.join(', ') || 'none'}</dd></dl>
            {demoRes.setBitIndexes.length > 0 && <BitGrid bits={demoRes.setBitIndexes} size={demoRes.bitSize} cols={60} />}
          </div>
        )}
      </div>
    </div>
  );
}
