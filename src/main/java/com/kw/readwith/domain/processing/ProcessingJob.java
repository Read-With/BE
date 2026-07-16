package com.kw.readwith.domain.processing;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.ProcessingJobStatus;
import com.kw.readwith.domain.enums.ProcessingPipelineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "processing_job", indexes = {
        @Index(name = "idx_processing_job_book_pipeline", columnList = "book_id,pipeline_type"),
        @Index(name = "idx_processing_job_status", columnList = "status")
})
public class ProcessingJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // 처리 job 식별자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;   // 어떤 책의 작업인지 가리킴

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_type", nullable = false, length = 30)
    private ProcessingPipelineType pipelineType;   // 정규화인지 AI 분석인지 구분

    @Column(name = "run_id", nullable = false, length = 80)
    private String runId;   // 이번 실행 묶음 id

    @Column(name = "source_version", length = 80)
    private String sourceVersion;   // 어떤 원본 EPUB 기준으로 돌렸는지 표시

    @Column(name = "artifact_path", length = 255)
    private String artifactPath;   // 결과물이 저장된 루트 경로

    @Column(name = "external_job_id", length = 120)
    private String externalJobId;

    @Column(name = "input_file_id", length = 120)
    private String inputFileId;

    @Column(name = "output_file_id", length = 120)
    private String outputFileId;

    @Column(name = "error_file_id", length = 120)
    private String errorFileId;

    @Column(name = "rule_version", length = 50)
    private String ruleVersion;   // 이 job이 사용한 정규화 규칙 버전

    @Column(name = "locator_version", length = 50)
    private String locatorVersion;   // 이 job이 사용한 locator 버전

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProcessingJobStatus status;   // 현재 실행 상태

    @Column(name = "current_step", length = 80)
    private String currentStep;   // 어디 단계에서 멈췄는지 표시

    @Column(name = "failure_code", length = 80)
    private String failureCode;   // 실패 원인 분류 코드

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;   // 운영자가 바로 볼 실패 메시지

    @Column(name = "triggered_by", length = 80)
    private String triggeredBy;   // 업로드/재시도 같은 실행 트리거

    @Column(name = "started_at")
    private LocalDateTime startedAt;   // 실제 시작 시각

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;   // 성공/실패 종료 시각

    public void markProcessing(String currentStep) {
        this.status = ProcessingJobStatus.PROCESSING;
        this.currentStep = currentStep;
        this.startedAt = LocalDateTime.now();
        this.finishedAt = null;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markReady(String artifactPath, String currentStep) {
        this.status = ProcessingJobStatus.READY;
        this.currentStep = currentStep;
        this.artifactPath = artifactPath;
        this.finishedAt = LocalDateTime.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markFailed(String currentStep, String failureCode, String failureMessage) {
        this.status = ProcessingJobStatus.FAILED;
        this.currentStep = currentStep;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.finishedAt = LocalDateTime.now();
    }

    public void assignNormalizationVersion(String ruleVersion, String locatorVersion) {
        this.ruleVersion = ruleVersion;
        this.locatorVersion = locatorVersion;
    }

    public void markExternalJobSubmitted(String externalJobId, String inputFileId, String externalStatus) {
        this.externalJobId = externalJobId;
        this.inputFileId = inputFileId;
        this.status = ProcessingJobStatus.PROCESSING;
        this.currentStep = "openai_" + externalStatus;
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        this.finishedAt = null;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void updateExternalJobStatus(String externalStatus, String outputFileId, String errorFileId) {
        this.currentStep = "openai_" + externalStatus;
        this.outputFileId = outputFileId;
        this.errorFileId = errorFileId;
    }

    public void advanceProcessing(String currentStep) {
        this.status = ProcessingJobStatus.PROCESSING;
        this.currentStep = currentStep;
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }
}
