package com.kw.readwith.domain;

import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.mapping.ChapterRelationshipEdge;
import com.kw.readwith.domain.mapping.UserReadState;
import com.kw.readwith.domain.mapping.Favorite;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class Book extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 120, nullable = false)
    private String author;

    @Column(length = 10, nullable = false)
    private String language;   // ISO-639 code (e.g., "ko", "en", "en-US")

    @Column(nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    @Builder.Default
    private boolean summary = false;

    @Column(name = "cover_img_url", length = 255)
    private String coverImgUrl;

    @Column(name = "summary_url", length = 255)
    private String summaryUrl;

    @Column(name = "epub_path", length = 255)
    private String epubPath;

    @Column(name = "normalization_status", length = 30)
    private String normalizationStatus;

    @Column(name = "rule_version", length = 50)
    private String ruleVersion;

    @Column(name = "locator_version", length = 50)
    private String locatorVersion;

    @Column(name = "normalized_artifact_path", length = 255)
    private String normalizedArtifactPath;

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
}
