import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, clock, num, pct } from '../api.js';
import { Badge, Button, ErrorBox, Loading, Stat, useLoad } from '../ui.jsx';
import { DeliveryBadge } from './Deliveries.jsx';

const STRATS = [['fcfs', 'First-come-first-served'], ['maxFlow', 'Maximum flow'], ['minCost', 'Smart (min-cost)'], ['fair', 'Fair share']];
const KEY = { FCFS: 'fcfs', MAX_FLOW: 'maxFlow', MIN_COST: 'minCost', FAIR: 'fair' };

export default function NgoHome({ go }) {
  const { data: d, error, loading } = useLoad(() => api('/dashboard/ngo'));
  if (loading && !d) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  const p = d.profile, f = d.fairness;
  const fairData = f.hasPlan ? STRATS.map(([k, name]) => ({ name, meals: f[k] })) : [];
  const waiting = d.deliveries.filter((x) => x.status === 'DISPATCHED' || x.status === 'PICKED_UP');
  return (
    <>
      <div className="page-head">
        <div><h1>{p.name}</h1><p>What is coming to you, what you have received, and why food does or does not reach you.</p></div>
        <Button variant="primary lg" onClick={() => go('deliveries')} disabled={!waiting.length}>{waiting.length ? `Confirm ${waiting.length} delivery(ies)` : 'No deliveries to confirm'}</Button>
      </div>

      <div className="grid g4" style={{ marginBottom: 16 }}>
        <Stat k="Incoming now" v={num(d.incomingMeals)} s="on the way to you" tone="good" />
        <Stat k="Received & confirmed" v={num(d.receivedMeals)} s="this session" tone="good" />
        <Stat k="In the latest plan" v={num(d.plannedMeals)} s="not yet published" />
        <Stat k="Need covered" v={pct(d.fulfilmentRate)} s={`of ${num(p.requiredMeals)} requested (collectable ${num(Math.min(p.requiredMeals, p.pickupCapacity))})`} />
      </div>

      {d.tips.length > 0 && <div className="card" style={{ marginBottom: 16 }}><h2 style={{ marginBottom: 10 }}>Suggestions for you</h2><div className="alerts">{d.tips.map((t, i) => <div key={i} className="alert info"><div>{t}</div></div>)}</div></div>}

      <div className="grid g2" style={{ marginBottom: 16 }}>
        <div className="card">
          <div className="card-head"><div><h2>How the allocation method affects you</h2><p>{f.hasPlan ? `Meals ${p.name} would get in the latest run under each method` : 'Appears after the coordinator runs an allocation.'}</p></div>{f.hasPlan && <Badge tone="green">{f.committed ? 'published: ' : 'chosen: '}{f.strategy.replace('_', ' ').toLowerCase()}</Badge>}</div>
          {f.hasPlan ? (
            <ResponsiveContainer width="100%" height={210}>
              <BarChart data={fairData} layout="vertical" margin={{ left: 40 }}><CartesianGrid horizontal={false} stroke="#e8ebe4" /><XAxis type="number" tick={{ fontSize: 12 }} /><YAxis type="category" dataKey="name" tick={{ fontSize: 12 }} width={140} /><Tooltip />
                <Bar isAnimationActive={false} dataKey="meals" name="Meals for you">{fairData.map((x, i) => <Cell key={i} fill={STRATS[i][0] === KEY[f.strategy] ? '#1f6b48' : '#c7cdc2'} />)}</Bar></BarChart>
            </ResponsiveContainer>) : <div className="muted">Nothing to compare yet.</div>}
        </div>
        <div className="card">
          <div className="card-head"><div><h2>Your meals per week</h2><p>Lifetime total {num(d.lifetimeMeals)} meals (includes seeded history)</p></div></div>
          <ResponsiveContainer width="100%" height={210}>
            <BarChart data={d.history}><CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey="label" tick={{ fontSize: 11 }} /><YAxis tick={{ fontSize: 12 }} width={40} /><Tooltip /><Bar isAnimationActive={false} dataKey="meals" name="Meals" fill="#2b5f8a" radius={[4, 4, 0, 0]} /></BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="card flush scroll">
        <div style={{ padding: '18px 20px 0' }}><h2>Which donors can reach you?</h2><p className="muted">Feasibility right now: arrival must be before the batch deadline and within your {p.maxTravelMinutes} minute travel limit.</p></div>
        <table style={{ marginTop: 10 }}>
          <thead><tr><th>Batch</th><th>Donor</th><th className="num">Meals</th><th className="num">Travel</th><th>Reachable</th><th>Why</th></tr></thead>
          <tbody>{d.reach.map((r) => <tr key={r.batchId}><td><b>{r.batchId}</b></td><td>{r.donor}</td><td className="num">{r.meals}</td><td className="num">{r.travelMinutes ?? '-'}{r.travelMinutes != null && ' min'}</td>
            <td>{r.feasible ? <Badge tone="green">yes</Badge> : <Badge tone="red">no</Badge>}</td><td className="muted" style={{ maxWidth: 380 }}>{r.reason}</td></tr>)}</tbody>
        </table>
      </div>
    </>
  );
}
