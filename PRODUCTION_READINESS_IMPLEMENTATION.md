# Tantor Kafka Production Readiness Implementation

This update adds the production-grade control-plane foundation requested in `Pasted text(268).txt`.

## Implemented production layers

1. **Job Engine and Step-Level Tracking**
   - `job_master`, `job_steps`, `job_step_events`.
   - Agent endpoint: `POST /api/v1/agents/tasks/step`.
   - UI APIs: `GET /api/v1/ui/clusters/{clusterId}/jobs`, `GET /api/v1/ui/clusters/{clusterId}/jobs/{jobId}`.

2. **Retry / Resume / Rollback / Cleanup framework**
   - `job_step_artifacts`, `rollback_plans`, `rollback_steps`, `cleanup_plans`, `cleanup_steps`.
   - APIs under `/api/v1/ui/production/jobs/{jobId}/...`.
   - Rollback plans are generated from tracked artifacts and preserve logs/config copies.

3. **KRaft quorum validation**
   - Validates unique `node.id`, odd controller count, PROD minimum 3 controllers, generated `controller.quorum.voters`, and role correctness.
   - API: `POST /api/v1/ui/production/validations/kraft`.
   - Deployment request validation blocks unsafe 2-controller PROD KRaft.

4. **ZooKeeper mode validation**
   - Validates unique `myid`, ZooKeeper node presence, PROD 3-node minimum, unique broker IDs, same `zookeeper.connect`, and same `zoo.cfg` server list.
   - API: `POST /api/v1/ui/production/validations/zookeeper`.

5. **Package validation**
   - `kafka_package_validations` stores extension, detected version, SHA-256, extraction test status, duplicate status, malware scan status, and final package status.
   - API: `POST /api/v1/ui/production/packages/validate`.

6. **Config versioning + diff + rollback version**
   - `cluster_config_versions` stores old config, new config, diff, checksum, version, approval/apply status.
   - API: `POST /api/v1/ui/production/configs/versions`.

7. **Rolling restart safety**
   - Rolling restart now performs a health check before the restart sequence and before each broker restart.
   - Safety intent: no under-replicated partitions and cluster re-healthy after every broker.

8. **External cluster mode support**
   - DB model supports `management_level` so clusters can be `BOOTSTRAP_ONLY`, `AGENT_MANAGED`, or `INTERNAL_MANAGED`.

9. **Secrets management references**
   - `secret_references` stores only vault/external references; no secret plaintext is stored.
   - API: `POST /api/v1/ui/production/secrets/references`.

10. **Governance**
   - `approval_requests`, `operation_locks`, `idempotency_keys`, `review_plans`.
   - APIs for approval, locks, and review plans.

11. **Audit/alerting/log/DR foundations**
   - Existing audit/activity stays intact.
   - Added `backup_records`, `centralized_log_references`, and health/reconciliation tables.

12. **Reconciliation, maintenance, decommission**
   - `reconciliation_findings`, `host_prerequisite_checks`, `cluster_health_snapshots`, `decommission_plans`.
   - APIs for maintenance mode, decommission plan, health snapshot, reconciliation, and backup records.

## Important production note

This update adds server-side tables, services, APIs, and validation guards. Real BFSI go-live still needs environment-specific hardening:

- mTLS certificates and trust chain.
- Vault/CyberArk integration for actual secret retrieval.
- Immutable audit sink or WORM storage.
- HA DB and control-plane deployment.
- Real malware scanning integration.
- Real partition reassign/drain for broker decommission.
- CI/CD build verification in your connected environment.
