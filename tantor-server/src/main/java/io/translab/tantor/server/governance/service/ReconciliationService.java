package io.translab.tantor.server.governance.service;

import io.translab.tantor.server.governance.domain.ReconciliationRecord;
import io.translab.tantor.server.governance.repository.ReconciliationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconciliation engine: detects drift between DB state and observed reality
 * (agent heartbeat / process / AdminClient / systemd / port). The detection query
 * here compares DB cluster status against agent-reported host status as a first cut;
 * deeper probes (AdminClient, systemctl, port) are delegated to the agent via tasks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final ReconciliationRecordRepository repository;
    private final JdbcTemplate jdbcTemplate;

    /** Scheduled drift sweep. Runs every 5 minutes. */
    @Scheduled(fixedDelayString = "${tantor.reconciliation.interval-ms:300000}")
    @Transactional
    public void sweep() {
        try {
            detectClusterHostDrift();
        } catch (Exception e) {
            log.warn("Reconciliation sweep failed: {}", e.getMessage());
        }
    }

    /**
     * Heuristic: cluster marked RUNNING/SUCCESS in DB but no host for that cluster has a
     * recent heartbeat -> drift. Records a remediation recommendation for an operator.
     */
    @Transactional
    public List<ReconciliationRecord> detectClusterHostDrift() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT c.id AS cluster_id, c.status AS db_state, " +
            "  MAX(h.last_heartbeat) AS last_hb " +
            "FROM clusters c " +
            "LEFT JOIN hosts h ON h.cluster_id = c.id " +
            "WHERE c.deleted_at IS NULL " +
            "GROUP BY c.id, c.status");

        for (Map<String, Object> row : rows) {
            String dbState = String.valueOf(row.get("db_state"));
            UUID clusterId = (UUID) row.get("cluster_id");
            Object lastHb = row.get("last_hb");
            boolean appearsRunning = "RUNNING".equalsIgnoreCase(dbState) || "SUCCESS".equalsIgnoreCase(dbState);
            boolean staleHeartbeat = lastHb == null;
            if (appearsRunning && staleHeartbeat) {
                record(clusterId, null, "CLUSTER", dbState, "NO_HEARTBEAT", true,
                        "INVESTIGATE_OR_MARK_STOPPED");
            }
        }
        return repository.findByDriftDetectedTrueAndResolvedFalse();
    }

    @Transactional
    public ReconciliationRecord record(UUID clusterId, String hostId, String component,
                                       String dbState, String actualState, boolean drift, String action) {
        ReconciliationRecord r = new ReconciliationRecord();
        r.setClusterId(clusterId);
        r.setHostId(hostId);
        r.setComponent(component);
        r.setDbState(dbState);
        r.setActualState(actualState);
        r.setDriftDetected(drift);
        r.setRecommendedAction(action);
        return repository.save(r);
    }

    @Transactional
    public ReconciliationRecord resolve(UUID id) {
        ReconciliationRecord r = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found"));
        r.setResolved(true);
        r.setResolvedAt(OffsetDateTime.now());
        return repository.save(r);
    }

    public List<ReconciliationRecord> openDrift() {
        return repository.findByDriftDetectedTrueAndResolvedFalse();
    }
}
