# Tantor Kafka BFSI Production Hardening Delta - V12

This update hardens the uploaded `Tantor_kafka_bfsi_hardened.zip` beyond the earlier governance skeleton.

## What was strengthened

### 1. API security is no longer open by default
Earlier, several `/api/v1/ui/**` and `/api/v1/clusters/**` endpoints were permitted without authentication. The updated `SecurityConfig` now requires JWT authentication for all `/api/v1/**` application APIs except login, health/info, and the dedicated agent path.

### 2. Dedicated agent authentication
Agents now authenticate using a machine token:

```text
X-Tantor-Agent-Token: <TANTOR_AGENT_TOKEN>
X-Tantor-Agent-Host: <host_id>
```

The backend validates this token in constant time via `AgentAuthenticationFilter`. The agent client sends these headers for register, heartbeat, task polling, task result, step reporting, and artifact download calls.

Recommended production setup:

```bash
export TANTOR_AGENT_TOKEN='<random-64-char-secret-from-vault>'
export TANTOR_AGENT_AUTH_ENABLED=true
```

### 3. Startup hardening guardrails
`StartupHardeningVerifier` checks for unsafe defaults in:

```text
TANTOR_JWT_SECRET
TANTOR_AGENT_TOKEN
TANTOR_SECRETS_MASTER_KEY
```

Use this in production/DR:

```bash
export TANTOR_FAIL_ON_DEFAULT_SECRETS=true
```

### 4. Migration normalization
`V12__production_schema_normalization.sql` fixes the biggest production risk in the previous hardened ZIP: schema drift caused by V10 and V11 creating overlapping table names with different columns.

Normalized areas:

```text
approval_requests
operation_locks
idempotency_keys
audit_logs
secret_references
clusters
hosts
```

It also adds first-class metadata tables for:

```text
agent_credentials
agent_registration_requests
kraft_cluster_metadata
kraft_nodes
kraft_storage_formats
zookeeper_ensembles
zookeeper_nodes
kafka_broker_zk_mappings
topology_validation_results
```

### 5. UI authentication propagation
Most UI pages used raw `fetch`. The new `src/lib/fetchWithAuth.ts` globally attaches the JWT bearer token to `/api/v1/**` calls and redirects to `/login` on 401.

### 6. Production secret handling correction
Secret reference saves now write both normalized forms:

```text
reference_id
external_ref
```

No raw secret value should be stored in DB.

## Required runtime variables

Minimum production values:

```bash
export TANTOR_DB_URL='jdbc:postgresql://<db-host>:5432/tantor'
export TANTOR_DB_USER='<db-user>'
export TANTOR_DB_PASSWORD='<from-vault>'
export TANTOR_JWT_SECRET='<base64-encoded-strong-secret>'
export TANTOR_AGENT_TOKEN='<random-64-char-secret>'
export TANTOR_SECRETS_MASTER_KEY='<random-32-byte-or-strong-key>'
export TANTOR_FAIL_ON_DEFAULT_SECRETS=true
export TANTOR_SWAGGER_PUBLIC=false
```

## Agent config

Agent config now supports:

```yaml
agent:
  agent_token: "${TANTOR_AGENT_TOKEN}"
  insecure_skip_verify: false
```

For production, use TLS/mTLS and keep `insecure_skip_verify: false`.

## Validation notes

The sandbox could not run full Maven/Go/NPM builds because Maven is unavailable and Go/NPM dependencies could not be downloaded. Run these in a connected build environment:

```bash
cd tantor-server && mvn clean package -DskipTests
cd ../tantor-agent && go test ./...
cd ../tantor-ui && npm install && npm run build
```
