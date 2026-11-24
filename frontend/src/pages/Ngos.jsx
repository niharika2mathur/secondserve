import { useState } from 'react';
import { api, num } from '../api.js';
import { Bar, Button, Empty, ErrorBox, Field, Loading, Modal, useLoad, useToast } from '../ui.jsx';

const blank = { ngoId: '', name: '', requiredMeals: '', pickupCapacity: '', maxTravelMinutes: '', contact: '' };

export default function Ngos() {
  const { data, error, loading, reload } = useLoad(() => api('/ngos'));
  const [form, setForm] = useState(null);
  const [editing, setEditing] = useState(null);
  const [formErr, setFormErr] = useState(null);
  const [busy, setBusy] = useState(false);
  const notify = useToast();
  const open = (n) => { setFormErr(null); setEditing(n ? n.ngoId : null); setForm(n ? { ...n } : { ...blank }); };
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const save = async (e) => {
    e.preventDefault(); setBusy(true); setFormErr(null);
    try {
      if (editing) await api('/ngos/' + encodeURIComponent(editing), { method: 'PUT', body: form });
      else await api('/ngos', { method: 'POST', body: form });
      notify(editing ? 'NGO updated' : 'NGO registered'); setForm(null); reload();
    } catch (err) { setFormErr(err); } finally { setBusy(false); }
  };
  const del = async (n) => {
    if (!window.confirm(`Delete ${n.name}? Its travel times are removed too.`)) return;
    try { await api('/ngos/' + encodeURIComponent(n.ngoId), { method: 'DELETE' }); notify('NGO deleted'); reload(); } catch (err) { notify(err.message, 'err'); }
  };

  return (
    <>
      <div className="page-head">
        <div><h1>Recipient NGOs</h1><p>Each NGO can absorb at most the smaller of its required meals and its pickup capacity in one planning window. That value becomes the NGO-to-sink capacity in the flow network.</p></div>
        <Button variant="primary" onClick={() => open(null)}>Add NGO</Button>
      </div>
      <ErrorBox error={error} />
      <div className="card flush scroll">
        {loading && !data ? <Loading /> : data?.length === 0 ? <Empty title="No NGOs registered">Add at least one organisation to allocate to.</Empty> : (
          <table>
            <thead><tr><th>ID</th><th>Organisation</th><th className="num">Required</th><th className="num">Pickup capacity</th><th>Pickup vs required</th><th className="num">Max travel</th><th>Contact</th><th></th></tr></thead>
            <tbody>{data?.map((n) => (
              <tr key={n.ngoId}>
                <td><b>{n.ngoId}</b></td><td>{n.name}</td><td className="num">{num(n.requiredMeals)}</td><td className="num">{num(n.pickupCapacity)}</td>
                <td style={{ minWidth: 120 }}><Bar value={Math.min(n.pickupCapacity, n.requiredMeals)} max={n.requiredMeals} /><span className="muted">intake cap {Math.min(n.pickupCapacity, n.requiredMeals)}</span></td>
                <td className="num">{n.maxTravelMinutes} min</td><td className="muted">{n.contact || '-'}</td>
                <td><div className="row" style={{ flexWrap: 'nowrap' }}><Button size="sm" onClick={() => open(n)}>Edit</Button><Button size="sm" variant="danger" onClick={() => del(n)}>Delete</Button></div></td>
              </tr>))}</tbody>
          </table>
        )}
      </div>
      {form && (
        <Modal title={editing ? `Edit ${editing}` : 'Add NGO'} onClose={() => setForm(null)}>
          <form onSubmit={save} noValidate>
            <div className="form-grid">
              <Field label="NGO ID" hint="Unique, e.g. N-08"><input value={form.ngoId} onChange={set('ngoId')} disabled={!!editing} autoFocus /></Field>
              <Field label="Organisation name"><input value={form.name} onChange={set('name')} /></Field>
              <Field label="Required meal packs"><input type="number" min="1" value={form.requiredMeals} onChange={set('requiredMeals')} /></Field>
              <Field label="Maximum pickup capacity" hint="Meal packs it can collect in this window"><input type="number" min="1" value={form.pickupCapacity} onChange={set('pickupCapacity')} /></Field>
              <Field label="Maximum acceptable travel time (min)"><input type="number" min="1" value={form.maxTravelMinutes} onChange={set('maxTravelMinutes')} /></Field>
              <Field label="Contact (optional)"><input value={form.contact} onChange={set('contact')} /></Field>
              <div className="full"><ErrorBox error={formErr} /></div>
            </div>
            <div className="row" style={{ justifyContent: 'flex-end', marginTop: 18 }}><Button type="button" onClick={() => setForm(null)}>Cancel</Button><Button variant="primary" busy={busy}>{editing ? 'Save changes' : 'Add NGO'}</Button></div>
          </form>
        </Modal>
      )}
    </>
  );
}
