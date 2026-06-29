package io.translab.tantor.server.governance.dto;

import lombok.Data;

@Data
public class ApprovalDecisionRequest {
    private String reason; // required for reject
}
