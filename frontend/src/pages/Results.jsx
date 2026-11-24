import { useState } from 'react';
import { Bar as RBar, BarChart, CartesianGrid, Cell, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, fmtTime, ms, num, pct } from '../api.js';
import NetworkGraph, { GraphLegend } from '../NetworkGraph.jsx';
import { Badge, Bar, Button, Empty, ErrorBox, Loading, Stat, statusText, statusTone, useLoad, useToast } from '../ui.jsx';


const PALETTE = ['#1f6b48', '#2b5f8a', '#b4620b', '#7a4aa0', '#b3382c', '#3f7a5e', '#8a6400'];
const tms = (s) => new Date(s.length === 16 ? s + ':00' : s).getTime();
const hhmm = (ms) => new Date(ms).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false });

function Gantt({ schedule }) {
  const trips = schedule.trips;
  if (!trips.length) return <div className="muted">No trips: nothing was allocated.</div>;
  const vols = [...new Set(trips.map((t) => t.volunteer))];
  const ngoIds = [...new Set(trips.map((t) => t.ngoId))];
  const t0 = Math.min(...trips.map((t) => tms(t.departAt))), t1 = Math.max(...trips.map((t) => tms(t.freeAt)));
  const W = 980, L = 52, R = 14, T = 26, rowH = 38, H = T + vols.length * rowH + 8;
  const x = (ms) => L + ((ms - t0) / (t1 - t0 || 1)) * (W - L - R);
  const ticks = []; for (let m = Math.ceil(t0 / 1800000) * 1800000; m <= t1; m += 1800000) ticks.push(m);
  return (
    <div className="gantt"><svg width={W} height={H} role="img" aria-label="Volunteer schedule">
      {ticks.map((m) => <g key={m}><line x1={x(m)} x2={x(m)} y1={T - 6} y2={H - 4} stroke="#e2e6de" /><text x={x(m)} y={14} fontSize="11" textAnchor="middle" fill="#7b887f">{hhmm(m)}</text></g>)}
      {vols.map((v, i) => <text key={v} x={8} y={T + i * rowH + 23} fontSize="13" fontWeight="600" fill="#15211b">{v}</text>)}
      {trips.map((t) => {
        const r = vols.indexOf(t.volunteer), c = PALETTE[ngoIds.indexOf(t.ngoId) % PALETTE.length];
        const a = x(tms(t.departAt)), m = x(tms(t.arriveAt)), e = x(tms(t.freeAt)), y = T + r * rowH + 4;
        return (
          <g key={t.tripId}><title>{`${t.volunteer}: ${t.meals} meals ${t.batchId} -> ${t.ngoName}\nleave ${hhmm(tms(t.departAt))}, arrive ${hhmm(tms(t.arriveAt))}, free ${hhmm(tms(t.freeAt))}`}</title>
            <rect x={a} y={y} width={Math.max(2, m - a)} height={28} rx="6" fill={c} /><rect x={m} y={y} width={Math.max(2, e - m)} height={28} rx="6" fill={c} opacity="0.3" />
            {m - a > 54 && <text x={a + 6} y={y + 18} fontSize="11.5" fill="#fff" fontWeight="600">{t.meals} &rarr; {t.ngoId}</text>}</g>);
      })}
    </svg>
      <div className="legend" style={{ padding: '8px 12px' }}>{ngoIds.map((n, i) => <span key={n}><i style={{ borderColor: PALETTE[i % PALETTE.length] }} />{n}</span>)}<span>solid = driving to the NGO, faded = return + hand-over</span></div>
    </div>
  );
}

const SK = { FCFS: 'fcfs', MAX_FLOW: 'maxFlow', MIN_COST: 'minCost', FAIR: 'fair' };

export default function Results({ go }) {
  const { data: r, error, loading, reload } = useLoad(() => api('/allocation/results'));
  const [pub, setPub] = useState(false);
  const notify = useToast();
  const publish = async () => {
    if (!window.confirm('Publish this allocation as a dispatch plan? Donors and NGOs are notified and the meals are reserved.')) return;
    setPub(true);
    try { const c = await api('/dispatch/commit', { method: 'POST', body: {} }); notify(`Plan published: ${c.deliveries} deliveries, ${c.meals} meals, ${c.volunteers} volunteer(s)`); reload(); }
    catch (e) { notify(e.message, 'err'); } finally { setPub(false); }
  };
  if (loading && !r) return <Loading />;
  if (error?.status === 404 || (!r && error)) {
    return (<><div className="page-head"><h1>Allocation results</h1></div><div className="card"><Empty title="No allocation has been run yet">Results appear here after you run the allocation.<div style={{ marginTop: 14 }}><Button variant="primary" onClick={() => go('allocation')}>Go to Allocation Engine</Button></div></Empty></div></>);
  }
  const s = r.summary, b = r.baseline;
  const cmp = [{ name: 'Edmonds-Karp', meals: s.allocated }, { name: 'FCFS greedy', meals: b.allocated }];
  return (
    <>
      <div className="page-head"><div><h1>Allocation results</h1><p>Computed by the Java backend for planning time {fmtTime(r.planningTime)} (run #{r.runId}, {fmtTime(r.executedAt)}).</p></div></div>
      {r.stale && <div className="notice warn" style={{ marginBottom: 16 }}>Records changed after this run, so these results may be out of date.</div>}
      {r.committed ? (
        <div className="notice ok row" style={{ marginBottom: 16, justifyContent: 'space-between' }}><span><b>Published.</b> Run #{r.runId} is the active dispatch plan; donors and NGOs have been notified. The next allocation only plans meals that are still uncommitted.</span><Button size="sm" onClick={() => go('deliveries')}>Track deliveries</Button></div>
      ) : (
        <div className="card row" style={{ marginBottom: 16, justifyContent: 'space-between' }}>
          <div><b>Ready to dispatch?</b><div className="muted">Publishing turns these {num(s.allocated)} meals into {r.rows.filter((x) => x.status === 'ALLOCATED').length} deliveries with volunteers assigned.</div></div>
          <Button variant="primary lg" busy={pub} disabled={r.stale || s.allocated === 0} onClick={publish}>Publish dispatch plan</Button>
        </div>)}
      <div className="grid g4" style={{ marginBottom: 16 }}>
        <Stat k="Meals redistributed" v={num(s.allocated)} s={`of ${num(s.usableSupply)} usable supply`} tone="good" />
        <Stat k="Remaining donor surplus" v={num(s.remainingSurplus)} s="includes expired batches" tone={s.remainingSurplus ? 'warn' : ''} />
        <Stat k="Remaining NGO demand" v={num(s.remainingDemand)} s={`of ${num(s.totalDemand)} requested`} tone={s.remainingDemand ? 'warn' : ''} />
        <Stat k="Allocation efficiency" v={pct(s.efficiency)} s="allocated / min(usable supply, collectable demand)" />
      </div>

      <div className="card flush scroll" style={{ marginBottom: 16 }}>
        <div style={{ padding: '18px 20px 0' }}><h2>Allocation methods compared</h2><p className="muted">{r.strategyNote}</p></div>
        <table style={{ marginTop: 10 }}>
          <thead><tr><th>Method</th><th className="num">Meals</th><th className="num">Avg travel</th><th className="num">Tight-margin meals</th><th className="num">Leftover freshness</th><th className="num">Smallest NGO share</th><th className="num">NGOs served</th><th className="num">Equity (Jain)</th><th className="num">Time</th></tr></thead>
          <tbody>{r.comparison.map((c) => (
            <tr key={c.key} className={c.key === r.strategy ? 'hl' : ''}><td><b>{c.name}</b>{c.key === r.strategy && <> <Badge tone="green">in use</Badge></>}<div className="muted" style={{ maxWidth: 300 }}>{c.note}</div></td>
              <td className="num">{num(c.allocated)}</td><td className="num">{c.avgTravelMinutes.toFixed(1)} min</td><td className="num">{c.tightMeals}</td>
              <td className="num">{c.avgLeftoverMinutesToExpiry == null ? '-' : Math.round(c.avgLeftoverMinutesToExpiry) + ' min'}</td><td className="num">{c.minReceipt}</td><td className="num">{c.ngosServed}/{c.reachableNgos}</td><td className="num">{c.jain.toFixed(3)}</td><td className="num">{ms(c.millis)}</td></tr>))}</tbody>
        </table>
        <p className="muted" style={{ padding: '4px 20px 16px' }}>Tight-margin = delivered with under 30 min before the deadline. Leftover freshness = average minutes to expiry of the meals not placed (higher means they can still be used next round). Equity = Jain index of each reachable NGO's fulfilment %; 1.0 is perfectly even.</p>
      </div>

      <div className="grid g2" style={{ marginBottom: 16 }}>
        <div className="card">
          <div className="card-head"><div><h2>Who receives what, by method</h2><p>Meals per NGO under each method</p></div></div>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={r.perNgo}><CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey="ngoId" tick={{ fontSize: 12 }} /><YAxis tick={{ fontSize: 12 }} width={40} /><Tooltip /><Legend />
              <RBar isAnimationActive={false} dataKey="fcfs" name="FCFS" fill="#c7cdc2" /><RBar isAnimationActive={false} dataKey="maxFlow" name="Max flow" fill="#8fb3a0" /><RBar isAnimationActive={false} dataKey="minCost" name="Smart" fill="#1f6b48" /><RBar isAnimationActive={false} dataKey="fair" name="Fair" fill="#2b5f8a" /></BarChart>
          </ResponsiveContainer>
        </div>
        <div className="card">
          <div className="card-head"><div><h2>Volunteers needed</h2><p>{r.schedule.note}</p></div><Badge tone="green">{r.schedule.volunteersNeeded} volunteer(s)</Badge></div>
          <dl className="kv"><dt>Trips</dt><dd>{r.schedule.tripCount} (max {r.schedule.vehicleCapacity} meals each)</dd><dt>Peak simultaneous trips</dt><dd>{r.schedule.peakConcurrent}</dd><dt>Optimal</dt><dd>{r.schedule.optimal ? 'yes: volunteers = peak overlap (lower bound)' : 'no'}</dd></dl>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-head"><div><h2>Volunteer schedule</h2><p>One row per volunteer; hover a bar for details. Hand-over buffer {r.schedule.handoverMinutes} min per trip.</p></div></div>
        <Gantt schedule={r.schedule} />
      </div>

      <div className="card flush scroll" style={{ marginBottom: 16 }}>
        <div style={{ padding: '18px 20px 0' }}><h2>Allocation table</h2></div>
        <table style={{ marginTop: 10 }}>
          <thead><tr><th>Donation batch</th><th>Donor</th><th>NGO</th><th className="num">Allocated meals</th><th>Status</th><th>Reason</th></tr></thead>
          <tbody>{r.rows.map((x, i) => (
            <tr key={i}><td><b>{x.batchId}</b></td><td>{x.donor}</td><td>{x.ngoId === '-' ? '-' : `${x.ngoId} - ${x.ngoName}`}</td><td className="num">{num(x.meals)}</td>
              <td><Badge tone={statusTone(x.status)}>{statusText(x.status)}</Badge></td><td className="muted" style={{ maxWidth: 380 }}>{x.reason}</td></tr>))}</tbody>
        </table>
      </div>

      <div className="grid g2" style={{ marginBottom: 16 }}>
        <div className="card flush scroll">
          <div style={{ padding: '18px 20px 0' }}><h2>NGO outcomes and pickup utilisation</h2></div>
          <table style={{ marginTop: 10 }}>
            <thead><tr><th>NGO</th><th className="num">Received</th><th>Pickup use</th><th className="num">Unmet</th><th>Status</th></tr></thead>
            <tbody>{r.ngos.map((n) => (
              <tr key={n.ngoId}><td><b>{n.ngoId}</b><div className="muted">{n.name}</div></td><td className="num">{n.allocated} / {n.requiredMeals}</td>
                <td style={{ minWidth: 110 }}><Bar value={n.allocated} max={n.pickupCapacity} /><span className="muted">{pct(n.pickupUtilisation)} of {n.pickupCapacity}</span></td>
                <td className="num">{n.unmet}</td>
                <td><Badge tone={statusTone(n.status)}>{statusText(n.status)}</Badge>{n.reason && <div className="muted" style={{ marginTop: 4, maxWidth: 260 }}>{n.reason}</div>}</td></tr>))}</tbody>
          </table>
        </div>
        <div className="card flush scroll">
          <div style={{ padding: '18px 20px 0' }}><h2>Donor batch outcomes</h2></div>
          <table style={{ marginTop: 10 }}>
            <thead><tr><th>Batch</th><th className="num">Placed</th><th className="num">Left</th><th>Status</th></tr></thead>
            <tbody>{r.batches.map((x) => (
              <tr key={x.batchId}><td><b>{x.batchId}</b><div className="muted">{x.donor}</div></td><td className="num">{x.allocated} / {x.supply}</td><td className="num">{x.remaining}</td>
                <td><Badge tone={statusTone(x.status)}>{statusText(x.status)}</Badge>{x.reason && <div className="muted" style={{ marginTop: 4, maxWidth: 280 }}>{x.reason}</div>}</td></tr>))}</tbody>
          </table>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="card-head"><div><h2>Bottleneck (minimum cut)</h2><p>{r.bottleneck.explanation}</p></div><Badge tone="amber">cut capacity {num(r.bottleneck.cutCapacity)}</Badge></div>
        {r.bottleneck.edges.length === 0 ? <div className="muted">No saturated edges in the cut.</div> : (
          <ul style={{ margin: 0, paddingLeft: 20, display: 'grid', gap: 4 }}>{r.bottleneck.edges.map((e) => <li key={e.id}><Badge tone={e.kind === 'SUPPLY' ? 'green' : 'blue'}>{e.kind === 'SUPPLY' ? 'Supply used up' : 'NGO limit reached'}</Badge> {e.label}</li>)}</ul>
        )}
        <div style={{ marginTop: 16 }}><NetworkGraph nodes={r.graph.nodes} edges={r.graph.edges} showUnavailable={false} /></div>
        <div style={{ marginTop: 10 }}><GraphLegend /></div>
      </div>

      <div className="card">
        <div className="card-head"><div><h2>Baseline comparison: maximum flow vs first-come-first-served</h2><p>Same data, same feasibility rules, same capacities.</p></div></div>
        <div className="grid g2">
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={cmp} layout="vertical" margin={{ left: 30 }}><CartesianGrid horizontal={false} stroke="#e8ebe4" /><XAxis type="number" tick={{ fontSize: 12 }} /><YAxis dataKey="name" type="category" tick={{ fontSize: 12 }} width={100} /><Tooltip />
              <RBar isAnimationActive={false} dataKey="meals" name="Meals allocated">{cmp.map((c, i) => <Cell key={i} fill={i === 0 ? '#1f6b48' : '#c7cdc2'} />)}</RBar></BarChart>
          </ResponsiveContainer>
          <div>
            <dl className="kv"><dt>Edmonds-Karp</dt><dd>{num(s.allocated)} meals in {ms(s.engineMillis)}</dd><dt>FCFS greedy</dt><dd>{num(b.allocated)} meals</dd><dt>Difference</dt><dd>{b.gapMeals >= 0 ? '+' : ''}{b.gapMeals} meals for max flow</dd></dl>
            <p className="muted" style={{ marginTop: 10 }}>{b.note} Greedy never reconsiders a choice, so an early assignment can use up an NGO that only that batch could reach. Edmonds-Karp can push flow back along a reverse edge to reroute. This does not mean flow is always faster; see Benchmarks.</p>
          </div>
        </div>
      </div>
    </>
  );
}
