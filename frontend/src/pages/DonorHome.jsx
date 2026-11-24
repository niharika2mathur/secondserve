import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, clock, minutesText, num } from '../api.js';
import { Badge, Button, ErrorBox, Loading, Stat, useLoad } from '../ui.jsx';

export const BATCH_TONE = { DELIVERED: 'green', IN_TRANSIT: 'blue', PLANNED: 'blue', WAITING: '', NEEDS_ROUTES: 'amber', EXPIRED: 'red' };
export const BATCH_TEXT = { DELIVERED: 'Delivered', IN_TRANSIT: 'In transit', PLANNED: 'Planned', WAITING: 'Waiting', NEEDS_ROUTES: 'Needs routes', EXPIRED: 'Expired' };

export default function DonorHome({ go }) {
  const { data: d, error, loading } = useLoad(() => api('/dashboard/donor'));
  if (loading && !d) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  const i = d.impact;
  const pct = i.nextTier ? Math.min(100, (100 * i.lifetimeMeals) / i.nextTierAt) : 100;
  return (
    <>
      <div className="page-head">
        <div><h1>{d.orgName}</h1><p>Your surplus batches, where they are going, and the difference they make.</p></div>
        <Button variant="primary lg" onClick={() => go('batches')}>Add a surplus batch</Button>
      </div>

      <div className="grid g4" style={{ marginBottom: 16 }}>
        <Stat k="Meals offered (current batches)" v={num(d.mealsOffered)} s={`${d.batches} batch(es)`} />
        <Stat k="On their way" v={num(d.mealsDispatched)} s="assigned to an NGO" tone="good" />
        <Stat k="Delivered" v={num(d.mealsDelivered)} s="confirmed by the NGO" tone="good" />
        <Stat k="Not placed yet" v={num(d.mealsAwaiting)} s={d.mealsExpired ? `${num(d.mealsExpired)} expired` : 'waiting for allocation'} tone={d.mealsAwaiting || d.mealsExpired ? 'warn' : ''} />
      </div>

      {d.alerts.length > 0 && <div className="card" style={{ marginBottom: 16 }}><h2 style={{ marginBottom: 12 }}>Action needed</h2>
        <div className="alerts">{d.alerts.map((x, k) => <div key={k} className={'alert ' + x.level}><div><b>{x.title}</b><span>{x.message}</span></div>{x.route && <Button size="sm" onClick={() => go(x.route)}>Open</Button>}</div>)}</div></div>}

      <div className="grid g2" style={{ marginBottom: 16 }}>
        <div className="card">
          <div className="card-head"><h2>Your impact</h2><span className={'tier ' + i.tier}>{i.tier}</span></div>
          <div className="grid g2" style={{ marginBottom: 12 }}>
            <Stat k="Meals rescued, all time" v={num(i.lifetimeMeals)} s={`about ${num(Math.round(i.kg))} kg of food (0.4 kg per pack assumed)`} tone="good" />
            <Stat k="Your rank" v={`#${i.rank}`} s={`of ${i.donorCount} donors`} />
          </div>
          {i.nextTier ? <><div className="bar" style={{ height: 10 }}><i style={{ width: pct + '%' }} /></div>
            <p className="muted" style={{ marginTop: 6 }}>{num(i.mealsToNextTier)} more meals to reach <b>{i.nextTier}</b>.</p></> : <p className="muted">Top tier reached. Thank you.</p>}
          <p className="muted" style={{ marginTop: 8 }}>NGO rating of your hand-overs: {i.ratingCount ? <b>{i.avgRating.toFixed(1)} / 5</b> : 'none yet'} ({i.ratingCount} rating(s), including seeded history).</p>
        </div>
        <div className="card">
          <div className="card-head"><div><h2>Your meals per week</h2><p>Last 8 weeks</p></div></div>
          <ResponsiveContainer width="100%" height={210}>
            <BarChart data={d.history}><CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey="label" tick={{ fontSize: 11 }} /><YAxis tick={{ fontSize: 12 }} width={40} /><Tooltip /><Bar isAnimationActive={false} dataKey="meals" name="Meals" fill="#1f6b48" radius={[4, 4, 0, 0]} /></BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="card flush scroll">
        <div style={{ padding: '18px 20px 0' }}><h2>Your batches</h2></div>
        <table style={{ marginTop: 10 }}>
          <thead><tr><th>Batch</th><th className="num">Meals</th><th>Deadline</th><th>Time left</th><th>Status</th></tr></thead>
          <tbody>{d.myBatches.length === 0 && <tr><td colSpan="5" className="muted">No batches yet. Add one so the coordinator can place it.</td></tr>}
            {d.myBatches.map((b) => (
              <tr key={b.batchId}><td><b>{b.batchId}</b><div className="muted">{b.notes}</div></td><td className="num">{b.meals}</td><td>{clock(b.safeUntil)}</td>
                <td>{b.status === 'DELIVERED' ? '-' : <Badge tone={b.minutesLeft <= 0 ? 'red' : b.minutesLeft <= 90 ? 'amber' : ''}>{minutesText(b.minutesLeft)}</Badge>}</td>
                <td><Badge tone={BATCH_TONE[b.status]}>{BATCH_TEXT[b.status]}</Badge><div className="muted" style={{ maxWidth: 360, marginTop: 4 }}>{b.hint}</div></td></tr>))}</tbody>
        </table>
      </div>
    </>
  );
}
