import { useMemo, useState } from 'react';

const COLORS = { FLOW: '#1f6b48', FEASIBLE: '#a9c4b6', CUT: '#c2570c', UNAVAILABLE: '#b7bdb3', PATH: '#2b5f8a', IDLE: '#cfd5cb' };

/**
 * Layered flow-network drawing: Source -> donor batches -> NGOs -> Sink.
 * nodes: [{id,label,sub,type:'source'|'batch'|'ngo'|'sink',status}]
 * edges: [{from,to,capacity|null,flow,state,reason,label}]   state: FLOW | FEASIBLE | CUT | UNAVAILABLE | PATH | IDLE
 */
export default function NetworkGraph({ nodes, edges, showUnavailable = true, height }) {
  const [hover, setHover] = useState(null);
  const layout = useMemo(() => {
    const col = { source: [], batch: [], ngo: [], sink: [] };
    nodes.forEach((n) => col[n.type].push(n));
    const rows = Math.max(col.batch.length, col.ngo.length, 1);
    const gap = 50, H = height || rows * gap + 70;
    const X = { source: 70, batch: 300, ngo: 690, sink: 920 };
    const pos = {};
    for (const t of Object.keys(col)) {
      const list = col[t];
      const span = (list.length - 1) * gap;
      list.forEach((n, i) => { pos[n.id] = { x: X[t], y: H / 2 - span / 2 + i * gap }; });
    }
    return { pos, H, W: 1000 };
  }, [nodes, height]);

  const { pos, H, W } = layout;
  const visible = edges.filter((e) => showUnavailable || e.state !== 'UNAVAILABLE');
  const hl = (e) => hover && (e.from === hover || e.to === hover);
  const order = { UNAVAILABLE: 0, IDLE: 0, FEASIBLE: 1, FLOW: 2, CUT: 3, PATH: 4 };
  const sorted = [...visible].sort((a, b) => order[a.state] - order[b.state]);

  return (
    <div className="graph-wrap">
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" style={{ minWidth: 820, display: 'block' }} role="img" aria-label="Flow network from source through donor batches and NGOs to sink">
        {sorted.map((e, i) => {
          const a = pos[e.from], b = pos[e.to];
          if (!a || !b) return null;
          const x1 = a.x + (e.from === 'S' || e.from === 'Source' ? 24 : 78), x2 = b.x - (e.to === 'T' || e.to === 'Sink' ? 24 : 78);
          const dx = (x2 - x1) / 2;
          const d = `M${x1},${a.y} C${x1 + dx},${a.y} ${x2 - dx},${b.y} ${x2},${b.y}`;
          const color = COLORS[e.state] || COLORS.IDLE;
          const w = e.state === 'UNAVAILABLE' ? 1 : e.state === 'FEASIBLE' || e.state === 'IDLE' ? 1.4 : e.state === 'PATH' ? 3.6 : e.state === 'CUT' ? 3.4 : 1.8 + Math.min(3, Math.sqrt(e.flow || 0) / 3);
          const faded = hover && !hl(e);
          const showLabel = e.label !== undefined ? e.label !== '' : e.flow > 0 || e.state === 'CUT' || (e.capacity != null && e.state !== 'UNAVAILABLE');
          const text = e.label !== undefined ? e.label : e.capacity == null ? `${e.flow}` : `${e.flow}/${e.capacity}`;
          return (
            <g key={i} opacity={faded ? 0.12 : e.state === 'UNAVAILABLE' ? 0.55 : 1}>
              <path d={d} fill="none" stroke={color} strokeWidth={w} strokeDasharray={e.state === 'UNAVAILABLE' ? '3 4' : undefined} strokeLinecap="round">
                {e.reason && <title>{e.reason}</title>}
              </path>
              {showLabel && (e.state !== 'FEASIBLE' || e.capacity != null) && (
                <text x={(x1 + x2) / 2} y={(a.y + b.y) / 2 - 4}
                  fontSize="11" fill={color === COLORS.FEASIBLE ? '#6b7a71' : color} textAnchor="middle" fontWeight="600" paintOrder="stroke" stroke="#fcfdfb" strokeWidth="3">{text}</text>
              )}
            </g>
          );
        })}
        {nodes.map((n) => {
          const p = pos[n.id];
          const terminal = n.type === 'source' || n.type === 'sink';
          const expired = n.status === 'EXPIRED';
          return (
            <g key={n.id} transform={`translate(${p.x},${p.y})`} onMouseEnter={() => setHover(n.id)} onMouseLeave={() => setHover(null)} style={{ cursor: 'default' }}>
              {terminal ? (
                <><circle r="24" fill="#1f4d3a" /><text textAnchor="middle" dy="4" fill="#fff" fontSize="11" fontWeight="700">{n.type === 'source' ? 'SRC' : 'SINK'}</text></>
              ) : (
                <>
                  <rect x="-78" y="-19" width="156" height="38" rx="9" fill={expired ? '#eceeea' : n.type === 'batch' ? '#e6efe9' : '#e6eef5'} stroke={expired ? '#c9cfc5' : n.type === 'batch' ? '#a9cbb7' : '#b7cfe3'} />
                  <text x="0" y="-3" textAnchor="middle" fontSize="12" fontWeight="700" fill={expired ? '#8a948d' : '#15211b'}>{n.label}{expired ? ' (expired)' : ''}</text>
                  <text x="0" y="12" textAnchor="middle" fontSize="9.5" fill="#5b6a61">{n.sub.length > 30 ? n.sub.slice(0, 29) + '…' : n.sub}</text>
                  <title>{n.label} - {n.sub}</title>
                </>
              )}
            </g>
          );
        })}
        <text x="300" y="16" textAnchor="middle" fontSize="11" fill="#7b887f" fontWeight="600">DONOR BATCHES</text>
        <text x="690" y="16" textAnchor="middle" fontSize="11" fill="#7b887f" fontWeight="600">RECIPIENT NGOs</text>
      </svg>
    </div>
  );
}

export function GraphLegend() {
  const items = [['FLOW', 'Carries meals'], ['FEASIBLE', 'Feasible, unused'], ['CUT', 'Bottleneck (min-cut edge)'], ['UNAVAILABLE', 'Not feasible (hover for reason)']];
  return <div className="legend">{items.map(([k, t]) => <span key={k}><i style={{ borderColor: COLORS[k], borderTopStyle: k === 'UNAVAILABLE' ? 'dashed' : 'solid' }} />{t}</span>)}</div>;
}
