import { useEffect, useState } from 'react';
import { apiFetch, hasPermission, getSession } from '../lib/api';
import { CheckCircle, XCircle, Clock, RefreshCw } from 'lucide-react';

interface Approval {
  id: string;
  actionType: string;
  resourceType: string;
  resourceId?: string;
  environment?: string;
  requestedBy: string;
  approvedBy?: string;
  status: string;
  rejectionReason?: string;
  requestedAt?: string;
}

export function Approvals() {
  const [rows, setRows] = useState<Approval[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const canDecide = hasPermission('APPROVAL_DECIDE');
  const me = getSession();

  const load = async () => {
    setLoading(true); setError('');
    try {
      const res = await apiFetch('/api/v1/approvals');
      setRows(await res.json());
    } catch (e: any) { setError(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const decide = async (id: string, action: 'approve' | 'reject') => {
    try {
      const body = action === 'reject'
        ? JSON.stringify({ reason: prompt('Reason for rejection?') || 'No reason given' })
        : undefined;
      const res = await apiFetch(`/api/v1/approvals/${id}/${action}`, { method: 'POST', body });
      if (!res.ok) {
        const b = await res.json().catch(() => ({}));
        throw new Error(b.error || `Failed (${res.status})`);
      }
      load();
    } catch (e: any) { alert(e.message); }
  };

  const badge = (s: string) => {
    const color = s === 'APPROVED' ? '#28a745' : s === 'REJECTED' ? '#e25555'
      : s === 'PENDING' ? '#d39e00' : '#888';
    return <span style={{ color, fontWeight: 600 }}>{s}</span>;
  };

  return (
    <div className="animate-fade-in glass-panel" style={{ padding: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ margin: 0 }}>Maker-Checker Approvals</h2>
        <button onClick={load} style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <RefreshCw size={15} /> Refresh
        </button>
      </div>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>
        Risky actions in approval-required environments (SIT/UAT/PROD/DR) wait here. A checker
        other than the requester must approve before execution.
      </p>
      {error && <div style={{ color: '#e25555', marginBottom: 12 }}>{error}</div>}
      {loading ? <p>Loading…</p> : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '1px solid var(--border, #2a2a3a)' }}>
              <th style={{ padding: 8 }}>Action</th><th>Resource</th><th>Env</th>
              <th>Requested by</th><th>Status</th><th>Decision</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(r => (
              <tr key={r.id} style={{ borderBottom: '1px solid var(--border, #20202c)' }}>
                <td style={{ padding: 8 }}>{r.actionType}</td>
                <td>{r.resourceType}{r.resourceId ? ` · ${r.resourceId}` : ''}</td>
                <td>{r.environment}</td>
                <td>{r.requestedBy}</td>
                <td><Clock size={12} style={{ verticalAlign: -1 }} /> {badge(r.status)}</td>
                <td>
                  {r.status === 'PENDING' && canDecide && r.requestedBy !== me?.username ? (
                    <span style={{ display: 'flex', gap: 8 }}>
                      <button onClick={() => decide(r.id, 'approve')} title="Approve"
                        style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                        <CheckCircle size={14} /> Approve
                      </button>
                      <button onClick={() => decide(r.id, 'reject')} title="Reject"
                        style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                        <XCircle size={14} /> Reject
                      </button>
                    </span>
                  ) : r.status === 'PENDING' && r.requestedBy === me?.username ? (
                    <em style={{ color: 'var(--text-secondary)' }}>Awaiting another checker</em>
                  ) : (
                    <span style={{ color: 'var(--text-secondary)' }}>
                      {r.approvedBy ? `by ${r.approvedBy}` : '—'}
                      {r.rejectionReason ? ` · ${r.rejectionReason}` : ''}
                    </span>
                  )}
                </td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={6} style={{ padding: 16, color: 'var(--text-secondary)' }}>No approval requests.</td></tr>}
          </tbody>
        </table>
      )}
    </div>
  );
}
