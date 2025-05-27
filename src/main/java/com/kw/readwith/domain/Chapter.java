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

    private int pageStart;
    private int pageEnd;
    private int startPos;
    private int endPos;

    @Lob @Column(columnDefinition = "TEXT")
    private String rawText;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summaryText;

    @Column(nullable = false)
    private boolean povSummariesCached;

    /* 관계 */
    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL)
    private List<Event> events = new ArrayList<>();

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL)
    private List<ChapterCharacterStat> charStats = new ArrayList<>();

    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL)
    private List<CharacterPovSummary> povSummaries = new ArrayList<>();
}