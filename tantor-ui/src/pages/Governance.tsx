import { useEffect, useState } from 'react';
import { apiFetch } from '../lib/api';
import { ShieldCheck, Lock, FileLock2, GitCompare, Scale } from 'lucide-react';

export function Governance() {
  const [compliance, setCompliance] = useState<any>(null);
  const [policies, setPolicies] = useState<any[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const [c, p] = await Promise.all([
          apiFetch('/api/v1/governance/compliance').then(r => r.json()),
          apiFetch('/api/v1/governance/environment-policies').then(r => r.json()),
        ]);
        setCompliance(c); setPolicies(p);
      } catch (e: any) { setError(e.message); }
    })();
  }, []);

  if (error) return <div className="glass-panel" style={{ padding: '1.5rem', color: '#e25555' }}>{error}</div>;
  if (!compliance) return <div className="glass-panel" style={{ padding: '1.5rem' }}>Loading…</div>;

  const Card = ({ icon, title, children }: any) => (
    <div className="glass-panel" style={{ padding: '1.1rem', flex: '1 1 280px' }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>{icon}<strong>{title}</strong></div>
      <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{children}</div>
    </div>
  );

  return (
    <div className="animate-fade-in">
      <h2 style={{ marginTop: 0 }}>BFSI Governance & Compliance</h2>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14 }}>
        <Card icon={<ShieldCheck size={18} />} title="Maker-Checker">
          Enabled · {compliance.makerChecker.pendingApprovals} pending<br />
          {compliance.makerChecker.segregationOfDuties}
        </Card>
        <Card icon={<FileLock2 size={18} />} title="Immutable Audit">
          {compliance.immutableAudit.enforcedBy}
        </Card>
        <Card icon={<Lock size={18} />} title="Secrets">
          Raw secrets in DB: {String(compliance.secretsManagement.rawSecretsInDb)}<br />
          Providers: {compliance.secretsManagement.providersSupported.join(', ')}
        </Card>
        <Card icon={<Scale size={18} />} title="RBAC">
          Roles: {compliance.rbac.roles.join(', ')}<br />
          Permissions: {compliance.rbac.permissionCount}
        </Card>
        <Card icon={<GitCompare size={18} />} title="Drift Detection">
          Open drift records: {compliance.driftDetection.openDrift}
        </Card>
      </div>

      <h3 style={{ marginTop: 24 }}>Environment Policies</h3>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead><tr style={{ textAlign: 'left' }}>
          <th style={{ padding: 8 }}>Environment</th><th>Approval</th><th>Min approvers</th>
          <th>Audit retention (days)</th><th>Separate creds</th>
        </tr></thead>
        <tbody>
          {policies.map(p => (
            <tr key={p.environment} style={{ borderBottom: '1px solid var(--border, #20202c)' }}>
              <td style={{ padding: 8 }}><strong>{p.environment}</strong></td>
              <td>{p.requiresApproval ? 'Required' : 'Optional'}</td>
              <td>{p.minApprovers}</td>
              <td>{p.auditRetentionDays}</td>
              <td>{p.separateCredentials ? 'Yes' : 'No'}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3 style={{ marginTop: 24 }}>Regulatory Mapping</h3>
      <ul style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
        {compliance.regulatoryMapping.map((r: string) => <li key={r}>{r}</li>)}
      </ul>
    </div>
  );
}
