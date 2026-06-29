package io.translab.tantor.server.governance.dto;

import lombok.Data;

@Data
public class RotateSecretRequest {
    private String newValue;
}
