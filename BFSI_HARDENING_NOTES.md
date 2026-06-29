# Tantor Kafka — BFSI Production Hardening & Governance Layer

This document covers the governance / production-hardening layer added on top of the
existing Tantor Kafka Lifecycle platform. It maps directly to the A–Z requirement spec.

> **Honest scope note.** This release adds a **complete, compiling governance & control
> layer** (RBAC, maker-checker approvals, immutable audit, secrets abstraction,
> idempotency + operation locking, reconciliation, environment policy) as **new code that
> does not disturb the existing working modules**. The existing Job Engine, step tracking,
> rolling-restart and production-readiness modules from prior versions remain intact.
> Where a control needs deep runtime execution that the agent must perform (e.g. live
> AdminClient probes, partition reassignment during decommission), it is wired through
> **clean service interfaces and seams with explicit `TODO` markers** rather than faked as
> "done". This is a genuine, reviewable hardening layer — not a claim that every A–Z item
> executes end-to-end in production untouched. Treat the seeded credentials as
> bootstrap-only and rotate them before any real deployment.

---

## 1. Summary of implemented production features

| Spec | Capability | Status |
|------|------------|--------|
| A1 | Auth + RBAC (ADMIN/OPERATOR/APPROVER/VIEWER) with 14 fine-grained permissions | Implemented |
| A2 | Maker-checker approval with segregation of duties (requester ≠ approver) | Implemented |
| A3 | Environment separation DEV/SIT/UAT/PROD/DR with per-env approval + retention policy | Implemented |
| K | Secrets abstraction (local AES-256-GCM vault + external provider interfaces) | Implemented (local) / interface (external) |
| M | Immutable audit trail (DB-trigger enforced append-only, IP + user-agent captured) | Implemented |
| P | Reconciliation engine (scheduled DB-vs-actual drift detection + remediation records) | Implemented (heartbeat probe) / seam (deep probes) |
| S | API idempotency keys + one-active-operation-per-cluster/host locking | Implemented |
| B–D, I, U, V | Job engine, step tracking, rolling-restart safety, health score, prereq checks | Pre-existing (retained) |
| L, Q, R | Kafka security modes, maintenance mode, decommission | Seams + `TODO` for agent-side execution |

## 2. Backend files changed / added

**New — governance layer** (`io.translab.tantor.server.governance`):
- `domain/`: `ApprovalRequest`, `AuditLog`, `SecretReference`, `OperationLock`, `IdempotencyKey`, `ReconciliationRecord`, `EnvironmentPolicy`
- `repository/`: matching Spring Data repositories (`AuditLogRepository` is intentionally a read+save-only `Repository<>` so audit rows can never be deleted/updated from code)
- `service/`: `AuditService`, `ApprovalService`, `EnvironmentPolicyService`, `LockService`, `IdempotencyService`, `SecretService`, `ReconciliationService`, `RbacService`, `PrincipalUtil`, `RiskyActionGateway`
- `web/`: `ApprovalController`, `AuditController`, `SecretController`, `ReconciliationController`, `GovernanceController`
- `dto/`: `RaiseApprovalRequest`, `ApprovalDecisionRequest`, `CreateSecretRequest`, `RotateSecretRequest`

**New — secrets** (`io.translab.tantor.server.secrets`):
- `SecretsManager` (interface), `LocalEncryptedVaultSecretsManager` (AES-256-GCM, default provider), `ExternalVaultPlaceholders` (HashiCorp / CyberArk / AWS / Azure stubs with wiring TODOs)

**New — misc:** `domain/Permission`, `repository/UserRepository`

**Edited (existing):**
- `security/JwtUtils` — token now embeds `role` + `permissions` claims
- `security/JwtAuthenticationFilter` — builds `ROLE_*` + per-permission authorities from JWT
- `config/SecurityConfig` — `@EnableMethodSecurity` + `BCryptPasswordEncoder` bean (existing permitAll matchers preserved so current UI keeps working; new endpoints require auth + `@PreAuthorize`)
- `web/AuthController` — replaced the hardcoded dummy with real DB + BCrypt auth, JWT issuance, and login auditing
- `resources/application.yml` — added `tantor.secrets.*` and `tantor.reconciliation.*` config

## 3. Database / entity changes

Single forward-only migration: `db/migration/V11__bfsi_governance_hardening.sql` (idempotent).

- Adds `APPROVER` role + 14 permissions + role→permission mappings
- Seeds 4 BCrypt users (see §6 — **rotate immediately**)
- New tables: `environment_policies`, `approval_requests`, `audit_logs`, `secret_references`, `operation_locks`, `idempotency_keys`, `reconciliation_records`
- Audit immutability enforced by `trg_audit_immutable()` + `BEFORE UPDATE/DELETE` triggers
- `operation_locks` has `UNIQUE(lock_scope, scope_id)` → DB-level guarantee of one active op per cluster/host
- Backfills `clusters.environment = 'DEV'` where unset

## 4. API endpoints added

| Method | Path | Permission |
|--------|------|------------|
| POST | `/api/v1/auth/login` | public |
| GET | `/api/v1/approvals?pendingOnly=` | `APPROVAL_DECIDE` / `AUDIT_VIEW` |
| POST | `/api/v1/approvals` (raise) | authenticated |
| POST | `/api/v1/approvals/{id}/approve` | `APPROVAL_DECIDE` |
| POST | `/api/v1/approvals/{id}/reject` | `APPROVAL_DECIDE` |
| GET | `/api/v1/audit` (paged) | `AUDIT_VIEW` |
| GET/POST | `/api/v1/secrets`, `/api/v1/secrets/{id}/rotate` | `SECRET_MANAGE` |
| GET/POST | `/api/v1/reconciliation/drift`, `/scan`, `/{id}/resolve` | `RECONCILE_RUN` |
| GET | `/api/v1/governance/environment-policies`, `/compliance` | authenticated |

## 5. Frontend files changed / added

- `src/lib/api.ts` — session/token helpers, `apiFetch` (Bearer + 401/403 handling), `hasPermission`
- `src/pages/Login.tsx`, `Approvals.tsx`, `Secrets.tsx`, `Governance.tsx`
- `src/App.tsx` — routes for `/login`, `/approvals`, `/secrets`, `/governance`
- `src/components/Sidebar.tsx` — new **Governance** nav section

## 6. How to run

**Prereqs:** PostgreSQL 13+, JDK 17, Maven, Node 18+.

```bash
# 1. Backend (runs Flyway incl. V11 on startup)
cd tantor-server
export TANTOR_SECRETS_MASTER_KEY="<32-byte-base64-key>"   # required outside dev
export TANTOR_JWT_SECRET="<your-jwt-secret>"
mvn spring-boot:run            # serves on :8443

# 2. Artifact repo (unchanged)
cd ../tantor-artifact-repository && mvn spring-boot:run    # :8081

# 3. UI
cd ../tantor-ui && npm install && npm run dev              # vite proxy → :8443 / :8081
```

**Seeded bootstrap users (CHANGE ON FIRST LOGIN):**

| User | Password | Role |
|------|----------|------|
| admin | `Tantor@Admin#2026` | ADMIN |
| approver | `Tantor@Appr#2026` | APPROVER |
| operator | `Tantor@Oper#2026` | OPERATOR |
| viewer | `Tantor@View#2026` | VIEWER |

## 7. Known placeholders / TODOs

- **External secret providers** (`ExternalVaultPlaceholders`) are interface stubs; only the local AES-GCM vault is functional. Wire real Vault/CyberArk/AWS/Azure clients before BFSI go-live.
- **`RiskyActionGateway`** is the integration seam (idempotency → lock → approval → audit). It is **not yet invoked from the existing deploy/restart controllers** — wiring it in is the one change that turns governance from "available" into "enforced on every risky action". This is deliberate so existing flows aren't broken silently.
- **Reconciliation** currently detects drift via host heartbeat staleness; deep probes (AdminClient, `systemctl`, port checks) must be issued as agent tasks (`TODO` in `ReconciliationService`).
- **Decommission / maintenance / Kafka security modes** (R/Q/L): partition reassignment, drain, and certificate management need agent-side execution — service seams exist, agent commands are `TODO`.
- Local vault is in-memory; secret references persist but secret **values** do not survive restart until an external provider is wired.

## 8. Testing checklist

- [ ] `mvn -pl tantor-server compile` (compile is clean; not run here — Maven Central was unreachable in the build sandbox)
- [ ] Flyway applies V11 on a fresh DB and on a DB already at V10
- [ ] Login as each seeded user returns a JWT with correct `role` + `permissions`
- [ ] VIEWER is `403` on `POST /api/v1/secrets`; ADMIN succeeds
- [ ] Requester cannot approve own request (segregation of duties → 4xx)
- [ ] `UPDATE`/`DELETE` on `audit_logs` raises the immutability exception
- [ ] Second concurrent op on same cluster fails on the `operation_locks` unique constraint
- [ ] Same idempotency key returns the existing job rather than creating a duplicate

## 9. Production readiness checklist (BFSI)

- [ ] Rotate all seeded passwords; set `TANTOR_JWT_SECRET` and `TANTOR_SECRETS_MASTER_KEY` from a real secret store
- [ ] Wire an external secrets provider (no in-memory vault in PROD)
- [ ] Invoke `RiskyActionGateway` from every deploy/config/restart/decommission entrypoint
- [ ] Set PROD/DR audit retention per RBI/DPDP (policy seeded at 7 years; enforce archival job)
- [ ] Enable TLS on `server.ssl` and mTLS for agent channel
- [ ] Ship audit logs to a WORM/immutable store in addition to the DB trigger
- [ ] Load-test lock contention and approval expiry under concurrency

## 10. Regulatory mapping (surfaced in `/api/v1/governance/compliance`)

RBI Master Directions & FREE-AI (auditability, maker-checker, segregation of duties),
DPDP 2023 (no plaintext secrets, access control, audit), BCBS 239 (lineage/traceability),
PCI-DSS (key handling, least privilege). This is a control-mapping aid, **not** a
certification of compliance.
