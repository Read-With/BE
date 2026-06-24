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
    private Long id;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 120, nullable = false)
    private String author;

    @Column(length = 10, nullable = false)
    private String language;

    @Column(nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    @Builder.Default
    private boolean summary = false;

    @Column(name = "cover_img_url", length = 255)
    private String coverImgUrl;

    @Column(name = "summary_url", length = 255)
    private String summaryUrl;

    @Column(name = "book_prompt", columnDefinition = "TEXT")
    private String bookPrompt;

    @Column(name = "epub_path", length = 255)
    private String epubPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "normalization_status", length = 30)
    @Builder.Default
    private NormalizationStatus normalizationStatus = NormalizationStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 30)
    @Builder.Default
    private AnalysisStatus analysisStatus = AnalysisStatus.NONE;

    @Column(name = "rule_version", length = 50)
    private String ruleVersion;

    @Column(name = "locator_version", length = 50)
    private String locatorVersion;

    @Column(name = "normalized_artifact_path", length = 255)
    private String normalizedArtifactPath;

    @Column(name = "normalization_run_id", length = 80)
    private String normalizationRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

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

    public void updateBookPrompt(String bookPrompt) {
        this.bookPrompt = bookPrompt;
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
