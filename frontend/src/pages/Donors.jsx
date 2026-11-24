import { useState } from 'react';
import { api, fmtTime, toInput, num } from '../api.js';
import { Badge, Button, Empty, ErrorBox, Field, Loading, Modal, useLoad, useToast } from '../ui.jsx';

const plus = (min) => { const d = new Date(Date.now() + min * 60000 - new Date().getTimezoneOffset() * 60000); return d.toISOString().slice(0, 16); };
const blank = () => ({ batchId: '', donorName: '', meals: '', availableFrom: plus(0), safeUntil: plus(180), notes: '' });
const SAMPLE_CSV = `batchId,donor,meals,availableFrom,safeUntil,notes
BATCH-201,Blue Lagoon Cafe,45,-10,+150,Lunch buffet
BATCH-101,Duplicate Hotel Entry,60,-10,+120,Accidental re-submission
BATCH-202,Metro Food Court,35,0,+90,Packed rice meals
BATCH-203,Broken Row,abc,-5,+60,Invalid meal count`;

export default function Donors() {
  const { data, error, loading, reload } = useLoad(() => api('/donations'));
  const [form, setForm] = useState(null);
  const [editing, setEditing] = useState(null);
  const [formErr, setFormErr] = useState(null);
  const [busy, setBusy] = useState(false);
  const [csv, setCsv] = useState(null);
  const [report, setReport] = useState(null);
  const notify = useToast();

  const open = (d) => { setFormErr(null); setEditing(d ? d.batchId : null); setForm(d ? { ...d, availableFrom: toInput(d.availableFrom), safeUntil: toInput(d.safeUntil) } : blank()); };
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const save = async (e) => {
    e.preventDefault(); setBusy(true); setFormErr(null);
    try {
      if (editing) { await api('/donations/' + encodeURIComponent(editing), { method: 'PUT', body: form }); notify('Donation updated'); }
      else {
        const r = await api('/donations', { method: 'POST', body: form });
        const s = r.screening;
        notify(s.outcome === 'NEW' ? `${r.donation.batchId} accepted - Bloom filter: definitely new (no exact lookup needed)` : `${r.donation.batchId} accepted - Bloom false positive; exact lookup found no record`);
      }
      setForm(null); reload();
    } catch (err) { setFormErr(err); if (err.status === 409) notify('Duplicate batch ID rejected', 'err'); } finally { setBusy(false); }
  };

  const del = async (d) => {
    if (!window.confirm(`Delete ${d.batchId} (${d.donorName})? Its travel times are removed too.`)) return;
    try { await api('/donations/' + encodeURIComponent(d.batchId), { method: 'DELETE' }); notify('Donation deleted'); reload(); }
    catch (err) { notify(err.message, 'err'); }
  };

  const importCsv = async () => {
    setBusy(true);
    try { const r = await api('/donations/import-csv', { method: 'POST', body: { csv } }); setReport(r); reload(); notify(`Import finished: ${r.imported} imported, ${r.duplicatesRejected} duplicates rejected, ${r.invalid} invalid`, r.imported ? 'ok' : 'err'); }
    catch (err) { notify(err.message, 'err'); } finally { setBusy(false); }
  };
  const readFile = (f) => { if (!f) return; const r = new FileReader(); r.onload = () => setCsv(String(r.result)); r.readAsText(f); };

  return (
    <>
      <div className="page-head">
        <div><h1>Donors</h1><p>Register surplus meal batches. Every new batch ID passes through the Bloom filter; a possible duplicate is confirmed with an exact lookup before it is ever rejected.</p></div>
        <div className="row"><Button onClick={() => { setReport(null); setCsv(SAMPLE_CSV); }}>Import CSV</Button><Button variant="primary" onClick={() => open(null)}>Add donation</Button></div>
      </div>
      <ErrorBox error={error} />
      <div className="card flush scroll">
        {loading && !data ? <Loading /> : data?.length === 0 ? <Empty title="No donations registered">Use "Add donation" or import a CSV to begin.</Empty> : (
          <table>
            <thead><tr><th>Batch ID</th><th>Donor / business</th><th className="num">Meal packs</th><th>Available from</th><th>Latest safe delivery</th><th>Notes</th><th></th></tr></thead>
            <tbody>{data?.map((d) => (
              <tr key={d.batchId}>
                <td><b>{d.batchId}</b></td><td>{d.donorName}</td><td className="num">{num(d.meals)}</td><td>{fmtTime(d.availableFrom)}</td>
                <td>{fmtTime(d.safeUntil)} {new Date(d.safeUntil) < new Date() && <Badge tone="red">past</Badge>}</td><td className="muted">{d.notes}</td>
                <td><div className="row" style={{ flexWrap: 'nowrap' }}><Button size="sm" onClick={() => open(d)}>Edit</Button><Button size="sm" variant="danger" onClick={() => del(d)}>Delete</Button></div></td>
              </tr>))}</tbody>
          </table>
        )}
      </div>

      {form && (
        <Modal title={editing ? `Edit ${editing}` : 'Add donation'} onClose={() => setForm(null)}>
          <form onSubmit={save} noValidate>
            <div className="form-grid">
              <Field label="Donation batch ID" hint="Unique, e.g. BATCH-301"><input value={form.batchId} onChange={set('batchId')} disabled={!!editing} autoFocus /></Field>
              <Field label="Donor / business name"><input value={form.donorName} onChange={set('donorName')} /></Field>
              <Field label="Available meal packs"><input type="number" min="1" value={form.meals} onChange={set('meals')} /></Field>
              <span />
              <Field label="Availability time"><input type="datetime-local" value={form.availableFrom} onChange={set('availableFrom')} /></Field>
              <Field label="Latest safe delivery time" hint="Operational deadline you enter"><input type="datetime-local" value={form.safeUntil} onChange={set('safeUntil')} /></Field>
              <div className="full"><Field label="Notes (optional)"><textarea rows="2" value={form.notes} onChange={set('notes')} /></Field></div>
              <div className="full"><ErrorBox error={formErr} /></div>
            </div>
            <div className="row" style={{ justifyContent: 'flex-end', marginTop: 18 }}><Button type="button" onClick={() => setForm(null)}>Cancel</Button><Button variant="primary" busy={busy}>{editing ? 'Save changes' : 'Add donation'}</Button></div>
          </form>
        </Modal>
      )}

      {csv !== null && (
        <Modal title="Import donations from CSV" onClose={() => setCsv(null)}>
          <p className="muted" style={{ marginBottom: 10 }}>Columns: batchId, donor, meals, availableFrom, safeUntil, notes. Times may be ISO (2026-11-17T14:30) or minutes from now (<span className="mono">-10</span>, <span className="mono">+120</span>). The prefilled sample includes a duplicate of BATCH-101 and a malformed row.</p>
          <input type="file" accept=".csv,text/csv,text/plain" onChange={(e) => readFile(e.target.files[0])} style={{ marginBottom: 10 }} />
          <textarea className="inp mono" rows="8" value={csv} onChange={(e) => setCsv(e.target.value)} />
          {report && (
            <div className="notice info" style={{ marginTop: 12 }}>
              <b>{report.imported} imported, {report.duplicatesRejected} duplicate(s) rejected, {report.invalid} invalid</b>
              <ul>{report.rows.filter((r) => r.status !== 'IMPORTED' || r.message !== 'Imported').map((r, i) => <li key={i}>Line {r.line} {r.batchId}: {r.status.replace(/_/g, ' ').toLowerCase()} - {r.message}</li>)}</ul>
            </div>
          )}
          <div className="row" style={{ justifyContent: 'flex-end', marginTop: 16 }}><Button onClick={() => setCsv(null)}>Close</Button><Button variant="primary" busy={busy} onClick={importCsv}>Import</Button></div>
        </Modal>
      )}
    </>
  );
}
