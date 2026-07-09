package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingJobResponseDTO {
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

    public static ProcessingJobResponseDTO from(ProcessingJob job) {
        return ProcessingJobResponseDTO.builder()
                .id(job.getId())
                .bookId(job.getBook().getId())
                .bookTitle(job.getBook().getTitle())
                .pipelineType(job.getPipelineType())
                .runId(job.getRunId())
                .sourceVersion(job.getSourceVersion())
                .artifactPath(job.getArtifactPath())
                .status(job.getStatus())
                .currentStep(job.getCurrentStep())
                .failureCode(job.getFailureCode())
                .failureMessage(job.getFailureMessage())
                .triggeredBy(job.getTriggeredBy())
                .ruleVersion(job.getRuleVersion())
                .locatorVersion(job.getLocatorVersion())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }
}
