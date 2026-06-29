import { useEffect, useState } from 'react';
import { apiFetch } from '../lib/api';
import { KeyRound, Plus, RefreshCw } from 'lucide-react';

interface SecretRef {
  id: string; secretName: string; secretType: string;
  provider: string; environment?: string; createdBy?: string; rotatedAt?: string;
}

export function Secrets() {
  const [rows, setRows] = useState<SecretRef[]>([]);
  const [error, setError] = useState('');
  const [form, setForm] = useState({ secretName: '', secretType: 'SASL', secretValue: '', environment: 'PROD' });

  const load = async () => {
    try { setRows(await apiFetch('/api/v1/secrets').then(r => r.json())); }
    catch (e: any) { setError(e.message); }
  };
  useEffect(() => { load(); }, []);

  const create = async () => {
    try {
      const res = await apiFetch('/api/v1/secrets', { method: 'POST', body: JSON.stringify(form) });
      if (!res.ok) throw new Error(`Failed (${res.status})`);
      setForm({ secretName: '', secretType: 'SASL', secretValue: '', environment: 'PROD' });
      load();
    } catch (e: any) { alert(e.message); }
  };

  return (
    <div className="animate-fade-in glass-panel" style={{ padding: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0, display: 'flex', gap: 8, alignItems: 'center' }}><KeyRound size={18} /> Secret References</h2>
        <button onClick={load}><RefreshCw size={15} /></button>
      </div>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>
        Only references are stored here. Secret values are written to the configured vault
        (LOCAL_VAULT / HashiCorp / CyberArk / AWS / Azure) and never returned by the API.
      </p>
      {error && <div style={{ color: '#e25555' }}>{error}</div>}

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', margin: '12px 0' }}>
        <input placeholder="Name" value={form.secretName}
          onChange={e => setForm({ ...form, secretName: e.target.value })} />
        <select value={form.secretType} onChange={e => setForm({ ...form, secretType: e.target.value })}>
          {['KEYSTORE_PASSWORD', 'TRUSTSTORE_PASSWORD', 'SASL', 'AGENT_TOKEN', 'DB', 'LDAP'].map(t => <option key={t}>{t}</option>)}
        </select>
        <select value={form.environment} onChange={e => setForm({ ...form, environment: e.target.value })}>
          {['DEV', 'SIT', 'UAT', 'PROD', 'DR'].map(t => <option key={t}>{t}</option>)}
        </select>
        <input placeholder="Value (write-only)" type="password" value={form.secretValue}
          onChange={e => setForm({ ...form, secretValue: e.target.value })} />
        <button onClick={create} style={{ display: 'flex', gap: 4, alignItems: 'center' }}><Plus size={14} /> Add</button>
      </div>

      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead><tr style={{ textAlign: 'left' }}>
          <th style={{ padding: 8 }}>Name</th><th>Type</th><th>Provider</th><th>Env</th><th>Created by</th>
        </tr></thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.id} style={{ borderBottom: '1px solid var(--border, #20202c)' }}>
              <td style={{ padding: 8 }}>{r.secretName}</td><td>{r.secretType}</td>
              <td>{r.provider}</td><td>{r.environment}</td><td>{r.createdBy}</td>
            </tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={5} style={{ padding: 16, color: 'var(--text-secondary)' }}>No secrets registered.</td></tr>}
        </tbody>
      </table>
    </div>
  );
}
