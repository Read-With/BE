package com.kw.readwith.dto.admin;

import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import com.kw.readwith.domain.processing.ProcessingJob;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "서버 비동기 처리 job 상태")
public class ProcessingJobResponseDTO {
    private Long id;
    private Long bookId;
    private String bookTitle;
    private ProcessingPipelineType pipelineType;
    private String runId;
    private String sourceVersion;
    private String artifactPath;
    @Schema(description = "외부 처리 시스템의 job ID. 이미지 fan-out에서는 OpenAI Batch ID입니다.", nullable = true, example = "batch_abc123")
    private String externalJobId;
    @Schema(description = "OpenAI Batch 입력 JSONL file ID", nullable = true, example = "file-input123")
    private String inputFileId;
    @Schema(description = "OpenAI Batch 성공 결과 JSONL file ID", nullable = true, example = "file-output123")
    private String outputFileId;
    @Schema(description = "OpenAI Batch 실패 결과 JSONL file ID", nullable = true, example = "file-error123")
    private String errorFileId;
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
                .externalJobId(job.getExternalJobId())
                .inputFileId(job.getInputFileId())
                .outputFileId(job.getOutputFileId())
                .errorFileId(job.getErrorFileId())
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
