import { useEffect, useState } from 'react';
import { api, num } from '../api.js';
import NetworkGraph, { GraphLegend } from '../NetworkGraph.jsx';
import { Badge, Button, ErrorBox, Field, Loading, Stat, useLoad, useToast } from '../ui.jsx';

export default function Allocation({ go }) {
  const dash = useLoad(() => api('/dashboard'));
  const [planning, setPlanning] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const [result, setResult] = useState(null);
  const [showUn, setShowUn] = useState(true);
  const [strategy, setStrategy] = useState('MIN_COST');
  const [vehicle, setVehicle] = useState(100);
  const notify = useToast();
  useEffect(() => { if (dash.data && !planning) setPlanning(dash.data.suggestedPlanningTime.slice(0, 16)); }, [dash.data]); // eslint-disable-line
  const feas = useLoad(() => (planning ? api('/feasibility?planningTime=' + encodeURIComponent(planning)) : Promise.resolve(null)), [planning]);
  useEffect(() => { api('/allocation/results').then(setResult).catch(() => {}); }, []);

  const run = async () => {
    setBusy(true); setErr(null);
    try { const r = await api('/allocation/run', { method: 'POST', body: { planningTime: planning, strategy, vehicleCapacity: Number(vehicle) } }); setResult(r); notify(`${r.summary.allocated} meal packs allocated (${r.strategy.replace('_', ' ').toLowerCase()}), ${r.schedule.volunteersNeeded} volunteer(s) needed`); dash.reload(); }
    catch (e) { setErr(e); setResult(null); } finally { setBusy(false); }
  };
  const reset = async (mode) => {
    if (mode === 'empty' && !window.confirm('Remove ALL donations, NGOs and travel times? (Use "Restore sample data" afterwards.)')) return;
    try { await api('/data/reset', { method: 'POST', body: { mode } }); setResult(null); setErr(null); notify(mode === 'empty' ? 'All data cleared' : 'Sample data restored'); dash.reload(); feas.reload(); }
    catch (e) { notify(e.message, 'err'); }
  };

  const d = dash.data, v = result?.validation, s = result?.summary;
  return (
    <>
      <div className="page-head">
        <div><h1>Allocation engine</h1><p>Builds the network Source &rarr; donor batches &rarr; NGOs &rarr; Sink and runs the Java Edmonds-Karp implementation. Maximum flow decides HOW MANY meals can be placed; the method you choose below decides WHICH meals go where.</p></div>
      </div>
      <div className="card">
        <div className="row" style={{ alignItems: 'flex-end', justifyContent: 'space-between' }}>
          <div className="row" style={{ alignItems: 'flex-end', gap: 20 }}>
            <Field label="Planning date and time"><input type="datetime-local" value={planning} onChange={(e) => setPlanning(e.target.value)} /></Field>
            {d && <><Stat k="Donation batches" v={d.batches} /><Stat k="NGOs" v={d.ngos} /><Stat k="Feasible routes" v={feas.data ? `${feas.data.feasibleRoutes}/${feas.data.totalRoutes}` : '-'} /></>}
          </div>
          <Button variant="primary lg" busy={busy} onClick={run} disabled={!planning}>Run Allocation</Button>
        </div>
      </div>
      <div className="card" style={{ marginTop: 16 }}>
        <div className="card-head"><div><h2>Allocation method</h2><p>All three place the same number of meals (the maximum flow); they differ in who gets them.</p></div>
          <Field label="Meals per volunteer trip"><input type="number" min="10" step="10" style={{ width: 110 }} value={vehicle} onChange={(e) => setVehicle(e.target.value)} /></Field></div>
        <div className="strategy">{[['MAX_FLOW', 'Maximum flow', 'Edmonds-Karp. The most meals placed; ties are broken by augmenting-path order.'], ['MIN_COST', 'Smart (recommended)', 'Min-cost max-flow. Same meals, least travel, safest delivery margins, urgent batches used first.'], ['FAIR', 'Fair share', 'Max-min fairness by progressive filling: lifts the smallest NGO share as high as possible.']].map(([k, t, d]) => (
          <label key={k} className={strategy === k ? 'on' : ''}><span><input type="radio" name="strategy" checked={strategy === k} onChange={() => setStrategy(k)} /><b>{t}</b></span><small>{d}</small></label>))}</div>
      </div>
      <div style={{ marginTop: 16 }}><ErrorBox error={err} title="Allocation could not run" /></div>

      {result && (
        <div className="stack" style={{ marginTop: 16 }}>
          {result.stale && <div className="notice warn">Records changed after this run; run the allocation again to refresh.</div>}
          <div className="card">
            <div className="card-head"><h2>Pipeline</h2><Badge tone="green">{result.algorithm}</Badge></div>
            <ol style={{ margin: 0, paddingLeft: 20, display: 'grid', gap: 4 }}>
              <li>Validated <b>{v.donations}</b> batches and <b>{v.ngos}</b> NGOs.</li>
              <li>Screened {v.idsScreened} batch IDs with a Bloom filter + exact check: <b>{v.duplicateIdsFound}</b> duplicates{v.bloomFalsePositivesInCheck ? `, ${v.bloomFalsePositivesInCheck} false positive(s) correctly accepted` : ''}.</li>
              <li>Removed <b>{v.expiredBatches}</b> expired batch(es); <b>{v.feasibleConnections}</b> of {v.totalConnections} donor-NGO routes are feasible.</li>
              <li>Built the flow network and ran Edmonds-Karp: {s.augmentations} augmenting paths, {s.bfsRuns} BFS passes, {s.engineMillis.toFixed(2)} ms.</li>
              <li>Maximum flow <b>{num(s.maxFlow)}</b> = minimum cut capacity <b>{num(s.minCutCapacity)}</b>.</li>
              <li>Computed all four methods on the same network and applied <b>{result.strategy.replace('_', ' ').toLowerCase()}</b>: {result.strategyNote}</li>
              <li>Scheduled the trips with greedy interval partitioning: <b>{result.schedule.volunteersNeeded}</b> volunteer(s), {result.schedule.tripCount} trip(s).</li>
            </ol>
          </div>
          <div className="card">
            <div className="card-head"><div><h2>Flow network</h2><p>Edge labels show assigned flow / capacity. Middle edges have no capacity of their own (the limits are supply and NGO intake).</p></div>
              <label className="row muted"><input type="checkbox" checked={showUn} onChange={(e) => setShowUn(e.target.checked)} /> show unavailable routes</label></div>
            <NetworkGraph nodes={result.graph.nodes} edges={result.graph.edges} showUnavailable={showUn} />
            <div style={{ marginTop: 12 }}><GraphLegend /></div>
          </div>
          <div className="row"><Button variant="primary" onClick={() => go('results')}>View allocation results</Button><Button onClick={() => go('lab')}>See the algorithm step by step</Button></div>
        </div>
      )}
      {!result && !err && !busy && <div className="card" style={{ marginTop: 16 }}><div className="empty"><b>No allocation yet</b>Pick a planning time and press Run Allocation.</div></div>}

      <div className="card" style={{ marginTop: 16 }}>
        <div className="card-head"><div><h2>Demo data</h2><p>Reset to the realistic synthetic dataset, or clear everything to demonstrate validation of an empty dataset.</p></div>
          <div className="row"><Button onClick={() => reset('sample')}>Restore sample data</Button><Button variant="danger" onClick={() => reset('empty')}>Clear all data</Button></div></div>
      </div>
    </>
  );
}
