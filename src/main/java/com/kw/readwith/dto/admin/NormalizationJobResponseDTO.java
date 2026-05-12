package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizationJobResponseDTO {
    private Long id;
    private Long bookId;
    private String bookTitle;
    private ProcessingPipelineType pipelineType;
    private String runId;
    private String sourceVersion;
    private String artifactPath;
    private ProcessingJobStatus status;
    private String currentStep;
    private String failureCode;
    private String failureMessage;
    private String triggeredBy;
    private String ruleVersion;
    private String locatorVersion;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
