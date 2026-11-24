import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, num } from '../api.js';
import { Badge, Button, ErrorBox, Loading, Stat, useLoad, useToast } from '../ui.jsx';

const STEP = [['DISPATCHED', 'Dispatched'], ['PICKED_UP', 'Picked up'], ['DELIVERED', 'Delivered'], ['FAILED', 'Failed']];

export default function AdminHome({ go }) {
  const { data: d, error, loading, reload } = useLoad(() => api('/dashboard/admin'));
  const ops = useLoad(() => api('/dashboard'));
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const notify = useToast();
  if (loading && !d) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  const a = d.analytics;

  const quickRun = async () => {
    setBusy(true); setErr(null);
    try {
      const r = await api('/allocation/run', { method: 'POST', body: { planningTime: ops.data.suggestedPlanningTime, strategy: 'MIN_COST', vehicleCapacity: 100 } });
      notify(`Smart allocation placed ${r.summary.allocated} meals using ${r.schedule.volunteersNeeded} volunteer(s)`); go('results');
    } catch (e) { setErr(e); reload(); } finally { setBusy(false); }
  };

  return (
    <>
      <div className="page-head">
        <div><h1>Coordinator dashboard</h1><p>The whole network at a glance: what needs action now, where deliveries stand, and the impact so far.</p></div>
        <Button variant="primary lg" busy={busy} disabled={!ops.data} onClick={quickRun}>Run smart allocation</Button>
      </div>
      <ErrorBox error={err} title="Allocation could not run" />

      <div className="grid g4" style={{ marginBottom: 16 }}>
        <Stat k="Meals delivered (confirmed)" v={num(d.deliveredMeals)} s={`${d.deliveredCount} deliveries confirmed by NGOs`} tone="good" />
        <Stat k="Deliveries in flight" v={d.deliveriesInFlight} s="dispatched or picked up" tone={d.deliveriesInFlight ? 'warn' : ''} />
        <Stat k="Volunteers needed (current plan)" v={d.volunteersNeeded} s="from interval partitioning" />
        <Stat k="Registrations awaiting approval" v={d.pendingApprovals} s={`${d.activeUsers} active accounts`} tone={d.pendingApprovals ? 'warn' : ''} />
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-head"><h2>Needs your attention</h2>{d.planReady && <Button variant="primary" onClick={() => go('results')}>Review &amp; publish plan</Button>}</div>
        {d.alerts.length === 0 ? <div className="notice ok">Nothing urgent. All batches are placed or have no deadline pressure.</div> : (
          <div className="alerts">{d.alerts.map((x, i) => (
            <div key={i} className={'alert ' + x.level}><div><b>{x.title}</b><span>{x.message}</span></div>{x.route && <Button size="sm" onClick={() => go(x.route)}>Open</Button>}</div>))}</div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-head"><h2>Delivery pipeline</h2><Button size="sm" onClick={() => go('deliveries')}>All deliveries</Button></div>
        <div className="pipeline">{STEP.map(([k, l]) => <div key={k}><div className="n">{d.deliveryStatus[k] || 0}</div><div className="l">{l}</div></div>)}</div>
      </div>

      <div className="grid g2">
        <div className="card">
          <div className="card-head"><div><h2>Meals rescued per week</h2><p>{num(a.totalMeals)} meals in total, about {num(Math.round(a.kg))} kg (assuming 0.4 kg per pack)</p></div></div>
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={a.weekly}><CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey="label" tick={{ fontSize: 11 }} /><YAxis tick={{ fontSize: 12 }} width={44} /><Tooltip /><Bar isAnimationActive={false} dataKey="meals" name="Meals" fill="#1f6b48" radius={[4, 4, 0, 0]} /></BarChart>
          </ResponsiveContainer>
          <p className="muted" style={{ marginTop: 6 }}>Includes {a.historyDays} days of seeded synthetic history plus deliveries confirmed in this session ({num(a.liveMeals)} meals).</p>
        </div>
        <div className="card">
          <div className="card-head"><div><h2>Top donors and NGOs</h2><p>Found with a Count-Min Sketch heavy-hitter stream</p></div><Button size="sm" onClick={() => go('analytics')}>Details</Button></div>
          <h3 style={{ marginBottom: 6 }}>Donors</h3>
          <table><tbody>{a.topDonors.map((h, i) => <tr key={h.name}><td>{i + 1}. {h.name}</td><td className="num">{num(h.estimated)} <span className="muted">est.</span></td></tr>)}</tbody></table>
          <h3 style={{ margin: '14px 0 6px' }}>NGOs</h3>
          <table><tbody>{a.topNgos.map((h, i) => <tr key={h.name}><td>{i + 1}. {h.name}</td><td className="num">{num(h.estimated)} <span className="muted">est.</span></td></tr>)}</tbody></table>
        </div>
      </div>
    </>
  );
}
