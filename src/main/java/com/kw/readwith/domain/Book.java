package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.enums.AnalysisStatus;
import com.kw.readwith.domain.enums.NormalizationStatus;
import com.kw.readwith.domain.mapping.ChapterRelationshipEdge;
import com.kw.readwith.domain.mapping.Favorite;
import com.kw.readwith.domain.mapping.UserReadState;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Book extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // 책 PK

    @Column(length = 200, nullable = false)
    private String title;   // 책 목록/리더에 보여줄 제목

    @Column(length = 120, nullable = false)
    private String author;   // 책 목록/리더에 보여줄 저자

    @Column(length = 10, nullable = false)
    private String language;   // 검색/표시에 쓰는 언어 코드

    @Column(nullable = false)
    private boolean isDefault;   // 기본 제공 책인지 구분

    @Column(nullable = false)
    @Builder.Default
    private boolean summary = false;   // 요약 자료 준비 여부(legacy 호환)

    @Column(name = "cover_img_url", length = 255)
    private String coverImgUrl;   // 책 카드 커버 이미지

    @Column(name = "summary_url", length = 255)
    private String summaryUrl;   // 책 전체 요약 리소스 경로

    @Column(name = "epub_path", length = 255)
    private String epubPath;   // 업로드한 원본 EPUB 경로

    @Enumerated(EnumType.STRING)
    @Column(name = "normalization_status", length = 30)
    @Builder.Default
    private NormalizationStatus normalizationStatus = NormalizationStatus.NONE;   // 본문 읽기 가능 상태

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 30)
    @Builder.Default
    private AnalysisStatus analysisStatus = AnalysisStatus.NONE;   // AI 분석 자료 준비 상태

    @Column(name = "rule_version", length = 50)
    private String ruleVersion;   // active 정규화 규칙 버전

    @Column(name = "locator_version", length = 50)
    private String locatorVersion;   // active locator 계산 버전

    @Column(name = "normalized_artifact_path", length = 255)
    private String normalizedArtifactPath;   // active 정규화 산출물 루트 경로

    @Column(name = "normalization_run_id", length = 80)
    private String normalizationRunId;   // 현재 reader가 보는 active 정규화 run id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;   // null == 서버 기본 제공

    /* 관계 */
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Chapter> chapters = new ArrayList<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Character> characters = new ArrayList<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ChapterRelationshipEdge> chapterEdges = new ArrayList<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    private List<UserReadState> readStates = new ArrayList<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Favorite> favorites = new ArrayList<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Bookmark> bookmarks = new ArrayList<>();

    /**
     * 비즈니스 로직
     */
    public void updateSummary(String summaryUrl) {
        this.summary = true;
        this.summaryUrl = summaryUrl;
    }

    public void completeSummary() {
        this.summary = true;
    }

    public void assignUploadedSource(String epubPath) {
        this.epubPath = epubPath;
    }

    public void updateCoverImage(String coverImgUrl) {
        this.coverImgUrl = coverImgUrl;
    }

    public void markNormalizationQueued() {
        if (!hasActiveNormalization()) {
            this.normalizationStatus = NormalizationStatus.QUEUED;
        }
    }

    public void markNormalizationProcessing() {
        if (!hasActiveNormalization()) {
            this.normalizationStatus = NormalizationStatus.PROCESSING;
        }
    }

    public void markNormalizationReady(String ruleVersion, String locatorVersion, String normalizationRunId, String normalizedArtifactPath) {
        this.normalizationStatus = NormalizationStatus.READY;
        this.ruleVersion = ruleVersion;
        this.locatorVersion = locatorVersion;
        this.normalizationRunId = normalizationRunId;
        this.normalizedArtifactPath = normalizedArtifactPath;
    }

    public void markNormalizationFailed() {
        if (!hasActiveNormalization()) {
            this.normalizationStatus = NormalizationStatus.FAILED;
        }
    }

    public void resetAnalysisStatus() {
        this.analysisStatus = AnalysisStatus.NONE;
        this.summary = false;
    }

    public void markAnalysisReady() {
        this.analysisStatus = AnalysisStatus.READY;
        this.summary = true;
    }

    public void markAnalysisRejected() {
        this.analysisStatus = AnalysisStatus.REJECTED;
        this.summary = false;
    }

    public boolean isNormalizationReady() {
        return this.normalizationStatus == NormalizationStatus.READY;
    }

    public boolean isAnalysisReady() {
        return this.analysisStatus == AnalysisStatus.READY;
    }

    public boolean hasActiveNormalization() {
        return this.normalizationStatus == NormalizationStatus.READY
                && this.normalizationRunId != null
                && !this.normalizationRunId.isBlank()
                && this.normalizedArtifactPath != null
                && !this.normalizedArtifactPath.isBlank();
    }
}
