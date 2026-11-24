import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, bytes, fmtTime, ms, num } from '../api.js';
import { Badge, Button, Empty, ErrorBox, Field, Loading, useLoad, useToast } from '../ui.jsx';

const Chart = ({ title, sub, data, xKey, series, log, fmt }) => (
  <div className="card">
    <div className="card-head"><div><h3>{title}</h3>{sub && <p>{sub}</p>}</div></div>
    <ResponsiveContainer width="100%" height={250}>
      <BarChart data={data}><CartesianGrid vertical={false} stroke="#e8ebe4" /><XAxis dataKey={xKey} tick={{ fontSize: 12 }} />
        <YAxis scale={log ? 'log' : 'auto'} domain={log ? ['auto', 'auto'] : [0, 'auto']} tick={{ fontSize: 12 }} width={64} allowDataOverflow tickFormatter={fmt} /><Tooltip formatter={fmt} /><Legend />
        {series.map(([k, name, color]) => <Bar key={k} dataKey={k} name={name} fill={color} />)}</BarChart>
    </ResponsiveContainer>
  </div>
);

export default function Benchmarks() {
  const { data, loading, reload } = useLoad(async () => { try { return await api('/algorithms/benchmarks/latest'); } catch (e) { if (e.status === 404) return null; throw e; } });
  const [reps, setReps] = useState(7);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const [fresh, setFresh] = useState(null);
  const notify = useToast();
  const r = fresh || data;

  const run = async () => {
    setBusy(true); setErr(null);
    try { setFresh(await api('/algorithms/benchmarks', { method: 'POST', body: { seed: 2026, repetitions: Number(reps) } })); notify('Benchmarks finished (measured just now)'); }
    catch (e) { setErr(e); } finally { setBusy(false); }
  };

  const flow = r?.flow.map((f) => ({ ...f, size: `${f.donors}x${f.ngos}` })) || [];
  const bloom = r?.bloom.map((b) => ({ ...b, size: num(b.identifiers) + ' IDs', fpPct: +(b.observedFpRate * 100).toFixed(3), thPct: +(b.theoreticalFpRate * 100).toFixed(3) })) || [];
  return (
    <>
      <div className="page-head">
        <div><h1>Performance benchmarks</h1><p>These experiments run live in the Java backend with fixed random seeds. Nothing on this page is precomputed; timings are medians on whatever machine hosts the backend.</p></div>
        <div className="row" style={{ alignItems: 'flex-end' }}><Field label="Repetitions"><select value={reps} onChange={(e) => setReps(e.target.value)}><option value="3">3 (quick)</option><option value="7">7</option><option value="15">15 (steadier)</option></select></Field><Button variant="primary lg" busy={busy} onClick={run}>Run benchmarks</Button></div>
      </div>
      <ErrorBox error={err} />
      {loading && !r ? <Loading /> : !r ? <div className="card"><Empty title="No benchmark has been run in this session">Press "Run benchmarks". It takes a few seconds. Saved results from the project author are in docs/benchmark-results.md.</Empty></div> : (
        <div className="stack">
          <div className="notice info">Measured {fmtTime(r.executedAt)} on Java {r.javaVersion} with {r.availableProcessors} CPU(s), seed {r.seed}, median of {r.repetitions} runs.</div>

          <h2>1. Edmonds-Karp vs first-come-first-served</h2>
          <div className="grid g2">
            <Chart title="Meals allocated" sub="Same instance, same constraints" data={flow} xKey="size" series={[['edmondsKarpAllocated', 'Edmonds-Karp', '#1f6b48'], ['greedyAllocated', 'FCFS greedy', '#c7cdc2']]} />
            <Chart title="Execution time (median, log scale)" data={flow} xKey="size" log fmt={(v) => v.toFixed(3) + ' ms'} series={[['edmondsKarpMillis', 'Edmonds-Karp', '#1f6b48'], ['greedyMillis', 'FCFS greedy', '#c7cdc2']]} />
          </div>
          <div className="card flush scroll"><table>
            <thead><tr><th>Donors x NGOs</th><th className="num">Feasible routes</th><th className="num">Supply</th><th className="num">EK meals</th><th className="num">FCFS meals</th><th className="num">Gap</th><th className="num">EK matches</th><th className="num">FCFS matches</th><th className="num">EK time</th><th className="num">FCFS time</th><th className="num">Augmentations</th><th className="num">Network memory (est.)</th><th>Valid</th></tr></thead>
            <tbody>{flow.map((f) => <tr key={f.size}><td><b>{f.size}</b></td><td className="num">{f.feasibleRoutes}</td><td className="num">{num(f.totalSupply)}</td><td className="num">{num(f.edmondsKarpAllocated)}</td><td className="num">{num(f.greedyAllocated)}</td><td className="num">{f.gapMeals >= 0 ? '+' : ''}{f.gapMeals} ({f.gapPercent.toFixed(1)}%)</td><td className="num">{f.edmondsKarpMatches}</td><td className="num">{f.greedyMatches}</td><td className="num">{ms(f.edmondsKarpMillis)}</td><td className="num">{ms(f.greedyMillis)}</td><td className="num">{f.augmentations}</td><td className="num">{bytes(f.networkBytesEstimate)}</td><td>{f.validatedAgainstConstraints ? <Badge tone="green">yes</Badge> : <Badge tone="red">NO</Badge>}</td></tr>)}</tbody></table></div>
          <div className="card"><p>Maximum flow never allocates fewer meals than greedy, and its result is provably optimal for the network. The size of the advantage depends on the instance: it appears when greedy's early choices use up capacity that another batch could only reach through that NGO, and it can be zero when routes are plentiful. Greedy is much cheaper per run (roughly O(B&middot;N)) while Edmonds-Karp has an O(V&middot;E&sup2;) worst case, so the extra time buys extra meals placed. It is not faster.</p></div>

          <h2 style={{ marginTop: 8 }}>2. Bloom filter vs exact membership checking</h2>
          <div className="grid g2">
            <Chart title="Memory (log scale)" sub="Bit array vs HashSet holding the same IDs (HashSet figure is an estimate)" data={bloom} xKey="size" log fmt={(v) => bytes(v)} series={[['bloomBytes', 'Bloom filter', '#1f6b48'], ['exactSetBytesApprox', 'Exact HashSet (approx.)', '#c7cdc2']]} />
            <Chart title="False-positive rate (%)" sub="Observed on never-inserted IDs vs theoretical" data={bloom} xKey="size" fmt={(v) => v + '%'} series={[['fpPct', 'Observed', '#c2570c'], ['thPct', 'Theoretical', '#8fb3a0']]} />
            <Chart title="Query time for all queries (ms, log scale)" sub="Bloom alone vs HashSet alone vs Bloom + exact verification" data={bloom} xKey="size" log fmt={(v) => v.toFixed(3) + ' ms'} series={[['bloomQueryMillis', 'Bloom only', '#1f6b48'], ['exactQueryMillis', 'HashSet only', '#c7cdc2'], ['twoStageMillis', 'Bloom + exact', '#2b5f8a']]} />
          </div>
          <div className="card flush scroll"><table>
            <thead><tr><th>Identifiers</th><th className="num">Bits (m)</th><th className="num">k</th><th className="num">Negative queries</th><th className="num">False positives</th><th className="num">Observed</th><th className="num">Theoretical</th><th className="num">False negatives</th><th className="num">Exact lookups avoided</th><th className="num">Bloom time</th><th className="num">HashSet time</th><th className="num">Bloom + exact</th></tr></thead>
            <tbody>{bloom.map((b) => <tr key={b.identifiers}><td><b>{num(b.identifiers)}</b></td><td className="num">{num(b.bitSize)}</td><td className="num">{b.hashCount}</td><td className="num">{num(b.negativeQueries)}</td><td className="num">{b.falsePositives}</td><td className="num">{b.fpPct}%</td><td className="num">{b.thPct}%</td><td className="num">{b.falseNegatives}</td><td className="num">{num(b.exactLookupsAvoided)}</td><td className="num">{ms(b.bloomQueryMillis)}</td><td className="num">{ms(b.exactQueryMillis)}</td><td className="num">{ms(b.twoStageMillis)}</td></tr>)}</tbody></table></div>
          {r.strategy && <>
            <h2 style={{ marginTop: 8 }}>3. Min-cost flow and fair share vs plain maximum flow</h2>
            <div className="grid g2">
              <Chart title="Total travel (meal-minutes)" sub="Same meals placed; lower is better" data={r.strategy.map((x) => ({ ...x, size: `${x.donors}x${x.ngos}` }))} xKey="size" series={[['ekMealMinutes', 'Max flow', '#c7cdc2'], ['minCostMealMinutes', 'Min-cost', '#1f6b48']]} />
              <Chart title="Smallest NGO share (meals)" sub="Among NGOs that can be reached; higher is fairer" data={r.strategy.map((x) => ({ ...x, size: `${x.donors}x${x.ngos}` }))} xKey="size" series={[['ekMinReceipt', 'Max flow', '#c7cdc2'], ['fairMinReceipt', 'Fair share', '#2b5f8a']]} />
            </div>
            <div className="card flush scroll"><table>
              <thead><tr><th>Size</th><th className="num">Meals (all methods)</th><th className="num">Max flow time</th><th className="num">Min-cost time</th><th className="num">Fair time</th><th className="num">Fair: max-flow calls</th><th className="num">Travel saved</th><th className="num">Tight meals</th><th className="num">Jain index</th><th>Same total &amp; valid</th></tr></thead>
              <tbody>{r.strategy.map((x) => <tr key={x.donors}><td><b>{x.donors}x{x.ngos}</b></td><td className="num">{num(x.meals)}</td><td className="num">{ms(x.ekMillis)}</td><td className="num">{ms(x.minCostMillis)}</td><td className="num">{ms(x.fairMillis)}</td><td className="num">{x.fairMaxFlowCalls}</td><td className="num">{x.travelSavingPercent.toFixed(1)}%</td><td className="num">{x.ekTightMeals} &rarr; {x.minCostTightMeals}</td><td className="num">{x.ekJain.toFixed(3)} &rarr; {x.fairJain.toFixed(3)}</td><td>{x.sameTotal && x.allValid ? <Badge tone="green">yes</Badge> : <Badge tone="red">NO</Badge>}</td></tr>)}</tbody></table></div>
            <div className="card"><p>Min-cost flow and fair share never place fewer meals than plain maximum flow (they place exactly as many); they choose a better allocation among the equally large ones. The price is time: min-cost is slower than a single Edmonds-Karp run, and fair share runs many max-flows (see the call count) and grows fastest.</p></div>

            <h2 style={{ marginTop: 8 }}>4. Count-Min Sketch vs exact HashMap</h2>
            <div className="grid g2">
              <Chart title="Memory (log scale)" sub="Sketch size is fixed; the map grows with the number of donors" data={r.sketch.map((x) => ({ ...x, size: num(x.events) + ' events' }))} xKey="size" log fmt={(v) => bytes(v)} series={[['sketchBytes', 'Count-Min Sketch', '#1f6b48'], ['exactBytesApprox', 'HashMap (approx.)', '#c7cdc2']]} />
              <Chart title="Update time for the whole stream (ms)" data={r.sketch.map((x) => ({ ...x, size: num(x.events) + ' events' }))} xKey="size" fmt={(v) => v.toFixed(1) + ' ms'} series={[['sketchMillis', 'Count-Min Sketch', '#1f6b48'], ['exactMillis', 'HashMap', '#c7cdc2']]} />
            </div>
            <div className="card flush scroll"><table>
              <thead><tr><th>Events</th><th className="num">Distinct donors</th><th className="num">Error bound (eps x N)</th><th className="num">Max over-estimate</th><th className="num">Keys within bound</th><th>Never under-estimates</th><th>Top 10 correct</th></tr></thead>
              <tbody>{r.sketch.map((x) => <tr key={x.events}><td><b>{num(x.events)}</b></td><td className="num">{num(x.distinctKeys)}</td><td className="num">{num(Math.round(x.errorBound))}</td><td className="num">{num(x.maxOverestimate)}</td><td className="num">{x.withinBoundPercent.toFixed(2)}%</td><td>{x.neverUnderestimates ? <Badge tone="green">yes</Badge> : <Badge tone="red">NO</Badge>}</td><td>{x.top10Correct ? <Badge tone="green">yes</Badge> : <Badge tone="amber">no</Badge>}</td></tr>)}</tbody></table></div>
            <div className="card"><p>At 10,000 events the exact map is actually smaller than the sketch, and timings are similar. The sketch wins on memory once there are many distinct keys, because its size depends only on epsilon and delta.</p></div>

            <h2 style={{ marginTop: 8 }}>5. Volunteer scheduling: greedy interval partitioning</h2>
            <div className="card flush scroll"><table>
              <thead><tr><th>Trips</th><th className="num">Volunteers used</th><th className="num">Depth (lower bound)</th><th>Optimal</th><th className="num">Heap time</th><th className="num">Linear first-fit time</th></tr></thead>
              <tbody>{r.schedule.map((x) => <tr key={x.trips}><td><b>{num(x.trips)}</b></td><td className="num">{x.volunteers}</td><td className="num">{x.depth}</td><td>{x.optimal ? <Badge tone="green">yes</Badge> : <Badge tone="red">NO</Badge>}</td><td className="num">{ms(x.heapMillis)}</td><td className="num">{ms(x.linearScanMillis)}</td></tr>)}</tbody></table></div>
            <div className="card"><p>Both strategies use exactly the minimum number of volunteers. The heap version is <b>not</b> faster here: with only a few dozen volunteers a linear scan is cheaper than heap bookkeeping, and the O(n log k) advantage appears only when k is large.</p></div>
          </>}
          <div className="card"><h3 style={{ marginBottom: 6 }}>Notes</h3><ul style={{ margin: 0, paddingLeft: 20 }}>{r.notes.map((n, i) => <li key={i}>{n}</li>)}</ul></div>
        </div>
      )}
    </>
  );
}
