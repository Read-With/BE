package com.kw.readwith.domain;

import com.kw.readwith.domain.cache.ChapterCharacterStat;
import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.mapping.CharacterPovSummary;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/* ─────────────── Chapter ────────────────────────── */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Table(indexes = @Index(columnList = "book_id, idx", unique = true))
public class Chapter extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Book book;

    @Column(nullable = false)
    private int idx;                   // 1-based

    @Column(length = 200)
    private String title;

    @Column(name = "spine_href", length = 255)
    private String spineHref;

    @Column(name = "paragraph_count")
    private Integer paragraphCount;

    @Lob
    @Column(name = "paragraph_starts_json", columnDefinition = "TEXT")
    private String paragraphStartsJson;

    @Lob
    @Column(name = "paragraph_lengths_json", columnDefinition = "TEXT")
    private String paragraphLengthsJson;

    @Column(name = "total_code_points")
    private Integer totalCodePoints;

    private int pageStart;
    private int pageEnd;
    private int startPos;
    private int endPos;

    @Lob @Column(columnDefinition = "TEXT")
    private String rawText;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "summary_upload_url", length = 255)
    private String summaryUploadUrl;

    @Column(nullable = false)
    private boolean povSummariesCached;

    /* 관계 */
    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Event> events = new ArrayList<>();

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ChapterCharacterStat> charStats = new ArrayList<>();

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL)
    @Builder.Default
    private List<CharacterPovSummary> povSummaries = new ArrayList<>();

    /**
     * 비즈니스 로직
     */
    public void markAsSummarized() {
        this.povSummariesCached = true;
        this.summaryUploadUrl = null; // JSON을 직접 파싱하여 저장하므로 URL은 null로 처리
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public void deleteSummary() {
        this.summaryText = null;
        this.summaryUploadUrl = null;
    }

    public void markPovSummariesAsUncached() {
        this.povSummariesCached = false;
    }
}
