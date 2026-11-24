import { useState } from 'react';
import { api, num } from '../api.js';
import { Badge, Button, ErrorBox, Field, Loading, Stat, useLoad } from '../ui.jsx';

const now = () => { const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000); d.setSeconds(0, 0); return d.toISOString().slice(0, 16); };

export default function Insights() {
  const [planning, setPlanning] = useState(now());
  const rec = useLoad(() => api('/insights/recommendations?planningTime=' + encodeURIComponent(planning)), [planning]);
  const ngos = useLoad(() => api('/ngos'));
  const dons = useLoad(() => api('/donations'));
  const [f, setF] = useState({ ngoId: '', extraPickup: '', extraTravelMinutes: '', batchId: '', extraMeals: '' });
  const [res, setRes] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const run = async (e) => {
    e.preventDefault(); setBusy(true); setErr(null); setRes(null);
    const body = { planningTime: planning };
    Object.entries(f).forEach(([k, v]) => { if (v !== '') body[k] = v; });
    try { setRes(await api('/insights/what-if', { method: 'POST', body })); } catch (x) { setErr(x); } finally { setBusy(false); }
  };

  return (
    <>
      <div className="page-head"><div><h1>What-if insights</h1><p>The minimum cut says <i>where</i> the bottleneck is. These tools say <i>what to change</i>: each scenario edits a copy of the data and re-runs maximum flow, so the gains are exact, not guessed.</p></div>
        <Field label="Planning time"><input type="datetime-local" value={planning} onChange={(e) => setPlanning(e.target.value)} /></Field></div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-head"><h2>Recommended single changes</h2>{rec.data && <Badge tone="green">baseline {num(rec.data.baselineMeals)} meals</Badge>}</div>
        {rec.loading && !rec.data ? <Loading /> : rec.error ? <ErrorBox error={rec.error} /> : (
          <>
            {rec.data.recommendations.length === 0 ? <div className="notice info">{rec.data.note}</div> : (
              <div className="alerts">{rec.data.recommendations.map((r, i) => (
                <div key={i} className="alert info"><div><b>{r.title}</b><span>{r.detail}</span></div><Badge tone="green">+{r.gainMeals} meals</Badge></div>))}</div>)}
            {rec.data.recommendations.length > 0 && <p className="muted" style={{ marginTop: 10 }}>{rec.data.note}</p>}
          </>)}
      </div>

      <div className="card">
        <div className="card-head"><div><h2>Try your own scenario</h2><p>Change one NGO and/or one donor batch, then compare with today's maximum flow.</p></div></div>
        <form onSubmit={run} className="stack">
          <div className="form-grid">
            <Field label="NGO to change"><select value={f.ngoId} onChange={set('ngoId')}><option value="">(none)</option>{ngos.data?.map((n) => <option key={n.ngoId} value={n.ngoId}>{n.ngoId} - {n.name}</option>)}</select></Field>
            <Field label="Extra pickup capacity (meals)"><input type="number" min="0" value={f.extraPickup} onChange={set('extraPickup')} /></Field>
            <Field label="Extra travel time accepted (min)"><input type="number" min="0" value={f.extraTravelMinutes} onChange={set('extraTravelMinutes')} /></Field>
            <div />
            <Field label="Donation batch to change"><select value={f.batchId} onChange={set('batchId')}><option value="">(none)</option>{dons.data?.map((d) => <option key={d.batchId} value={d.batchId}>{d.batchId} - {d.donorName}</option>)}</select></Field>
            <Field label="Extra meals donated"><input type="number" min="0" value={f.extraMeals} onChange={set('extraMeals')} /></Field>
          </div>
          <ErrorBox error={err} />
          <div><Button type="submit" variant="primary" busy={busy}>Run scenario</Button></div>
        </form>
        {res && (
          <div className="grid g3" style={{ marginTop: 16 }}>
            <Stat k="Today" v={num(res.baselineMeals)} s={`${res.baselineRoutes} feasible routes`} />
            <Stat k="With the change" v={num(res.scenarioMeals)} s={`${res.scenarioRoutes} feasible routes`} tone="good" />
            <Stat k="Difference" v={(res.delta >= 0 ? '+' : '') + res.delta} s={res.description} tone={res.delta > 0 ? 'good' : ''} />
          </div>)}
      </div>
    </>
  );
}
