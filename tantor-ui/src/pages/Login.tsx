import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../lib/api';
import { ShieldCheck } from 'lucide-react';

export function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  const submit = async () => {
    setBusy(true); setError('');
    try {
      await login(username, password);
      navigate('/governance');
    } catch (e: any) {
      setError(e.message || 'Login failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="glass-panel animate-fade-in" style={{ maxWidth: 420, margin: '4rem auto', padding: '2rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18 }}>
        <ShieldCheck size={22} />
        <h2 style={{ margin: 0 }}>Sign in</h2>
      </div>
      <p style={{ color: 'var(--text-secondary)', fontSize: 13, marginBottom: 18 }}>
        Governance, approvals, audit and secrets require authentication.
      </p>
      <label style={{ fontSize: 13 }}>Username</label>
      <input value={username} onChange={e => setUsername(e.target.value)}
        style={{ width: '100%', padding: 10, margin: '6px 0 14px', borderRadius: 8 }} />
      <label style={{ fontSize: 13 }}>Password</label>
      <input type="password" value={password} onChange={e => setPassword(e.target.value)}
        onKeyDown={e => e.key === 'Enter' && submit()}
        style={{ width: '100%', padding: 10, margin: '6px 0 14px', borderRadius: 8 }} />
      {error && <div style={{ color: '#e25555', fontSize: 13, marginBottom: 12 }}>{error}</div>}
      <button onClick={submit} disabled={busy}
        style={{ width: '100%', padding: 12, borderRadius: 8, cursor: 'pointer' }}>
        {busy ? 'Signing in…' : 'Sign in'}
      </button>
      <p style={{ color: 'var(--text-secondary)', fontSize: 12, marginTop: 16 }}>
        Use your assigned RBAC account. Default seed credentials must be rotated or disabled before any non-dev use.
      </p>
    </div>
  );
}
