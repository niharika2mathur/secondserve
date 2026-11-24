import { useEffect, useMemo, useState } from 'react';
import { api, fmtTime } from '../api.js';
import { Badge, Button, Empty, ErrorBox, Field, Loading, useLoad, useToast } from '../ui.jsx';

export default function Travel() {
  const dash = useLoad(() => api('/dashboard'));
  const [planning, setPlanning] = useState('');
  const [draft, setDraft] = useState({});
  const [busy, setBusy] = useState(false);
  const [saveErr, setSaveErr] = useState(null);
  const notify = useToast();
  useEffect(() => { if (dash.data && !planning) setPlanning(dash.data.suggestedPlanningTime.slice(0, 16)); }, [dash.data]); // eslint-disable-line
  const travel = useLoad(() => api('/travel'));
  const feas = useLoad(() => (planning ? api('/feasibility?planningTime=' + encodeURIComponent(planning)) : Promise.resolve(null)), [planning]);

  const pairs = useMemo(() => { const m = {}; feas.data?.pairs.forEach((p) => { m[p.donationId + '|' + p.ngoId] = p; }); return m; }, [feas.data]);
  const dirty = Object.keys(draft).length;

  const save = async () => {
    setBusy(true); setSaveErr(null);
    try {
      const entries = Object.entries(draft).map(([k, v]) => { const [donationId, ngoId] = k.split('|'); return { donationId, ngoId, minutes: v === '' ? null : v }; });
      await api('/travel', { method: 'PUT', body: { entries } });
      setDraft({}); notify(`Saved ${entries.length} travel time${entries.length === 1 ? '' : 's'}`); travel.reload(); feas.reload();
    } catch (e) { setSaveErr(e); } finally { setBusy(false); }
  };

  const t = travel.data;
  const bad = feas.data?.pairs.filter((p) => !p.feasible && p.code !== 'EXPIRED') || [];
  return (
    <>
      <div className="page-head">
        <div><h1>Travel &amp; feasibility</h1><p>Enter estimated travel minutes for each donor-NGO pair. A route becomes an edge in the flow network only if the batch is still before its deadline, arrival is on time, and travel is within the NGO's limit.</p></div>
        <div className="row"><Field label="Planning date and time"><input type="datetime-local" value={planning} onChange={(e) => setPlanning(e.target.value)} /></Field></div>
      </div>
      <ErrorBox error={travel.error || feas.error} />
      <div className="card">
        <div className="card-head">
          <div><h2>Travel-time matrix (minutes)</h2><p>Leave a cell blank for "no route". Colour shows feasibility at the planning time above.</p></div>
          <div className="row">
            {feas.data && <Badge tone="green">{feas.data.feasibleRoutes} of {feas.data.totalRoutes} routes feasible</Badge>}
            <Button variant="primary" busy={busy} disabled={!dirty} onClick={save}>Save {dirty ? `${dirty} change${dirty > 1 ? 's' : ''}` : 'changes'}</Button>
          </div>
        </div>
        <ErrorBox error={saveErr} title="Travel times were not saved" />
        {!t ? <Loading /> : t.donations.length === 0 || t.ngos.length === 0 ? <Empty title="Nothing to configure yet">Register at least one donation and one NGO first.</Empty> : (
          <div className="scroll"><table className="matrix">
            <thead><tr><th>Donation batch</th>{t.ngos.map((n) => <th key={n.id} title={n.label}>{n.id}<div className="muted" style={{ fontWeight: 400 }}>{n.label.slice(0, 16)}</div></th>)}</tr></thead>
            <tbody>{t.donations.map((d) => (
              <tr key={d.id}><td><b>{d.id}</b><div className="muted">{d.label}</div></td>
                {t.ngos.map((n) => {
                  const k = d.id + '|' + n.id, p = pairs[k];
                  const val = k in draft ? draft[k] : t.minutes[k] ?? '';
                  const cls = k in draft ? '' : !p ? '' : p.feasible ? 'ok' : p.code === 'EXPIRED' ? 'exp' : 'no';
                  return <td key={n.id}><input className={'cell ' + cls} type="number" min="1" value={val} title={p?.reason || ''} aria-label={`Minutes ${d.id} to ${n.id}`}
                    onChange={(e) => { const v = e.target.value; setDraft((x) => { const y = { ...x }; if (v === String(t.minutes[k] ?? '')) delete y[k]; else y[k] = v; return y; }); }} /></td>;
                })}</tr>))}</tbody>
          </table></div>
        )}
        <div className="legend" style={{ marginTop: 12 }}><span><i style={{ borderColor: '#a9cbb7' }} />Feasible</span><span><i style={{ borderColor: '#e8c999' }} />Not feasible (hover cell for the reason)</span><span><i style={{ borderColor: '#d3d8cf' }} />Batch already expired</span></div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <div className="card-head"><h2>Why routes are not feasible</h2><span className="muted">{feas.data?.expiredBatchIds.length ? `Expired at planning time: ${feas.data.expiredBatchIds.join(', ')}` : ''}</span></div>
        {feas.loading && !feas.data ? <Loading /> : bad.length === 0 ? <Empty title="Every non-expired route is feasible" /> : (
          <div className="scroll"><table><thead><tr><th>Batch</th><th>NGO</th><th>Reason</th></tr></thead>
            <tbody>{bad.map((p) => <tr key={p.donationId + p.ngoId}><td>{p.donationId}</td><td>{p.ngoId}</td><td>{p.reason}</td></tr>)}</tbody></table></div>
        )}
        <p className="muted" style={{ marginTop: 10 }}>Safe-delivery deadlines are values entered by the user; this tool makes no claim about actual food safety.</p>
      </div>
    </>
  );
}
