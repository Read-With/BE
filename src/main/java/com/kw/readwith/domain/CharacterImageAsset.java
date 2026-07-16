package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import com.kw.readwith.domain.enums.CharacterImageGenerationMode;
import com.kw.readwith.domain.processing.ProcessingJob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "character_image_asset")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CharacterImageAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_role", length = 30, nullable = false)
    private CharacterImageAssetRole assetRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_mode", length = 30, nullable = false)
    private CharacterImageGenerationMode generationMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_reference_asset_id")
    private CharacterImageAsset sourceReferenceAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processing_job_id")
    private ProcessingJob processingJob;

    @Column(name = "reference_version", nullable = false)
    @Builder.Default
    private int referenceVersion = 0;

    @Column(name = "slot_no")
    private Integer slotNo;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private CharacterImageAssetStatus status;

    @Column(name = "s3_url", length = 1024)
    private String s3Url;

    @Column(length = 80)
    private String model;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Lob
    @Column(name = "qa_result_json", columnDefinition = "TEXT")
    private String qaResultJson;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "openai_request_id", length = 120)
    private String openaiRequestId;

    @Column(name = "attempt_no", nullable = false)
    @Builder.Default
    private int attemptNo = 1;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public void generated(String s3Url, String model, String promptHash, String openaiRequestId) {
        this.s3Url = s3Url;
        this.model = model;
        this.promptHash = promptHash;
        this.openaiRequestId = openaiRequestId;
        this.status = CharacterImageAssetStatus.QA_PENDING;
    }

    public void markQaPassed(String qaResultJson) {
        this.qaResultJson = qaResultJson;
        this.status = CharacterImageAssetStatus.QA_PASSED;
        this.failureCode = null;
    }

    public void markQaFailed(String qaResultJson, String failureCode) {
        this.qaResultJson = qaResultJson;
        this.failureCode = failureCode;
        this.status = CharacterImageAssetStatus.QA_FAILED;
    }

    public void approve() {
        this.status = CharacterImageAssetStatus.APPROVED;
    }

    public void reject() {
        this.status = CharacterImageAssetStatus.REJECTED;
    }

    public void publish() {
        this.status = CharacterImageAssetStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void fail(String failureCode) {
        this.failureCode = failureCode;
        this.status = CharacterImageAssetStatus.FAILED;
    }

    public void retryResultApplication() {
        this.failureCode = null;
        this.status = CharacterImageAssetStatus.GENERATING;
    }

    public void markStaleReference() {
        this.status = CharacterImageAssetStatus.STALE_REFERENCE;
    }

    public void markSuperseded() {
        this.status = CharacterImageAssetStatus.SUPERSEDED;
    }

    public void beginReferenceCandidate(Character referenceCharacter, int slotNo) {
        this.character = referenceCharacter;
        this.assetRole = CharacterImageAssetRole.REFERENCE_CANDIDATE;
        this.generationMode = CharacterImageGenerationMode.TEXT_TO_IMAGE;
        this.sourceReferenceAsset = null;
        this.referenceVersion = 0;
        this.slotNo = slotNo;
        this.status = CharacterImageAssetStatus.GENERATING;
        this.model = null;
        this.promptHash = null;
        this.qaResultJson = null;
        this.failureCode = null;
        this.openaiRequestId = null;
        this.publishedAt = null;
        this.attemptNo += 1;
    }

    public void beginCharacterImage(Character targetCharacter,
                                    CharacterImageAsset sourceReferenceAsset,
                                    int referenceVersion) {
        this.character = targetCharacter;
        this.assetRole = CharacterImageAssetRole.CHARACTER_IMAGE;
        this.generationMode = CharacterImageGenerationMode.REFERENCE_EDIT;
        this.sourceReferenceAsset = sourceReferenceAsset;
        this.processingJob = null;
        this.referenceVersion = referenceVersion;
        this.slotNo = null;
        this.status = CharacterImageAssetStatus.GENERATING;
        this.model = null;
        this.promptHash = null;
        this.qaResultJson = null;
        this.failureCode = null;
        this.openaiRequestId = null;
        this.publishedAt = null;
        this.attemptNo += 1;
    }

    public void assignProcessingJob(ProcessingJob processingJob, String model, String promptHash) {
        this.processingJob = processingJob;
        this.model = model;
        this.promptHash = promptHash;
    }
}
