import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, bytes, num } from '../api.js';
import { Badge, ErrorBox, Loading, Stat, useLoad } from '../ui.jsx';

const Table = ({ title, rows }) => (
  <div className="card flush scroll">
    <div style={{ padding: '18px 20px 0' }}><h2>{title}</h2></div>
    <table style={{ marginTop: 10 }}>
      <thead><tr><th>#</th><th>Name</th><th className="num">Sketch estimate</th><th className="num">Exact</th><th className="num">Over-estimate</th></tr></thead>
      <tbody>{rows.map((h, i) => <tr key={h.name}><td>{i + 1}</td><td>{h.name}</td><td className="num">{num(h.estimated)}</td><td className="num">{num(h.exact)}</td><td className="num">{h.overestimate}</td></tr>)}</tbody>
    </table>
  </div>
);

export default function Analytics() {
  const { data: a, error, loading } = useLoad(() => api('/analytics'));
  if (loading && !a) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  const s = a.sketch;
  return (
    <>
      <div className="page-head"><div><h1>Impact analytics</h1><p>Totals over a ledger of completed deliveries. Rankings come from a streaming Count-Min Sketch and are checked against exact counts.</p></div></div>
      <div className="notice info" style={{ marginBottom: 16 }}>The ledger holds {a.historyDays} days of <b>seeded synthetic history</b> (for demonstration) plus every delivery an NGO has confirmed in this session ({num(a.liveMeals)} meals so far). The kg figure assumes 0.4 kg per meal pack; it is an assumption, not a measurement.</div>
      <div className="grid g4" style={{ marginBottom: 16 }}>
        <Stat k="Meals rescued" v={num(a.totalMeals)} s={`about ${num(Math.round(a.kg))} kg`} tone="good" />
        <Stat k="Deliveries" v={num(a.deliveries)} s={`${a.activeDonors} donors, ${a.activeNgos} NGOs`} />
        <Stat k="Average rating" v={a.ratingCount ? a.avgRating.toFixed(2) + ' / 5' : '-'} s={`${num(a.ratingCount)} rated`} />
        <Stat k="Live this session" v={num(a.liveMeals)} s="meals confirmed by NGOs" />
      </div>
      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-head"><h2>Meals per week</h2></div>
        <ResponsiveContainer width="100%" height={260}><BarChart data={a.weekly}><CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey="label" tick={{ fontSize: 12 }} /><YAxis tick={{ fontSize: 12 }} width={50} /><Tooltip /><Bar isAnimationActive={false} dataKey="meals" name="Meals" fill="#1f6b48" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer>
      </div>
      <div className="grid g2" style={{ marginBottom: 16 }}><Table title="Top donors (heavy hitters)" rows={a.topDonors} /><Table title="Top NGOs (heavy hitters)" rows={a.topNgos} /></div>
      <div className="card">
        <div className="card-head"><div><h2>About the Count-Min Sketch</h2><p>Randomized algorithm, Unit 6</p></div>{s.donorTopMatchesExact && s.ngoTopMatchesExact ? <Badge tone="green">top 5 identical to exact counts</Badge> : <Badge tone="amber">top 5 differs from exact</Badge>}</div>
        <dl className="kv">
          <dt>Parameters</dt><dd>epsilon {s.epsilon}, delta {s.delta} (width {s.width} x depth {s.depth})</dd>
          <dt>Guarantee</dt><dd>never under-estimates; over-estimates by at most {num(Math.round(s.errorBound))} meals (epsilon x total) with probability at least {(100 * (1 - s.delta)).toFixed(0)}%</dd>
          <dt>Events processed</dt><dd>{num(s.events)} deliveries, {s.distinctDonors} distinct donors, {s.distinctNgos} NGOs</dd>
          <dt>Memory</dt><dd>sketch {bytes(s.sketchBytes)} per field vs exact map about {bytes(s.exactMapBytesApprox)}</dd>
        </dl>
        <p className="muted" style={{ marginTop: 10 }}>{s.note}</p>
      </div>
    </>
  );
}
