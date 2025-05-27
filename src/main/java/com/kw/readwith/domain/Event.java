package com.kw.readwith.domain;

import com.kw.readwith.domain.cache.EventCharacterStat;
import com.kw.readwith.domain.common.BaseEntity;
import com.kw.readwith.domain.mapping.EventRelationshipEdge;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/* ─────────────── Event ────────────────────────── */
@Entity @Table(name = "book_event",
        indexes = @Index(columnList = "chapter_id, idx", unique = true))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class Event extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Chapter chapter;

    @Column(nullable = false)
    private int idx;          // within chapter

    private int pageStart;
    private int pageEnd;
    private int startPos;
    private int endPos;

    @Lob @Column(columnDefinition = "TEXT")
    private String rawText;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summaryText;

    @Lob
    @Column(columnDefinition = "LONGBLOB")   // 🔄 vector → LONGBLOB
    private byte[] embeddingVector; // byte[] 직렬화

    /* 관계 */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<EventCharacterStat> charStats = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<EventRelationshipEdge> edges = new ArrayList<>();
}