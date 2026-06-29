import { useState } from 'react';
import './ProductionReadiness.css';

const defaultKraftPayload = JSON.stringify({
  environment: 'PROD',
  controller_port: 9093,
  services: [
    { host_id: '191', role: 'broker_controller', node_id: 1 },
    { host_id: '149', role: 'broker_controller', node_id: 2 },
    { host_id: '333', role: 'broker_controller', node_id: 3 },
  ],
}, null, 2);

const defaultZkPayload = JSON.stringify({
  environment: 'PROD',
  zookeeper_port: 2181,
  zookeeper_peer_port: 2888,
  zookeeper_election_port: 3888,
  services: [
    { host_id: '191', role: 'broker_zookeeper', node_id: 1 },
    { host_id: '149', role: 'broker_zookeeper', node_id: 2 },
    { host_id: '333', role: 'broker_zookeeper', node_id: 3 },
  ],
}, null, 2);

export function ProductionReadiness() {
  const [kraftPayload, setKraftPayload] = useState(defaultKraftPayload);
  const [zkPayload, setZkPayload] = useState(defaultZkPayload);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState('');

  async function postJson(path: string, payload: string) {
    setError('');
    setResult(null);
    try {
      const response = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: payload,
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data?.error || 'Request failed');
      setResult(data);
    } catch (err: any) {
      setError(err.message || String(err));
    }
  }

  return (
    <div className="production-readiness animate-fade-in">
      <div className="page-header">
        <div>
          <h1>Production Readiness</h1>
          <p>KRaft/ZooKeeper validation, rollback governance, approval, locks, package/config/secrets, health, reconciliation and DR APIs.</p>
        </div>
      </div>

      <div className="readiness-grid">
        <section className="readiness-card">
          <h2>KRaft Quorum Validation</h2>
          <p>Checks node.id uniqueness, controller count, PROD 3-controller rule and quorum voters.</p>
          <textarea value={kraftPayload} onChange={(e) => setKraftPayload(e.target.value)} />
          <button onClick={() => postJson('/api/v1/ui/production/validations/kraft', kraftPayload)}>Validate KRaft</button>
        </section>

        <section className="readiness-card">
          <h2>ZooKeeper Validation</h2>
          <p>Checks unique myid, ZooKeeper quorum, same zoo.cfg server list and broker ID uniqueness.</p>
          <textarea value={zkPayload} onChange={(e) => setZkPayload(e.target.value)} />
          <button onClick={() => postJson('/api/v1/ui/production/validations/zookeeper', zkPayload)}>Validate ZooKeeper</button>
        </section>
      </div>

      <section className="readiness-card full-width">
        <h2>Result</h2>
        {error && <div className="readiness-error">{error}</div>}
        <pre>{result ? JSON.stringify(result, null, 2) : 'Run a validation to see production readiness output.'}</pre>
      </section>

      <section className="readiness-card full-width">
        <h2>Implemented Production Controls</h2>
        <div className="control-list">
          <span>Job Engine</span>
          <span>Step Tracking</span>
          <span>Retry / Resume / Rollback</span>
          <span>KRaft Quorum Validation</span>
          <span>ZooKeeper Validation</span>
          <span>Package Validation</span>
          <span>Config Versioning</span>
          <span>Operation Locks</span>
          <span>Approval Workflow</span>
          <span>Secrets References</span>
          <span>Health Score</span>
          <span>Reconciliation</span>
          <span>Maintenance Mode</span>
          <span>Decommission Plan</span>
          <span>Backup Records</span>
        </div>
      </section>
    </div>
  );
}
