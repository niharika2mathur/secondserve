import { api, fmtTime } from '../api.js';
import { Badge, Button, Empty, ErrorBox, Loading, useLoad } from '../ui.jsx';

const tone = { DELIVERY: 'blue', BATCH: 'green', APPROVAL: 'amber', ACCOUNT: 'green' };

export default function Notifications({ refreshCount }) {
  const { data, error, loading, reload } = useLoad(() => api('/notifications'));
  const readAll = async () => { await api('/notifications/read-all', { method: 'POST', body: {} }); reload(); refreshCount && refreshCount(); };
  if (loading && !data) return <Loading />;
  return (
    <>
      <div className="page-head"><div><h1>Notifications</h1><p>Updates about your deliveries, batches and account.</p></div><Button onClick={readAll} disabled={!data?.some((n) => !n.read)}>Mark all as read</Button></div>
      <ErrorBox error={error} />
      <div className="card flush">
        {data && data.length === 0 ? <Empty title="Nothing yet">Notifications appear here when a plan is published or a delivery changes.</Empty> : (
          <ul className="notif">{data?.map((n) => (
            <li key={n.id} className={n.read ? '' : 'unread'}>
              <div className="row" style={{ justifyContent: 'space-between' }}><b>{n.title}</b><span className="muted">{fmtTime(n.time)}</span></div>
              <div>{n.message}</div><Badge tone={tone[n.type] || ''}>{n.type.toLowerCase()}</Badge>
            </li>))}</ul>
        )}
      </div>
    </>
  );
}
