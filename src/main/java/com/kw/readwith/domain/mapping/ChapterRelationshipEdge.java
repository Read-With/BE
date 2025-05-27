package com.kw.readwith.domain.mapping;

import com.kw.readwith.domain.Book;
import com.kw.readwith.domain.Character;
import com.kw.readwith.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/* ─────────────── ChapterRelationshipEdge ────────────────────────── */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
@Table(indexes = {
        @Index(columnList = "book_id, chapter_idx, from_char_id, to_char_id", unique = true)
})
public class ChapterRelationshipEdge extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false)
    private Book book;

    @Column(name = "chapter_idx", nullable = false)
    private int chapterIdx;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "from_char_id", nullable = false)
    private Character fromCharacter;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "to_char_id", nullable = false)
    private Character toCharacter;

    private int cumulativeInteraction;
    private float sentimentWeightedSum;

    /* 실시간 가상 컬럼: cumulativeSentiment = sentimentWeightedSum / cumulativeInteraction */

    @Column(length = 7)
    private String edgeColorHex;

    private float edgeWidth;
}