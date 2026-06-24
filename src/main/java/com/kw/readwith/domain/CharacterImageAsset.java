package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.CharacterImageAssetRole;
import com.kw.readwith.domain.enums.CharacterImageAssetStatus;
import com.kw.readwith.domain.enums.CharacterImageGenerationMode;
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

    @Column(name = "reference_version", nullable = false)
    @Builder.Default
    private int referenceVersion = 0;

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

    public void markStaleReference() {
        this.status = CharacterImageAssetStatus.STALE_REFERENCE;
    }

    public void markSuperseded() {
        this.status = CharacterImageAssetStatus.SUPERSEDED;
    }
}
