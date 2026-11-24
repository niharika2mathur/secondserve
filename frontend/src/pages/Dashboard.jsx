import { useState } from 'react';
import { Bar as RBar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, fmtTime, num } from '../api.js';
import { Badge, Button, Empty, ErrorBox, Loading, Stat, useLoad, useToast } from '../ui.jsx';

export default function Dashboard({ go }) {
  const { data: d, error, loading, reload } = useLoad(() => api('/dashboard'));
  const [busy, setBusy] = useState(false);
  const [runErr, setRunErr] = useState(null);
  const notify = useToast();

  const run = async () => {
    setBusy(true); setRunErr(null);
    try {
      const r = await api('/allocation/run', { method: 'POST', body: { planningTime: d.suggestedPlanningTime, strategy: 'MIN_COST' } });
      notify(`Allocation complete: ${r.summary.allocated} meal packs placed`);
      go('results');
    } catch (e) { setRunErr(e); reload(); } finally { setBusy(false); }
  };

  if (loading && !d) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  const urgent = [...d.batchChart].sort((a, b) => a.minutesToDeadline - b.minutesToDeadline).slice(0, 6);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Operations dashboard</h1>
          <p>Surplus prepared meals versus NGO demand for the current planning window. Figures are read live from the Java backend.</p>
        </div>
        <Button variant="primary lg" busy={busy} onClick={run}>Run Allocation</Button>
      </div>
      <ErrorBox error={runErr} title="Allocation could not run" />
      {d.hasRun && d.stale && <div className="notice warn" style={{ marginBottom: 16 }}>Records changed since the last allocation run, so allocated figures may be out of date. Run the allocation again.</div>}

      <div className="grid g4" style={{ marginBottom: 16 }}>
        <Stat k="Total available meal packs" v={num(d.totalMeals)} s={d.expiredMeals ? `${num(d.expiredMeals)} already past deadline` : `${d.batches} batches`} />
        <Stat k="Total NGO demand" v={num(d.totalDemand)} s={`${num(d.effectiveDemand)} collectable (pickup limits)`} />
        <Stat k="Meal packs allocated" v={d.allocated == null ? 'Not run' : num(d.allocated)} s={d.hasRun ? `planning time ${fmtTime(d.lastRunPlanningTime)}` : 'Click Run Allocation'} tone="good" />
        <Stat k="Unallocated surplus" v={d.unallocated == null ? '-' : num(d.unallocated)} s="incl. expired batches" tone={d.unallocated ? 'warn' : ''} />
        <Stat k="Active donors" v={d.donors} s={`${d.batches} donation batches`} />
        <Stat k="Recipient organisations" v={d.ngos} />
        <Stat k="Nearing safe-delivery deadline" v={d.approachingDeadline} s={`within ${d.approachingWindowMinutes} min`} tone={d.approachingDeadline ? 'warn' : ''} />
        <Stat k="Duplicate requests detected" v={d.duplicatesDetected} s={`Bloom false positives seen: ${d.falsePositivesObserved}`} tone={d.duplicatesDetected ? 'bad' : ''} />
      </div>

      {d.batches === 0 && d.ngos === 0 ? (
        <div className="card"><Empty title="No data yet">Add donations and NGOs, or restore the sample dataset from the Allocation Engine page.</Empty></div>
      ) : (
        <div className="grid g2">
          <div className="card">
            <div className="card-head"><h2>Meals per donation batch</h2><span className="muted">{d.hasRun ? 'allocated vs total' : 'allocation not run yet'}</span></div>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={d.batchChart.map((b) => ({ ...b, id: b.batchId.replace('BATCH-', 'B'), unallocated: b.meals - b.allocated }))}>
                <CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey="id" tick={{ fontSize: 12 }} /><YAxis tick={{ fontSize: 12 }} /><Tooltip /><Legend />
                {d.hasRun ? <><RBar dataKey="allocated" name="Allocated" stackId="a" fill="#1f6b48" /><RBar dataKey="unallocated" name="Unallocated" stackId="a" fill="#d9a35b" /></> : <RBar dataKey="meals" name="Meal packs" fill="#3f7a5e" />}
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="card">
            <div className="card-head"><h2>NGO demand and intake</h2><span className="muted">required vs pickup limit{d.hasRun ? ' vs received' : ''}</span></div>
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={d.ngoChart.map((n) => ({ ...n, id: n.ngoId }))}>
                <CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey="id" tick={{ fontSize: 12 }} /><YAxis tick={{ fontSize: 12 }} /><Tooltip /><Legend />
                <RBar dataKey="demand" name="Required" fill="#cfd5cb" /><RBar dataKey="pickup" name="Pickup limit" fill="#8fb3a0" />
                {d.hasRun && <RBar dataKey="received" name="Received" fill="#1f6b48" />}
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="card">
            <div className="card-head"><h2>Closest safe-delivery deadlines</h2></div>
            {urgent.length === 0 ? <Empty title="No donations" /> : (
              <table><tbody>{urgent.map((b) => (
                <tr key={b.batchId}><td><b>{b.batchId}</b><div className="muted">{b.donor}</div></td><td className="num">{num(b.meals)} meals</td>
                  <td className="num">{b.minutesToDeadline <= 0 ? <Badge tone="red">Expired</Badge> : <Badge tone={b.minutesToDeadline <= d.approachingWindowMinutes ? 'amber' : 'green'}>{b.minutesToDeadline} min left</Badge>}</td></tr>
              ))}</tbody></table>
            )}
            <p className="muted" style={{ marginTop: 10 }}>Deadlines are operational values entered by the user, not food-safety determinations.</p>
          </div>
          <div className="card">
            <div className="card-head"><h2>Duplicate screening (Bloom filter)</h2><a href="#/lab">Open Algorithm Lab</a></div>
            <dl className="kv">
              <dt>Filter size</dt><dd>{num(d.bloom.bitSize)} bits, {d.bloom.hashCount} hash functions</dd>
              <dt>Batch IDs inserted</dt><dd>{d.bloom.insertions}</dd>
              <dt>IDs screened</dt><dd>{d.bloom.screened} ({d.bloom.exactLookupsAvoided} skipped the exact lookup)</dd>
              <dt>Possibly present</dt><dd>{d.bloom.possiblyPresent} &rarr; {d.bloom.confirmedDuplicates} duplicates, {d.bloom.falsePositives} false positives</dd>
              <dt>Expected false-positive rate</dt><dd>{(d.bloom.expectedFppNow * 100).toFixed(4)}%</dd>
            </dl>
          </div>
        </div>
      )}
    </>
  );
}
