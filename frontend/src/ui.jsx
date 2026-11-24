import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';

export const Toasts = createContext(() => {});
export const useToast = () => useContext(Toasts);

export function ToastHost({ children }) {
  const [items, setItems] = useState([]);
  const id = useRef(0);
  const notify = useCallback((msg, type = 'ok') => {
    const k = ++id.current;
    setItems((x) => [...x, { k, msg, type }]);
    setTimeout(() => setItems((x) => x.filter((t) => t.k !== k)), 4800);
  }, []);
  return (
    <Toasts.Provider value={notify}>
      {children}
      <div className="toasts" role="status" aria-live="polite">{items.map((t) => <div key={t.k} className={'toast ' + t.type}>{t.msg}</div>)}</div>
    </Toasts.Provider>
  );
}

export function useLoad(fn, deps = []) {
  const [state, set] = useState({ data: null, error: null, loading: true });
  const reload = useCallback(async () => {
    set((s) => ({ ...s, loading: true }));
    try { set({ data: await fn(), error: null, loading: false }); }
    catch (e) { set({ data: null, error: e, loading: false }); }
  }, deps); // eslint-disable-line
  useEffect(() => { reload(); }, [reload]);
  return { ...state, reload };
}

export const Spinner = () => <span className="spin" aria-label="Loading" />;
export const Loading = ({ text = 'Loading...' }) => <div className="empty"><Spinner /> <div style={{ marginTop: 8 }}>{text}</div></div>;
export const Empty = ({ title, children }) => <div className="empty"><b>{title}</b>{children}</div>;

export function ErrorBox({ error, title }) {
  if (!error) return null;
  return (
    <div className="notice err" role="alert">
      <b>{title || error.message}</b>{title && <div>{error.message}</div>}
      {error.details?.length > 0 && <ul>{error.details.map((d, i) => <li key={i}>{d}</li>)}</ul>}
    </div>
  );
}

export const Badge = ({ tone = '', children }) => <span className={'badge ' + tone}>{children}</span>;
export const Stat = ({ k, v, s, tone = '' }) => <div className={'stat ' + tone}><div className="k">{k}</div><div className="v">{v}</div>{s && <div className="s">{s}</div>}</div>;
export const Bar = ({ value, max }) => <div className="bar"><i style={{ width: Math.min(100, max ? (100 * value) / max : 0) + '%' }} /></div>;

export function Button({ busy, children, variant = '', size = '', ...p }) {
  return <button className={`btn ${variant} ${size}`} disabled={busy || p.disabled} {...p}>{busy && <Spinner />}{children}</button>;
}

export function Field({ label, hint, children }) {
  return <div className="field"><label>{label}</label>{children}{hint && <span className="hint">{hint}</span>}</div>;
}

export function Modal({ title, onClose, children }) {
  useEffect(() => {
    const h = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);
  return (
    <div className="modal-bg" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}><h2>{title}</h2>{children}</div>
    </div>
  );
}

export const statusTone = (s) => ({
  ALLOCATED: 'green', FULLY_ALLOCATED: 'green', DEMAND_MET: 'green', PARTIALLY_ALLOCATED: 'amber', PARTIALLY_SERVED: 'amber', AT_PICKUP_LIMIT: 'blue',
  UNALLOCATED: 'red', NOT_SERVED: 'red', EXPIRED: 'red',
}[s] || '');
export const statusText = (s) => s.toLowerCase().replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
