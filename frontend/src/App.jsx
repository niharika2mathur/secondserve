import { useCallback, useEffect, useState } from 'react';
import { api } from './api.js';
import { AuthProvider, useAuth } from './auth.jsx';
import { ToastHost } from './ui.jsx';
import Login from './pages/Login.jsx';
import AdminHome from './pages/AdminHome.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Donors from './pages/Donors.jsx';
import Ngos from './pages/Ngos.jsx';
import Travel from './pages/Travel.jsx';
import Allocation from './pages/Allocation.jsx';
import Results from './pages/Results.jsx';
import Lab from './pages/Lab.jsx';
import Benchmarks from './pages/Benchmarks.jsx';
import Deliveries from './pages/Deliveries.jsx';
import Insights from './pages/Insights.jsx';
import Analytics from './pages/Analytics.jsx';
import Users from './pages/Users.jsx';
import DonorHome from './pages/DonorHome.jsx';
import MyBatches from './pages/MyBatches.jsx';
import NgoHome from './pages/NgoHome.jsx';
import MyNeeds from './pages/MyNeeds.jsx';
import Notifications from './pages/Notifications.jsx';

const I = (d) => <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d={d} /></svg>;
const ICON = {
  home: 'M3 12l9-8 9 8M5 10v10h5v-6h4v6h5V10', list: 'M4 7h16M4 12h16M4 17h10', heart: 'M12 21s-7-4.5-7-10a4 4 0 017-2.6A4 4 0 0119 11c0 5.5-7 10-7 10z',
  truck: 'M5 17a2 2 0 104 0 2 2 0 00-4 0zM15 17a2 2 0 104 0 2 2 0 00-4 0zM3 17V6h11v11M14 9h4l3 4v4h-2', flow: 'M5 5h4v4H5zM15 5h4v4h-4zM10 15h4v4h-4zM9 7h6M7 9v3h10V9M12 12v3',
  doc: 'M5 4h14v16H5zM9 9h6M9 13h6M9 17h3', lab: 'M9 3h6M10 3v6l-5 9a2 2 0 002 3h10a2 2 0 002-3l-5-9V3', bars: 'M4 20V10M10 20V4M16 20v-8M22 20H2',
  box: 'M3 7l9-4 9 4v10l-9 4-9-4zM3 7l9 4 9-4M12 11v10', bulb: 'M9 18h6M10 21h4M12 3a6 6 0 00-4 10.5c.7.8 1 1.5 1 2.5h6c0-1 .3-1.7 1-2.5A6 6 0 0012 3z',
  chart: 'M3 3v18h18M7 15l4-4 3 3 5-6', users: 'M16 19v-1a4 4 0 00-4-4H7a4 4 0 00-4 4v1M9.5 10a3 3 0 100-6 3 3 0 000 6zM21 19v-1a4 4 0 00-3-3.9M16 4.1a3 3 0 010 5.8', bell: 'M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 01-3.4 0',
  user: 'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z', gear: 'M12 15a3 3 0 100-6 3 3 0 000 6zM19 12h2M3 12h2M12 3v2M12 19v2M5.6 5.6L7 7M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4',
};

const ADMIN = [
  { id: 'dashboard', label: 'Dashboard', icon: 'home', C: AdminHome, group: 'Overview' },
  { id: 'operations', label: 'Operations', icon: 'bars', C: Dashboard },
  { id: 'donors', label: 'Donors', icon: 'list', C: Donors, group: 'Manage' },
  { id: 'ngos', label: 'NGOs', icon: 'heart', C: Ngos },
  { id: 'travel', label: 'Travel & Feasibility', icon: 'truck', C: Travel },
  { id: 'allocation', label: 'Allocation Engine', icon: 'flow', C: Allocation, group: 'Plan & dispatch' },
  { id: 'results', label: 'Results & Plan', icon: 'doc', C: Results },
  { id: 'deliveries', label: 'Deliveries', icon: 'box', C: Deliveries },
  { id: 'insights', label: 'What-if Insights', icon: 'bulb', C: Insights, group: 'Insight' },
  { id: 'analytics', label: 'Impact Analytics', icon: 'chart', C: Analytics },
  { id: 'users', label: 'Users & Audit', icon: 'users', C: Users, group: 'Administration' },
  { id: 'lab', label: 'Algorithm Lab', icon: 'lab', C: Lab, group: 'Algorithms' },
  { id: 'benchmarks', label: 'Benchmarks', icon: 'bars', C: Benchmarks },
];
const DONOR = [
  { id: 'dashboard', label: 'My dashboard', icon: 'home', C: DonorHome },
  { id: 'batches', label: 'My batches', icon: 'list', C: MyBatches },
  { id: 'deliveries', label: 'Deliveries', icon: 'box', C: Deliveries },
];
const NGO = [
  { id: 'dashboard', label: 'My dashboard', icon: 'home', C: NgoHome },
  { id: 'needs', label: 'My needs', icon: 'gear', C: MyNeeds },
  { id: 'deliveries', label: 'Incoming deliveries', icon: 'box', C: Deliveries },
];
const NAV = { ADMIN: ADMIN, DONOR: DONOR, NGO: NGO };
const ROLE_LABEL = { ADMIN: 'Coordinator', DONOR: 'Donor', NGO: 'NGO' };

const current = () => (window.location.hash.replace(/^#\/?/, '') || 'dashboard');

function Shell() {
  const { user, logout } = useAuth();
  const [route, setRoute] = useState(current());
  const [unread, setUnread] = useState(0);
  const refreshCount = useCallback(() => { api('/notifications').then((l) => setUnread(l.filter((n) => !n.read).length)).catch(() => {}); }, []);
  useEffect(() => {
    const h = () => { setRoute(current()); window.scrollTo(0, 0); };
    window.addEventListener('hashchange', h);
    return () => window.removeEventListener('hashchange', h);
  }, []);
  useEffect(() => { refreshCount(); const t = setInterval(refreshCount, 30000); return () => clearInterval(t); }, [refreshCount, route]);

  const pages = NAV[user.role];
  const all = [...pages, { id: 'notifications', label: 'Notifications', C: Notifications }];
  const page = all.find((p) => p.id === route) || pages[0];
  const Page = page.C;
  return (
    <div className="app">
      <nav className="side" aria-label="Main navigation">
        <div className="brand">
          <div className="brand-mark"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1f4d3a" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round"><path d="M5 13l4 4 10-11" /></svg></div>
          <div><b>SecondServe</b><small>Food rescue allocation</small></div>
        </div>
        {pages.map((p) => (
          <div key={p.id} style={{ display: 'contents' }}>
            {p.group && <div className="nav-group">{p.group}</div>}
            <a href={'#/' + p.id} className={'nav' + (p.id === page.id ? ' on' : '')} aria-current={p.id === page.id ? 'page' : undefined}>{I(ICON[p.icon])}{p.label}</a>
          </div>
        ))}
        <a href="#/notifications" className={'nav' + (page.id === 'notifications' ? ' on' : '')} style={{ marginTop: 6 }}>{I(ICON.bell)}Notifications{unread > 0 && <span className="count">{unread}</span>}</a>
        <div className="side-foot">
          <div className="userbox">
            <div className="row" style={{ gap: 8 }}>{I(ICON.user)}<div><b>{user.displayName}</b><div className="role-tag">{ROLE_LABEL[user.role]}{user.orgName ? ' · ' + user.orgName : ''}</div></div></div>
            <button className="btn sm signout" onClick={logout}>Sign out</button>
          </div>
        </div>
      </nav>
      <main className="main"><Page go={(id) => { window.location.hash = '#/' + id; }} user={user} refreshCount={refreshCount} /></main>
    </div>
  );
}

function Gate() {
  const { user, ready } = useAuth();
  if (!ready) return <div className="login-wrap"><div className="empty">Loading...</div></div>;
  return user ? <Shell /> : <Login />;
}

export default function App() {
  return <ToastHost><AuthProvider><Gate /></AuthProvider></ToastHost>;
}
