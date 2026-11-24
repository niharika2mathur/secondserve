import { useState } from 'react';
import { api, fmtTime } from '../api.js';
import { Badge, Button, Empty, ErrorBox, Field, Loading, Modal, useLoad, useToast } from '../ui.jsx';

const TONE = { DISPATCHED: 'amber', PICKED_UP: 'blue', DELIVERED: 'green', FAILED: 'red' };
const TEXT = { DISPATCHED: 'Dispatched', PICKED_UP: 'Picked up', DELIVERED: 'Delivered', FAILED: 'Failed' };
export const DeliveryBadge = ({ s }) => <Badge tone={TONE[s]}>{TEXT[s] || s}</Badge>;
const Stars = ({ n }) => (n ? <span className="stars" title={n + ' / 5'}>{'★'.repeat(n)}{'☆'.repeat(5 - n)}</span> : <span className="muted">-</span>);

export default function Deliveries({ user, refreshCount }) {
  const path = user.role === 'ADMIN' ? '/deliveries' : '/my/deliveries';
  const { data, error, loading, reload } = useLoad(() => api(path));
  const [filter, setFilter] = useState('ALL');
  const [dlg, setDlg] = useState(null); // {kind, d}
  const [form, setForm] = useState({});
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);
  const notify = useToast();

  const act = async (fn, okMsg) => { setBusy(true); setErr(null); try { await fn(); notify(okMsg); setDlg(null); reload(); refreshCount && refreshCount(); } catch (x) { setErr(x); } finally { setBusy(false); } };
  const handover = (d) => act(() => api(`/deliveries/${d.deliveryId}/handover`, { method: 'POST', body: {} }), `${d.deliveryId} marked as picked up`);
  const confirm = () => act(() => api(`/deliveries/${dlg.d.deliveryId}/confirm`, { method: 'POST', body: { receivedMeals: form.received, rating: form.rating || undefined, note: form.note } }), 'Receipt confirmed. Thank you!');
  const report = () => act(() => api(`/deliveries/${dlg.d.deliveryId}/report`, { method: 'POST', body: { note: form.note } }), 'Delivery reported as failed. Its meals return to the pool.');

  if (loading && !data) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  const rows = data.filter((d) => filter === 'ALL' || d.status === filter);
  const counts = (s) => data.filter((d) => d.status === s).length;
  const open = (kind, d) => { setErr(null); setForm({ received: d.meals, rating: '', note: '' }); setDlg({ kind, d }); };

  return (
    <>
      <div className="page-head"><div><h1>{user.role === 'NGO' ? 'Incoming deliveries' : 'Deliveries'}</h1>
        <p>{user.role === 'DONOR' ? 'Confirm when the volunteer has collected your meals.' : user.role === 'NGO' ? 'Confirm what arrived and rate the hand-over. This closes the loop for the donor and for the analytics.' : 'Every published delivery and its life cycle: dispatched, picked up, delivered or failed.'}</p></div>
        <div className="tabs">{['ALL', 'DISPATCHED', 'PICKED_UP', 'DELIVERED', 'FAILED'].map((s) => <button key={s} className={filter === s ? 'on' : ''} onClick={() => setFilter(s)}>{s === 'ALL' ? `All (${data.length})` : `${TEXT[s]} (${counts(s)})`}</button>)}</div>
      </div>
      <div className="card flush scroll">
        {rows.length === 0 ? <Empty title="No deliveries here">Deliveries appear after the coordinator publishes a dispatch plan.</Empty> : (
          <table>
            <thead><tr><th>Delivery</th><th>From &rarr; to</th><th className="num">Meals</th><th>Volunteer</th><th>Leave / arrive by</th><th>Status</th><th>Feedback</th><th></th></tr></thead>
            <tbody>{rows.map((d) => (
              <tr key={d.deliveryId}>
                <td><b>{d.deliveryId}</b><div className="muted">{d.batchId} · run #{d.planId}</div></td>
                <td>{d.donorName}<div className="muted">&rarr; {d.ngoName}</div></td>
                <td className="num">{d.status === 'DELIVERED' && d.receivedMeals !== d.meals ? `${d.receivedMeals} / ${d.meals}` : d.meals}</td>
                <td>{d.volunteers}</td>
                <td>{fmtTime(d.departAt)}<div className="muted">{fmtTime(d.arriveBy)}</div></td>
                <td><DeliveryBadge s={d.status} /></td>
                <td><Stars n={d.rating} />{d.note && <div className="muted" style={{ maxWidth: 220 }}>{d.note}</div>}</td>
                <td><div className="row" style={{ justifyContent: 'flex-end' }}>
                  {user.role === 'DONOR' && d.status === 'DISPATCHED' && <Button size="sm" variant="primary" onClick={() => handover(d)}>Mark picked up</Button>}
                  {user.role === 'NGO' && (d.status === 'DISPATCHED' || d.status === 'PICKED_UP') && <Button size="sm" variant="primary" onClick={() => open('confirm', d)}>Confirm receipt</Button>}
                  {d.status !== 'DELIVERED' && d.status !== 'FAILED' && <Button size="sm" variant="danger" onClick={() => open('report', d)}>{user.role === 'ADMIN' ? 'Cancel' : 'Report problem'}</Button>}
                </div></td>
              </tr>))}</tbody>
          </table>)}
      </div>

      {dlg?.kind === 'confirm' && (
        <Modal title={`Confirm ${dlg.d.deliveryId}`} onClose={() => setDlg(null)}>
          <p className="muted" style={{ marginBottom: 12 }}>{dlg.d.meals} meals from {dlg.d.donorName}.</p>
          <div className="form-grid">
            <Field label="Meals actually received"><input type="number" min="1" max={dlg.d.meals} value={form.received} onChange={(e) => setForm({ ...form, received: e.target.value })} /></Field>
            <Field label="Rating of the hand-over"><select value={form.rating} onChange={(e) => setForm({ ...form, rating: e.target.value })}><option value="">No rating</option>{[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{'★'.repeat(n)} {n}</option>)}</select></Field>
            <div className="full"><Field label="Note (optional)"><input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></Field></div>
          </div>
          <div style={{ marginTop: 12 }}><ErrorBox error={err} /></div>
          <div className="row" style={{ justifyContent: 'flex-end', marginTop: 14 }}><Button onClick={() => setDlg(null)}>Cancel</Button><Button variant="primary" busy={busy} onClick={confirm}>Confirm receipt</Button></div>
        </Modal>)}
      {dlg?.kind === 'report' && (
        <Modal title={`${user.role === 'ADMIN' ? 'Cancel' : 'Report a problem with'} ${dlg.d.deliveryId}`} onClose={() => setDlg(null)}>
          <p className="muted" style={{ marginBottom: 12 }}>The {dlg.d.meals} meals are released and can be allocated again. Everyone involved is notified.</p>
          <Field label="What went wrong?"><input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} placeholder="e.g. vehicle broke down, NGO closed" /></Field>
          <div style={{ marginTop: 12 }}><ErrorBox error={err} /></div>
          <div className="row" style={{ justifyContent: 'flex-end', marginTop: 14 }}><Button onClick={() => setDlg(null)}>Back</Button><Button variant="danger" busy={busy} onClick={report}>Mark as failed</Button></div>
        </Modal>)}
    </>
  );
}
