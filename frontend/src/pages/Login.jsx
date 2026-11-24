import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Button, ErrorBox, Field } from '../ui.jsx';

const DEMOS = [
  { label: 'Coordinator (admin)', u: 'admin', p: 'admin123', tone: 'green' },
  { label: 'Donor: Grand Orchid Hotel', u: 'orchid', p: 'demo123', tone: 'amber' },
  { label: 'NGO: Hope Shelter Home', u: 'hope', p: 'demo123', tone: 'blue' },
];

export default function Login() {
  const { login } = useAuth();
  const [mode, setMode] = useState('login');
  const [f, setF] = useState({ username: '', password: '' });
  const [reg, setReg] = useState({ role: 'DONOR', username: '', password: '', displayName: '', orgName: '', requiredMeals: 100, pickupCapacity: 80, maxTravelMinutes: 30, contact: '' });
  const [err, setErr] = useState(null);
  const [done, setDone] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault(); setBusy(true); setErr(null);
    try { await login(f.username.trim(), f.password); } catch (x) { setErr(x); } finally { setBusy(false); }
  };
  const register = async (e) => {
    e.preventDefault(); setBusy(true); setErr(null);
    try {
      await api('/auth/register', { method: 'POST', body: reg });
      setDone('Registration received. A coordinator must approve your account before you can sign in.');
      setMode('login');
    } catch (x) { setErr(x); } finally { setBusy(false); }
  };
  const r = (k) => (e) => setReg({ ...reg, [k]: e.target.value });

  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="brand dark">
          <div className="brand-mark"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1f4d3a" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round"><path d="M5 13l4 4 10-11" /></svg></div>
          <div><b>SecondServe</b><small>Food rescue allocation</small></div>
        </div>
        <div className="tabs" style={{ marginBottom: 18 }}>
          <button className={mode === 'login' ? 'on' : ''} onClick={() => { setMode('login'); setErr(null); }}>Sign in</button>
          <button className={mode === 'register' ? 'on' : ''} onClick={() => { setMode('register'); setErr(null); setDone(null); }}>Create account</button>
        </div>
        {done && <div className="notice ok" style={{ marginBottom: 14 }}>{done}</div>}
        {mode === 'login' ? (
          <form onSubmit={submit} className="stack">
            <Field label="Username"><input autoFocus autoComplete="username" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} required /></Field>
            <Field label="Password"><input type="password" autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} required /></Field>
            <ErrorBox error={err} />
            <Button variant="primary lg" busy={busy} type="submit">Sign in</Button>
            <div className="demo-box">
              <div className="muted" style={{ marginBottom: 8 }}>Demo accounts (click to fill):</div>
              <div className="row">{DEMOS.map((d) => <button type="button" key={d.u} className={'badge ' + d.tone + ' chip'} onClick={() => setF({ username: d.u, password: d.p })}>{d.label}</button>)}</div>
            </div>
          </form>
        ) : (
          <form onSubmit={register} className="stack">
            <div className="form-grid">
              <Field label="I am a"><select value={reg.role} onChange={r('role')}><option value="DONOR">Donor (restaurant, hotel, canteen...)</option><option value="NGO">NGO / community kitchen</option></select></Field>
              <Field label="Organisation name"><input value={reg.orgName} onChange={r('orgName')} required /></Field>
              <Field label="Your name"><input value={reg.displayName} onChange={r('displayName')} required /></Field>
              <Field label="Username" hint="letters, digits, . _ -"><input value={reg.username} onChange={r('username')} required /></Field>
              <Field label="Password" hint="at least 8 characters"><input type="password" value={reg.password} onChange={r('password')} required /></Field>
              {reg.role === 'NGO' && <>
                <Field label="Meals you need per window"><input type="number" min="1" value={reg.requiredMeals} onChange={r('requiredMeals')} /></Field>
                <Field label="Max pickup capacity"><input type="number" min="1" value={reg.pickupCapacity} onChange={r('pickupCapacity')} /></Field>
                <Field label="Max travel time (min)"><input type="number" min="1" value={reg.maxTravelMinutes} onChange={r('maxTravelMinutes')} /></Field>
                <Field label="Contact (optional)"><input value={reg.contact} onChange={r('contact')} /></Field>
              </>}
            </div>
            <ErrorBox error={err} />
            <Button variant="primary lg" busy={busy} type="submit">Request account</Button>
            <div className="muted">New accounts are reviewed by a coordinator before they can sign in.</div>
          </form>
        )}
      </div>
    </div>
  );
}
