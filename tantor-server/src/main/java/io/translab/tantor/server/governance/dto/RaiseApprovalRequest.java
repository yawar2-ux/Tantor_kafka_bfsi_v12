package io.translab.tantor.server.governance.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class RaiseApprovalRequest {
    private String actionType;
    private String resourceType;
    private String resourceId;
    private String environment;
    private String payloadJson;
    private UUID jobId;
    private String idempotencyKey;
}
