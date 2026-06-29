package io.translab.tantor.server.dto;

import lombok.Data;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TaskStepReportDto {
    private String taskId;
    private String jobId;
    private String stepId;
    private String hostId;
    private String stepCode;
    private String stepName;
    private String component;
    private String status;
    private String logOutput;
    private String logFilePath;
    private String errorCode;
    private String errorMsg;
}
