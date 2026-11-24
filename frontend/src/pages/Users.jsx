import { useState } from 'react';
import { api, fmtTime } from '../api.js';
import { Badge, Button, ErrorBox, Loading, useLoad, useToast } from '../ui.jsx';

const TONE = { ACTIVE: 'green', PENDING: 'amber', REJECTED: 'red', DISABLED: 'red' };

export default function Users() {
  const [tab, setTab] = useState('users');
  const users = useLoad(() => api('/admin/users'));
  const audit = useLoad(() => api('/admin/audit'));
  const notify = useToast();
  const act = async (u, action, msg) => {
    try { await api(`/admin/users/${u.userId}/${action}`, { method: 'POST', body: {} }); notify(msg); users.reload(); audit.reload(); }
    catch (x) { notify(x.message, 'err'); }
  };
  return (
    <>
      <div className="page-head"><div><h1>Users &amp; audit</h1><p>Approve new donor and NGO registrations, disable accounts, and review who did what.</p></div>
        <div className="tabs"><button className={tab === 'users' ? 'on' : ''} onClick={() => setTab('users')}>Accounts</button><button className={tab === 'audit' ? 'on' : ''} onClick={() => setTab('audit')}>Audit log</button></div></div>
      {tab === 'users' ? (users.loading && !users.data ? <Loading /> : (
        <div className="card flush scroll"><ErrorBox error={users.error} />
          <table><thead><tr><th>Username</th><th>Name</th><th>Role</th><th>Organisation</th><th>Status</th><th></th></tr></thead>
            <tbody>{users.data?.map((u) => (
              <tr key={u.userId}><td><b>{u.username}</b></td><td>{u.displayName}</td><td><Badge tone={u.role === 'ADMIN' ? 'green' : u.role === 'NGO' ? 'blue' : 'amber'}>{u.role}</Badge></td><td>{u.orgName || '-'}{u.ngoId && <div className="muted">{u.ngoId}</div>}</td><td><Badge tone={TONE[u.status]}>{u.status}</Badge></td>
                <td><div className="row" style={{ justifyContent: 'flex-end' }}>
                  {u.status === 'PENDING' && <><Button size="sm" variant="primary" onClick={() => act(u, 'approve', `${u.username} approved`)}>Approve</Button><Button size="sm" variant="danger" onClick={() => act(u, 'reject', `${u.username} rejected`)}>Reject</Button></>}
                  {u.status === 'ACTIVE' && u.role !== 'ADMIN' && <Button size="sm" onClick={() => act(u, 'disable', `${u.username} disabled`)}>Disable</Button>}
                  {u.status === 'DISABLED' && <Button size="sm" onClick={() => act(u, 'enable', `${u.username} re-enabled`)}>Enable</Button>}
                </div></td></tr>))}</tbody></table></div>
      )) : (audit.loading && !audit.data ? <Loading /> : (
        <div className="card flush scroll"><table><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Detail</th></tr></thead>
          <tbody>{audit.data?.map((e) => <tr key={e.id}><td>{fmtTime(e.time)}</td><td><b>{e.actor}</b> <span className="muted">{e.role}</span></td><td><Badge>{e.action}</Badge></td><td className="muted">{e.detail}</td></tr>)}</tbody></table></div>
      ))}
    </>
  );
}
