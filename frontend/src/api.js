export class ApiError extends Error {
  constructor(message, status, details) { super(message); this.status = status; this.details = details || []; }
}

const TOKEN_KEY = 'secondserve.token';
export const getToken = () => { try { return localStorage.getItem(TOKEN_KEY); } catch { return null; } };
export const setToken = (t) => { try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY); } catch { /* storage blocked */ } };

export async function api(path, { method = 'GET', body } = {}) {
  let res;
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (getToken()) headers.Authorization = 'Bearer ' + getToken();
  try {
    res = await fetch('/api' + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError('Cannot reach the Java backend on port 8080. Start it first (see README).', 0, []);
  }
  if (res.status === 204) return null;
  let data = null;
  try { data = await res.json(); } catch { /* empty body */ }
  if (res.status === 401 && path !== '/auth/login') { setToken(null); window.dispatchEvent(new Event('auth-expired')); }
  if (!res.ok) throw new ApiError(data?.message || `Request failed (${res.status})`, res.status, data?.details);
  return data;
}

export const fmtTime = (iso) => {
  if (!iso) return '-';
  const d = new Date(iso.length === 16 ? iso + ':00' : iso);
  return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
};
export const toInput = (iso) => (iso || '').slice(0, 16);
export const pct = (x) => (x * 100).toFixed(1) + '%';
export const num = (n) => (n == null ? '-' : Number(n).toLocaleString('en-IN'));
export const ms = (v) => (v < 0.01 ? v.toFixed(4) : v < 1 ? v.toFixed(3) : v.toFixed(2)) + ' ms';
export const bytes = (b) => (b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(2) + ' MB');
export const minutesText = (m) => (m <= 0 ? 'expired' : m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60} min`);
export const clock = (iso) => (iso ? iso.slice(11, 16) : '-');
