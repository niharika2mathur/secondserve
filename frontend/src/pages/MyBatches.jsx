import { useState } from 'react';
import { api, clock, minutesText, toInput } from '../api.js';
import { Badge, Button, ErrorBox, Field, Loading, Modal, useLoad, useToast } from '../ui.jsx';
import { BATCH_TEXT, BATCH_TONE } from './DonorHome.jsx';

const plus = (min) => { const d = new Date(Date.now() + min * 60000 - new Date().getTimezoneOffset() * 60000); return d.toISOString().slice(0, 16); };
const blank = () => ({ batchId: '', meals: '', availableFrom: plus(0), safeUntil: plus(180), notes: '' });

export default function MyBatches() {
  const { data, error, loading, reload } = useLoad(() => api('/dashboard/donor'));
  const [form, setForm] = useState(null);
  const [editing, setEditing] = useState(null);
  const [formErr, setFormErr] = useState(null);
  const [busy, setBusy] = useState(false);
  const notify = useToast();
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const open = (b) => { setFormErr(null); setEditing(b ? b.batchId : null); setForm(b ? { batchId: b.batchId, meals: b.meals, availableFrom: toInput(b.availableFrom), safeUntil: toInput(b.safeUntil), notes: b.notes || '' } : blank()); };

  const save = async (e) => {
    e.preventDefault(); setBusy(true); setFormErr(null);
    try {
      if (editing) { await api('/my/donations/' + encodeURIComponent(editing), { method: 'PUT', body: form }); notify('Batch updated'); }
      else {
        const r = await api('/my/donations', { method: 'POST', body: form });
        notify(r.routesInherited ? `${r.donation.batchId} added. ${r.routesInherited} route times were copied from ${r.routesInheritedFrom}.` : `${r.donation.batchId} added. The coordinator will add route times for it.`);
      }
      setForm(null); reload();
    } catch (x) { setFormErr(x); if (x.status === 409) notify('Duplicate batch ID rejected', 'err'); } finally { setBusy(false); }
  };
  const del = async (b) => {
    if (!window.confirm(`Delete ${b.batchId}?`)) return;
    try { await api('/my/donations/' + encodeURIComponent(b.batchId), { method: 'DELETE' }); notify('Batch deleted'); reload(); } catch (x) { notify(x.message, 'err'); }
  };

  if (loading && !data) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  return (
    <>
      <div className="page-head"><div><h1>My batches</h1><p>Register surplus meals as soon as you know about them: the later you tell us, the less time there is to place them. Batches already in a dispatch plan are locked.</p></div><Button variant="primary lg" onClick={() => open(null)}>Add batch</Button></div>
      <div className="card flush scroll">
        <table>
          <thead><tr><th>Batch</th><th className="num">Meals</th><th>Available from</th><th>Safe until</th><th>Time left</th><th>Status</th><th></th></tr></thead>
          <tbody>{data.myBatches.length === 0 && <tr><td colSpan="7" className="muted">No batches yet.</td></tr>}
            {data.myBatches.map((b) => {
              const locked = b.dispatched > 0 || b.delivered > 0;
              return (
                <tr key={b.batchId}><td><b>{b.batchId}</b><div className="muted">{b.notes}</div></td><td className="num">{b.meals}</td><td>{clock(b.availableFrom)}</td><td>{clock(b.safeUntil)}</td>
                  <td>{b.status === 'DELIVERED' ? '-' : minutesText(b.minutesLeft)}</td><td><Badge tone={BATCH_TONE[b.status]}>{BATCH_TEXT[b.status]}</Badge></td>
                  <td className="row" style={{ justifyContent: 'flex-end' }}><Button size="sm" disabled={locked} title={locked ? 'Already in a dispatch plan' : ''} onClick={() => open(b)}>Edit</Button><Button size="sm" variant="danger" disabled={locked} onClick={() => del(b)}>Delete</Button></td></tr>);
            })}</tbody>
        </table>
      </div>
      {form && (
        <Modal title={editing ? `Edit ${editing}` : 'Add a surplus batch'} onClose={() => setForm(null)}>
          <form onSubmit={save} className="stack">
            <div className="form-grid">
              <Field label="Batch ID" hint="e.g. ORC-201. Duplicates are caught by the Bloom filter + exact check."><input value={form.batchId} onChange={set('batchId')} disabled={!!editing} required /></Field>
              <Field label="Meal packs"><input type="number" min="1" value={form.meals} onChange={set('meals')} required /></Field>
              <Field label="Available from"><input type="datetime-local" value={form.availableFrom} onChange={set('availableFrom')} required /></Field>
              <Field label="Latest safe delivery time" hint="Your own operational deadline"><input type="datetime-local" value={form.safeUntil} onChange={set('safeUntil')} required /></Field>
              <div className="full"><Field label="Notes (optional)"><input value={form.notes} onChange={set('notes')} /></Field></div>
            </div>
            <ErrorBox error={formErr} />
            <div className="row" style={{ justifyContent: 'flex-end' }}><Button type="button" onClick={() => setForm(null)}>Cancel</Button><Button type="submit" variant="primary" busy={busy}>{editing ? 'Save' : 'Add batch'}</Button></div>
          </form>
        </Modal>
      )}
    </>
  );
}
