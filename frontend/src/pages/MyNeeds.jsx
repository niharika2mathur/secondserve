import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { Button, ErrorBox, Field, Loading, useLoad, useToast } from '../ui.jsx';

export default function MyNeeds() {
  const { data, error, loading, reload } = useLoad(() => api('/my/ngo'));
  const [f, setF] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);
  const notify = useToast();
  useEffect(() => { if (data) setF({ requiredMeals: data.requiredMeals, pickupCapacity: data.pickupCapacity, maxTravelMinutes: data.maxTravelMinutes, contact: data.contact || '' }); }, [data]);
  if (loading && !data) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  if (!f) return null;
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const save = async (e) => {
    e.preventDefault(); setBusy(true); setErr(null);
    try { await api('/my/ngo', { method: 'PUT', body: f }); notify('Your needs were updated. The coordinator will see them in the next allocation.'); reload(); }
    catch (x) { setErr(x); } finally { setBusy(false); }
  };
  const eff = Math.min(Number(f.requiredMeals) || 0, Number(f.pickupCapacity) || 0);
  return (
    <>
      <div className="page-head"><div><h1>My needs</h1><p>Keep these figures current. The allocation engine will never send you more than the smaller of your need and your pickup capacity.</p></div></div>
      <div className="card" style={{ maxWidth: 720 }}>
        <form onSubmit={save} className="stack">
          <Field label="Organisation"><input value={data.name} disabled /></Field>
          <div className="form-grid">
            <Field label="Meals you need per window"><input type="number" min="1" value={f.requiredMeals} onChange={set('requiredMeals')} required /></Field>
            <Field label="Maximum pickup capacity" hint="What your vehicles and volunteers can collect"><input type="number" min="1" value={f.pickupCapacity} onChange={set('pickupCapacity')} required /></Field>
            <Field label="Maximum travel time (minutes)" hint="Longer trips mean more donors can reach you"><input type="number" min="1" value={f.maxTravelMinutes} onChange={set('maxTravelMinutes')} required /></Field>
            <Field label="Contact"><input value={f.contact} onChange={set('contact')} /></Field>
          </div>
          <div className="notice info">Effective intake per window: <b>{eff}</b> meals = min(need, pickup capacity).</div>
          <ErrorBox error={err} />
          <div><Button type="submit" variant="primary" busy={busy}>Save changes</Button></div>
        </form>
      </div>
    </>
  );
}
